use serde::Serialize;
use std::{
    collections::BTreeMap,
    path::PathBuf,
    sync::Arc,
    time::{SystemTime, UNIX_EPOCH},
};
use tauri::{Manager, ipc::Channel};
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::TcpStream,
    process::{Child, Command},
    sync::{Mutex, Notify},
};

const ADB_ADDRESS: &str = "127.0.0.1:5037";

fn now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("系统时间早于 Unix epoch")
        .as_millis() as u64
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Field {
    pub status: &'static str,
    pub value: String,
    pub source: String,
    pub collected_at: u64,
}

fn field(status: &'static str, value: impl Into<String>, source: impl Into<String>) -> Field {
    Field {
        status,
        value: value.into(),
        source: source.into(),
        collected_at: now(),
    }
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Device {
    pub id: String,
    pub serial: String,
    pub transport_id: Option<String>,
    pub connection: String,
    pub mode: Field,
    pub fields: BTreeMap<String, Field>,
    pub observed_at: u64,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Snapshot {
    pub devices: Vec<Device>,
    pub selected_id: Option<String>,
    pub adb_error: Option<String>,
    pub fastboot_error: Option<String>,
    pub server: String,
    pub tools_path: String,
    pub scanned_at: Option<u64>,
}

struct State {
    snapshot: Snapshot,
    subscribers: Vec<Channel<Snapshot>>,
}

impl State {
    fn publish(&mut self) {
        self.subscribers
            .retain(|channel| channel.send(self.snapshot.clone()).is_ok());
    }
}

pub struct Devices {
    state: Mutex<State>,
    wake: Notify,
    tools: PathBuf,
}

impl Devices {
    pub fn new(app: &tauri::AppHandle) -> Result<Arc<Self>, tauri::Error> {
        let tools = if cfg!(debug_assertions) {
            PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("resources/platform-tools")
        } else {
            app.path().resource_dir()?.join("platform-tools")
        };
        Ok(Arc::new(Self {
            state: Mutex::new(State {
                snapshot: Snapshot {
                    devices: vec![],
                    selected_id: None,
                    adb_error: None,
                    fastboot_error: None,
                    server: "尚未查询 ADB server".into(),
                    tools_path: tools.display().to_string(),
                    scanned_at: None,
                },
                subscribers: vec![],
            }),
            wake: Notify::new(),
            tools,
        }))
    }

    pub async fn subscribe(&self, channel: Channel<Snapshot>) -> Result<(), String> {
        let mut state = self.state.lock().await;
        channel
            .send(state.snapshot.clone())
            .map_err(|error| error.to_string())?;
        state.subscribers.push(channel);
        Ok(())
    }

    pub async fn unsubscribe(&self, channel_id: u32) {
        self.state
            .lock()
            .await
            .subscribers
            .retain(|channel| channel.id() != channel_id);
    }

    pub async fn select(&self, id: Option<String>) -> Result<(), String> {
        let mut state = self.state.lock().await;
        if let Some(ref id) = id
            && !state.snapshot.devices.iter().any(|device| &device.id == id)
        {
            return Err("设备已断开，请重新选择".into());
        }
        state.snapshot.selected_id = id;
        state.publish();
        Ok(())
    }

    pub fn refresh(&self) {
        self.wake.notify_one();
    }

    pub async fn run(self: Arc<Self>) {
        // Keep only the foreground server we started. Dropping this child never kills a shared server.
        let mut owned_server: Option<Child> = None;
        loop {
            // A non-ADB program can occupy 5037 without replying to the protocol.
            let adb = match tokio::time::timeout(
                std::time::Duration::from_secs(20),
                self.scan_adb(&mut owned_server),
            )
            .await
            {
                Ok(result) => result,
                Err(_) => Err("ADB 扫描 20 秒未完成；端口占用或设备无响应，本轮结果已丢弃".into()),
            };
            let fastboot = self.scan_fastboot().await;
            let mut state = self.state.lock().await;
            let mut devices = Vec::new();
            match adb {
                Ok((found, server)) => {
                    devices.extend(found);
                    state.snapshot.adb_error = None;
                    state.snapshot.server = server;
                }
                Err(error) => {
                    state.snapshot.adb_error = Some(error);
                }
            }
            match fastboot {
                Ok(found) => {
                    devices.extend(found);
                    state.snapshot.fastboot_error = None;
                }
                Err(error) => {
                    state.snapshot.fastboot_error = Some(error);
                }
            }
            if !devices
                .iter()
                .any(|device| Some(&device.id) == state.snapshot.selected_id.as_ref())
            {
                state.snapshot.selected_id = None;
            }
            state.snapshot.devices = devices;
            state.snapshot.scanned_at = Some(now());
            state.publish();
            drop(state);
            // Fastboot has no device-change subscription. Periodic read-only enumeration also
            // recollects ADB metadata after authorization/offline transitions.
            let _ =
                tokio::time::timeout(std::time::Duration::from_secs(3), self.wake.notified()).await;
        }
    }

    fn command(&self, tool: &str) -> Command {
        let filename = if cfg!(windows) {
            format!("{tool}.exe")
        } else {
            tool.into()
        };
        let mut command = Command::new(self.tools.join(filename));
        command.kill_on_drop(true);
        #[cfg(windows)]
        command.creation_flags(0x08000000); // CREATE_NO_WINDOW
        command
    }

    async fn scan_adb(&self, owned: &mut Option<Child>) -> Result<(Vec<Device>, String), String> {
        let mut socket = match TcpStream::connect(ADB_ADDRESS).await {
            Ok(socket) => socket,
            Err(error) if error.kind() == std::io::ErrorKind::ConnectionRefused => {
                if let Some(child) = owned.as_mut() {
                    if let Some(status) = child.try_wait().map_err(|error| error.to_string())? {
                        return Err(format!(
                            "本应用 ADB server 已退出：{status}；请检查端口与工具后重新启动应用"
                        ));
                    }
                    return Err("本应用 ADB server 正在启动，等待下次扫描".into());
                }
                *owned = Some(
                    self.command("adb")
                        .args(["-L", "tcp:127.0.0.1:5037", "server", "nodaemon"])
                        .stdin(std::process::Stdio::null())
                        .stdout(std::process::Stdio::null())
                        .stderr(std::process::Stdio::inherit())
                        .spawn()
                        .map_err(|error| format!("启动随包 ADB server：{error}"))?,
                );
                return Err("未发现 ADB server；已启动本应用的前台 server，等待连接".into());
            }
            Err(error) => return Err(format!("连接 ADB server {ADB_ADDRESS}：{error}")),
        };
        request(&mut socket, "host:version").await?;
        let version = read_string(&mut socket).await?;
        if version != "0029" {
            return Err(format!(
                "ADB server 协议版本冲突：{version}，随包工具要求 0029（41）；未停止或替换现有 server。请在所属工具中处理后刷新。"
            ));
        }
        let server = format!(
            "{} · {ADB_ADDRESS} · 协议 41（不代表 server 二进制版本）",
            if owned.is_some() {
                "本应用前台 server"
            } else {
                "共享现有 server，归属外部工具"
            }
        );
        let listing = host_query("host:devices-l").await?;
        let mut devices = parse_adb_devices(&listing)?;
        for device in &mut devices {
            if matches!(
                device.connection.as_str(),
                "device" | "recovery" | "sideload"
            ) {
                self.collect_adb(device).await;
            }
        }
        Ok((devices, server))
    }

    async fn collect_adb(&self, device: &mut Device) {
        let properties = shell(device, "getprop").await;
        let names = [
            ("model", "ro.product.model"),
            ("product", "ro.product.device"),
            ("build", "ro.build.fingerprint"),
            ("android", "ro.build.version.release"),
            ("api", "ro.build.version.sdk"),
            ("hardwareSerial", "ro.serialno"),
        ];
        match properties {
            Ok(output) => match parse_properties(&output) {
                Ok(values) => {
                    for (name, key) in names {
                        device.fields.insert(
                            name.into(),
                            match values.get(key).filter(|value| !value.is_empty()) {
                                Some(value) => {
                                    field("provided", value, format!("adb shell getprop {key}"))
                                }
                                None => field(
                                    "notProvided",
                                    "设备未提供",
                                    format!("adb shell getprop {key}"),
                                ),
                            },
                        );
                    }
                }
                Err(error) => {
                    device.fields.insert(
                        "properties".into(),
                        field("readFailed", error, "adb shell getprop"),
                    );
                }
            },
            Err(error) => {
                for (name, key) in names {
                    device.fields.insert(
                        name.into(),
                        failed_field(&error, &format!("adb shell getprop {key}")),
                    );
                }
            }
        }
        for (name, command) in [("permission", "id"), ("kernel", "uname -r")] {
            let value = match shell(device, command).await {
                Ok(output) => field("provided", output.trim(), format!("adb shell {command}")),
                Err(error) => failed_field(&error, &format!("adb shell {command}")),
            };
            device.fields.insert(name.into(), value);
        }
    }

    async fn scan_fastboot(&self) -> Result<Vec<Device>, String> {
        let output = self
            .command("fastboot")
            .args(["devices", "-l"])
            .output()
            .await
            .map_err(|error| format!("执行随包 Fastboot：{error}"))?;
        let listing = tool_output(output, "fastboot devices -l")?;
        let mut serials = std::collections::BTreeSet::new();
        for line in listing.lines().filter(|line| !line.trim().is_empty()) {
            let words: Vec<_> = line.split_whitespace().collect();
            if words.len() < 2 || words[1] != "fastboot" {
                return Err(format!("无法解析 Fastboot 设备行：{line}"));
            }
            if !serials.insert(words[0]) {
                return Err(format!(
                    "Fastboot 序列号重复，无法唯一指定目标：{}",
                    words[0]
                ));
            }
        }
        let mut devices = Vec::new();
        for serial in serials {
            let mode = match self.fastboot_variable(serial, "is-userspace").await {
                Ok(value) if value == "yes" => {
                    field("provided", "fastbootd", "fastboot getvar is-userspace")
                }
                Ok(value) if value == "no" => field(
                    "provided",
                    "bootloaderFastboot",
                    "fastboot getvar is-userspace",
                ),
                Ok(value) => field(
                    "readFailed",
                    format!("未识别的 is-userspace：{value}"),
                    "fastboot getvar is-userspace",
                ),
                Err(error) => failed_field(&error, "fastboot getvar is-userspace"),
            };
            let mut fields = BTreeMap::new();
            for (name, variable) in [
                ("product", "product"),
                ("hardwareSerial", "serialno"),
                ("unlocked", "unlocked"),
            ] {
                let value = match self.fastboot_variable(serial, variable).await {
                    Ok(value) if value.is_empty() => field(
                        "notProvided",
                        "设备未提供",
                        format!("fastboot getvar {variable}"),
                    ),
                    Ok(value) => field("provided", value, format!("fastboot getvar {variable}")),
                    Err(error) => failed_field(&error, &format!("fastboot getvar {variable}")),
                };
                fields.insert(name.into(), value);
            }
            devices.push(Device {
                id: format!("fastboot:{serial}"),
                serial: serial.into(),
                transport_id: None,
                connection: "fastboot".into(),
                mode,
                fields,
                observed_at: now(),
            });
        }
        Ok(devices)
    }

    async fn fastboot_variable(&self, serial: &str, name: &str) -> Result<String, String> {
        // Fastboot waits indefinitely for a disconnected -s target. A read-only probe
        // must end so unplugging a device cannot freeze subsequent discovery.
        let output = tokio::time::timeout(
            std::time::Duration::from_secs(5),
            self.command("fastboot")
                .args(["-s", serial, "getvar", name])
                .output(),
        )
        .await
        .map_err(|_| format!("Fastboot {serial} getvar {name}：5 秒内未收到响应，已结束读取进程"))?
        .map_err(|error| error.to_string())?;
        parse_fastboot_variable(
            &tool_output(output, &format!("fastboot -s {serial} getvar {name}"))?,
            name,
        )
    }
}

fn failed_field(error: &str, source: &str) -> Field {
    field(
        if error.to_ascii_lowercase().contains("permission denied") {
            "permissionDenied"
        } else {
            "readFailed"
        },
        error,
        source,
    )
}

fn tool_output(output: std::process::Output, operation: &str) -> Result<String, String> {
    let stdout = String::from_utf8(output.stdout).map_err(|error| error.to_string())?;
    let stderr = String::from_utf8(output.stderr).map_err(|error| error.to_string())?;
    if !output.status.success() {
        return Err(format!(
            "{operation}：{}\n{stdout}\n{stderr}",
            output.status
        ));
    }
    Ok(format!("{stdout}\n{stderr}"))
}

async fn request(socket: &mut TcpStream, service: &str) -> Result<(), String> {
    socket
        .write_all(format!("{:04x}{service}", service.len()).as_bytes())
        .await
        .map_err(|error| error.to_string())?;
    let mut status = [0; 4];
    socket
        .read_exact(&mut status)
        .await
        .map_err(|error| format!("ADB {service} 响应：{error}"))?;
    match &status {
        b"OKAY" => Ok(()),
        b"FAIL" => Err(format!("ADB {service}：{}", read_string(socket).await?)),
        _ => Err(format!("ADB {service} 非法状态：{status:?}")),
    }
}

async fn read_string(socket: &mut TcpStream) -> Result<String, String> {
    let mut length = [0; 4];
    socket
        .read_exact(&mut length)
        .await
        .map_err(|error| error.to_string())?;
    let length = usize::from_str_radix(
        std::str::from_utf8(&length).map_err(|error| error.to_string())?,
        16,
    )
    .map_err(|error| error.to_string())?;
    let mut value = vec![0; length];
    socket
        .read_exact(&mut value)
        .await
        .map_err(|error| error.to_string())?;
    String::from_utf8(value).map_err(|error| error.to_string())
}

async fn host_query(service: &str) -> Result<String, String> {
    let mut socket = TcpStream::connect(ADB_ADDRESS)
        .await
        .map_err(|error| error.to_string())?;
    request(&mut socket, service).await?;
    read_string(&mut socket).await
}

async fn shell(device: &Device, command: &str) -> Result<String, String> {
    // Offline transitions can leave an existing transport socket without an exit packet.
    tokio::time::timeout(
        std::time::Duration::from_secs(5),
        shell_read(device, command),
    )
    .await
    .map_err(|_| format!("ADB shell {command}：5 秒内未完成读取，连接已关闭"))?
}

async fn shell_read(device: &Device, command: &str) -> Result<String, String> {
    let transport = device
        .transport_id
        .as_ref()
        .expect("ADB 枚举必须包含 transport_id");
    let mut socket = TcpStream::connect(ADB_ADDRESS)
        .await
        .map_err(|error| error.to_string())?;
    request(&mut socket, &format!("host:transport-id:{transport}")).await?;
    request(&mut socket, &format!("shell,v2,raw:{command}")).await?;
    let mut stdout = Vec::new();
    let mut stderr = Vec::new();
    loop {
        let mut header = [0; 5];
        socket
            .read_exact(&mut header)
            .await
            .map_err(|error| format!("ADB shell {command}：{error}"))?;
        let length = u32::from_le_bytes(header[1..5].try_into().expect("四字节长度")) as usize;
        // ADB shell protocol permits at most 1 MiB per packet.
        if length > 1024 * 1024 {
            return Err(format!("ADB shell 非法包长度：{length}"));
        }
        let mut payload = vec![0; length];
        socket
            .read_exact(&mut payload)
            .await
            .map_err(|error| error.to_string())?;
        match header[0] {
            1 => stdout.extend(payload),
            2 => stderr.extend(payload),
            3 => {
                if payload.len() != 1 {
                    return Err(format!("ADB shell 非法退出包：{payload:?}"));
                }
                if payload[0] != 0 {
                    return Err(format!(
                        "ADB shell {command} 退出 {}：{}",
                        payload[0],
                        String::from_utf8_lossy(&stderr)
                    ));
                }
                return String::from_utf8(stdout).map_err(|error| error.to_string());
            }
            id => return Err(format!("ADB shell 未识别的包类型：{id}")),
        }
    }
}

fn parse_adb_devices(output: &str) -> Result<Vec<Device>, String> {
    output
        .lines()
        .filter(|line| !line.trim().is_empty())
        .map(|line| {
            let words: Vec<_> = line.split_whitespace().collect();
            if words.len() < 2 {
                return Err(format!("无法解析 ADB 设备行：{line}"));
            }
            let mut fields = BTreeMap::new();
            let mut transport = None;
            for word in &words[2..] {
                if let Some((name, value)) = word.split_once(':') {
                    if name == "transport_id" {
                        transport = Some(value.to_string());
                    }
                    if matches!(name, "usb" | "model" | "product" | "device") {
                        fields.insert(
                            format!("adb_{name}"),
                            field("provided", value, "adb host:devices-l"),
                        );
                    }
                }
            }
            let transport =
                transport.ok_or_else(|| format!("ADB 设备缺少 transport_id：{line}"))?;
            Ok(Device {
                id: format!("adb:{}:{transport}", words[0]),
                serial: words[0].into(),
                transport_id: Some(transport),
                connection: words[1].into(),
                mode: field(
                    "provided",
                    "adb",
                    "adb host:devices-l（具体状态见 connection）",
                ),
                fields,
                observed_at: now(),
            })
        })
        .collect()
}

fn parse_properties(output: &str) -> Result<BTreeMap<String, String>, String> {
    // Property values (e.g. persist.sys.boot.reason.history on MIUI) contain newlines.
    output
        .trim()
        .strip_prefix('[')
        .ok_or_else(|| "getprop 缺少起始分隔符".to_string())?
        .split("\n[")
        .map(|line| {
            let pair = line
                .strip_suffix(']')
                .and_then(|line| line.split_once("]: ["));
            pair.map(|(key, value)| (key.into(), value.into()))
                .ok_or_else(|| format!("getprop 非法输出：{line}"))
        })
        .collect()
}

fn parse_fastboot_variable(output: &str, name: &str) -> Result<String, String> {
    let prefix = format!("{name}:");
    let values: Vec<_> = output
        .lines()
        .filter_map(|line| {
            line.trim()
                .strip_prefix("(bootloader) ")
                .unwrap_or(line.trim())
                .strip_prefix(&prefix)
                .map(str::trim)
        })
        .collect();
    match values.as_slice() {
        [value] => Ok((*value).into()),
        _ => Err(format!("Fastboot 未返回唯一的 {name}：{output}")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn multiline_property_does_not_discard_device_build() {
        let properties = parse_properties("[persist.sys.boot.reason.history]: [bootloader,1794292290\nreboot,1794292100]\n[ro.build.fingerprint]: [Xiaomi/cannon/build]\n").unwrap();
        assert_eq!(properties["ro.build.fingerprint"], "Xiaomi/cannon/build");
        assert_eq!(
            properties["persist.sys.boot.reason.history"],
            "bootloader,1794292290\nreboot,1794292100"
        );
    }
}

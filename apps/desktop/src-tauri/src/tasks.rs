use crate::devices::{Device, Devices};
use serde::{Deserialize, Serialize};
use std::{
    collections::BTreeMap,
    fs,
    io::Write,
    path::PathBuf,
    process::Stdio,
    sync::Arc,
    time::{SystemTime, UNIX_EPOCH},
};
use tauri::ipc::Channel;
use tokio::{
    io::{AsyncRead, AsyncReadExt},
    process::Command,
    sync::{Mutex, watch},
};

fn now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("系统时间早于 epoch")
        .as_millis() as u64
}

#[derive(Clone, Copy, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum Mode {
    Adb,
    BootloaderFastboot,
    Fastbootd,
    Recovery,
}

impl Mode {
    fn matches(self, device: &Device) -> bool {
        match self {
            Self::Adb => device.connection == "device" && device.mode.value == "adb",
            Self::Recovery => device.connection == "recovery",
            Self::BootloaderFastboot => {
                device.mode.status == "provided" && device.mode.value == "bootloaderFastboot"
            }
            Self::Fastbootd => device.mode.status == "provided" && device.mode.value == "fastbootd",
        }
    }
}

#[derive(Clone, Deserialize, Serialize)]
#[serde(tag = "kind", rename_all = "camelCase")]
pub enum Operation {
    Screenshot,
    Reboot { target: Mode },
}

#[derive(Clone, Copy, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum Status {
    Preparing,
    Running,
    WaitingDevice,
    WaitingConfirmation,
    Verifying,
    Succeeded,
    Failed,
    Cancelled,
    Interrupted,
}

impl Status {
    fn active(self) -> bool {
        matches!(
            self,
            Self::Preparing
                | Self::Running
                | Self::WaitingDevice
                | Self::WaitingConfirmation
                | Self::Verifying
        )
    }
}

#[derive(Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Artifact {
    pub path: String,
    pub bytes: u64,
    pub kind: String,
}

#[derive(Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Task {
    pub id: String,
    pub device: Device,
    pub operation: Operation,
    pub status: Status,
    pub phase: String,
    pub cancellable: bool,
    pub verified: bool,
    pub verification: String,
    pub observed_device: Option<Device>,
    pub created_at: u64,
    pub updated_at: u64,
    pub exit_code: Option<i32>,
    pub command_finished_at: Option<u64>,
    pub reason: Option<String>,
    pub tool_version: String,
    pub tool_path: String,
    pub artifacts: Vec<Artifact>,
    pub log_directory: String,
    pub persistence_error: Option<String>,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Snapshot {
    pub sequence: u64,
    pub tasks: Vec<Task>,
}

struct State {
    sequence: u64,
    tasks: BTreeMap<String, Task>,
    subscribers: Vec<Channel<Snapshot>>,
    cancellation: BTreeMap<String, watch::Sender<bool>>,
    leases: BTreeMap<String, String>,
}

impl State {
    fn snapshot(&self) -> Snapshot {
        Snapshot {
            sequence: self.sequence,
            tasks: self.tasks.values().rev().cloned().collect(),
        }
    }
    fn publish(&mut self) {
        self.sequence += 1;
        let snapshot = self.snapshot();
        self.subscribers
            .retain(|channel| channel.send(snapshot.clone()).is_ok());
    }
}

pub struct Tasks {
    state: Mutex<State>,
    directory: PathBuf,
    devices: Arc<Devices>,
}

impl Tasks {
    pub fn new(directory: PathBuf, devices: Arc<Devices>) -> Result<Arc<Self>, String> {
        fs::create_dir_all(&directory).map_err(|e| format!("创建任务目录：{e}"))?;
        let mut tasks = BTreeMap::new();
        for entry in fs::read_dir(&directory).map_err(|e| e.to_string())? {
            let path = entry
                .map_err(|e| e.to_string())?
                .path()
                .join("summary.json");
            if !path.exists() {
                continue;
            } // A crash can happen before the first summary is committed.
            let mut task: Task =
                serde_json::from_slice(&fs::read(&path).map_err(|e| e.to_string())?)
                    .map_err(|e| format!("{}：{e}", path.display()))?;
            if task.status.active() {
                task.status = Status::Interrupted;
                task.phase = "上次进程异常退出；请重新读取设备状态，不自动续写".into();
                task.cancellable = false;
                task.verified = false;
                task.verification = "中断后尚未重新验证设备".into();
                task.reason = Some("应用退出时任务未结束；取消或进程退出不表示设备回退".into());
                task.updated_at = now();
                persist(&task)?;
            }
            tasks.insert(task.id.clone(), task);
        }
        Ok(Arc::new(Self {
            directory,
            devices,
            state: Mutex::new(State {
                sequence: 0,
                tasks,
                subscribers: vec![],
                cancellation: BTreeMap::new(),
                leases: BTreeMap::new(),
            }),
        }))
    }

    pub async fn subscribe(&self, channel: Channel<Snapshot>) -> Result<(), String> {
        let mut state = self.state.lock().await;
        channel.send(state.snapshot()).map_err(|e| e.to_string())?;
        state.subscribers.push(channel);
        Ok(())
    }

    pub async fn unsubscribe(&self, id: u32) {
        self.state
            .lock()
            .await
            .subscribers
            .retain(|channel| channel.id() != id);
    }

    pub async fn has_active(&self) -> bool {
        let state = self.state.lock().await;
        !state.leases.is_empty() || state.tasks.values().any(|task| task.status.active())
    }

    // All terminal/script/unlock entry points must hold this same native lease for their entire lifetime.
    // Releasing it is allowed only after their process/stream has exited, including cancellation.
    pub async fn acquire(&self, device: &Device, owner: &str) -> Result<(), String> {
        let mut state = self.state.lock().await;
        // A reboot may change serial. Until identity is resolved, a newly enumerated device
        // cannot safely be distinguished from that occupied target.
        if let Some(task) = state
            .tasks
            .values()
            .find(|task| task.status.active() && matches!(task.operation, Operation::Reboot { .. }))
        {
            return Err(format!(
                "任务 {} 正在跨模式确认身份，请先验证或结束等待，再创建新的设备会话",
                task.id
            ));
        }
        let keys = identity_keys(device);
        for key in &keys {
            if let Some(owner) = state.leases.get(key) {
                return Err(format!("设备被 {owner} 占用；请先结束该任务或会话"));
            }
        }
        for key in keys {
            state.leases.insert(key, owner.into());
        }
        Ok(())
    }

    pub async fn release(&self, owner: &str) {
        self.state
            .lock()
            .await
            .leases
            .retain(|_, value| value != owner);
    }

    pub async fn create(
        self: &Arc<Self>,
        device_id: String,
        operation: Operation,
    ) -> Result<String, String> {
        let device = self
            .devices
            .snapshot()
            .await
            .devices
            .into_iter()
            .find(|d| d.id == device_id)
            .ok_or("设备已断开，请重新选择")?;
        match operation {
            Operation::Screenshot if device.connection != "device" => {
                return Err("截图需要已授权的 Android ADB 设备".into());
            }
            Operation::Reboot { target } => {
                if !matches!(
                    device.connection.as_str(),
                    "device" | "recovery" | "fastboot"
                ) {
                    return Err("当前连接状态不能重启".into());
                }
                if target.matches(&device) {
                    return Err("设备已处于目标模式".into());
                }
                if device.connection == "fastboot" && target == Mode::Recovery {
                    return Err("尚未接入 Fastboot 到 Recovery 的可靠重启路径".into());
                }
            }
            Operation::Screenshot => {}
        }
        static NEXT_ID: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
        let id = format!(
            "{}-{}",
            now(),
            NEXT_ID.fetch_add(1, std::sync::atomic::Ordering::Relaxed)
        );
        self.acquire(&device, &id).await?;
        let directory = self.directory.join(&id);
        if let Err(e) = fs::create_dir(&directory) {
            self.release(&id).await;
            return Err(format!("创建任务记录：{e}"));
        }
        let tool = if device.connection == "fastboot" {
            "fastboot"
        } else {
            "adb"
        };
        let task = Task {
            id: id.clone(),
            device,
            operation,
            status: Status::Preparing,
            phase: "准备执行".into(),
            cancellable: true,
            verified: false,
            verification: "尚未验证".into(),
            observed_device: None,
            created_at: now(),
            updated_at: now(),
            exit_code: None,
            command_finished_at: None,
            reason: None,
            tool_version: "Platform-tools 36.0.2（随包固定版本）".into(),
            tool_path: self
                .devices
                .tools()
                .join(if cfg!(windows) {
                    format!("{tool}.exe")
                } else {
                    tool.into()
                })
                .display()
                .to_string(),
            artifacts: vec![],
            log_directory: directory.display().to_string(),
            persistence_error: None,
        };
        if let Err(e) = persist(&task) {
            self.release(&id).await;
            return Err(e);
        }
        let (sender, receiver) = watch::channel(false);
        let mut state = self.state.lock().await;
        state.tasks.insert(id.clone(), task.clone());
        state.cancellation.insert(id.clone(), sender);
        state.publish();
        drop(state);
        let service = self.clone();
        tauri::async_runtime::spawn(async move {
            service.execute(task, receiver).await;
        });
        Ok(id)
    }

    async fn update(&self, id: &str, change: impl FnOnce(&mut Task)) {
        let mut state = self.state.lock().await;
        let task = state.tasks.get_mut(id).expect("运行任务存在");
        change(task);
        task.updated_at = now();
        if let Err(e) = persist(task) {
            task.persistence_error = Some(e);
        }
        state.publish();
    }

    pub async fn cancel(&self, id: &str) -> Result<(), String> {
        let state = self.state.lock().await;
        let task = state.tasks.get(id).ok_or("任务不存在")?;
        if !task.cancellable {
            return Err("当前阶段不可取消".into());
        }
        state
            .cancellation
            .get(id)
            .expect("活跃任务有取消通道")
            .send(true)
            .map_err(|e| e.to_string())
    }

    async fn execute(self: Arc<Self>, task: Task, mut cancellation: watch::Receiver<bool>) {
        let result = self.perform(&task, &mut cancellation).await;
        match result {
            Ok(()) => {}
            Err(reason) => {
                self.update(&task.id, |t| {
                    t.status = Status::Failed;
                    t.phase = "执行失败".into();
                    t.reason = Some(reason);
                    t.cancellable = false;
                })
                .await;
            }
        }
        let status = self.state.lock().await.tasks[&task.id].status;
        if !matches!(status, Status::WaitingDevice | Status::WaitingConfirmation) {
            self.release(&task.id).await;
            self.state.lock().await.cancellation.remove(&task.id);
        }
    }

    async fn perform(
        &self,
        task: &Task,
        cancellation: &mut watch::Receiver<bool>,
    ) -> Result<(), String> {
        let destination = match task.operation {
            Operation::Screenshot => {
                self.update(&task.id, |t| t.phase = "选择截图保存位置".into())
                    .await;
                let file = rfd::AsyncFileDialog::new()
                    .set_title("保存设备截图")
                    .set_file_name("screenshot.png")
                    .add_filter("PNG", &["png"])
                    .save_file()
                    .await;
                if *cancellation.borrow() || file.is_none() {
                    self.cancelled(&task.id).await;
                    return Ok(());
                }
                Some(file.expect("已处理对话框取消").path().to_path_buf())
            }
            Operation::Reboot { .. } => None,
        };
        if *cancellation.borrow() {
            self.cancelled(&task.id).await;
            return Ok(());
        }
        let mut command = Command::new(&task.tool_path);
        command
            .stdin(Stdio::null())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .kill_on_drop(true);
        #[cfg(unix)]
        command.process_group(0);
        #[cfg(windows)]
        command.creation_flags(0x08000000);
        if let Some(transport) = &task.device.transport_id {
            command.args(["-t", transport]);
        } else {
            command.args(["-s", &task.device.serial]);
        }
        match task.operation {
            Operation::Screenshot => {
                command.args(["exec-out", "screencap", "-p"]);
            }
            Operation::Reboot { target } => {
                command.arg("reboot");
                match target {
                    Mode::Adb => {}
                    Mode::Recovery => {
                        command.arg("recovery");
                    }
                    Mode::Fastbootd => {
                        command.arg("fastboot");
                    }
                    Mode::BootloaderFastboot => {
                        command.arg("bootloader");
                    }
                }
            }
        }
        let stdout_path = PathBuf::from(&task.log_directory).join(if destination.is_some() {
            "screenshot.png"
        } else {
            "stdout.log"
        });
        // Create output files before launching, so a filesystem failure cannot leave a running child.
        let stdout_file =
            fs::File::create(&stdout_path).map_err(|e| format!("创建输出文件：{e}"))?;
        let stderr_file = fs::File::create(PathBuf::from(&task.log_directory).join("stderr.log"))
            .map_err(|e| format!("创建 stderr：{e}"))?;
        let mut child = command
            .spawn()
            .map_err(|e| format!("启动 {}：{e}", task.tool_path))?;
        let tree = match crate::task_process::ProcessTree::attach(&child) {
            Ok(tree) => tree,
            Err(error) => {
                child
                    .kill()
                    .await
                    .map_err(|e| format!("{error}；停止未受管进程：{e}"))?;
                return Err(error);
            }
        };
        let pid = child.id().expect("刚启动的子进程存在 PID");
        let stdout = child.stdout.take().expect("已配置 stdout 管道");
        let stderr = child.stderr.take().expect("已配置 stderr 管道");
        self.update(&task.id, |t| {
            t.status = Status::Running;
            t.phase = "执行设备命令".into();
        })
        .await;
        let out = tauri::async_runtime::spawn(copy_output(stdout, stdout_file));
        let err = tauri::async_runtime::spawn(copy_output(stderr, stderr_file));
        let (status, cancelled) = tokio::select! {
            biased;
            _ = async { let _ = cancellation.wait_for(|cancelled| *cancelled).await; } => {
                terminate_tree(pid).await?;
                (child.wait().await.map_err(|e| e.to_string())?, true)
            }
            status = child.wait() => (status.map_err(|e| format!("等待工具退出：{e}"))?, false),
        };
        self.update(&task.id, |t| {
            t.exit_code = status.code();
            t.command_finished_at = Some(now());
        })
        .await;
        tree.finish().await?;
        let stdout_result = out.await.map_err(|e| e.to_string())?;
        let stderr_result = err.await.map_err(|e| e.to_string())?;
        stdout_result?;
        stderr_result?;
        if cancelled {
            self.cancelled(&task.id).await;
            return Ok(());
        }
        if !status.success() {
            return Err(format!(
                "工具退出 {status}；具体输出见 {}",
                task.log_directory
            ));
        }
        match task.operation {
            Operation::Screenshot => {
                self.update(&task.id, |t| {
                    t.status = Status::Verifying;
                    t.phase = "校验 PNG 并保存产物".into();
                    t.cancellable = false;
                })
                .await;
                verify_png(&stdout_path)?;
                let raw_bytes = fs::metadata(&stdout_path).map_err(|e| e.to_string())?.len();
                self.update(&task.id, |t| {
                    t.artifacts.push(Artifact {
                        path: stdout_path.display().to_string(),
                        bytes: raw_bytes,
                        kind: "deviceScreenshot".into(),
                    })
                })
                .await;
                let destination = destination.expect("截图已选择保存路径");
                fs::copy(&stdout_path, &destination).map_err(|e| {
                    format!(
                        "保存 {}：{e}；原始截图保留在 {}",
                        destination.display(),
                        stdout_path.display()
                    )
                })?;
                let bytes = fs::metadata(&destination).map_err(|e| e.to_string())?.len();
                self.update(&task.id, |t| {
                    t.status = Status::Succeeded;
                    t.phase = "截图已保存".into();
                    t.verified = true;
                    t.verification = "真实设备截图 PNG 结构与目标文件保存已验证".into();
                    t.artifacts.push(Artifact {
                        path: destination.display().to_string(),
                        bytes,
                        kind: "screenshot".into(),
                    });
                })
                .await;
            }
            Operation::Reboot { .. } => {
                self.update(&task.id, |t| {
                    t.status = Status::WaitingDevice;
                    t.phase = "命令已结束，等待重新读取模式与身份；点击验证".into();
                })
                .await;
                self.devices.refresh();
            }
        }
        Ok(())
    }

    async fn cancelled(&self, id: &str) {
        self.update(id, |t| {
            t.status = Status::Cancelled;
            t.phase = "已取消；不表示设备回退".into();
            t.cancellable = false;
            t.verification = "取消后设备状态未验证，请刷新设备".into();
        })
        .await;
        self.devices.refresh();
    }

    pub async fn verify(&self, id: &str, confirmed_device: Option<String>) -> Result<(), String> {
        let task = self
            .state
            .lock()
            .await
            .tasks
            .get(id)
            .cloned()
            .ok_or("任务不存在")?;
        let target = match task.operation {
            Operation::Reboot { target } => target,
            Operation::Screenshot => return Err("截图在保存时验证".into()),
        };
        if !matches!(
            task.status,
            Status::WaitingDevice
                | Status::WaitingConfirmation
                | Status::Cancelled
                | Status::Interrupted
        ) {
            return Err("当前任务不处于待验证阶段".into());
        }
        let snapshot = self.devices.snapshot().await;
        let after = task
            .command_finished_at
            .ok_or("任务尚未发出重启命令，无法验证结果")?;
        let fresh: Vec<_> = snapshot
            .devices
            .into_iter()
            .filter(|d| d.observed_at > after && target.matches(d))
            .collect();
        let matched: Vec<_> = fresh
            .iter()
            .filter(|d| same_identity(&task.device, d))
            .collect();
        let observed = match confirmed_device {
            Some(id) => fresh
                .iter()
                .find(|d| d.id == id)
                .cloned()
                .ok_or("选择的设备已断开、观测已过期或尚未进入目标模式")?,
            None => match matched.as_slice() {
                [device] => (*device).clone(),
                _ => {
                    self.update(&task.id, |t| {
                        if t.status.active() {
                            t.status = Status::WaitingConfirmation;
                        }
                        t.phase = "未找到唯一关联身份；刷新后选择目标模式设备并人工确认".into();
                    })
                    .await;
                    return Ok(());
                }
            },
        };
        // A changed serial must acquire the new identity before the original lease is released.
        let mut state = self.state.lock().await;
        let current_status = state.tasks[id].status;
        if current_status != task.status {
            return Err("任务状态已变化，请重新验证".into());
        }
        for key in identity_keys(&observed) {
            if let Some(owner) = state.leases.get(&key)
                && owner != id
            {
                return Err(format!("目标设备被 {owner} 占用"));
            }
        }
        let current = state.tasks.get_mut(id).expect("任务存在");
        current.verified = true;
        current.verification = if same_identity(&task.device, &observed) {
            "已重读目标模式与唯一序列号身份".into()
        } else {
            "已重读目标模式；跨模式身份由用户明确确认".into()
        };
        current.observed_device = Some(observed);
        if current.status.active() {
            current.status = Status::Succeeded;
        }
        current.phase = "目标模式已验证（不代表 Bootloader 解锁或设备回退）".into();
        current.cancellable = false;
        current.updated_at = now();
        if let Err(e) = persist(current) {
            current.persistence_error = Some(e);
        }
        state.leases.retain(|_, owner| owner != id);
        state.cancellation.remove(id);
        state.publish();
        Ok(())
    }

    pub async fn cancel_waiting(&self, id: &str) -> Result<(), String> {
        let status = self
            .state
            .lock()
            .await
            .tasks
            .get(id)
            .map(|t| t.status)
            .ok_or("任务不存在")?;
        if matches!(status, Status::WaitingDevice | Status::WaitingConfirmation) {
            self.cancelled(id).await;
            self.release(id).await;
            self.state.lock().await.cancellation.remove(id);
            Ok(())
        } else {
            self.cancel(id).await
        }
    }

    pub async fn logs(&self, id: &str) -> Result<String, String> {
        let task = self
            .state
            .lock()
            .await
            .tasks
            .get(id)
            .cloned()
            .ok_or("任务不存在")?;
        let mut text = format!("任务 {} · {}\n", task.id, task.tool_version);
        for name in ["stdout.log", "stderr.log"] {
            let path = PathBuf::from(&task.log_directory).join(name);
            match fs::read_to_string(&path) {
                Ok(output) => text.push_str(&format!("\n[{name}]\n{output}")),
                Err(e)
                    if e.kind() == std::io::ErrorKind::NotFound
                        && task.status == Status::Preparing => {}
                Err(e)
                    if e.kind() == std::io::ErrorKind::NotFound
                        && name == "stdout.log"
                        && matches!(task.operation, Operation::Screenshot) =>
                {
                    text.push_str("\n[stdout] 二进制 PNG 保存在任务目录\n")
                }
                Err(e) => return Err(format!("读取 {}：{e}", path.display())),
            }
        }
        Ok(text)
    }

    pub async fn export(&self, id: &str, include_identifiers: bool) -> Result<(), String> {
        let task = self
            .state
            .lock()
            .await
            .tasks
            .get(id)
            .cloned()
            .ok_or("任务不存在")?;
        let logs = self.logs(id).await?;
        let mut report = format!(
            "彳亍工具箱诊断 · 主机接收时间为 Unix 毫秒，界面展示 JST\n设备标识：{}\n不包含截图文件内容；包含任务摘要、工具路径、退出原因、stdout/stderr 和产物路径。\n{}\n{logs}",
            if include_identifiers {
                "包含（用户明确选择）"
            } else {
                "已替换序列号、transport、USB 路径；自由文本仍应在分享前核对"
            },
            serde_json::to_string_pretty(&task).expect("Task 可序列化")
        );
        if !include_identifiers {
            let mut identifiers = Vec::new();
            for device in std::iter::once(&task.device).chain(task.observed_device.iter()) {
                identifiers.extend([device.id.clone(), device.serial.clone()]);
                if let Some(transport) = &device.transport_id {
                    identifiers.push(format!("\"transportId\": \"{transport}\""));
                }
                for name in ["hardwareSerial", "adb_usb"] {
                    if let Some(field) = device.fields.get(name)
                        && field.status == "provided"
                    {
                        identifiers.push(field.value.clone());
                    }
                }
            }
            identifiers.sort_by_key(|value| std::cmp::Reverse(value.len()));
            for identifier in identifiers.into_iter().filter(|value| !value.is_empty()) {
                report = report.replace(&identifier, "[设备标识已移除]");
            }
        }
        if let Some(file) = rfd::AsyncFileDialog::new()
            .set_title("导出任务诊断")
            .set_file_name("task-diagnostic.txt")
            .save_file()
            .await
        {
            fs::write(file.path(), report).map_err(|e| format!("保存诊断：{e}"))?;
        }
        Ok(())
    }
}

fn persist(task: &Task) -> Result<(), String> {
    let directory = PathBuf::from(&task.log_directory);
    let bytes = serde_json::to_vec_pretty(task).expect("Task 可序列化");
    let mut file =
        fs::File::create(directory.join("summary.next.json")).map_err(|e| e.to_string())?;
    file.write_all(&bytes)
        .and_then(|()| file.sync_all())
        .map_err(|e| e.to_string())?;
    fs::rename(
        directory.join("summary.next.json"),
        directory.join("summary.json"),
    )
    .map_err(|e| format!("保存任务摘要：{e}"))
}

fn identity_keys(device: &Device) -> Vec<String> {
    let mut keys = vec![format!("serial:{}", device.serial)];
    if let Some(field) = device.fields.get("hardwareSerial")
        && field.status == "provided"
        && !field.value.is_empty()
    {
        keys.push(format!("serial:{}", field.value));
    }
    keys
}

fn same_identity(first: &Device, second: &Device) -> bool {
    let keys = identity_keys(first);
    identity_keys(second).iter().any(|key| keys.contains(key))
}

async fn copy_output(mut stream: impl AsyncRead + Unpin, mut file: fs::File) -> Result<(), String> {
    let mut buffer = [0; 16384];
    let mut failure = None;
    loop {
        let count = stream.read(&mut buffer).await.map_err(|e| e.to_string())?;
        if count == 0 {
            break;
        }
        // Keep draining after a disk error so the child cannot deadlock on a full pipe.
        if failure.is_none()
            && let Err(e) = file.write_all(&buffer[..count])
        {
            failure = Some(e.to_string());
        }
    }
    if let Some(e) = failure {
        return Err(format!("写入工具输出：{e}"));
    }
    file.sync_all().map_err(|e| e.to_string())
}

fn verify_png(path: &std::path::Path) -> Result<(), String> {
    use std::io::Read;
    let mut file = fs::File::open(path).map_err(|e| e.to_string())?;
    let mut header = [0; 24];
    file.read_exact(&mut header)
        .map_err(|e| format!("截图不完整：{e}"))?;
    if &header[..8] != b"\x89PNG\r\n\x1a\n"
        || &header[12..16] != b"IHDR"
        || header[16..20] == [0; 4]
        || header[20..24] == [0; 4]
    {
        return Err("设备没有返回有效 PNG 截图".into());
    }
    use std::io::{Seek, SeekFrom};
    file.seek(SeekFrom::End(-12)).map_err(|e| e.to_string())?;
    let mut end = [0; 12];
    file.read_exact(&mut end).map_err(|e| e.to_string())?;
    if end != *b"\0\0\0\0IEND\xaeB`\x82" {
        return Err("截图 PNG 缺少结束块".into());
    }
    Ok(())
}

async fn terminate_tree(pid: u32) -> Result<(), String> {
    #[cfg(unix)]
    {
        // All task tools are started in a dedicated process group, never the shared ADB server.
        if unsafe { libc::kill(-(pid as i32), libc::SIGKILL) } != 0 {
            let e = std::io::Error::last_os_error();
            if e.raw_os_error() != Some(libc::ESRCH) {
                return Err(format!("停止任务进程组：{e}"));
            }
        }
    }
    #[cfg(windows)]
    {
        let status = Command::new("taskkill.exe")
            .args(["/PID", &pid.to_string(), "/T", "/F"])
            .creation_flags(0x08000000)
            .status()
            .await
            .map_err(|e| e.to_string())?;
        if !status.success() {
            return Err(format!("停止进程树失败：{status}"));
        }
    }
    Ok(())
}

use serde::Serialize;
use tokio::sync::Mutex;

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Snapshot {
    pub version: &'static str,
    pub platform: &'static str,
    pub arch: &'static str,
    pub task_service: ServiceStatus,
}

#[derive(Clone, Serialize)]
#[serde(tag = "status", rename_all = "camelCase")]
pub enum ServiceStatus {
    Ready { reason: &'static str },
}

pub struct Workspace(pub Mutex<Snapshot>);

impl Workspace {
    pub fn new() -> Self {
        Self(Mutex::new(Snapshot {
            version: env!("CARGO_PKG_VERSION"),
            platform: std::env::consts::OS,
            arch: std::env::consts::ARCH,
            task_service: ServiceStatus::Ready {
                reason: "原生任务服务已就绪；任务固定设备，切页后继续执行。",
            },
        }))
    }
}

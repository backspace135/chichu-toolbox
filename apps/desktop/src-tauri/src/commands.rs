use crate::devices::{Devices, Snapshot as DeviceSnapshot};
use crate::workspace::{Snapshot, Workspace};
use std::sync::Arc;

#[tauri::command]
pub async fn workspace_snapshot(state: tauri::State<'_, Workspace>) -> Result<Snapshot, ()> {
    Ok(state.0.lock().await.clone())
}

#[tauri::command]
pub async fn subscribe_devices(
    state: tauri::State<'_, Arc<Devices>>,
    channel: tauri::ipc::Channel<DeviceSnapshot>,
) -> Result<(), String> {
    state.subscribe(channel).await
}

#[tauri::command]
pub async fn select_device(
    state: tauri::State<'_, Arc<Devices>>,
    id: Option<String>,
) -> Result<(), String> {
    state.select(id).await
}

#[tauri::command]
pub async fn unsubscribe_devices(
    state: tauri::State<'_, Arc<Devices>>,
    channel_id: u32,
) -> Result<(), ()> {
    state.unsubscribe(channel_id).await;
    Ok(())
}

#[tauri::command]
pub fn refresh_devices(state: tauri::State<'_, Arc<Devices>>) {
    state.refresh();
}

#[tauri::command]
pub async fn subscribe_tasks(
    state: tauri::State<'_, Arc<crate::tasks::Tasks>>,
    channel: tauri::ipc::Channel<crate::tasks::Snapshot>,
) -> Result<(), String> {
    state.subscribe(channel).await
}

#[tauri::command]
pub async fn unsubscribe_tasks(
    state: tauri::State<'_, Arc<crate::tasks::Tasks>>,
    channel_id: u32,
) -> Result<(), ()> {
    state.unsubscribe(channel_id).await;
    Ok(())
}

#[tauri::command]
pub async fn create_task(
    state: tauri::State<'_, Arc<crate::tasks::Tasks>>,
    device_id: String,
    operation: crate::tasks::Operation,
) -> Result<String, String> {
    state.inner().create(device_id, operation).await
}

#[tauri::command]
pub async fn cancel_task(
    state: tauri::State<'_, Arc<crate::tasks::Tasks>>,
    id: String,
) -> Result<(), String> {
    state.cancel_waiting(&id).await
}

#[tauri::command]
pub async fn verify_task(
    state: tauri::State<'_, Arc<crate::tasks::Tasks>>,
    id: String,
    confirmed_device: Option<String>,
) -> Result<(), String> {
    state.verify(&id, confirmed_device).await
}

#[tauri::command]
pub async fn task_logs(
    state: tauri::State<'_, Arc<crate::tasks::Tasks>>,
    id: String,
) -> Result<String, String> {
    state.logs(&id).await
}

#[tauri::command]
pub async fn export_task(
    state: tauri::State<'_, Arc<crate::tasks::Tasks>>,
    id: String,
    include_identifiers: bool,
) -> Result<(), String> {
    state.export(&id, include_identifiers).await
}

#[tauri::command]
pub async fn inspect_unlock(
    state: tauri::State<'_, crate::unlock::Unlock>,
    devices: tauri::State<'_, Arc<Devices>>,
    tasks: tauri::State<'_, Arc<crate::tasks::Tasks>>,
    device_id: String,
) -> Result<crate::unlock::Assessment, String> {
    state.inspect(&devices, &tasks, &device_id).await
}

#[tauri::command]
pub async fn recheck_unlock(
    state: tauri::State<'_, crate::unlock::Unlock>,
    devices: tauri::State<'_, Arc<Devices>>,
    tasks: tauri::State<'_, Arc<crate::tasks::Tasks>>,
    scheme_id: String,
) -> Result<crate::unlock::ExecutionSnapshot, String> {
    state.recheck(&devices, &tasks, &scheme_id).await
}

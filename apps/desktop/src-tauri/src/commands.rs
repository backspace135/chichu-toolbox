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

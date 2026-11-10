use crate::workspace::{Snapshot, Workspace};

#[tauri::command]
pub async fn workspace_snapshot(state: tauri::State<'_, Workspace>) -> Result<Snapshot, ()> {
    Ok(state.0.lock().await.clone())
}

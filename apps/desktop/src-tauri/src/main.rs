#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod commands;
mod workspace;

fn main() {
    tauri::Builder::default()
        .manage(workspace::Workspace::new())
        .invoke_handler(tauri::generate_handler![commands::workspace_snapshot])
        .run(tauri::generate_context!())
        .expect("无法启动彳亍工具箱桌面工作区");
}

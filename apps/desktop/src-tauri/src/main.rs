#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod commands;
mod devices;
mod workspace;

struct DeviceWorker(tauri::async_runtime::JoinHandle<()>);

fn main() {
    tauri::Builder::default()
        .manage(workspace::Workspace::new())
        .setup(|app| {
            use tauri::Manager;
            let devices = devices::Devices::new(app.handle())?;
            app.manage(devices.clone());
            app.manage(DeviceWorker(tauri::async_runtime::spawn(devices.run())));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::workspace_snapshot,
            commands::subscribe_devices,
            commands::unsubscribe_devices,
            commands::select_device,
            commands::refresh_devices
        ])
        .build(tauri::generate_context!())
        .expect("无法启动彳亍工具箱桌面工作区")
        .run(|app, event| {
            if let tauri::RunEvent::Exit = event {
                use tauri::Manager;
                app.state::<DeviceWorker>().0.abort();
            }
        });
}

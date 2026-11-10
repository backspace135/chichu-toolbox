#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod commands;
mod devices;
mod task_process;
mod tasks;
mod workspace;

struct DeviceWorker(tauri::async_runtime::JoinHandle<()>);

fn main() {
    if task_process::run_watcher() {
        return;
    }
    tauri::Builder::default()
        .manage(workspace::Workspace::new())
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                use tauri::Manager;
                let tasks = window.state::<std::sync::Arc<tasks::Tasks>>();
                if tauri::async_runtime::block_on(tasks.has_active()) {
                    api.prevent_close();
                    tauri::async_runtime::spawn(async {
                        rfd::AsyncMessageDialog::new().set_title("任务尚未结束")
                            .set_description("任务中心仍有活动任务。请查看阶段，完成验证或取消后再关闭窗口；停止进程不表示设备回退。")
                            .show().await;
                    });
                }
            }
        })
        .setup(|app| {
            use tauri::Manager;
            let devices = devices::Devices::new(app.handle())?;
            let tasks = tasks::Tasks::new(app.path().app_local_data_dir()?.join("tasks"), devices.clone())?;
            app.manage(tasks);
            app.manage(devices.clone());
            app.manage(DeviceWorker(tauri::async_runtime::spawn(devices.run())));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::workspace_snapshot,
            commands::subscribe_devices,
            commands::unsubscribe_devices,
            commands::select_device,
            commands::refresh_devices,
            commands::subscribe_tasks,
            commands::unsubscribe_tasks,
            commands::create_task,
            commands::cancel_task,
            commands::verify_task,
            commands::task_logs,
            commands::export_task
        ])
        .build(tauri::generate_context!())
        .expect("无法启动彳亍工具箱桌面工作区")
        .run(|app, event| {
            if let tauri::RunEvent::ExitRequested { api, .. } = &event {
                use tauri::Manager;
                let tasks = app.state::<std::sync::Arc<tasks::Tasks>>();
                if tauri::async_runtime::block_on(tasks.has_active()) {
                    api.prevent_exit();
                    tauri::async_runtime::spawn(async {
                        rfd::AsyncMessageDialog::new().set_title("仍有设备任务运行")
                            .set_description("请在任务中心查看当前阶段，完成验证或取消可取消任务后再关闭。停止进程不表示设备回退。")
                            .set_buttons(rfd::MessageButtons::Ok).show().await;
                    });
                }
            }
            if let tauri::RunEvent::Exit = event {
                use tauri::Manager;
                app.state::<DeviceWorker>().0.abort();
            }
        });
}

use tokio::process::Child;

// macOS has no Linux PR_SET_PDEATHSIG. A separate native watcher holds a pipe;
// app death closes it and the watcher kills only the task's dedicated process group.
#[cfg(unix)]
pub struct ProcessTree {
    watcher: Child,
    pipe: Option<tokio::process::ChildStdin>,
}

#[cfg(unix)]
impl ProcessTree {
    pub fn attach(child: &Child) -> Result<Self, String> {
        use std::process::Stdio;
        let pid = child.id().expect("刚创建的进程有 PID");
        let mut watcher =
            tokio::process::Command::new(std::env::current_exe().map_err(|e| e.to_string())?)
                .args(["--task-process-watch", &pid.to_string()])
                .stdin(Stdio::piped())
                .stdout(Stdio::null())
                .stderr(Stdio::inherit())
                .spawn()
                .map_err(|e| format!("创建任务进程监护：{e}"))?;
        let pipe = watcher.stdin.take();
        Ok(Self { watcher, pipe })
    }
    pub async fn finish(mut self) -> Result<(), String> {
        drop(self.pipe.take());
        let status = self.watcher.wait().await.map_err(|e| e.to_string())?;
        if !status.success() {
            return Err(format!("进程监护退出：{status}"));
        }
        Ok(())
    }
}

#[cfg(unix)]
pub fn run_watcher() -> bool {
    use std::io::Read;
    let args: Vec<_> = std::env::args().collect();
    if args.get(1).map(String::as_str) != Some("--task-process-watch") {
        return false;
    }
    let pid: i32 = args
        .get(2)
        .expect("监护参数缺少 PID")
        .parse()
        .expect("监护 PID 必须为数字");
    assert!(pid > 1, "监护 PID 必须指向任务进程组");
    let mut byte = [0];
    let count = std::io::stdin().read(&mut byte).expect("读取监护管道失败");
    assert_eq!(count, 0, "监护管道只允许 EOF，不允许发送命令");
    if unsafe { libc::kill(-pid, libc::SIGKILL) } != 0 {
        let error = std::io::Error::last_os_error();
        assert_eq!(
            error.raw_os_error(),
            Some(libc::ESRCH),
            "停止任务进程组：{error}"
        );
    }
    true
}

#[cfg(windows)]
pub struct ProcessTree(windows_sys::Win32::Foundation::HANDLE);
#[cfg(windows)]
unsafe impl Send for ProcessTree {}

#[cfg(windows)]
impl ProcessTree {
    pub fn attach(child: &Child) -> Result<Self, String> {
        use windows_sys::Win32::{Foundation::CloseHandle, System::JobObjects::*};
        unsafe {
            let job = CreateJobObjectW(std::ptr::null(), std::ptr::null());
            if job.is_null() {
                return Err(format!("创建任务 Job：{}", std::io::Error::last_os_error()));
            }
            let mut info: JOBOBJECT_EXTENDED_LIMIT_INFORMATION = std::mem::zeroed();
            info.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
            if SetInformationJobObject(
                job,
                JobObjectExtendedLimitInformation,
                &info as *const _ as _,
                std::mem::size_of_val(&info) as u32,
            ) == 0
                || AssignProcessToJobObject(job, child.raw_handle().expect("子进程句柄") as _) == 0
            {
                let error = std::io::Error::last_os_error();
                CloseHandle(job);
                return Err(format!("绑定任务进程树：{error}"));
            }
            Ok(Self(job))
        }
    }
    pub async fn finish(self) -> Result<(), String> {
        drop(self);
        Ok(())
    }
}

#[cfg(windows)]
impl Drop for ProcessTree {
    fn drop(&mut self) {
        unsafe {
            windows_sys::Win32::Foundation::CloseHandle(self.0);
        }
    }
}

#[cfg(windows)]
pub fn run_watcher() -> bool {
    false
}

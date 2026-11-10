import { useState } from 'react';
import { Alert, Badge, Button, Checkbox, Code, Divider, Group, NativeSelect, Paper, Stack, Text } from '@mantine/core';
import { cancelTask, exportTask, taskLogs, verifyTask } from '../bridge/tasks';
import type { Task, TaskSnapshot } from '../bridge/tasks';
import type { DeviceSnapshot } from '../bridge/workspace';

const statuses: Record<Task['status'], string> = { preparing: '准备中', running: '执行中', waitingDevice: '等待设备', waitingConfirmation: '等待身份确认', verifying: '验证中', succeeded: '成功', failed: '失败', cancelled: '已取消', interrupted: '已中断' };
const jst = (time: number) => new Date(time).toLocaleString('zh-CN', { timeZone: 'Asia/Tokyo', hour12: false });

function TaskRecord({ task, devices }: { task: Task; devices: DeviceSnapshot | null }) {
  const [error, setError] = useState<string | null>(null);
  const [logs, setLogs] = useState<string | null>(null);
  const [confirmed, setConfirmed] = useState('');
  const [includeIdentifiers, setIncludeIdentifiers] = useState(false);
  const run = (work: Promise<unknown>) => { setError(null); work.catch((e: unknown) => setError(String(e))); };
  return <Paper withBorder p="md"><Stack gap="sm">
    <Group justify="space-between"><Text fw={600}>{task.operation.kind === 'screenshot' ? '屏幕截图' : `模式重启 → ${task.operation.target}`}</Text><Badge color={task.status === 'failed' ? 'red' : task.status === 'succeeded' ? 'green' : 'gray'}>{statuses[task.status]}</Badge></Group>
    <Text size="sm">固定设备：{task.device.serial}</Text><Text size="sm">{task.phase}</Text>
    <Text size="sm">最终状态：{task.verified ? '已验证' : '未验证'} · {task.verification}</Text>
    <Text size="xs" c="dimmed">创建 {jst(task.createdAt)} JST<br />更新 {jst(task.updatedAt)} JST<br />{task.toolVersion} · 退出码 {task.exitCode ?? '尚无'}<br />{task.toolPath}</Text>
    {task.reason && <Alert color="red">{task.reason}</Alert>}
    {task.persistenceError && <Alert color="red" title="记录保存失败">{task.persistenceError}</Alert>}
    {task.artifacts.map(file => <Text key={file.path} size="sm" style={{ overflowWrap: 'anywhere' }}>{file.path} · {file.bytes} 字节</Text>)}
    <Text size="xs" style={{ overflowWrap: 'anywhere' }}>日志目录：{task.logDirectory}</Text>
    <Group>{task.cancellable && <Button variant="default" onClick={() => run(cancelTask(task.id))}>取消任务</Button>}
      {task.operation.kind === 'reboot' && ['waitingDevice', 'waitingConfirmation', 'cancelled', 'interrupted'].includes(task.status) && <Button variant="default" onClick={() => run(verifyTask(task.id))}>验证当前设备状态</Button>}
      <Button variant="subtle" onClick={() => run(taskLogs(task.id).then(setLogs))}>读取输出</Button>
    </Group>
    {task.status === 'waitingConfirmation' && <><Text size="sm">无法自动唯一关联。请核对物理设备及目标模式后确认，不能按设备排列顺序选择。</Text><NativeSelect aria-label="人工确认跨模式目标" value={confirmed} onChange={event => setConfirmed(event.currentTarget.value)} data={[{ value: '', label: '选择实际目标设备' }, ...(devices?.devices.map(device => ({ value: device.id, label: `${device.serial} · ${device.connection} · ${device.mode.value}` })) ?? [])]} /><Button disabled={!confirmed} onClick={() => run(verifyTask(task.id, confirmed))}>确认这是原任务目标并验证</Button></>}
    {error && <Alert color="red" role="alert">{error}</Alert>}{logs !== null && <Code block>{logs}</Code>}
    <Checkbox label="诊断导出包含设备标识" checked={includeIdentifiers} onChange={event => setIncludeIdentifiers(event.currentTarget.checked)} /><Text size="xs" c="dimmed">导出摘要、输出、工具与产物路径，不包含截图内容；默认移除设备标识，分享前请检查自由文本。</Text><Button variant="default" onClick={() => run(exportTask(task.id, includeIdentifiers))}>导出诊断</Button>
  </Stack></Paper>;
}

export function TaskCenter({ snapshot, devices }: { snapshot: TaskSnapshot | null; devices: DeviceSnapshot | null }) {
  return <Stack><Text size="sm" c="dimmed">取消或关闭进程不表示设备回退。任务记录包含设备标识，分享前请核对；授权秘密不写入记录。</Text><Divider />{snapshot ? snapshot.tasks.length ? snapshot.tasks.map(task => <TaskRecord key={task.id} task={task} devices={devices} />) : <Text>尚无任务记录</Text> : <Text>正在读取原生任务状态…</Text>}</Stack>;
}

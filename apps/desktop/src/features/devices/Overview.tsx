import { Alert, Badge, Button, Group, Menu, Paper, SimpleGrid, Stack, Table, Text, Title } from '@mantine/core';
import { useState } from 'react';
import { refreshDevices } from '../../bridge/workspace';
import type { Device, DeviceSnapshot } from '../../bridge/workspace';
import { createTask } from '../../bridge/tasks';
import type { Mode, Operation } from '../../bridge/tasks';
import { IconPackage, IconPhoto, IconTerminal2, IconUpload } from '@tabler/icons-react';
import { ServiceNotice } from '../../components/ServiceNotice';
import type { PageId } from '../../app/navigation';
import styles from '../../app/Workspace.module.css';

const labels: Record<string, string> = { model: '型号', product: '产品代号', build: '完整构建', android: 'Android', api: 'API', hardwareSerial: '设备报告序列号', permission: 'Shell 身份（未尝试提权）', kernel: '内核', unlocked: 'Bootloader 解锁变量', adb_usb: 'ADB USB 路径', adb_model: 'ADB 枚举型号', adb_product: 'ADB 枚举产品', adb_device: 'ADB 枚举代号', properties: '系统属性读取' };
const fieldStates = { provided: '已读取', notProvided: '未提供', permissionDenied: '无权限', readFailed: '读取失败' };
const modes: Record<string, string> = { adb: 'ADB', bootloaderFastboot: 'Bootloader Fastboot', fastbootd: 'Fastbootd' };
const jst = (time: number) => new Date(time).toLocaleString('zh-CN', { timeZone: 'Asia/Tokyo', hour12: false });

export function Overview({ reason, devices, selected, navigate, showTasks }: { reason: string; devices: DeviceSnapshot | null; selected: Device | undefined; navigate: (page: PageId) => void; showTasks: () => void }) {
  const [refreshError, setRefreshError] = useState<string | null>(null);
  const [taskError, setTaskError] = useState<string | null>(null);
  const start = (operation: Operation) => { if (selected) createTask(selected.id, operation).then(() => { setTaskError(null); showTasks(); }, (error: unknown) => setTaskError(String(error))); };
  const refresh = () => { refreshDevices().then(() => setRefreshError(null), (error: unknown) => setRefreshError(String(error))); };
  return <Stack gap="xl">
    <Paper><Group justify="space-between"><Title order={2}>{selected ? selected.serial : '连接你的 Android 设备'}</Title><Button variant="default" onClick={refresh}>刷新设备</Button></Group>
      {selected ? <Stack gap="sm" mt="md"><Group><Badge color={selected.connection === 'device' ? 'green' : 'gray'}>{selected.connection}</Badge><Text size="sm">{selected.mode.status === 'provided' ? modes[selected.mode.value] : `Fastboot 模式未确认：${selected.mode.value}`}</Text></Group><Text size="sm" c="dimmed">{selected.connection === 'unauthorized' ? '请解锁手机并确认 USB 调试授权。' : selected.connection === 'offline' ? '设备 offline，请检查手机与 USB 连接。' : `观测时间 ${jst(selected.observedAt)} JST`}</Text></Stack> : <ServiceNotice title={devices?.scannedAt ? `发现 ${devices.devices.length} 台设备` : '正在扫描设备'} description={reason} />}
      {devices?.adbError && <Alert color="red" title="ADB 扫描失败" mt="md">{devices.adbError}</Alert>}
      {devices?.fastbootError && <Alert color="red" title="Fastboot 扫描失败" mt="md">{devices.fastbootError}</Alert>}
      {refreshError && <Alert color="red" title="刷新失败" mt="md">{refreshError}</Alert>}
      {taskError && <Alert color="red" title="任务创建失败" mt="md">{taskError}</Alert>}
      {selected && <Group mt="md"><Menu><Menu.Target><Button variant="default">重启到模式</Button></Menu.Target><Menu.Dropdown>{([{ value: 'adb', label: 'Android 系统（ADB）' }, { value: 'bootloaderFastboot', label: 'Bootloader Fastboot' }, { value: 'fastbootd', label: 'Fastbootd' }, { value: 'recovery', label: 'Recovery' }] satisfies { value: Mode; label: string }[]).map(mode => <Menu.Item key={mode.value} onClick={() => start({ kind: 'reboot', target: mode.value })}>{mode.label}</Menu.Item>)}</Menu.Dropdown></Menu></Group>}
      {devices && <Text size="xs" c="dimmed" mt="md" style={{ overflowWrap: 'anywhere' }}>{devices.server}<br />Platform-tools 36.0.2 · {devices.toolsPath}</Text>}
    </Paper>
    <Title order={3}>常用工具</Title>
    <SimpleGrid cols={{ base: 2, lg: 4 }} spacing="md">
      {([
        { name: '安装应用', icon: IconPackage, page: 'apps' },
        { name: '传输文件', icon: IconUpload, page: 'files' },
        { name: '屏幕截图', icon: IconPhoto, page: 'files' },
        { name: '打开终端', icon: IconTerminal2, page: 'terminal' },
      ] as const).map(({ name, icon: Icon, page }) => <Paper key={name} className={styles.quickTool}>
        <Icon size={23} stroke={1.65} aria-hidden />
        <Text fw={600} mt="md">{name}</Text>
        <Text c="dimmed" size="xs" mt={4}>{name === '屏幕截图' ? '读取真实设备并选择保存位置' : '业务能力尚未接入'}</Text>
        <Button variant="subtle" mt="sm" px={0} disabled={name === '屏幕截图' && selected?.connection !== 'device'} onClick={() => name === '屏幕截图' ? start({ kind: 'screenshot' }) : navigate(page)}>{name === '屏幕截图' ? '截图并保存' : '查看入口'}</Button>
      </Paper>)}
    </SimpleGrid>
    <div className={styles.columns}>
      <Paper className={styles.unlock}>
        <Title order={2}>解锁 Bootloader</Title>
        <Text size="sm" c="dimmed" mt="sm">连接设备后检查适配条件，按已验证方案引导执行。</Text>
        <Button mt="xl" onClick={() => navigate('unlock')}>查看解锁流程</Button>
      </Paper>
      <Paper><Title order={3}>设备信息</Title>{selected ? <Table.ScrollContainer minWidth={360}><Table mt="md"><Table.Tbody>{Object.entries(selected.fields).map(([name, value]) => <Table.Tr key={name}><Table.Td>{labels[name]}</Table.Td><Table.Td><Text size="sm" style={{ overflowWrap: 'anywhere' }}>{value.value}</Text><Text size="xs" c="dimmed">{fieldStates[value.status]} · {value.source}<br />{jst(value.collectedAt)} JST</Text></Table.Td></Table.Tr>)}</Table.Tbody></Table><Text size="xs" c="dimmed" mt="sm">身份关联仅使用当前观测的序列号、transport 与 USB 路径；切换模式后不会按枚举顺序自动关联。ADB 属性不作为 Bootloader 最终验证。</Text></Table.ScrollContainer> : <Text size="sm" c="dimmed" mt="lg">从顶部选择设备后展示真实采集结果。</Text>}</Paper>
    </div>
    <Paper><Group justify="space-between"><Title order={3}>最近操作</Title><Button variant="subtle" onClick={showTasks}>查看任务中心</Button></Group><Text size="sm" c="dimmed" mt="md">查看截图、重启任务的真实阶段、输出、验证与保存产物。</Text></Paper>
  </Stack>;
}

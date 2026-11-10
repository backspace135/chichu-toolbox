import { useEffect, useState } from 'react';
import { ActionIcon, Alert, AppShell, Badge, Burger, Button, Drawer, Group, Kbd, Loader, Menu, Modal, Stack, Text, TextInput, Title, Tooltip, UnstyledButton, useComputedColorScheme, useMantineColorScheme } from '@mantine/core';
import { useDisclosure, useHotkeys, useLocalStorage } from '@mantine/hooks';
import { IconChevronDown, IconHistory, IconMoon, IconSearch, IconSun } from '@tabler/icons-react';
import { selectDevice, subscribeDevices, workspaceSnapshot } from '../bridge/workspace';
import { subscribeTasks } from '../bridge/tasks';
import type { TaskSnapshot } from '../bridge/tasks';
import { TaskCenter } from '../components/TaskCenter';
import type { DeviceSnapshot, WorkspaceSnapshot } from '../bridge/workspace';
import { Overview } from '../features/devices/Overview';
import { Unlock } from '../features/unlock/Unlock';
import { Apps } from '../features/apps/Apps';
import { Files } from '../features/files/Files';
import { Terminal } from '../features/terminal/Terminal';
import { Settings } from '../features/settings/Settings';
import { pages } from './navigation';
import type { PageId } from './navigation';
import styles from './Workspace.module.css';
import brandIcon from '../../../../docs/assets/app-icon.svg';

type Connection = { status: 'loading' } | { status: 'ready'; snapshot: WorkspaceSnapshot } | { status: 'error'; message: string };

export function App() {
  const [page, setPage] = useState<PageId>('overview');
  const [connection, setConnection] = useState<Connection>({ status: 'loading' });
  const [devices, setDevices] = useState<DeviceSnapshot | null>(null);
  const [taskSnapshot, setTaskSnapshot] = useState<TaskSnapshot | null>(null);
  const [taskError, setTaskError] = useState<string | null>(null);
  const [deviceError, setDeviceError] = useState<string | null>(null);
  const [compact, setCompact] = useLocalStorage({ key: 'chichu-desktop-compact', defaultValue: false });
  const [navigationOpen, navigation] = useDisclosure();
  const [searchOpen, search] = useDisclosure();
  const [tasksOpen, tasks] = useDisclosure();
  const [query, setQuery] = useState('');
  const { setColorScheme } = useMantineColorScheme();
  const colorScheme = useComputedColorScheme('light');
  useHotkeys([['mod+K', () => search.open()]]);

  useEffect(() => {
    let active = true;
    workspaceSnapshot().then(snapshot => {
      if (active) setConnection({ status: 'ready', snapshot });
    }, (error: unknown) => {
      if (active) setConnection({ status: 'error', message: String(error) });
    });
    const subscription = subscribeDevices(snapshot => { if (active) setDevices(snapshot); });
    const taskSubscription = subscribeTasks(snapshot => { if (active) setTaskSnapshot(snapshot); });
    taskSubscription.catch((error: unknown) => { if (active) setTaskError(String(error)); });
    subscription.catch((error: unknown) => {
      if (active) setDeviceError(String(error));
    });
    return () => { active = false; void subscription.then(unsubscribe => unsubscribe()).catch((error: unknown) => console.error('设备订阅清理失败', error)); void taskSubscription.then(unsubscribe => unsubscribe()).catch((error: unknown) => console.error('任务订阅清理失败', error)); };
  }, []);

  const current = pages.find(item => item.id === page);
  if (!current) throw new Error(`未注册的工作区页面：${page}`);
  const navigate = (id: PageId) => { setPage(id); navigation.close(); search.close(); };
  const snapshot = connection.status === 'ready' ? connection.snapshot : null;
  const selected = devices?.devices.find(device => device.id === devices.selectedId);
  const reason = selected ? `当前设备 ${selected.serial} · ${selected.connection}。本页业务能力尚未接入。` : '使用 USB 连接设备，开启 USB 调试并在手机上授权，然后从顶部选择设备。';
  const chooseDevice = (id: string | null) => { selectDevice(id).then(() => setDeviceError(null), (error: unknown) => setDeviceError(String(error))); };
  const navItem = (item: typeof pages[number]) => <UnstyledButton key={item.id} className={styles.navItem} data-active={page === item.id || undefined} aria-current={page === item.id ? 'page' : undefined} onClick={() => navigate(item.id)}><item.icon size={19} stroke={1.65} aria-hidden /><span>{item.name}</span></UnstyledButton>;

  return <AppShell layout="alt" navbar={{ width: 228, breakpoint: '48em', collapsed: { mobile: !navigationOpen } }} header={{ height: 68 }} padding={0} className={styles.shell} data-compact={compact || undefined}>
    <AppShell.Navbar className={styles.sidebar}>
      <div className={styles.brand}><img src={brandIcon} className={styles.brandMark} alt="" /><div><Text fw={700}>彳亍工具箱</Text><Text size="xs" c="dimmed">让玩机，简单一点。</Text></div></div>
      <Button variant="default" leftSection={<IconSearch size={16} />} rightSection={<Kbd size="xs">{snapshot?.platform === 'macos' ? '⌘ K' : 'Ctrl K'}</Kbd>} onClick={search.open} mb="xl">搜索工具</Button>
      <nav aria-label="工作区导航">{pages.filter(item => item.id !== 'settings').map(navItem)}</nav>
      <div className={styles.sidebarBottom}>{navItem(pages[5])}<Text size="xs" c="dimmed" p="sm">内部开发版本</Text></div>
    </AppShell.Navbar>
    <AppShell.Header className={styles.header}>
      <Group gap="sm"><Burger opened={navigationOpen} onClick={navigation.toggle} hiddenFrom="sm" size="sm" aria-label="切换导航" /><Text size="xs" c="dimmed">工作区 / <span className={styles.breadcrumb}>{current.name}</span></Text></Group>
      <Group gap="sm">
        <Menu position="bottom-end"><Menu.Target><Button color="gray" variant="subtle" rightSection={<IconChevronDown size={15} />}>{selected ? selected.serial : '未选择设备'}</Button></Menu.Target><Menu.Dropdown><Menu.Label>设备连接</Menu.Label>{devices?.devices.map(device => <Menu.Item key={device.id} onClick={() => chooseDevice(device.id)}>{device.serial} · {device.connection}</Menu.Item>)}<Menu.Item onClick={() => chooseDevice(null)}>取消选择</Menu.Item>{devices?.devices.length === 0 && <Text size="sm" p="sm" maw={300}>{reason}</Text>}</Menu.Dropdown></Menu>
        <Tooltip label={colorScheme === 'dark' ? '切换浅色' : '切换深色'}><ActionIcon aria-label={colorScheme === 'dark' ? '切换浅色' : '切换深色'} onClick={() => setColorScheme(colorScheme === 'dark' ? 'light' : 'dark')}>{colorScheme === 'dark' ? <IconSun size={19} /> : <IconMoon size={19} />}</ActionIcon></Tooltip>
        <Tooltip label="任务中心"><ActionIcon aria-label="任务中心" onClick={tasks.open}><IconHistory size={19} /></ActionIcon></Tooltip>
      </Group>
    </AppShell.Header>
    <AppShell.Main className={styles.main}>
      <div className={styles.workspace} id="main-content">
        <div className={styles.pageHeading}><div><Title order={1}>{current.name}</Title><Text size="sm" c="dimmed" mt={6}>{current.description}</Text></div><Badge color={selected?.connection === 'device' ? 'green' : 'gray'}>{connection.status === 'loading' ? '读取服务中' : connection.status === 'error' ? '服务读取失败' : selected ? selected.connection : '未选择设备'}</Badge></div>
        {deviceError && <Alert color="red" title="设备服务错误" role="alert">{deviceError}</Alert>}
        {connection.status === 'loading' && <Group role="status"><Loader size="sm" /><Text>读取原生工作区状态…</Text></Group>}
        {connection.status === 'error' && <Alert color="red" title="无法读取原生工作区状态" role="alert"><Text size="sm" style={{ overflowWrap: 'anywhere' }}>{connection.message}</Text><Text size="sm" mt="sm">请在正式桌面窗口中运行应用；若仍失败，请保留此错误并重新启动。</Text></Alert>}
        {(connection.status === 'ready' || page === 'settings') && (() => {
          switch (page) {
            case 'overview': return <Overview reason={reason} devices={devices} selected={selected} navigate={navigate} showTasks={tasks.open} />;
            case 'unlock': return <Unlock key={selected?.id ?? 'none'} reason={reason} selected={selected} />;
            case 'apps': return <Apps reason={reason} />;
            case 'files': return <Files reason={reason} />;
            case 'terminal': return <Terminal />;
            case 'settings': return <Settings compact={compact} setCompact={setCompact} snapshot={snapshot} />;
          }
        })()}
      </div>
      <footer className={styles.footer}><Button variant="subtle" color="gray" leftSection={<IconHistory size={15} />} onClick={tasks.open}>任务中心</Button><Text size="xs" c="dimmed">{snapshot ? snapshot.taskService.reason : '任务服务状态尚未读取'}</Text></footer>
    </AppShell.Main>
    <Modal opened={searchOpen} onClose={search.close} title="搜索工具">
      <TextInput autoFocus aria-label="搜索页面" placeholder="输入页面或功能名称" value={query} onChange={event => setQuery(event.currentTarget.value)} leftSection={<IconSearch size={17} />} />
      <Stack gap={4} mt="md">{pages.filter(item => `${item.name}${item.description}`.toLowerCase().includes(query.toLowerCase())).map(item => <UnstyledButton key={item.id} className={styles.searchResult} onClick={() => navigate(item.id)}><item.icon size={20} aria-hidden /><div><Text fw={600} size="sm">{item.name}</Text><Text size="xs" c="dimmed">{item.description}</Text></div></UnstyledButton>)}
      {!pages.some(item => `${item.name}${item.description}`.toLowerCase().includes(query.toLowerCase())) && <Text size="sm" c="dimmed" p="md">没有匹配的工具</Text>}</Stack>
    </Modal>
    <Drawer opened={tasksOpen} onClose={tasks.close} title="任务中心" position="right" size="lg">{taskError && <Alert color="red">{taskError}</Alert>}<TaskCenter snapshot={taskSnapshot} devices={devices} /></Drawer>
  </AppShell>;
}

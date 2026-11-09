import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  ActionIcon, Alert, Badge, Button, Checkbox, Divider, Drawer, Group,
  MantineProvider, Menu, Modal, Paper, Progress, SegmentedControl,
  Stack, Switch, Table, Text, TextInput, ThemeIcon, Tooltip,
  createTheme, useMantineColorScheme,
} from '@mantine/core';
import {
  IconApps, IconArrowDown, IconArrowLeft, IconArrowRight, IconArrowUp,
  IconBattery3, IconBolt, IconBrandChrome,
  IconBrandGoogle, IconBrandTelegram, IconCheck,
  IconChevronDown, IconChevronRight, IconCircleCheck, IconClock,
  IconCode, IconCommand, IconCopy, IconDeviceMobile, IconDots,
  IconDownload, IconFile,
  IconFolder, IconFolderOpen, IconHistory, IconInfoCircle, IconLayoutDashboard,
  IconLock, IconLockOpen, IconMenu2, IconMicrophone, IconMoon,
  IconPackage, IconPhoto, IconPlayerPause, IconPlayerPlay, IconPlugConnected,
  IconRefresh, IconSearch, IconSettings, IconShieldCheck,
  IconSun, IconTerminal2, IconTrash, IconUpload, IconUsb, IconX,
} from '@tabler/icons-react';
import '@mantine/core/styles.css';
import './styles.css';
import brandIcon from '../app-icon.svg?raw';

const theme = createTheme({
  primaryColor: 'blue',
  primaryShade: { light: 6, dark: 6 },
  fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif',
  headings: { fontFamily: 'inherit', fontWeight: '650' },
  defaultRadius: 'md',
  colors: {
    blue: ['#edf3ff', '#dce8ff', '#bad1ff', '#91b6ff', '#699dff', '#4988f5', '#2f6cda', '#2864ce', '#2355ad', '#20498d'],
  },
  components: {
    Button: Button.extend({ defaultProps: { fw: 550, radius: 'md' } }),
    Badge: Badge.extend({ defaultProps: { tt: 'none', fw: 550, radius: 'sm', variant: 'light' } }),
    Paper: Paper.extend({ defaultProps: { radius: 'lg' } }),
    Tooltip: Tooltip.extend({ defaultProps: { withArrow: true, openDelay: 300 } }),
    Modal: Modal.extend({ defaultProps: { closeButtonProps: { 'aria-label': '关闭弹窗' } } }),
    Drawer: Drawer.extend({ defaultProps: { closeButtonProps: { 'aria-label': '关闭任务记录' } } }),
    Progress: Progress.extend({ defaultProps: { 'aria-label': '演示任务进度' } }),
  },
});

const cssVariablesResolver = () => ({
  variables: {},
  light: {
    '--mantine-color-dimmed': '#626f83',
    '--mantine-color-green-light-color': '#226934',
    '--mantine-color-gray-light-color': '#596578',
    '--mantine-color-gray-outline': '#596578',
    '--mantine-color-blue-light-color': '#235ab5',
  },
  dark: {},
});

const pages = [
  { id: 'overview', name: '设备概览', icon: IconLayoutDashboard, description: '查看设备信息与常用操作' },
  { id: 'unlock', name: '解锁 Bootloader', icon: IconLockOpen, description: '适配检查与解锁流程' },
  { id: 'apps', name: '应用管理', icon: IconApps, description: '安装、冻结和提取应用' },
  { id: 'files', name: '文件传输', icon: IconFolder, description: '浏览文件与传输记录' },
  { id: 'terminal', name: '终端与日志', icon: IconTerminal2, description: 'Android Shell 和运行日志' },
  { id: 'settings', name: '设置', icon: IconSettings, description: '外观与工具偏好' },
];
const devices = {
  xiaomi: { name: 'Xiaomi 15', code: 'dada', os: 'HyperOS 3', android: 'Android 16', chip: 'Snapdragon 8 Elite', ram: '12 GB', storage: '256 GB', used: 92.4, screen: '1200 × 2670', battery: 86, temperature: '31.2', build: 'OS3.0.4.0.WOCCNXM', serial: 'DEMO-XM15-0001' },
  mtk: { name: 'Redmi Note 11S', code: 'fleur', os: 'MIUI 14', android: 'Android 13', chip: 'MediaTek Helio G96', ram: '8 GB', storage: '128 GB', used: 48.2, screen: '1080 × 2400', battery: 72, temperature: '29.5', build: '演示构建 · 未验证适配', serial: 'DEMO-MTK-0002' },
};
const initialApps = [
  { id: 'chrome', name: 'Chrome', pkg: 'com.android.chrome', version: '142.0.7444.48', size: '268 MB', system: false, frozen: false, icon: IconBrandChrome, color: '#3c83e5', bg: '#eaf2ff' },
  { id: 'photos', name: '相册', pkg: 'com.miui.gallery', version: '4.3.0.22', size: '184 MB', system: true, frozen: false, icon: IconPhoto, color: '#eb8652', bg: '#fff0e8' },
  { id: 'toolbox', name: '彳亍工具箱', pkg: 'com.chichu.toolbox', version: '开发版本', size: '32.6 MB', system: false, frozen: false, icon: IconPackage, color: '#3475e8', bg: '#edf3ff' },
  { id: 'telegram', name: 'Telegram', pkg: 'org.telegram.messenger', version: '12.1.1', size: '152 MB', system: false, frozen: false, icon: IconBrandTelegram, color: '#279dc8', bg: '#e9f7fd' },
  { id: 'settings', name: '设置', pkg: 'com.android.settings', version: '16', size: '96.8 MB', system: true, frozen: false, icon: IconSettings, color: '#7d8795', bg: '#edf0f4' },
  { id: 'music', name: '音乐', pkg: 'com.miui.player', version: '9.4.3.1', size: '88.2 MB', system: true, frozen: true, icon: IconMicrophone, color: '#dd6687', bg: '#ffedf3' },
  { id: 'google', name: 'Google', pkg: 'com.google.android.googlequicksearchbox', version: '16.42.38', size: '342 MB', system: false, frozen: false, icon: IconBrandGoogle, color: '#4285f4', bg: '#eef4ff' },
];
const files = [
  { name: 'DCIM', kind: '文件夹', size: '128 个项目', modified: '今天 14:20', icon: IconFolder, color: '#d9aa57' },
  { name: 'Download', kind: '文件夹', size: '24 个项目', modified: '今天 13:48', icon: IconFolder, color: '#d9aa57' },
  { name: 'Pictures', kind: '文件夹', size: '36 个项目', modified: '昨天 19:32', icon: IconFolder, color: '#d9aa57' },
  { name: 'Documents', kind: '文件夹', size: '8 个项目', modified: '11 月 7 日', icon: IconFolder, color: '#d9aa57' },
  { name: 'chichu-toolbox.apk', kind: 'Android 安装包', size: '32.6 MB', modified: '今天 14:12', icon: IconPackage, color: '#3e90d7' },
  { name: 'device-report.txt', kind: '文本文件', size: '4.2 KB', modified: '今天 14:08', icon: IconFile, color: '#8291a5' },
];

function Glyph({ icon: Icon, size = 19, ...props }) { return <Icon size={size} stroke={1.65} {...props} />; }
function IconButton({ icon, label, ...props }) { return <Tooltip label={label}><ActionIcon aria-label={label} variant="subtle" color="gray" size={34} {...props}><Glyph icon={icon} /></ActionIcon></Tooltip>; }
function Brand({ small = false }) { return <div className={`brand ${small ? 'brand-small' : ''}`}><span className="brand-icon" dangerouslySetInnerHTML={{ __html: brandIcon }} /><div><strong>彳亍工具箱</strong>{!small && <span>让玩机，简单一点。</span>}</div></div>; }
function SectionTitle({ title, description, children }) { return <div className="section-heading"><div><h2>{title}</h2>{description && <p>{description}</p>}</div>{children}</div>; }
function EmptyConnection({ connect }) { return <Paper className="empty-connection" withBorder><ThemeIcon size={58} variant="light" radius="xl"><Glyph icon={IconUsb} size={29} /></ThemeIcon><h2>连接你的 Android 设备</h2><p>连接后，在这里管理应用、传输文件和检查解锁适配。</p><Button leftSection={<Glyph icon={IconPlugConnected} />} onClick={connect}>连接演示设备</Button><Text size="xs" c="dimmed">这是界面预览，不会访问真实 USB 设备。</Text></Paper>; }

function App() {
  const { colorScheme, setColorScheme } = useMantineColorScheme();
  const [page, setPage] = useState('overview');
  const [deviceId, setDeviceId] = useState('xiaomi');
  const [connected, setConnected] = useState(true);
  const [sidebar, setSidebar] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [toolQuery, setToolQuery] = useState('');
  const [toast, setToast] = useState(null);
  const [logsOpen, setLogsOpen] = useState(false);
  const [activities, setActivities] = useState([{ title: '已连接演示设备', detail: 'USB 调试已授权 · ADB 模式', time: '14:32', icon: IconPlugConnected, color: 'green' }, { title: '安装包已提取', detail: '彳亍工具箱 · 32.6 MB', time: '14:28', icon: IconPackage, color: 'blue' }]);
  const [modal, setModal] = useState(null);
  const [apps, setApps] = useState(initialApps);
  const [appQuery, setAppQuery] = useState('');
  const [appFilter, setAppFilter] = useState('全部应用');
  const [selected, setSelected] = useState([]);
  const [appDetails, setAppDetails] = useState(null);
  const [unlockState, setUnlockState] = useState('idle');
  const [transfer, setTransfer] = useState(null);
  const [folder, setFolder] = useState('/sdcard');
  const [terminal, setTerminal] = useState([{ command: 'getprop ro.product.model', output: 'Xiaomi 15' }, { command: 'wm size', output: 'Physical size: 1200x2670' }]);
  const [command, setCommand] = useState('');
  const [terminalTab, setTerminalTab] = useState('Android Shell');
  const [autoConnect, setAutoConnect] = useState(true);
  const [keepLogs, setKeepLogs] = useState(true);
  const [compact, setCompact] = useState(false);
  const timers = useRef(new Set());
  const device = devices[deviceId];
  const nav = pages.find(item => item.id === page);

  function later(fn, delay) { const id = setTimeout(() => { timers.current.delete(id); fn(); }, delay); timers.current.add(id); }
  useEffect(() => () => timers.current.forEach(clearTimeout), []);
  useEffect(() => { if (!toast) return; const id = setTimeout(() => setToast(null), 3600); return () => clearTimeout(id); }, [toast]);
  useEffect(() => { const shortcut = event => { if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); setSearchOpen(value => !value); } }; window.addEventListener('keydown', shortcut); return () => window.removeEventListener('keydown', shortcut); }, []);
  function notify(title, text = '此操作仅用于预览，不会访问真实设备。') { setToast({ title, text }); }
  function go(id) { setPage(id); setSidebar(false); }
  function record(title, detail, icon = IconCircleCheck, color = 'blue') { setActivities(items => [{ title, detail, icon, color, time: '刚刚' }, ...items]); }
  function exportLog() { const blob = new Blob(['彳亍工具箱 UI 演示记录\n以下均为演示数据\n\n' + activities.map(item => `${item.time} ${item.title} — ${item.detail}`).join('\n')], { type: 'text/plain;charset=utf-8' }); const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = 'chichu-mock-log.txt'; link.click(); URL.revokeObjectURL(url); notify('演示记录已导出', '下载文件包含当前预览中的演示记录。'); }
  function startTransfer(name = 'chichu-toolbox.apk') { setTransfer({ name, progress: 0 }); [25, 56, 82, 100].forEach((progress, index) => later(() => { setTransfer({ name, progress }); if (progress === 100) { record('文件传输演示完成', name, IconUpload); notify('传输演示完成', '没有读取或传输真实文件。'); } }, (index + 1) * 450)); }
  function checkUnlock() { setUnlockState('checking'); later(() => { setUnlockState(deviceId === 'xiaomi' ? 'unsupported' : 'review'); record('适配检查演示完成', deviceId === 'xiaomi' ? '当前预览未接入此设备方案' : 'MTK 候选方案 · 等待实机验证', IconShieldCheck); }, 1250); }
  function bulkFreeze() { setApps(items => items.map(item => selected.includes(item.id) ? { ...item, frozen: true } : item)); record('冻结应用演示', `${selected.length} 个应用`, IconPlayerPause); notify('已更新演示应用状态', `已将所选 ${selected.length} 个应用标记为冻结。`); setSelected([]); }
  function runCommand(event) { event.preventDefault(); const value = command.trim(); if (!value) return; if (value === 'clear') { setTerminal([]); setCommand(''); return; } const responses = { 'getprop ro.product.model': device.name, 'wm size': `Physical size: ${device.screen.replace(' × ', 'x')}`, 'id': 'uid=2000(shell) gid=2000(shell) groups=1004(input),1007(log)', 'help': '可试用：getprop ro.product.model、wm size、id、clear。\n此终端仅展示预设响应，不执行命令。' }; setTerminal(items => [...items, { command: value, output: responses[value] ?? '预览模式：此命令没有预设响应。输入 help 查看可试用命令。' }]); setCommand(''); }
  const visibleApps = apps.filter(item => `${item.name} ${item.pkg}`.toLowerCase().includes(appQuery.toLowerCase())).filter(item => appFilter === '全部应用' || (appFilter === '用户应用' && !item.system) || (appFilter === '系统应用' && item.system) || (appFilter === '已冻结' && item.frozen));
  const quickTools = [
    { title: '安装应用', text: 'APK 与拆分安装包', icon: IconPackage, color: 'blue', action: () => setModal('install') },
    { title: '传输文件', text: '电脑与手机双向传输', icon: IconFolder, color: 'orange', action: () => go('files') },
    { title: '屏幕截图', text: '预览并保存当前屏幕', icon: IconPhoto, color: 'grape', action: () => setModal('screenshot') },
    { title: '打开终端', text: '进入 Android Shell', icon: IconTerminal2, color: 'teal', action: () => go('terminal') },
  ];

  return <div className={`desktop-app ${compact ? 'compact' : ''}`}>
    <aside className={`sidebar ${sidebar ? 'sidebar-open' : ''}`}>
      <div className="sidebar-brand"><Brand /></div>
      <button className="sidebar-search" onClick={() => setSearchOpen(true)}><Glyph icon={IconSearch} size={17} /><span>搜索工具</span><kbd>⌘ K</kbd></button>
      <div className="nav-caption">工作空间</div>
      <nav aria-label="功能导航">{pages.filter(item => item.id !== 'settings').map(item => <button key={item.id} onClick={() => go(item.id)} className={`nav-item ${page === item.id ? 'nav-active' : ''}`} aria-current={page === item.id ? 'page' : undefined}><Glyph icon={item.icon} /><span>{item.name}</span>{item.id === 'unlock' && <span className="nav-dot" />}</button>)}</nav>
      <div className="sidebar-bottom"><button className={`nav-item ${page === 'settings' ? 'nav-active' : ''}`} onClick={() => go('settings')}><Glyph icon={IconSettings} /><span>设置</span></button><div className="preview-note"><span className="preview-dot" /><div><strong>Mantine 交互预览</strong><span>演示数据 · 不执行设备操作</span></div></div></div>
    </aside>
    {sidebar && <button className="sidebar-backdrop" aria-label="关闭导航" onClick={() => setSidebar(false)} />}
    <div className="main-shell">
      <header className="topbar"><Group gap={11}><span className="mobile-menu"><IconButton icon={IconMenu2} label="打开导航" onClick={() => setSidebar(true)} /></span><span className="breadcrumb">工作空间</span><Glyph icon={IconChevronRight} size={14} /><span className="breadcrumb-current">{nav.name}</span></Group><Group gap={13}><Menu position="bottom-end" width={280} shadow="md"><Menu.Target><button className="device-switch"><span className={`connection-dot ${!connected ? 'disconnected' : ''}`} /><span>{connected ? device.name : '未连接设备'}</span><Badge size="xs" color={connected ? 'gray' : 'orange'}>{connected ? 'USB' : '离线'}</Badge><Glyph icon={IconChevronDown} size={14} /></button></Menu.Target><Menu.Dropdown><Menu.Label>演示设备</Menu.Label>{Object.entries(devices).map(([id, item]) => <Menu.Item key={id} leftSection={<Glyph icon={IconDeviceMobile} />} rightSection={id === deviceId && connected ? <Glyph icon={IconCheck} size={16} /> : null} onClick={() => { setDeviceId(id); setConnected(true); setUnlockState('idle'); setSelected([]); notify(`已切换到 ${item.name}`, '当前设备信息均为界面演示数据。'); }}>{item.name}</Menu.Item>)}<Menu.Divider /><Menu.Item leftSection={<Glyph icon={connected ? IconX : IconPlugConnected} />} onClick={() => { setConnected(value => !value); notify(connected ? '演示设备已断开' : '演示设备已连接'); }}>{connected ? '断开演示设备' : '重新连接演示设备'}</Menu.Item></Menu.Dropdown></Menu><span className="header-divider" /><IconButton icon={colorScheme === 'dark' ? IconSun : IconMoon} label={colorScheme === 'dark' ? '切换浅色' : '切换深色'} onClick={() => setColorScheme(colorScheme === 'dark' ? 'light' : 'dark')} /><IconButton icon={IconHistory} label="打开任务记录" onClick={() => setLogsOpen(true)} /></Group></header>
      <main className="workspace">
        <div className="page-heading"><div><div className="page-kicker">{page === 'overview' ? '一切，从连接开始' : nav.description}</div><h1>{nav.name}</h1><p>{page === 'overview' ? '设备状态、常用工具，都在这里。' : page === 'unlock' ? '先了解设备适配情况，再进入清晰的分步流程。' : page === 'apps' ? '让手机里的每一个应用，都井井有条。' : page === 'files' ? '在电脑与手机之间，轻松移动文件。' : page === 'terminal' ? '熟悉的命令行，清楚的每一步。' : '按你的习惯，调整工具箱。'}</p></div><div className="heading-actions">{page === 'overview' && <><Badge size="lg" variant="outline" color="gray" leftSection={<Glyph icon={IconCode} size={13} />}>界面预览</Badge><Button variant="default" leftSection={<Glyph icon={IconRefresh} size={16} />} onClick={() => notify('演示状态已刷新', '所有数据均为静态示例，没有连接真实设备。')}>刷新设备</Button></>}{page === 'apps' && <Button leftSection={<Glyph icon={IconUpload} size={17} />} onClick={() => setModal('install')}>安装应用</Button>}{page === 'files' && <Button leftSection={<Glyph icon={IconUpload} size={17} />} onClick={() => setModal('transfer')}>发送文件</Button>}{page === 'terminal' && <Button variant="default" leftSection={<Glyph icon={IconDownload} size={17} />} onClick={exportLog}>导出记录</Button>}</div></div>

        {!connected && page !== 'settings' ? <EmptyConnection connect={() => { setConnected(true); notify('演示设备已连接'); }} /> : <>
          {page === 'overview' && <>
            <Paper className="device-hero" withBorder><div className="device-hero-main"><div className="phone-stage"><div className="phone-art"><div className="phone-camera"><i /><i /><i /></div><div className="phone-brand">xiaomi</div></div><div className="phone-art phone-front"><div className="phone-punch" /><div className="screen-orbit orbit-one" /><div className="screen-orbit orbit-two" /><span>14:32</span></div></div><div className="hero-copy"><div className="hero-status"><span className="connection-dot" />设备已连接<span className="hero-status-separator" />USB 3.0</div><h2>{device.name}</h2><p>{device.os}<span>·</span>{device.android}<span>·</span>{device.code}</p><Group gap={8} mt={19}><Badge color="green" variant="light" leftSection={<Glyph icon={IconCheck} size={12} />}>ADB 已授权</Badge><Badge color="gray" variant="light" leftSection={<Glyph icon={IconLock} size={12} />}>Bootloader 已锁定</Badge></Group></div></div><div className="hero-specs"><div><Glyph icon={IconBattery3} size={20} /><span>电量</span><strong>{device.battery}<small>%</small></strong><span className="spec-foot">USB 充电中</span></div><div><Glyph icon={IconBolt} size={20} /><span>电池温度</span><strong>{device.temperature}<small>°C</small></strong><span className="spec-foot">演示读数</span></div><div><Glyph icon={IconShieldCheck} size={20} /><span>权限状态</span><strong className="spec-text">Shell</strong><span className="spec-foot">UID 2000</span></div></div></Paper>
            <SectionTitle title="常用工具" description="少一点命令，多一点顺手。"><button className="text-link" onClick={() => setSearchOpen(true)}>查看全部<Glyph icon={IconArrowRight} size={15} /></button></SectionTitle>
            <div className="quick-grid">{quickTools.map(tool => <Paper component="button" withBorder className="quick-tool" key={tool.title} onClick={tool.action}><ThemeIcon variant="light" color={tool.color} size={42} radius="md"><Glyph icon={tool.icon} size={23} /></ThemeIcon><strong>{tool.title}</strong><span>{tool.text}</span><Glyph icon={IconChevronRight} className="quick-arrow" size={16} /></Paper>)}</div>
            <div className="overview-lower"><Paper className="unlock-promo" withBorder><div className="promo-symbol"><Glyph icon={IconLockOpen} size={29} /></div><Badge color="blue" size="sm">深度玩机</Badge><h2>解锁更多可能。</h2><p>检查 Bootloader 解锁适配情况，<br />用清晰的步骤，完成复杂的操作。</p><Button rightSection={<Glyph icon={IconArrowRight} size={16} />} onClick={() => go('unlock')}>检查解锁适配</Button><div className="promo-caption"><Glyph icon={IconInfoCircle} size={13} />方案与设备支持仍待验证</div></Paper><Paper withBorder className="information-panel"><SectionTitle title="设备信息"><button className="text-link" onClick={() => setModal('device')}>完整信息<Glyph icon={IconChevronRight} size={14} /></button></SectionTitle><dl className="device-data"><div><dt>处理器</dt><dd>{device.chip}</dd></div><div><dt>运行内存</dt><dd>{device.ram}</dd></div><div><dt>屏幕分辨率</dt><dd>{device.screen}</dd></div><div className="storage-row"><dt>设备存储</dt><dd><strong>{device.used} GB</strong> / {device.storage}</dd><dd className="storage-meter"><Progress aria-label="演示设备存储使用率" value={deviceId === 'xiaomi' ? 36 : 38} size={5} color="blue" radius="xl" /><span>已使用 {deviceId === 'xiaomi' ? '36' : '38'}%，还有足够空间</span></dd></div></dl></Paper></div>
            <Paper withBorder className="activity-panel"><SectionTitle title="最近操作"><button className="text-link" onClick={() => setLogsOpen(true)}>全部记录<Glyph icon={IconChevronRight} size={14} /></button></SectionTitle>{activities.slice(0, 2).map((item, i) => <div className="activity-row" key={`${item.title}-${i}`}><ThemeIcon size={33} color={item.color} variant="light" radius="xl"><Glyph icon={item.icon} size={17} /></ThemeIcon><div><strong>{item.title}</strong><span>{item.detail}</span></div><time>{item.time}</time><Badge color="gray" size="sm">演示</Badge></div>)}</Paper>
          </>}

          {page === 'unlock' && <div className="unlock-layout"><div><Paper withBorder className="unlock-main"><div className="unlock-title"><ThemeIcon size={50} radius="lg" variant="light"><Glyph icon={IconLockOpen} size={26} /></ThemeIcon><div><h2>Bootloader 解锁</h2><p>设备与方案匹配，是解锁的第一步。</p></div></div><div className="unlock-steps">{['连接设备', '检查适配', '执行解锁', '验证结果'].map((step, i) => <div className={`unlock-step ${i === 0 ? 'step-complete' : i === 1 ? 'step-current' : ''}`} key={step}><span>{i === 0 ? <Glyph icon={IconCheck} size={16} /> : i + 1}</span><strong>{step}</strong></div>)}</div><div className="unlock-device"><ThemeIcon color="gray" variant="light" size={45} radius="md"><Glyph icon={IconDeviceMobile} size={25} /></ThemeIcon><div><strong>{device.name}</strong><span>{device.chip} · {device.code}</span></div><Badge color="gray">已锁定</Badge></div><div className="check-list">{[{ label: '设备连接', detail: 'ADB 已连接，USB 调试已授权', ready: true }, { label: '设备身份', detail: `${device.code} · ${device.android}`, ready: true }, { label: '解锁方案', detail: deviceId === 'xiaomi' ? '尚未接入此设备的解锁方案' : 'mtkclient 候选 · 精确构建尚未验证', ready: false }, { label: '实际执行', detail: '此页面仅演示流程，不提供设备写入', ready: false }].map(item => <div className="check-row" key={item.label}><Glyph icon={item.ready ? IconCircleCheck : IconClock} size={19} className={item.ready ? 'status-green' : 'muted'} /><div><strong>{item.label}</strong><p>{item.detail}</p></div><Badge color={item.ready ? 'green' : 'gray'} size="sm">{item.ready ? '已读取（演示）' : '待确认'}</Badge></div>)}</div>{unlockState === 'unsupported' && <Alert icon={<Glyph icon={IconInfoCircle} />} color="blue" title="演示检查完成">当前预览没有接入 Xiaomi 15 的解锁实现。可在顶部切换 Redmi Note 11S，查看 MTK 候选方案状态。</Alert>}{unlockState === 'review' && <Alert icon={<Glyph icon={IconInfoCircle} />} color="blue" title="发现候选方案，仍需验证">mtkclient 是候选引擎；精确固件、LK 构建和主机平台尚未实机验证，不代表设备已受支持。</Alert>}<div className="unlock-actions"><Text size="xs" c="dimmed">适配结果以真实设备验证为准</Text><Button loading={unlockState === 'checking'} leftSection={<Glyph icon={IconShieldCheck} size={17} />} onClick={checkUnlock}>{unlockState === 'idle' ? '开始适配检查（演示）' : '重新检查（演示）'}</Button></div></Paper><div className="inline-hint"><Glyph icon={IconInfoCircle} size={16} /><span>预览只演示连接、检查和结果展示，不执行任何解锁命令。</span></div></div><Stack gap={18}><Paper withBorder className="side-panel"><h3>流程中的每一步，都清楚。</h3><p>当前阶段、设备状态和执行输出会持续保留。需要按键或插拔时，在对应步骤给出说明。</p><Divider my="lg" /><div className="side-feature"><Glyph icon={IconDeviceMobile} /><span>具体机型与构建匹配</span></div><div className="side-feature"><Glyph icon={IconFolderOpen} /><span>备份与文件产物统一管理</span></div><div className="side-feature"><Glyph icon={IconHistory} /><span>完整保留执行记录</span></div></Paper><Paper withBorder className="side-panel candidate-panel"><Badge color="gray">候选方案</Badge><h3>mtkclient</h3><p>面向部分 MediaTek 设备。首个接入方案与支持范围仍在讨论。</p><Button variant="subtle" px={0} rightSection={<Glyph icon={IconArrowRight} size={14} />} onClick={() => setModal('candidate')}>了解接入方向</Button></Paper></Stack></div>}

          {page === 'apps' && <Paper withBorder className="apps-panel"><div className="table-toolbar"><TextInput aria-label="搜索应用名称或包名" placeholder="搜索应用名称或包名" leftSection={<Glyph icon={IconSearch} size={17} />} value={appQuery} onChange={event => setAppQuery(event.currentTarget.value)} className="app-search" /><SegmentedControl value={appFilter} onChange={setAppFilter} data={['全部应用', '用户应用', '系统应用', '已冻结']} size="xs" /><IconButton icon={IconRefresh} label="刷新应用列表" onClick={() => notify('演示应用列表已刷新')} /></div><div className="table-summary"><span>{visibleApps.length} 个演示应用{selected.length > 0 && ` · 已选择 ${selected.length} 项`}</span>{selected.length > 0 ? <Group gap={7}><Button variant="light" size="xs" onClick={bulkFreeze}>冻结所选</Button><Button variant="subtle" size="xs" onClick={() => { record('提取安装包演示', `${selected.length} 个应用`, IconDownload); notify('提取演示完成', '没有读取手机安装包。'); setSelected([]); }}>提取安装包</Button></Group> : <span>应用状态均为演示</span>}</div><div className="table-scroll"><Table verticalSpacing="md" highlightOnHover className="apps-table"><Table.Thead><Table.Tr><Table.Th w={48}><Checkbox aria-label="全选当前应用" size="xs" checked={visibleApps.length > 0 && visibleApps.every(item => selected.includes(item.id))} indeterminate={visibleApps.some(item => selected.includes(item.id)) && !visibleApps.every(item => selected.includes(item.id))} onChange={event => setSelected(event.currentTarget.checked ? visibleApps.map(item => item.id) : [])} /></Table.Th><Table.Th>应用</Table.Th><Table.Th>类型</Table.Th><Table.Th>占用空间</Table.Th><Table.Th>状态</Table.Th><Table.Th ta="right">操作</Table.Th></Table.Tr></Table.Thead><Table.Tbody>{visibleApps.map(app => <Table.Tr key={app.id} data-selected={selected.includes(app.id) || undefined}><Table.Td><Checkbox aria-label={`选择${app.name}`} size="xs" checked={selected.includes(app.id)} onChange={event => setSelected(items => event.currentTarget.checked ? [...items, app.id] : items.filter(id => id !== app.id))} /></Table.Td><Table.Td><button className="app-cell" onClick={() => setAppDetails(app)}><span className="app-avatar" style={{ background: app.bg, color: app.color }}><Glyph icon={app.icon} size={23} /></span><span><strong>{app.name}</strong><small>{app.pkg}</small></span></button></Table.Td><Table.Td><Text size="xs" c="dimmed">{app.system ? '系统应用' : '用户应用'}</Text></Table.Td><Table.Td><span className="tabular">{app.size}</span></Table.Td><Table.Td><Badge size="sm" color={app.frozen ? 'gray' : 'green'} variant="light">{app.frozen ? '已冻结' : '已启用'}</Badge></Table.Td><Table.Td ta="right"><Menu position="bottom-end" width={170}><Menu.Target><ActionIcon variant="subtle" color="gray" aria-label={`${app.name}的操作`}><Glyph icon={IconDots} size={18} /></ActionIcon></Menu.Target><Menu.Dropdown><Menu.Item leftSection={<Glyph icon={IconInfoCircle} size={16} />} onClick={() => setAppDetails(app)}>应用详情</Menu.Item><Menu.Item leftSection={<Glyph icon={app.frozen ? IconPlayerPlay : IconPlayerPause} size={16} />} onClick={() => { setApps(items => items.map(item => item.id === app.id ? { ...item, frozen: !item.frozen } : item)); notify(`${app.name}${app.frozen ? '已解冻' : '已冻结'}（演示）`); }}>{app.frozen ? '解冻应用' : '冻结应用'}</Menu.Item><Menu.Item leftSection={<Glyph icon={IconDownload} size={16} />} onClick={() => { notify('提取安装包演示完成'); record('提取安装包演示', app.name, IconDownload); }}>提取安装包</Menu.Item></Menu.Dropdown></Menu></Table.Td></Table.Tr>)}</Table.Tbody></Table>{visibleApps.length === 0 && <div className="empty-table"><Glyph icon={IconSearch} size={29} /><strong>没有找到匹配的应用</strong><span>试试应用名称或包名的其他关键词。</span><Button size="xs" variant="light" onClick={() => { setAppQuery(''); setAppFilter('全部应用'); }}>清除筛选</Button></div>}</div><div className="table-footer"><span>只展示当前用户的演示应用</span><Group gap={8}><Button size="xs" variant="default" disabled>上一页</Button><Button size="xs" variant="light">1</Button><Button size="xs" variant="default" disabled>下一页</Button></Group></div></Paper>}

          {page === 'files' && <div className="file-layout"><Paper withBorder className="files-panel"><div className="file-path"><IconButton icon={IconArrowLeft} label="返回共享存储根目录" onClick={() => setFolder('/sdcard')} /><Glyph icon={IconDeviceMobile} size={17} /><button onClick={() => setFolder('/sdcard')}>内部共享存储</button>{folder !== '/sdcard' && <><Glyph icon={IconChevronRight} size={14} /><span>{folder.split('/').at(-1)}</span></>}<span className="file-path-code">{folder}</span></div><Table verticalSpacing="md" highlightOnHover><Table.Thead><Table.Tr><Table.Th>名称</Table.Th><Table.Th>大小 / 项目</Table.Th><Table.Th>修改时间</Table.Th><Table.Th w={45} /></Table.Tr></Table.Thead><Table.Tbody>{(folder === '/sdcard' ? files : [{ name: folder.includes('DCIM') ? 'Camera' : 'example.txt', kind: folder.includes('DCIM') ? '文件夹' : '文本文件', size: folder.includes('DCIM') ? '96 个项目' : '2.4 KB', modified: '今天 14:20', icon: folder.includes('DCIM') ? IconFolder : IconFile, color: '#d9aa57' }]).map(file => <Table.Tr key={file.name}><Table.Td><button className="file-name" onClick={() => file.kind === '文件夹' ? setFolder(`${folder}/${file.name}`) : notify(`已选择 ${file.name}`, '预览不会打开真实文件。')}><Glyph icon={file.icon} size={24} style={{ color: file.color }} /><span><strong>{file.name}</strong><small>{file.kind}</small></span></button></Table.Td><Table.Td><Text size="xs" c="dimmed">{file.size}</Text></Table.Td><Table.Td><Text size="xs" c="dimmed">{file.modified}</Text></Table.Td><Table.Td><IconButton icon={IconDownload} label={`接收${file.name}`} onClick={() => startTransfer(file.name)} /></Table.Td></Table.Tr>)}</Table.Tbody></Table><div className="file-bottom"><Glyph icon={IconInfoCircle} size={15} /><span>演示目录 · 仅展示 ADB 可访问的共享存储</span></div></Paper><Stack gap={18}><Paper withBorder className="transfer-drop"><ThemeIcon variant="light" size={52} radius="xl"><Glyph icon={IconUpload} size={25} /></ThemeIcon><h3>把文件，送到手机。</h3><p>支持多文件传输，进度一目了然。</p><Button variant="light" onClick={() => setModal('transfer')}>选择演示文件</Button><span>当前目录：{folder}</span></Paper><Paper withBorder className="side-panel"><SectionTitle title="传输记录" />{transfer ? <><div className="transfer-name"><Glyph icon={IconPackage} /><strong>{transfer.name}</strong></div><Progress value={transfer.progress} size={6} mt={15} /><div className="transfer-meta"><span>{transfer.progress === 100 ? '演示已完成' : '正在演示传输'}</span><span>{transfer.progress}%</span></div></> : <div className="transfer-empty"><Glyph icon={IconArrowsTransfer} size={26} /><p>还没有传输任务</p><span>开始一次演示，查看进度效果。</span></div>}</Paper></Stack></div>}

          {page === 'terminal' && <Paper withBorder className="terminal-panel"><div className="terminal-toolbar"><SegmentedControl value={terminalTab} onChange={setTerminalTab} data={['Android Shell', '任务日志']} size="xs" /><Group gap={5}><span className="terminal-preview-label">预设响应 · 不执行命令</span><IconButton icon={IconTrash} label="清空终端" onClick={() => setTerminal([])} /><IconButton icon={IconCopy} label="查看可复制记录" onClick={() => setLogsOpen(true)} /></Group></div><div className="terminal-surface">{terminalTab === 'Android Shell' ? <><div className="terminal-welcome">彳亍工具箱 · Android Shell 交互预览<br /><span>{device.name} / shell / 演示会话</span></div>{terminal.map((line, i) => <div className="terminal-entry" key={i}><div><span className="terminal-prompt">{device.code}:/ $</span> {line.command}</div><pre>{line.output}</pre></div>)}<form className="terminal-input" onSubmit={runCommand}><label htmlFor="shell-command">{device.code}:/ $</label><input id="shell-command" aria-label="输入演示 Shell 命令" value={command} onChange={event => setCommand(event.currentTarget.value)} placeholder="输入 help 查看可用演示命令" autoComplete="off" spellCheck={false} /><button type="submit" aria-label="提交演示命令"><Glyph icon={IconArrowRight} size={17} /></button></form></> : <><div className="terminal-welcome">任务输出 · 演示数据</div>{activities.map((item, i) => <div className="terminal-log" key={i}><span>{item.time}</span><strong>[demo]</strong> {item.title}<p>{item.detail}</p></div>)}</>}</div><div className="terminal-foot"><span><i />演示会话</span><span>输入 help 查看预设命令</span></div></Paper>}

          {page === 'settings' && <div className="settings-layout"><Paper withBorder className="settings-panel"><SectionTitle title="外观" description="一套界面，两种舒适的观感。" /><div className="setting-row"><div><strong>界面主题</strong><p>深色与浅色均可直接预览</p></div><SegmentedControl value={colorScheme} onChange={setColorScheme} data={[{ value: 'light', label: '浅色' }, { value: 'dark', label: '深色' }]} size="sm" /></div><div className="setting-row"><div><strong>紧凑模式</strong><p>减少列表与工作区间距</p></div><Switch aria-label="紧凑模式" checked={compact} onChange={event => setCompact(event.currentTarget.checked)} /></div><Divider my="xl" /><SectionTitle title="连接与记录" /><div className="setting-row"><div><strong>自动连接已授权设备</strong><p>此预览仅保存当前开关状态</p></div><Switch aria-label="自动连接已授权设备" checked={autoConnect} onChange={event => setAutoConnect(event.currentTarget.checked)} /></div><div className="setting-row"><div><strong>保留任务记录</strong><p>便于查看每一步的执行结果</p></div><Switch aria-label="保留任务记录" checked={keepLogs} onChange={event => setKeepLogs(event.currentTarget.checked)} /></div></Paper><Paper withBorder className="side-panel about-panel"><Brand /><p>桌面交互设计预览</p><Divider my="lg" /><dl><div><dt>界面组件</dt><dd>Mantine 9.8</dd></div><div><dt>桌面框架</dt><dd>Tauri 2（计划）</dd></div><div><dt>macOS</dt><dd>Apple Silicon</dd></div><div><dt>Windows</dt><dd>x64（暂定）</dd></div></dl><Text size="xs" c="dimmed" mt="xl">该预览用于评估组件与交互，不代表功能范围或支持设备已经确认。</Text></Paper></div>}
        </>}
        <footer className="workspace-footer"><span><span className={`connection-dot ${!connected ? 'disconnected' : ''}`} />{connected ? 'ADB 演示连接正常' : '设备未连接'}</span><span>Mantine 9.8<span className="footer-dot">·</span>本地交互预览</span></footer>
      </main>
      <div className="task-bar"><button onClick={() => setLogsOpen(true)}><Glyph icon={IconTerminal2} size={15} /><span>{transfer && transfer.progress < 100 ? `正在演示传输 · ${transfer.progress}%` : '任务中心'}<small>{activities.length} 条演示记录</small></span><Glyph icon={IconChevronDown} size={14} /></button><span><Glyph icon={IconCommand} size={13} />K 搜索工具</span></div>
    </div>

    {toast && <div className="toast-message" role="status"><ThemeIcon color="blue" variant="light" size={34} radius="xl"><Glyph icon={IconCheck} size={18} /></ThemeIcon><div><strong>{toast.title}</strong><p>{toast.text}</p></div><IconButton icon={IconX} label="关闭提示" onClick={() => setToast(null)} /></div>}
    <Modal opened={searchOpen} onClose={() => setSearchOpen(false)} title="搜索工具" centered radius="lg" size="md"><TextInput autoFocus placeholder="输入功能名称…" aria-label="搜索工具名称" leftSection={<Glyph icon={IconSearch} size={18} />} value={toolQuery} onChange={event => setToolQuery(event.currentTarget.value)} mb="md" /><Stack gap={4}>{pages.filter(item => `${item.name}${item.description}`.includes(toolQuery)).map(item => <button key={item.id} className="search-result" onClick={() => { go(item.id); setSearchOpen(false); setToolQuery(''); }}><ThemeIcon variant="light" color="gray" size={34}><Glyph icon={item.icon} size={18} /></ThemeIcon><div><strong>{item.name}</strong><span>{item.description}</span></div><Glyph icon={IconArrowRight} size={17} /></button>)}</Stack></Modal>
    <Drawer opened={logsOpen} onClose={() => setLogsOpen(false)} title="任务记录" position="right" size={420} padding="xl"><Text size="sm" c="dimmed" mb="xl">当前预览中的操作记录，全部为演示数据。</Text><Stack gap={0}>{activities.map((item, i) => <div className="drawer-activity" key={i}><ThemeIcon size={35} color={item.color} variant="light" radius="xl"><Glyph icon={item.icon} size={18} /></ThemeIcon><div><strong>{item.title}</strong><p>{item.detail}</p><span>{item.time} · 演示</span></div></div>)}</Stack><Button variant="light" fullWidth mt="xl" leftSection={<Glyph icon={IconDownload} size={17} />} onClick={exportLog}>导出演示记录</Button></Drawer>
    <Modal opened={appDetails !== null} onClose={() => setAppDetails(null)} title="应用详情" centered radius="lg">{appDetails && <><div className="app-detail-top"><span className="app-avatar" style={{ background: appDetails.bg, color: appDetails.color }}><Glyph icon={appDetails.icon} size={28} /></span><div><h3>{appDetails.name}</h3><Text size="xs" c="dimmed">{appDetails.pkg}</Text></div></div><dl className="modal-data"><div><dt>版本</dt><dd>{appDetails.version}</dd></div><div><dt>应用类型</dt><dd>{appDetails.system ? '系统应用' : '用户应用'}</dd></div><div><dt>占用空间</dt><dd>{appDetails.size}</dd></div><div><dt>数据来源</dt><dd>静态演示数据</dd></div></dl><Button fullWidth variant="light" onClick={() => { notify('提取安装包演示完成'); record('提取安装包演示', appDetails.name, IconDownload); setAppDetails(null); }}>提取安装包（演示）</Button></>}</Modal>
    <Modal opened={modal !== null} onClose={() => setModal(null)} title={modal === 'install' ? '安装应用' : modal === 'transfer' ? '发送文件' : modal === 'screenshot' ? '屏幕截图预览' : modal === 'device' ? '完整设备信息' : '解锁方案接入方向'} centered radius="lg" size={modal === 'screenshot' ? 'sm' : 'md'}>
      {(modal === 'install' || modal === 'transfer') && <><div className="mock-file-picker"><ThemeIcon variant="light" size={56} radius="xl"><Glyph icon={modal === 'install' ? IconPackage : IconUpload} size={28} /></ThemeIcon><h3>{modal === 'install' ? 'chichu-toolbox.apk' : 'example-document.pdf'}</h3><p>{modal === 'install' ? '32.6 MB · 演示安装包' : '2.4 MB · 演示文件'}</p><Badge color="gray">本次使用示例文件，无需选择真实文件</Badge></div><Text size="xs" c="dimmed" my="lg">目标设备：{device.name}。点击后仅展示任务进度，不读取文件或执行设备操作。</Text><Button fullWidth onClick={() => { if (modal === 'install') { record('应用安装演示完成', 'chichu-toolbox.apk · 32.6 MB', IconPackage, 'green'); notify('安装演示完成', '没有安装或修改任何真实应用。'); } else { startTransfer('example-document.pdf'); go('files'); } setModal(null); }}>{modal === 'install' ? '体验安装完成状态' : '体验传输进度'}</Button></>}
      {modal === 'device' && <><Badge mb="md" color="gray">以下均为演示数据</Badge><dl className="modal-data"><div><dt>设备</dt><dd>{device.name}</dd></div><div><dt>产品代号</dt><dd>{device.code}</dd></div><div><dt>系统版本</dt><dd>{device.os} / {device.android}</dd></div><div><dt>构建</dt><dd>{device.build}</dd></div><div><dt>处理器</dt><dd>{device.chip}</dd></div><div><dt>演示序列号</dt><dd>{device.serial}</dd></div></dl></>}
      {modal === 'screenshot' && <><div className="screenshot-preview"><div className="screenshot-phone"><span>14:32</span><strong>星期一</strong><small>11 月 9 日</small><div className="screenshot-apps">{[IconPhoto, IconSettings, IconBrandChrome, IconPackage].map((Icon, i) => <span key={i}><Icon size={22} stroke={1.6} /></span>)}</div></div></div><Text size="xs" c="dimmed" ta="center" mb="lg">示例屏幕插画，不是真实设备截图。</Text><Button fullWidth variant="light" onClick={() => { notify('截图预览已查看', '当前为 CSS 绘制的示例屏幕，没有截取真实设备。'); setModal(null); }}>完成预览</Button></>}
      {modal === 'candidate' && <Stack><Badge color="gray" w="fit-content">讨论中的候选方案</Badge><Text size="sm">mtkclient 提供部分 MediaTek 设备的通信和维护能力。桌面端计划以独立工具进程接入，具体机型、固件构建与双平台支持仍需验证。</Text><Text size="sm" c="dimmed">本预览仅展示候选方案信息，不代表 Redmi Note 11S 或任何设备已经受本项目支持。</Text><Button variant="light" onClick={() => setModal(null)}>了解了</Button></Stack>}
    </Modal>
  </div>;
}

function IconArrowsTransfer(props) { return <span className="transfer-icon"><Glyph icon={IconArrowUp} {...props} /><Glyph icon={IconArrowDown} {...props} /></span>; }

createRoot(document.getElementById('root')).render(<MantineProvider theme={theme} cssVariablesResolver={cssVariablesResolver} defaultColorScheme="light"><App /></MantineProvider>);

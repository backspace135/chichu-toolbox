import { IconApps, IconFolder, IconLayoutDashboard, IconLockOpen, IconSettings, IconTerminal2 } from '@tabler/icons-react';

export const pages = [
  { id: 'overview', name: '设备概览', icon: IconLayoutDashboard, description: '查看设备信息与常用操作' },
  { id: 'unlock', name: '解锁 Bootloader', icon: IconLockOpen, description: '适配检查与解锁流程' },
  { id: 'apps', name: '应用管理', icon: IconApps, description: '安装、冻结和提取应用' },
  { id: 'files', name: '文件传输', icon: IconFolder, description: '浏览文件与传输记录' },
  { id: 'terminal', name: '终端与日志', icon: IconTerminal2, description: 'Android Shell 和运行日志' },
  { id: 'settings', name: '设置', icon: IconSettings, description: '外观与工具信息' },
] as const;

export type PageId = typeof pages[number]['id'];

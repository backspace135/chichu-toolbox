import { ActionIcon, Badge, Button, createTheme, Drawer, Modal, Paper, Tooltip } from '@mantine/core';
import type { CSSVariablesResolver } from '@mantine/core';

export const theme = createTheme({
  primaryColor: 'blue',
  primaryShade: 6,
  fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif',
  headings: { fontFamily: 'inherit', fontWeight: '650', sizes: { h1: { fontSize: '27px' }, h2: { fontSize: '20px' }, h3: { fontSize: '15px' } } },
  defaultRadius: 'md',
  colors: { blue: ['#edf3ff', '#dce8ff', '#bad1ff', '#91b6ff', '#699dff', '#4988f5', '#2f6cda', '#2864ce', '#2355ad', '#20498d'] },
  components: {
    Button: Button.extend({ defaultProps: { fw: 550, radius: 'md' } }),
    ActionIcon: ActionIcon.extend({ defaultProps: { size: 34, variant: 'subtle', color: 'gray' } }),
    Badge: Badge.extend({ defaultProps: { tt: 'none', radius: 'sm', variant: 'light' } }),
    Paper: Paper.extend({ defaultProps: { radius: 'lg', withBorder: true, p: 'xl' } }),
    Tooltip: Tooltip.extend({ defaultProps: { withArrow: true, openDelay: 300 } }),
    Modal: Modal.extend({ defaultProps: { closeButtonProps: { 'aria-label': '关闭弹窗' } } }),
    Drawer: Drawer.extend({ defaultProps: { closeButtonProps: { 'aria-label': '关闭任务中心' } } }),
  },
});

export const cssVariablesResolver: CSSVariablesResolver = () => ({
  variables: {},
  light: {
    '--workspace': '#f5f7fa', '--surface': '#ffffff', '--sidebar': '#f9fafc', '--line': '#e8ecf2',
    '--text': '#25324a', '--muted': '#626f83', '--accent': '#2f6cda', '--selected': '#eaf0fc',
    '--selected-text': '#255dbd', '--hover': '#f3f6fa', '--unlock': '#f1f5ff',
    '--mantine-color-text': '#25324a', '--mantine-color-dimmed': '#626f83',
    '--mantine-color-body': '#ffffff', '--mantine-color-default-border': '#e8ecf2',
  },
  dark: {
    '--workspace': '#161a22', '--surface': '#202630', '--sidebar': '#1b2029', '--line': '#303845',
    '--text': '#e0e6f0', '--muted': '#a0abbd', '--accent': '#78a7ff', '--selected': '#263650',
    '--selected-text': '#8db5ff', '--hover': '#2a3341', '--unlock': '#202d45',
    '--mantine-color-text': '#e0e6f0', '--mantine-color-dimmed': '#a0abbd',
    '--mantine-color-body': '#202630', '--mantine-color-default-border': '#303845',
  },
});

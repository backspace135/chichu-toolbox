import { Paper, SegmentedControl, Stack, Switch, Text, Title, useMantineColorScheme } from '@mantine/core';
import type { WorkspaceSnapshot } from '../../bridge/workspace';
import styles from '../../app/Workspace.module.css';

export function Settings({ compact, setCompact, snapshot }: { compact: boolean; setCompact: (value: boolean) => void; snapshot: WorkspaceSnapshot | null }) {
  const { colorScheme, setColorScheme } = useMantineColorScheme();
  return <div className={styles.columns}>
    <Paper><Title order={3}>外观</Title>
      <div className={styles.settingRow}><div><Text fw={600} size="sm">主题</Text><Text size="xs" c="dimmed">跟随系统或选择深浅色，自动保存</Text></div>
        <SegmentedControl aria-label="主题" value={colorScheme} onChange={value => setColorScheme(value as 'auto' | 'light' | 'dark')} data={[{ value: 'auto', label: '系统' }, { value: 'light', label: '浅色' }, { value: 'dark', label: '深色' }]} />
      </div>
      <div className={styles.settingRow}><div><Text fw={600} size="sm">紧凑模式</Text><Text size="xs" c="dimmed">缩小工作区间距，自动保存</Text></div><Switch aria-label="紧凑模式" checked={compact} onChange={event => setCompact(event.currentTarget.checked)} /></div>
    </Paper>
    <Paper><Stack gap="sm"><Title order={3}>关于彳亍工具箱</Title><Text size="sm">内部开发版本 · 非正式发行</Text>
      {snapshot && <><Text size="sm">版本 {snapshot.version}</Text><Text size="sm">{snapshot.platform} / {snapshot.arch}</Text></>}
      <Text size="xs" c="dimmed">设备、任务与解锁能力仍在开发。桌面产品版本与最低系统支持尚未定案。</Text>
    </Stack></Paper>
  </div>;
}

import { Button, Group, Paper, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { IconPackage, IconPhoto, IconTerminal2, IconUpload } from '@tabler/icons-react';
import { ServiceNotice } from '../../components/ServiceNotice';
import type { PageId } from '../../app/navigation';
import styles from '../../app/Workspace.module.css';

export function Overview({ reason, navigate, showTasks }: { reason: string; navigate: (page: PageId) => void; showTasks: () => void }) {
  return <Stack gap="xl">
    <Paper><ServiceNotice title="连接你的 Android 设备" description={reason} /></Paper>
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
        <Text c="dimmed" size="xs" mt={4}>设备服务接入后可用</Text>
        <Button variant="subtle" mt="sm" px={0} onClick={() => navigate(page)}>查看入口</Button>
      </Paper>)}
    </SimpleGrid>
    <div className={styles.columns}>
      <Paper className={styles.unlock}>
        <Title order={2}>解锁 Bootloader</Title>
        <Text size="sm" c="dimmed" mt="sm">连接设备后检查适配条件，按已验证方案引导执行。</Text>
        <Button mt="xl" onClick={() => navigate('unlock')}>查看解锁流程</Button>
      </Paper>
      <Paper><Title order={3}>设备信息</Title><Text size="sm" c="dimmed" mt="lg">尚未读取设备。型号、系统和锁状态将在真实连接后展示。</Text></Paper>
    </div>
    <Paper><Group justify="space-between"><Title order={3}>最近操作</Title><Button variant="subtle" onClick={showTasks}>查看任务中心</Button></Group><Text size="sm" c="dimmed" mt="md">任务服务尚未接入，当前未执行操作。</Text></Paper>
  </Stack>;
}

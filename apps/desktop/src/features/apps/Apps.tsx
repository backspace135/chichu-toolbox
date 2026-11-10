import { Button, Group, Paper, Text, Title } from '@mantine/core';
import { ServiceNotice } from '../../components/ServiceNotice';

export function Apps({ reason }: { reason: string }) {
  return <Paper>
    <Group justify="space-between"><Title order={3}>设备应用</Title><Button disabled>安装应用</Button></Group>
    <ServiceNotice title="尚未读取应用列表" description={reason} />
    <Text c="dimmed" size="xs">应用管理服务尚未接入；此状态不表示设备上没有应用。</Text>
  </Paper>;
}

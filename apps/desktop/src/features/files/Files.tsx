import { Button, Paper, Text, Title } from '@mantine/core';
import { ServiceNotice } from '../../components/ServiceNotice';
import styles from '../../app/Workspace.module.css';

export function Files({ reason }: { reason: string }) {
  return <div className={styles.columns}>
    <Paper><Title order={3}>设备文件</Title><ServiceNotice title="尚未读取设备目录" description={reason} /></Paper>
    <Paper><Title order={3}>传输文件</Title><Text c="dimmed" size="sm" my="lg">文件与截图服务尚未接入，当前不能选择或传输文件。</Text><Button disabled>选择文件</Button></Paper>
  </div>;
}

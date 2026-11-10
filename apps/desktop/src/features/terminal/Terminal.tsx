import { Paper, Text, Title } from '@mantine/core';
import styles from '../../app/Workspace.module.css';

export function Terminal() {
  return <Paper p={0} className={styles.terminalPanel}>
    <Title order={3} p="lg">设备 Shell</Title>
    <div className={styles.terminal}><Text>尚未建立设备会话</Text><Text size="sm" mt="md">设备终端服务尚未接入。这里不会执行电脑端命令。</Text></div>
    <Text size="xs" c="dimmed" p="lg">会话未启动 · 无终端输出</Text>
  </Paper>;
}

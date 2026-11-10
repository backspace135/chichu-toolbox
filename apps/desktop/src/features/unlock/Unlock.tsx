import { Button, Paper, Stack, Stepper, Text, Title } from '@mantine/core';
import { ServiceNotice } from '../../components/ServiceNotice';
import styles from '../../app/Workspace.module.css';

export function Unlock({ reason }: { reason: string }) {
  return <div className={styles.columns}>
    <Paper>
      <Stepper active={0} size="sm" allowNextStepsSelect={false}>
        {['连接设备', '检查适配', '执行解锁', '验证结果'].map(label => <Stepper.Step key={label} label={label} />)}
      </Stepper>
      <ServiceNotice title="等待设备服务接入" description={reason} />
      <Button disabled>检查适配</Button>
      <Text c="dimmed" size="xs" mt="sm">尚未执行任何适配检查或设备操作。</Text>
    </Paper>
    <Stack>
      <Paper><Title order={3}>流程说明</Title><Text c="dimmed" size="sm" mt="md">读取真实设备与固件信息，检查方案条件，再由你启动执行。最终结果以设备状态验证为准。</Text></Paper>
      <Paper><Title order={3}>解锁方案</Title><Text c="dimmed" size="sm" mt="md">当前没有已接入且经过本项目验证的解锁方案。</Text></Paper>
    </Stack>
  </div>;
}

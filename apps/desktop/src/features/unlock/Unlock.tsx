import { useState } from 'react';
import { Alert, Badge, Button, Group, Paper, Stack, Stepper, Table, Text, Title } from '@mantine/core';
import { ServiceNotice } from '../../components/ServiceNotice';
import { inspectUnlock } from '../../bridge/unlock';
import type { Assessment } from '../../bridge/unlock';
import type { Device } from '../../bridge/workspace';
import styles from '../../app/Workspace.module.css';

const statusLabels = { applicable: '适用', needsChecks: '待补充检查', inapplicable: '不适用' };
const checkLabels = { passed: '通过', missing: '待检查', rejected: '不满足' };

export function Unlock({ reason, selected }: { reason: string; selected: Device | undefined }) {
  const [assessment, setAssessment] = useState<Assessment | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const inspect = async () => {
    if (!selected) return;
    setBusy(true); setError(null); setAssessment(null);
    try { setAssessment(await inspectUnlock(selected.id)); }
    catch (failure) { setError(String(failure)); }
    finally { setBusy(false); }
  };
  return <div className={styles.columns}>
    <Paper>
      <Stepper active={selected ? 1 : 0} size="sm" allowNextStepsSelect={false}>
        {['连接设备', '检查适配', '执行解锁', '验证结果'].map(label => <Stepper.Step key={label} label={label} />)}
      </Stepper>
      {!selected && <ServiceNotice title="请选择设备" description={reason} />}
      {selected && <Text my="lg">{selected.serial} · {selected.connection} · {selected.mode.value}</Text>}
      <Button disabled={!selected} loading={busy} onClick={() => void inspect()}>检查适配</Button>
      <Text c="dimmed" size="xs" mt="sm">仅采集当前模式可读字段；不会重启、上传 DA 或写入设备。检查结果是采集时点的快照，执行前必须重新检查。</Text>
      {error && <Alert color="red" title="适配检查失败" mt="md" role="alert">{error}</Alert>}
      {assessment && <Stack mt="lg">
        <Alert color={assessment.recommended ? 'blue' : 'gray'} title={assessment.recommended ? '存在适用方案' : '当前没有可推荐方案'}>
          规则 {assessment.ruleVersion} · {assessment.host.platform} / {assessment.host.arch}。没有当前主机与精确固件的支持证据，不进入执行阶段。
        </Alert>
        {assessment.candidates.map(candidate => <Stack key={candidate.scheme.id} gap="sm">
          <Group justify="space-between"><Title order={3}>{candidate.scheme.title}</Title><Badge color={candidate.applicability === 'inapplicable' ? 'red' : candidate.applicability === 'applicable' ? 'green' : 'gray'}>{statusLabels[candidate.applicability]}</Badge></Group>
          <Table.ScrollContainer minWidth={440}><Table><Table.Thead><Table.Tr><Table.Th>条件</Table.Th><Table.Th>结果</Table.Th><Table.Th>依据</Table.Th></Table.Tr></Table.Thead><Table.Tbody>{candidate.checks.map(check => <Table.Tr key={check.name}><Table.Td>{check.name}</Table.Td><Table.Td>{checkLabels[check.status]}</Table.Td><Table.Td style={{ overflowWrap: 'anywhere' }}>{check.reason}</Table.Td></Table.Tr>)}</Table.Tbody></Table></Table.ScrollContainer>
          <Text size="xs" c="dimmed">{candidate.rankingReason}</Text>
        </Stack>)}
        <Title order={3}>采集来源与时点</Title>
        {Object.entries(assessment.device.fields).map(([key, field]) => <Text key={key} size="xs" style={{ overflowWrap: 'anywhere' }}>{key} · {field.value} · {field.status}<br />{field.source} · {new Date(field.collectedAt).toLocaleString('zh-CN', { timeZone: 'Asia/Tokyo', hour12: false })} JST</Text>)}
      </Stack>}
    </Paper>
    <Stack>
      <Paper><Title order={3}>流程说明</Title><Text c="dimmed" size="sm" mt="md">读取真实设备与固件信息，检查方案条件，再由你启动执行。最终结果以设备状态验证为准。</Text></Paper>
      <Paper><Title order={3}>解锁方案</Title><Text c="dimmed" size="sm" mt="md">当前没有经过本项目验证的正式方案。MTK 路线仅为调查候选，不能因识别到 MTK 芯片就宣称支持。</Text>
        {assessment?.candidates.map(({ scheme }) => <Stack key={scheme.id} gap="xs" mt="md">
          <Text size="xs" style={{ overflowWrap: 'anywhere' }}>调查版本：{scheme.sourceRevision}</Text>
          <Text size="sm">数据影响：{scheme.impact}</Text>
          <Text fw={600} size="sm">准备阶段（不是适配条件）</Text>{scheme.preparation.map(step => <Text key={step} size="sm">{step}</Text>)}
          <Text fw={600} size="sm">人工步骤与已知问题</Text>{[...scheme.manualSteps, ...scheme.knownIssues].map(step => <Text key={step} size="sm">{step}</Text>)}
        </Stack>)}
      </Paper>
    </Stack>
  </div>;
}

import { Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { IconPlugConnected } from '@tabler/icons-react';

export function ServiceNotice({ title, description }: { title: string; description: string }) {
  return <Stack align="center" gap="md" py={44} role="status">
    <ThemeIcon size={58} radius="xl" variant="light"><IconPlugConnected size={28} stroke={1.65} /></ThemeIcon>
    <Title order={2}>{title}</Title>
    <Text c="dimmed" size="sm" ta="center" maw={480}>{description}</Text>
  </Stack>;
}

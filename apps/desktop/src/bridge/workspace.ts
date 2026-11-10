import { Channel, invoke } from '@tauri-apps/api/core';

export interface UnavailableService {
  status: 'ready';
  reason: string;
}

export interface WorkspaceSnapshot {
  version: string;
  platform: string;
  arch: string;
  taskService: UnavailableService;
}

export interface DeviceField {
  status: 'provided' | 'notProvided' | 'permissionDenied' | 'readFailed';
  value: string;
  source: string;
  collectedAt: number;
}

export interface Device {
  id: string;
  serial: string;
  transportId: string | null;
  connection: string;
  mode: DeviceField;
  fields: Record<string, DeviceField>;
  observedAt: number;
}

export interface DeviceSnapshot {
  devices: Device[];
  selectedId: string | null;
  adbError: string | null;
  fastbootError: string | null;
  server: string;
  toolsPath: string;
  scannedAt: number | null;
}

export async function subscribeDevices(onmessage: (snapshot: DeviceSnapshot) => void) {
  const channel = new Channel<DeviceSnapshot>();
  channel.onmessage = onmessage;
  await invoke<void>('subscribe_devices', { channel });
  return () => invoke<void>('unsubscribe_devices', { channelId: channel.id });
}

export function selectDevice(id: string | null) {
  return invoke<void>('select_device', { id });
}

export function refreshDevices() {
  return invoke<void>('refresh_devices');
}

export function workspaceSnapshot() {
  return invoke<WorkspaceSnapshot>('workspace_snapshot');
}

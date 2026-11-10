import { Channel, invoke } from '@tauri-apps/api/core';
import type { Device } from './workspace';

export type Mode = 'adb' | 'bootloaderFastboot' | 'fastbootd' | 'recovery';
export type Operation = { kind: 'screenshot' } | { kind: 'reboot'; target: Mode };
export interface Task {
  id: string;
  device: Device;
  operation: Operation;
  status: 'preparing' | 'running' | 'waitingDevice' | 'waitingConfirmation' | 'verifying' | 'succeeded' | 'failed' | 'cancelled' | 'interrupted';
  phase: string;
  cancellable: boolean;
  verified: boolean;
  verification: string;
  observedDevice: Device | null;
  createdAt: number;
  updatedAt: number;
  exitCode: number | null;
  reason: string | null;
  toolVersion: string;
  toolPath: string;
  artifacts: { path: string; bytes: number; kind: string }[];
  logDirectory: string;
  persistenceError: string | null;
}
export interface TaskSnapshot { sequence: number; tasks: Task[] }
export async function subscribeTasks(onmessage: (snapshot: TaskSnapshot) => void) {
  const channel = new Channel<TaskSnapshot>();
  channel.onmessage = onmessage;
  await invoke('subscribe_tasks', { channel });
  return () => invoke<void>('unsubscribe_tasks', { channelId: channel.id });
}
export const createTask = (deviceId: string, operation: Operation) => invoke<string>('create_task', { deviceId, operation });
export const cancelTask = (id: string) => invoke<void>('cancel_task', { id });
export const verifyTask = (id: string, confirmedDevice: string | null = null) => invoke<void>('verify_task', { id, confirmedDevice });
export const taskLogs = (id: string) => invoke<string>('task_logs', { id });
export const exportTask = (id: string, includeIdentifiers: boolean) => invoke<void>('export_task', { id, includeIdentifiers });

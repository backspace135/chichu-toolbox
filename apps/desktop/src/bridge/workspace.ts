import { invoke } from '@tauri-apps/api/core';

export interface UnavailableService {
  status: 'notImplemented';
  reason: string;
}

export interface WorkspaceSnapshot {
  version: string;
  platform: string;
  arch: string;
  deviceService: UnavailableService;
  taskService: UnavailableService;
}

export function workspaceSnapshot() {
  return invoke<WorkspaceSnapshot>('workspace_snapshot');
}

import { invoke } from '@tauri-apps/api/core';
import type { Device } from './workspace';

interface Tool { name: string; version: string; sha256: string }
interface Host { platform: string; arch: string }
interface Evidence { reference: string; host: Host; tool: Tool; conditions: Record<string, string>; maintenanceRevision: number }
interface Scheme {
  id: string; title: string; sourceRevision: string; requiredFields: string[];
  conditions: Record<string, string>; evidence: Evidence[]; resources: Tool[];
  knownIssues: string[]; impact: string; impactRank: number; manualSteps: string[]; preparation: string[];
}
export interface Assessment {
  ruleVersion: string; device: Device; host: Host; recommended: string | null;
  candidates: { scheme: Scheme; applicability: 'applicable' | 'needsChecks' | 'inapplicable';
    checks: { name: string; status: 'passed' | 'missing' | 'rejected'; reason: string }[]; rankingReason: string }[];
}
export function inspectUnlock(deviceId: string) {
  return invoke<Assessment>('inspect_unlock', { deviceId });
}

import type { ProtocolType } from '@/features/simulations/types';

export interface SimulationLogEntry {
  id: string;
  simulationId?: string | null;
  simulationName?: string | null;
  protocol: ProtocolType;
  status: number;
  durationMs: number;
  requestSummary?: string | null;
  responseSummary?: string | null;
  timestamp: string;
}

export interface SimulationLogSnapshot {
  totalRequests: number;
  httpRequests: number;
  tcpRequests: number;
  errorRequests: number;
  averageDurationMs: number;
  simulationMetrics?: Record<string, SimulationMetricsSummary>;
  recentLogs: SimulationLogEntry[];
}

export interface SimulationMetricsSummary {
  hits: number;
  errors: number;
  averageDurationMs: number;
}

export function parseSnapshotEvent(data: string): SimulationLogSnapshot | null {
  try {
    const parsed = JSON.parse(data) as unknown;
    return isSnapshot(parsed) ? parsed : null;
  } catch (_error) {
    return null;
  }
}

function isSnapshot(value: unknown): value is SimulationLogSnapshot {
  if (!isRecord(value)) return false;
  if (!isNumber(value.totalRequests)) return false;
  if (!isNumber(value.httpRequests)) return false;
  if (!isNumber(value.tcpRequests)) return false;
  if (!isNumber(value.errorRequests)) return false;
  if (!isNumber(value.averageDurationMs)) return false;
  if (value.simulationMetrics != null && !isSimulationMetricsRecord(value.simulationMetrics)) return false;
  if (!Array.isArray(value.recentLogs)) return false;
  return value.recentLogs.every(isLogEntry);
}

function isLogEntry(value: unknown): value is SimulationLogEntry {
  if (!isRecord(value)) return false;
  return (
    typeof value.id === 'string' &&
    optionalString(value.simulationId) &&
    optionalString(value.simulationName) &&
    (value.protocol === 'HTTP' || value.protocol === 'TCP') &&
    isNumber(value.status) &&
    isNumber(value.durationMs) &&
    optionalString(value.requestSummary) &&
    optionalString(value.responseSummary) &&
    typeof value.timestamp === 'string'
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function optionalString(value: unknown): value is string | null | undefined {
  return value == null || typeof value === 'string';
}

function isSimulationMetricsRecord(value: unknown): value is Record<string, SimulationMetricsSummary> {
  if (!isRecord(value)) return false;
  return Object.values(value).every(isSimulationMetricsSummary);
}

function isSimulationMetricsSummary(value: unknown): value is SimulationMetricsSummary {
  if (!isRecord(value)) return false;
  return isNumber(value.hits) && isNumber(value.errors) && isNumber(value.averageDurationMs);
}

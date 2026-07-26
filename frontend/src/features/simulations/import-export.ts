import type { SimulationConfig, SimulationConfigPayload } from './types';

type JsonRecord = Record<string, unknown>;

export interface SimulationExportBundle {
  format: string;
  exportedAt: string;
  count: number;
  configs: SimulationConfig[];
}

export function extractImportConfigs(content: string): SimulationConfigPayload[] {
  let parsed: unknown;
  try {
    parsed = JSON.parse(content);
  } catch (_error) {
    throw new Error('导入文件不是有效 JSON。');
  }

  const candidates = candidateConfigs(parsed);
  if (candidates.length === 0) {
    throw new Error('导入文件中没有可识别的模拟配置。');
  }

  return candidates.map((candidate, index) => normalizeImportConfig(candidate, index));
}

export function downloadTextFile(fileName: string, content: string, mimeType = 'application/json;charset=utf-8') {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

function candidateConfigs(parsed: unknown): unknown[] {
  if (Array.isArray(parsed)) {
    return parsed;
  }
  if (!isRecord(parsed)) {
    return [];
  }
  if (Array.isArray(parsed.configs)) {
    return parsed.configs;
  }
  return looksLikeSimulationConfig(parsed) ? [parsed] : [];
}

function normalizeImportConfig(candidate: unknown, index: number): SimulationConfigPayload {
  if (!isRecord(candidate) || !looksLikeSimulationConfig(candidate)) {
    throw new Error(`导入文件第 ${index + 1} 个配置格式不正确。`);
  }
  return candidate as unknown as SimulationConfigPayload;
}

function looksLikeSimulationConfig(value: JsonRecord) {
  return typeof value.name === 'string'
    && (value.protocol === 'HTTP' || value.protocol === 'TCP')
    && isRecord(value.defaultResponse);
}

function isRecord(value: unknown): value is JsonRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

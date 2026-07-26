import { describe, expect, it } from 'vitest';
import { extractImportConfigs } from './import-export';
import type { SimulationConfigPayload } from './types';

const baseConfig: SimulationConfigPayload = {
  id: 'sim-import',
  name: '导入配置',
  protocol: 'HTTP',
  enabled: true,
  http: { method: 'GET', path: '/import/{id}', matchMode: 'TEMPLATE' },
  branches: [],
  defaultResponse: { status: 200, headers: {}, body: 'ok' },
};

describe('simulation import and export helpers', () => {
  it('extracts configs from exported bundle JSON', () => {
    const configs = extractImportConfigs(JSON.stringify({ configs: [baseConfig] }));

    expect(configs).toEqual([baseConfig]);
  });

  it('extracts configs from raw array JSON', () => {
    const configs = extractImportConfigs(JSON.stringify([baseConfig]));

    expect(configs).toEqual([baseConfig]);
  });

  it('extracts one config from a raw config object', () => {
    const configs = extractImportConfigs(JSON.stringify(baseConfig));

    expect(configs).toEqual([baseConfig]);
  });

  it('rejects JSON without importable simulation configs', () => {
    expect(() => extractImportConfigs(JSON.stringify({ name: 'missing protocol' }))).toThrow('导入文件中没有可识别的模拟配置。');
  });
});

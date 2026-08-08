import { describe, expect, it } from 'vitest';
import type { SimulationConfig, SimulationConfigPayload } from './types';
import {
  availableTags,
  defaultPayload,
  displayEndpoint,
  filterSimulations,
  normalizeTags,
  shouldResetProtocolPayload,
  tagFilterOptions,
  validatePayload,
} from './sim-utils';

describe('simulation utilities', () => {
  it('formats HTTP endpoints with method and path', () => {
    const config: SimulationConfig = {
      id: 'http-users',
      name: 'User API',
      protocol: 'HTTP',
      enabled: true,
      http: { method: 'GET', path: '/api/users/{id}', matchMode: 'TEMPLATE' },
      branches: [],
      defaultResponse: { status: 404, headers: {}, body: 'Not found' },
    };

    expect(displayEndpoint(config)).toBe('GET /api/users/{id}');
  });

  it('formats TCP endpoints with host, port, and frame mode', () => {
    const config: SimulationConfig = {
      id: 'tcp-line',
      name: 'TCP line sim',
      protocol: 'TCP',
      enabled: true,
      tcp: { host: '127.0.0.1', port: 19001, frameMode: 'LINE' },
      branches: [],
      defaultResponse: { status: 404, headers: {}, body: 'not found' },
    };

    expect(displayEndpoint(config)).toBe('127.0.0.1:19001 LINE');
  });

  it('creates an HTTP starter payload with a success branch and 404 default response', () => {
    const payload = defaultPayload('HTTP');

    expect(payload.protocol).toBe('HTTP');
    expect(payload.branches).toHaveLength(1);
    expect(payload.branches[0]?.name).toBe('成功分支');
    expect(payload.branches[0]?.priority).toBe(100);
    expect(payload.branches[0]?.response.body).toContain('{{path.id}}');
    expect(payload.branches[0]?.response.body).toContain('{{random.uuid}}');
    expect(payload.defaultResponse.status).toBe(404);
  });

  it('creates a TCP starter payload with a body contains condition that uses no key', () => {
    const payload = defaultPayload('TCP');

    expect(payload.protocol).toBe('TCP');
    expect(payload.branches).toHaveLength(1);
    expect(payload.branches[0]?.priority).toBe(100);
    expect(validatePayload(payload)).toEqual([]);
  });

  it('rejects missing name, missing HTTP path, and missing TCP port', () => {
    const missingName = { ...defaultPayload('HTTP'), name: '   ' } satisfies SimulationConfigPayload;
    const missingHttpPath = {
      ...defaultPayload('HTTP'),
      name: 'HTTP sim',
      http: { method: 'GET', path: ' ', matchMode: 'TEMPLATE' },
    } satisfies SimulationConfigPayload;
    const missingTcpPort = {
      ...defaultPayload('TCP'),
      name: 'TCP sim',
      tcp: { host: '127.0.0.1', frameMode: 'LINE' },
    } satisfies SimulationConfigPayload;

    expect(validatePayload(missingName)).toContain('模拟名称不能为空。');
    expect(validatePayload(missingHttpPath)).toContain('HTTP 路径不能为空。');
    expect(validatePayload(missingTcpPort)).toContain('TCP 端口不能为空。');
  });

  it('rejects HTTP paths that do not start with a slash', () => {
    const payload = {
      ...defaultPayload('HTTP'),
      http: { method: 'GET', path: 'api/users', matchMode: 'TEMPLATE' },
    } satisfies SimulationConfigPayload;

    expect(validatePayload(payload)).toContain('HTTP 路径必须以 / 开头。');
  });

  it('rejects decimal TCP ports', () => {
    const payload = {
      ...defaultPayload('TCP'),
      tcp: { host: '127.0.0.1', port: 19001.5, frameMode: 'LINE' },
    } satisfies SimulationConfigPayload;

    expect(validatePayload(payload)).toContain('TCP 端口必须是 1 到 65535 之间的整数。');
  });

  it('rejects invalid default response status', () => {
    const payload = {
      ...defaultPayload('HTTP'),
      defaultResponse: { status: 99, headers: {}, body: 'invalid' },
    } satisfies SimulationConfigPayload;

    expect(validatePayload(payload)).toContain('默认响应状态码必须是 100 到 999 之间的整数。');
  });

  it('rejects null branch entries', () => {
    const payload = {
      ...defaultPayload('HTTP'),
      branches: [null],
    } as unknown as SimulationConfigPayload;

    expect(validatePayload(payload)).toContain('分支 1 必须是对象。');
  });

  it('rejects branches without responses', () => {
    const payload = {
      ...defaultPayload('HTTP'),
      branches: [{ name: 'No response', priority: 0, conditions: [] }],
    } as unknown as SimulationConfigPayload;

    expect(validatePayload(payload)).toContain('分支 1 响应不能为空。');
  });

  it('rejects invalid condition enum values', () => {
    const payload = {
      ...defaultPayload('HTTP'),
      branches: [
        {
          name: 'Bad condition',
          priority: 0,
          conditions: [{ source: 'COOKIE', key: 'status', operator: 'MATCHES', value: 'ok' }],
          response: { status: 200, headers: {}, body: 'ok' },
        },
      ],
    } as unknown as SimulationConfigPayload;

    expect(validatePayload(payload)).toContain('分支 1 条件 1 来源无效。');
    expect(validatePayload(payload)).toContain('分支 1 条件 1 操作符无效。');
  });

  it('rejects EQ conditions with missing values', () => {
    const payload = {
      ...defaultPayload('HTTP'),
      branches: [
        {
          name: 'Missing value',
          priority: 0,
          conditions: [{ source: 'QUERY', key: 'status', operator: 'EQ', value: '   ' }],
          response: { status: 200, headers: {}, body: 'ok' },
        },
      ],
    } satisfies SimulationConfigPayload;

    expect(validatePayload(payload)).toContain('分支 1 条件 1 值不能为空。');
  });

  it('allows EXISTS conditions without values', () => {
    const payload = {
      ...defaultPayload('HTTP'),
      branches: [
        {
          name: 'Exists without value',
          priority: 0,
          conditions: [{ source: 'QUERY', key: 'status', operator: 'EXISTS' }],
          response: { status: 200, headers: {}, body: 'ok' },
        },
      ],
    } satisfies SimulationConfigPayload;

    expect(validatePayload(payload)).not.toContain('分支 1 条件 1 值不能为空。');
  });

  it('normalizes tags by trimming blanks and removing duplicates', () => {
    expect(normalizeTags(['  订单  ', '', '回归', '订单', '  ', '联调'])).toEqual(['订单', '回归', '联调']);
  });

  it('filters simulations by exact tag while search matches names', () => {
    const orderConfig: SimulationConfig = {
      id: 'order',
      name: '订单查询',
      protocol: 'HTTP',
      enabled: true,
      tags: ['订单', '回归'],
      http: { method: 'GET', path: '/orders/{id}', matchMode: 'TEMPLATE' },
      branches: [],
      defaultResponse: { status: 200, headers: {}, body: 'ok' },
    };
    const paymentConfig: SimulationConfig = {
      id: 'payment',
      name: '支付回调',
      protocol: 'HTTP',
      enabled: true,
      tags: ['支付'],
      http: { method: 'POST', path: '/payments/callback', matchMode: 'EXACT' },
      branches: [],
      defaultResponse: { status: 200, headers: {}, body: 'ok' },
    };

    expect(availableTags([paymentConfig, orderConfig])).toEqual(['订单', '回归', '支付']);
    expect(filterSimulations([orderConfig, paymentConfig], {
      search: '',
      protocolFilter: 'ALL',
      enabledFilter: 'ALL',
      tagFilter: '回归',
    })).toEqual([orderConfig]);
    expect(filterSimulations([orderConfig, paymentConfig], {
      search: '回调',
      protocolFilter: 'ALL',
      enabledFilter: 'ALL',
      tagFilter: 'ALL',
    })).toEqual([paymentConfig]);
  });

  it('filters simulations by fuzzy name search only', () => {
    const nameMatchConfig: SimulationConfig = {
      id: 'name-match',
      name: '订单查询主流程',
      protocol: 'HTTP',
      enabled: true,
      tags: ['回归'],
      http: { method: 'GET', path: '/users/{id}', matchMode: 'TEMPLATE' },
      branches: [],
      defaultResponse: { status: 200, headers: {}, body: 'ok' },
    };
    const tagOnlyMatchConfig: SimulationConfig = {
      id: 'tag-only-match',
      name: '用户详情',
      protocol: 'HTTP',
      enabled: true,
      tags: ['订单'],
      http: { method: 'GET', path: '/profiles/{id}', matchMode: 'TEMPLATE' },
      branches: [],
      defaultResponse: { status: 200, headers: {}, body: 'ok' },
    };
    const pathOnlyMatchConfig: SimulationConfig = {
      id: 'path-only-match',
      name: '账户详情',
      protocol: 'HTTP',
      enabled: true,
      tags: ['用户'],
      http: { method: 'GET', path: '/orders/{id}', matchMode: 'TEMPLATE' },
      branches: [],
      defaultResponse: { status: 200, headers: {}, body: 'ok' },
    };

    expect(filterSimulations([nameMatchConfig, tagOnlyMatchConfig, pathOnlyMatchConfig], {
      search: '订单',
      protocolFilter: 'ALL',
      enabledFilter: 'ALL',
      tagFilter: 'ALL',
    })).toEqual([nameMatchConfig]);
  });

  it('builds tag filter options with an all option first', () => {
    expect(tagFilterOptions(['外接门', 'dasdas'])).toEqual([
      { value: 'ALL', label: '全部' },
      { value: '外接门', label: '外接门' },
      { value: 'dasdas', label: 'dasdas' },
    ]);
  });

});

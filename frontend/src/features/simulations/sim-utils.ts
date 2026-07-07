import type { ProtocolType, SimulationConfig, SimulationConfigPayload } from './types';

export function displayEndpoint(config: SimulationConfig): string {
  if (config.protocol === 'HTTP') {
    const method = (config.http?.method || 'GET').toUpperCase();
    const path = config.http?.path || '未配置路径';
    return `${method} ${path}`;
  }

  const host = config.tcp?.host || '127.0.0.1';
  const port = config.tcp?.port ?? '?';
  const frameMode = config.tcp?.frameMode || 'LINE';
  return `${host}:${port} ${frameMode}`;
}

export function countBranches(config: Pick<SimulationConfig, 'branches'>): number {
  return config.branches?.length || 0;
}

export function protocolBadgeVariant(protocol: ProtocolType): 'indigo' | 'mint' {
  return protocol === 'HTTP' ? 'indigo' : 'mint';
}

export function defaultPayload(protocol: ProtocolType): SimulationConfigPayload {
  if (protocol === 'TCP') {
    return {
      name: 'TCP 行报文模拟',
      protocol: 'TCP',
      enabled: true,
      http: null,
      tcp: {
        host: '127.0.0.1',
        port: 19001,
        frameMode: 'LINE',
      },
      requestTemplate: {
        headers: {},
        query: {},
        body: 'ping',
      },
      branches: [
        {
          name: '成功分支',
          priority: 0,
          conditions: [
            {
              source: 'TCP_BODY',
              key: null,
              operator: 'CONTAINS',
              value: 'ping',
            },
          ],
          response: {
            status: 200,
            headers: {},
            body: 'pong {{random.uuid}}',
            delayMs: 0,
          },
          responseVariants: [
            {
              status: 500,
              headers: {},
              body: 'ERR 模拟异常 {{random.uuid}}\n',
              delayMs: 0,
            },
          ],
          variantStrategy: 'ROUND_ROBIN',
        },
      ],
      defaultResponse: {
        status: 404,
        headers: {},
        body: '未找到',
        delayMs: 0,
      },
    };
  }

  return {
    name: 'HTTP 用户模拟',
    protocol: 'HTTP',
    enabled: true,
    http: {
      method: 'GET',
      path: '/api/users/{id}',
      matchMode: 'TEMPLATE',
    },
    tcp: null,
    requestTemplate: {
      headers: {},
      query: { status: 'ok' },
      body: null,
    },
    branches: [
      {
        name: '成功分支',
        priority: 0,
        conditions: [
          {
            source: 'QUERY',
            key: 'status',
            operator: 'EQ',
            value: 'ok',
          },
        ],
        response: {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
          body: '{\n  "id": "{{path.id}}",\n  "requestId": "{{random.uuid}}",\n  "status": "ok"\n}',
          delayMs: 0,
        },
        responseVariants: [
          {
            status: 500,
            headers: { 'Content-Type': 'application/json' },
            body: '{\n  "error": "模拟异常",\n  "requestId": "{{random.uuid}}"\n}',
            delayMs: 0,
          },
        ],
        variantStrategy: 'ROUND_ROBIN',
      },
    ],
    defaultResponse: {
      status: 404,
      headers: { 'Content-Type': 'application/json' },
      body: '{"error":"未找到"}',
      delayMs: 0,
    },
  };
}

const HTTP_MATCH_MODES = new Set(['EXACT', 'PREFIX', 'TEMPLATE']);
const TCP_FRAME_MODES = new Set(['LINE', 'LENGTH_HEADER', 'HEX']);
const CONDITION_SOURCES = new Set(['QUERY', 'HEADER', 'PATH', 'BODY', 'TCP_BODY']);
const CONDITION_OPERATORS = new Set(['EQ', 'NOT_EQ', 'CONTAINS', 'REGEX', 'EXISTS', 'JSON_PATH']);
const RESPONSE_VARIANT_STRATEGIES = new Set(['ROUND_ROBIN', 'RANDOM']);

export function validatePayload(payload: SimulationConfigPayload): string[] {
  const errors: string[] = [];

  if (typeof payload.name !== 'string' || !payload.name.trim()) {
    errors.push('模拟名称不能为空。');
  }

  if (payload.protocol === 'HTTP') {
    const method = payload.http?.method;
    const path = payload.http?.path;
    const matchMode = payload.http?.matchMode;

    if (typeof method !== 'string' || !method.trim()) {
      errors.push('HTTP 方法不能为空。');
    }
    if (typeof path !== 'string' || !path.trim()) {
      errors.push('HTTP 路径不能为空。');
    } else if (!path.trim().startsWith('/')) {
      errors.push('HTTP 路径必须以 / 开头。');
    }
    if (!HTTP_MATCH_MODES.has(String(matchMode))) {
      errors.push('HTTP 匹配模式无效。');
    }
  }

  if (payload.protocol === 'TCP') {
    const host = payload.tcp?.host;
    const port = payload.tcp?.port;
    const frameMode = payload.tcp?.frameMode;

    if (typeof host !== 'string' || !host.trim()) {
      errors.push('TCP 主机不能为空。');
    }
    if (port == null) {
      errors.push('TCP 端口不能为空。');
    } else if (!isIntegerInRange(port, 1, 65535)) {
      errors.push('TCP 端口必须是 1 到 65535 之间的整数。');
    }
    if (!TCP_FRAME_MODES.has(String(frameMode))) {
      errors.push('TCP 报文模式无效。');
    }
  }

  if (!isObject(payload.defaultResponse) || !isIntegerInRange(payload.defaultResponse.status, 100, 999)) {
    errors.push('默认响应状态码必须是 100 到 999 之间的整数。');
  }

  if (!Array.isArray(payload.branches)) {
    errors.push('分支必须是 JSON 数组。');
    return errors;
  }

  payload.branches.forEach((branch, branchIndex) => {
    const branchLabel = `分支 ${branchIndex + 1}`;
    if (!isObject(branch)) {
      errors.push(`${branchLabel} 必须是对象。`);
      return;
    }

    if (typeof branch.name !== 'string' || !branch.name.trim()) {
      errors.push(`${branchLabel} 名称不能为空。`);
    }

    if (!Number.isInteger(branch.priority)) {
      errors.push(`${branchLabel} 优先级必须是整数。`);
    }

    if (!isObject(branch.response)) {
      errors.push(`${branchLabel} 响应不能为空。`);
    } else if (branch.response.status != null && !isIntegerInRange(branch.response.status, 100, 999)) {
      errors.push(`${branchLabel} 响应状态码必须是 100 到 999 之间的整数。`);
    }

    if (branch.responseVariants != null) {
      if (!Array.isArray(branch.responseVariants)) {
        errors.push(`${branchLabel} 响应变体必须是数组。`);
      } else {
        branch.responseVariants.forEach((responseVariant, responseIndex) => {
          const responseLabel = `${branchLabel} 响应变体 ${responseIndex + 1}`;
          if (!isObject(responseVariant)) {
            errors.push(`${responseLabel} 必须是对象。`);
          } else if (responseVariant.status != null && !isIntegerInRange(responseVariant.status, 100, 999)) {
            errors.push(`${responseLabel} 状态码必须是 100 到 999 之间的整数。`);
          }
        });
      }
    }

    if (branch.variantStrategy != null && !RESPONSE_VARIANT_STRATEGIES.has(String(branch.variantStrategy))) {
      errors.push(`${branchLabel} 响应变体策略无效。`);
    }

    if (branch.conditions != null && !Array.isArray(branch.conditions)) {
      errors.push(`${branchLabel} 条件必须是数组。`);
      return;
    }

    branch.conditions?.forEach((condition, conditionIndex) => {
      const conditionLabel = `${branchLabel} 条件 ${conditionIndex + 1}`;
      if (!isObject(condition)) {
        errors.push(`${conditionLabel} 必须是对象。`);
        return;
      }

      const source = String(condition.source ?? '');
      const operator = String(condition.operator ?? '');
      const key = typeof condition.key === 'string' ? condition.key.trim() : '';

      if (!CONDITION_SOURCES.has(source)) {
        errors.push(`${conditionLabel} 来源无效。`);
      }
      if (!CONDITION_OPERATORS.has(operator)) {
        errors.push(`${conditionLabel} 操作符无效。`);
      }
      if ((source === 'QUERY' || source === 'HEADER' || source === 'PATH' || operator === 'JSON_PATH') && !key) {
        errors.push(`${conditionLabel} 键不能为空。`);
      }
      if (operator !== 'EXISTS' && (typeof condition.value !== 'string' || !condition.value.trim())) {
        errors.push(`${conditionLabel} 值不能为空。`);
      }
    });
  });

  return errors;
}

function isObject(value: unknown): value is Record<string, any> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isIntegerInRange(value: unknown, min: number, max: number): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= min && value <= max;
}

export function configToPayload(config: SimulationConfig): SimulationConfigPayload {
  return {
    id: config.id,
    name: config.name,
    protocol: config.protocol,
    enabled: config.enabled,
    http: config.protocol === 'HTTP' ? config.http || defaultPayload('HTTP').http : null,
    tcp: config.protocol === 'TCP' ? config.tcp || defaultPayload('TCP').tcp : null,
    requestTemplate: config.requestTemplate || { headers: {}, query: {}, body: null },
    branches: config.branches || [],
    defaultResponse: config.defaultResponse || defaultPayload(config.protocol).defaultResponse,
  };
}

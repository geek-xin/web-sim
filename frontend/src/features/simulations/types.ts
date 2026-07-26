export type ProtocolType = 'HTTP' | 'TCP';
export type HttpMatchMode = 'EXACT' | 'PREFIX' | 'TEMPLATE';
export type TcpFrameMode = 'LINE' | 'LENGTH_HEADER' | 'HEX';
export type ConditionSource = 'QUERY' | 'HEADER' | 'PATH' | 'BODY' | 'TCP_BODY';
export type ConditionOperator = 'EQ' | 'NOT_EQ' | 'CONTAINS' | 'REGEX' | 'EXISTS' | 'JSON_PATH';
export type ResponseVariantStrategy = 'ROUND_ROBIN' | 'RANDOM';

export interface HttpRule {
  method: string;
  path: string;
  matchMode: HttpMatchMode;
}

export interface TcpRule {
  host: string;
  port?: number;
  frameMode: TcpFrameMode;
}

export interface RequestTemplate {
  headers?: Record<string, string>;
  query?: Record<string, string>;
  body?: string | null;
}

export interface SimulationCondition {
  source: ConditionSource;
  key?: string | null;
  operator: ConditionOperator;
  value?: string | null;
}

export interface SimulationResponse {
  status?: number | null;
  headers?: Record<string, string>;
  body?: string | null;
  delayMs?: number | null;
}

export interface SimulationBranch {
  name: string;
  priority: number;
  conditions: SimulationCondition[];
  response: SimulationResponse;
  responseVariants?: SimulationResponse[] | null;
  variantStrategy?: ResponseVariantStrategy | null;
  probability?: number | null;
}

export interface SimulationConfig {
  id: string;
  name: string;
  protocol: ProtocolType;
  enabled: boolean;
  http?: HttpRule | null;
  tcp?: TcpRule | null;
  requestTemplate?: RequestTemplate | null;
  branches?: SimulationBranch[] | null;
  defaultResponse: SimulationResponse;
}

export interface SimulationConfigPayload {
  id?: string;
  name: string;
  protocol: ProtocolType;
  enabled: boolean;
  http?: HttpRule | null;
  tcp?: TcpRule | null;
  requestTemplate?: RequestTemplate | null;
  branches: SimulationBranch[];
  defaultResponse: SimulationResponse;
}

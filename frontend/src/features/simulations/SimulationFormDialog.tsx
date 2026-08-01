import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { X } from 'lucide-react';
import { toast } from 'sonner';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { cn } from '@/lib/utils';
import {
  configToPayload,
  defaultPayload,
  hasEnabledErrorBranch,
  hasErrorBranch,
  normalizeTags,
  setErrorBranchesEnabled,
  shouldResetProtocolPayload,
  validatePayload,
} from './sim-utils';
import type { HttpMatchMode, ProtocolType, SimulationBranch, SimulationConfig, SimulationConfigPayload, TcpFrameMode } from './types';

type FormMode = 'create' | 'edit' | 'copy';
type ResponseGuideTab = 'edit' | 'template' | 'fields';

interface SimulationFormDialogProps {
  open: boolean;
  mode: FormMode;
  config?: SimulationConfig | null;
  onOpenChange: (open: boolean) => void;
  onSubmit: (payload: SimulationConfigPayload) => Promise<void> | void;
}

export function SimulationFormDialog({ open, mode, config, onOpenChange, onSubmit }: SimulationFormDialogProps) {
  const [payload, setPayload] = useState<SimulationConfigPayload>(() => initialPayload(mode, config));
  const [tagsText, setTagsText] = useState(() => tagsToText(initialPayload(mode, config).tags));
  const [branchesText, setBranchesText] = useState(() => JSON.stringify(initialPayload(mode, config).branches, null, 2));
  const [submitting, setSubmitting] = useState(false);
  const [defaultGuideTab, setDefaultGuideTab] = useState<ResponseGuideTab>('edit');
  const [branchGuideTab, setBranchGuideTab] = useState<ResponseGuideTab>('edit');
  const protocolLocked = mode === 'edit' && payload.enabled;
  const parsedBranches = useMemo(() => parseBranchesText(branchesText), [branchesText]);
  const canToggleErrorBranch = parsedBranches != null && hasErrorBranch(parsedBranches);
  const errorBranchEnabled = parsedBranches != null && hasEnabledErrorBranch(parsedBranches);

  useEffect(() => {
    if (!open) return;
    const next = initialPayload(mode, config);
    setPayload(next);
    setTagsText(tagsToText(next.tags));
    setBranchesText(JSON.stringify(next.branches, null, 2));
    setDefaultGuideTab('edit');
    setBranchGuideTab('edit');
  }, [config, mode, open]);

  const title = useMemo(() => {
    if (mode === 'edit') return '编辑模拟配置';
    if (mode === 'copy') return '复制模拟配置';
    return '创建模拟配置';
  }, [mode]);

  const handleProtocolChange = (protocol: ProtocolType) => {
    if (!shouldResetProtocolPayload(payload.protocol, protocol)) {
      return;
    }
    const next = withFormattedDefaultBody(defaultPayload(protocol));
    setPayload((current) => ({
      ...next,
      id: current.id,
      name: current.name || next.name,
      tags: normalizeTags(splitTagsText(tagsText)),
      enabled: current.enabled,
    }));
    setBranchesText(JSON.stringify(next.branches, null, 2));
  };

  const formatDefaultBody = () => {
    setPayload((current) => ({
      ...current,
      defaultResponse: {
        ...current.defaultResponse,
        body: formatJsonText(current.defaultResponse.body),
      },
    }));
  };

  const toggleErrorBranch = () => {
    const branches = parseBranchesText(branchesText);
    if (!branches) {
      toast.error('分支 JSON 格式无效，无法切换错误分支。');
      return;
    }
    if (!hasErrorBranch(branches)) {
      toast.error('未找到错误分支。');
      return;
    }
    setBranchesText(JSON.stringify(setErrorBranchesEnabled(branches, !hasEnabledErrorBranch(branches)), null, 2));
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    let branches: SimulationConfigPayload['branches'];
    try {
      branches = JSON.parse(branchesText) as SimulationConfigPayload['branches'];
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '分支 JSON 格式无效。');
      return;
    }

    const normalized: SimulationConfigPayload = {
      ...payload,
      name: payload.name.trim(),
      tags: normalizeTags(splitTagsText(tagsText)),
      branches,
      http: payload.protocol === 'HTTP' ? payload.http : null,
      tcp: payload.protocol === 'TCP' ? payload.tcp : null,
      defaultResponse: {
        ...payload.defaultResponse,
        status: Number(payload.defaultResponse.status),
      },
    };

    const errors = validatePayload(normalized);
    if (errors.length > 0) {
      toast.error(errors.join('\n'));
      return;
    }

    try {
      setSubmitting(true);
      await onSubmit(normalized);
      onOpenChange(false);
    } catch (error) {
      const message = error instanceof Error ? error.message : '保存模拟配置失败。';
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[1180px] gap-0 overflow-hidden p-0 [&>button.absolute]:hidden">
        <DialogHeader className="relative z-20 rounded-t-[21px] border-b-[3px] border-clay-border bg-white/95 p-6 pr-20 shadow-[0_4px_0_rgba(17,17,17,0.12)] backdrop-blur">
          <button
            type="button"
            className="absolute right-5 top-5 inline-flex cursor-pointer items-center justify-center rounded-full border-[3px] border-clay-border bg-clay-pink p-1 shadow-clay-sm transition hover:-translate-y-0.5 hover:shadow-clay focus:outline-none focus:ring-4 focus:ring-clay-primary/35"
            aria-label="关闭编辑器"
            onClick={() => onOpenChange(false)}
          >
            <X className="h-4 w-4" />
          </button>
          <div className="flex flex-row items-start justify-between gap-4">
            <div className="grid gap-2">
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant={payload.protocol === 'HTTP' ? 'indigo' : 'mint'}>{payload.protocol}</Badge>
                <Badge variant={payload.enabled ? 'mint' : 'muted'}>{payload.enabled ? '已启用' : '已停用'}</Badge>
              </div>
              <DialogTitle>{title}</DialogTitle>
              <DialogDescription>
                配置基础字段、匹配规则、默认响应和分支 JSON。
              </DialogDescription>
            </div>
            <div className="absolute right-8 top-20 flex items-start gap-3 self-start">
              <Button
                type="submit"
                form="simulation-config-form"
                variant="primary"
                disabled={submitting}
              >
                {submitting ? '保存中...' : '保存模拟配置'}
              </Button>
            </div>
          </div>
        </DialogHeader>

        <form id="simulation-config-form" className="grid max-h-[calc(90vh-158px)] gap-5 overflow-y-auto p-6" onSubmit={handleSubmit}>
          <section className="grid gap-4 rounded-[24px] border-[3px] border-clay-border bg-clay-cream p-4 shadow-clay-sm">
            <div className="grid grid-cols-[minmax(0,1fr)_minmax(260px,0.8fr)_180px] items-stretch gap-4">
              <Field label="名称" htmlFor="simulation-name">
                <Input
                  id="simulation-name"
                  value={payload.name}
                  onChange={(event) => setPayload((current) => ({ ...current, name: event.target.value }))}
                  placeholder="模拟配置名称"
                  required
                />
              </Field>

              <Field label="标签" htmlFor="simulation-tags">
                <Input
                  id="simulation-tags"
                  value={tagsText}
                  onChange={(event) => setTagsText(event.target.value)}
                  placeholder="订单, 回归, 联调"
                />
              </Field>

              <div className="grid gap-2 text-sm font-black text-clay-ink">
                <span className="flex min-h-5 items-center justify-between gap-2">
                  协议
                  {protocolLocked ? (
                    <span className="text-[0.68rem] font-bold text-clay-muted">需先停用</span>
                  ) : null}
                </span>
                <ProtocolPicker
                  value={payload.protocol}
                  disabled={protocolLocked}
                  onChange={handleProtocolChange}
                />
              </div>
            </div>
          </section>

          {payload.protocol === 'HTTP' ? (
            <section className="grid gap-4 rounded-[24px] border-[3px] border-clay-border bg-white p-4 shadow-clay-sm" aria-label="HTTP 匹配字段">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="text-lg font-black text-clay-ink">HTTP 匹配</h3>
                <p className="text-[0.68rem] font-bold leading-relaxed text-clay-muted">
                  <strong className="text-clay-ink">EXACT</strong> 完全一致 · <strong className="text-clay-ink">PREFIX</strong> 前缀命中 · <strong className="text-clay-ink">TEMPLATE</strong> 支持 <code>{'{id}'}</code> 变量
                </p>
              </div>
              <div className="grid grid-cols-[160px_minmax(0,1fr)_300px] gap-4">
                <Field label="方法" htmlFor="http-method">
                  <Input
                    id="http-method"
                    value={payload.http?.method || ''}
                    onChange={(event) => setPayload((current) => ({
                      ...current,
                      http: { method: event.target.value.toUpperCase(), path: current.http?.path || '', matchMode: current.http?.matchMode || 'TEMPLATE' },
                    }))}
                    placeholder="GET"
                  />
                </Field>
                <Field label="路径" htmlFor="http-path">
                  <Input
                    id="http-path"
                    value={payload.http?.path || ''}
                    onChange={(event) => setPayload((current) => ({
                      ...current,
                      http: { method: current.http?.method || 'GET', path: event.target.value, matchMode: current.http?.matchMode || 'TEMPLATE' },
                    }))}
                    placeholder="/api/users/{id}"
                  />
                </Field>
                <div className="grid gap-2 text-sm font-black text-clay-ink">
                  <span>匹配模式</span>
                  <SegmentedPicker<HttpMatchMode>
                    ariaLabel="HTTP 匹配模式"
                    value={payload.http?.matchMode || 'TEMPLATE'}
                    options={[
                      { value: 'EXACT', label: 'EXACT' },
                      { value: 'PREFIX', label: 'PREFIX' },
                      { value: 'TEMPLATE', label: 'TEMPLATE' },
                    ]}
                    onChange={(matchMode) => setPayload((current) => ({
                      ...current,
                      http: { method: current.http?.method || 'GET', path: current.http?.path || '', matchMode },
                    }))}
                  />
                </div>
              </div>
            </section>
          ) : (
            <section className="grid gap-4 rounded-[24px] border-[3px] border-clay-border bg-white p-4 shadow-clay-sm" aria-label="TCP 监听字段">
              <h3 className="text-lg font-black text-clay-ink">TCP 监听</h3>
              <div className="grid grid-cols-[minmax(0,1fr)_160px_320px] gap-4">
                <Field label="主机" htmlFor="tcp-host">
                  <Input
                    id="tcp-host"
                    value={payload.tcp?.host || ''}
                    onChange={(event) => setPayload((current) => ({
                      ...current,
                      tcp: { host: event.target.value, port: current.tcp?.port, frameMode: current.tcp?.frameMode || 'LINE' },
                    }))}
                    placeholder="127.0.0.1"
                  />
                </Field>
                <Field label="端口" htmlFor="tcp-port">
                  <Input
                    id="tcp-port"
                    type="number"
                    min={1}
                    max={65535}
                    value={payload.tcp?.port ?? ''}
                    onChange={(event) => setPayload((current) => ({
                      ...current,
                      tcp: { host: current.tcp?.host || '127.0.0.1', port: event.target.value ? Number(event.target.value) : undefined, frameMode: current.tcp?.frameMode || 'LINE' },
                    }))}
                    placeholder="19001"
                  />
                </Field>
                <div className="grid gap-2 text-sm font-black text-clay-ink">
                  <span>报文模式</span>
                  <SegmentedPicker<TcpFrameMode>
                    ariaLabel="TCP 报文模式"
                    value={payload.tcp?.frameMode || 'LINE'}
                    options={[
                      { value: 'LINE', label: 'LINE' },
                      { value: 'LENGTH_HEADER', label: 'LENGTH' },
                      { value: 'HEX', label: 'HEX' },
                    ]}
                    onChange={(frameMode) => setPayload((current) => ({
                      ...current,
                      tcp: { host: current.tcp?.host || '127.0.0.1', port: current.tcp?.port, frameMode },
                    }))}
                  />
                </div>
              </div>
            </section>
          )}

          <div className="grid items-start grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] gap-5">
            <section className="grid content-start gap-4 rounded-[24px] border-[3px] border-clay-border bg-white p-4 shadow-clay-sm">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <h3 className="text-lg font-black text-clay-ink">默认响应</h3>
                <GuideTabs
                  ariaLabel="默认响应视图"
                  value={defaultGuideTab}
                  onChange={setDefaultGuideTab}
                />
              </div>
              {defaultGuideTab === 'template' ? <TemplateReference title="默认响应体模板" /> : null}
              {defaultGuideTab === 'fields' ? <DefaultBodyFieldReference /> : null}
              {defaultGuideTab === 'edit' ? (
                <div className="grid gap-4">
                  <Field label="状态码" htmlFor="default-status">
                    <Input
                      id="default-status"
                      type="number"
                      value={payload.defaultResponse.status ?? ''}
                      onChange={(event) => setPayload((current) => ({
                        ...current,
                        defaultResponse: { ...current.defaultResponse, status: event.target.value ? Number(event.target.value) : null },
                      }))}
                    />
                  </Field>
                  <Field label="响应体" htmlFor="default-body">
                    <Textarea
                      id="default-body"
                      value={payload.defaultResponse.body || ''}
                      onChange={(event) => setPayload((current) => ({
                        ...current,
                        defaultResponse: { ...current.defaultResponse, body: event.target.value },
                      }))}
                      onBlur={formatDefaultBody}
                      rows={12}
                    />
                  </Field>
                </div>
              ) : null}
            </section>

            <section className="grid content-start gap-4 rounded-[24px] border-[3px] border-clay-border bg-white p-4 shadow-clay-sm">
              <div className="grid gap-2">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <label className="text-sm font-black text-clay-ink" htmlFor="branches-json">分支 JSON</label>
                  <div className="flex flex-wrap items-center gap-2">
                    <GuideTabs
                      ariaLabel="分支 JSON 视图"
                      value={branchGuideTab}
                      onChange={setBranchGuideTab}
                    />
                    <Button
                      type="button"
                      variant={errorBranchEnabled ? 'danger' : 'primary'}
                      size="sm"
                      disabled={!canToggleErrorBranch}
                      onClick={toggleErrorBranch}
                    >
                      {errorBranchEnabled ? '禁用' : '启用'}
                    </Button>
                  </div>
                </div>
                <p id="branches-json-help" className="text-xs font-bold text-clay-muted">
                  可直接编辑分支数组；支持条件、优先级、主响应、probability 出现概率、responseVariants 响应变体，以及 ROUND_ROBIN/RANDOM 交错策略。
                </p>
                {branchGuideTab === 'template' ? <TemplateReference title="分支 / 错误响应体模板" /> : null}
                {branchGuideTab === 'fields' ? <BranchFieldReference /> : null}
                {branchGuideTab === 'edit' ? (
                  <Textarea
                    id="branches-json"
                    value={branchesText}
                    onChange={(event) => setBranchesText(event.target.value)}
                    rows={16}
                    aria-describedby="branches-json-help"
                  />
                ) : null}
              </div>
            </section>
          </div>

        </form>
      </DialogContent>
    </Dialog>
  );
}

function initialPayload(mode: FormMode, config?: SimulationConfig | null): SimulationConfigPayload {
  if (!config) return withFormattedDefaultBody(defaultPayload('HTTP'));
  const payload = withFormattedDefaultBody(configToPayload(config));
  if (mode === 'copy') {
    const { id: _id, ...copy } = payload;
    return { ...copy, name: `${payload.name} 副本`, enabled: false };
  }
  return payload;
}

function withFormattedDefaultBody(payload: SimulationConfigPayload): SimulationConfigPayload {
  return {
    ...payload,
    tags: normalizeTags(payload.tags),
    defaultResponse: {
      ...payload.defaultResponse,
      body: formatJsonText(payload.defaultResponse.body),
    },
  };
}

function tagsToText(tags?: string[]): string {
  return normalizeTags(tags).join(', ');
}

function splitTagsText(value: string): string[] {
  return value.split(/[,，\n]/);
}

function formatJsonText(value?: string | null): string {
  const text = value || '';
  if (!text.trim()) return text;
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch (_error) {
    return text;
  }
}

function parseBranchesText(value: string): SimulationBranch[] | null {
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed as SimulationBranch[] : null;
  } catch (_error) {
    return null;
  }
}

function Field({ label, htmlFor, children }: { label: string; htmlFor: string; children: ReactNode }) {
  return (
    <label className="grid gap-2 text-sm font-black text-clay-ink" htmlFor={htmlFor}>
      <span>{label}</span>
      {children}
    </label>
  );
}

function GuideTabs({
  ariaLabel,
  value,
  onChange,
}: {
  ariaLabel: string;
  value: ResponseGuideTab;
  onChange: (value: ResponseGuideTab) => void;
}) {
  return (
    <SegmentedPicker<ResponseGuideTab>
      ariaLabel={ariaLabel}
      value={value}
      options={[
        { value: 'edit', label: '编辑' },
        { value: 'template', label: '模板' },
        { value: 'fields', label: '字段' },
      ]}
      onChange={onChange}
    />
  );
}

function TemplateReference({ title }: { title: string }) {
  const examples = [
    { token: '{{path.id}}', detail: '路径模板变量，例如 /users/{id} 中的 id。' },
    { token: '{{query.seq}}', detail: '查询参数，例如 ?seq=10。' },
    { token: '{{request.header.X-Request-Id}}', detail: '请求头，区分大小写。' },
    { token: '{{tcp.body}}', detail: 'TCP 请求报文内容。' },
    { token: '{{random.uuid}}', detail: '随机 UUID。' },
    { token: '{{random.int:1,100}}', detail: '1 到 100 的随机整数，包含两端。' },
    { token: '{{random.float:0,1}}', detail: '0 到 1 的随机小数。' },
    { token: '{{random.bool}}', detail: '随机 true / false。' },
    { token: '{{random.timestamp}}', detail: '当前 ISO 时间。' },
    { token: '{{random.pick:A,B,C}}', detail: '从列表中随机选择一个值。' },
    { token: '{{random.name}}', detail: '随机姓名。' },
  ];

  return (
    <div className="rounded-2xl border-[3px] border-clay-border bg-clay-cream p-3 shadow-[1px_2px_0_rgba(17,17,17,0.45)]">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <strong className="text-sm font-black text-clay-ink">{title}</strong>
        <span className="text-[0.68rem] font-bold text-clay-muted">响应体和响应头均支持</span>
      </div>
      <div className="mt-3 grid grid-cols-2 gap-2">
        {examples.map((example) => (
          <div key={example.token} className="rounded-xl border-[2px] border-clay-border bg-white px-3 py-2">
            <code className="block break-all text-[0.7rem] font-black text-clay-ink">{example.token}</code>
            <span className="mt-1 block text-[0.68rem] font-bold leading-snug text-clay-muted">{example.detail}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function DefaultBodyFieldReference() {
  const fields = [
    { name: 'status', detail: '响应状态码。HTTP 通常使用 200、404、503；TCP 错误可用 0 或业务约定状态。' },
    { name: 'headers', detail: '响应头对象，值里也可以使用模板变量，例如 X-Trace-Id。' },
    { name: 'body', detail: '实际返回的响应体字符串；可以写 JSON、文本或 TCP 报文内容。' },
    { name: 'delayMs', detail: '响应延迟毫秒数，0 表示立即返回。' },
    { name: 'ok', detail: '示例业务字段，表示请求是否成功；可以按你的接口协议改名或删除。' },
    { name: 'id', detail: '示例业务字段，通常填 {{path.id}}，来自路径模板变量。' },
    { name: 'sequence', detail: '示例业务字段，通常填 {{query.seq}}，来自查询参数。' },
    { name: 'trace', detail: '示例业务字段，通常填 {{request.header.X-Request-Id}}，用于回传请求链路 ID。' },
    { name: 'randomName', detail: '示例随机姓名字段，使用 {{random.name}}。' },
    { name: 'randomInt', detail: '示例随机整数字段，使用 {{random.int:min,max}}。' },
    { name: 'timestamp', detail: '示例时间字段，使用 {{random.timestamp}} 生成当前 ISO 时间。' },
  ];

  return <FieldReference title="默认响应字段说明" fields={fields} />;
}

function BranchFieldReference() {
  const fields = [
    { name: 'name', detail: '分支名称，用于管理台展示和日志识别，例如“错误分支”。' },
    { name: 'priority', detail: '匹配优先级，数字越小越先判断。多个条件分支同时命中时，优先级小的先返回。' },
    { name: 'conditions', detail: '分支条件数组。为空时表示无条件分支，会参与默认响应之间的交错或概率选择。' },
    { name: 'conditions.source', detail: '条件来源：QUERY、HEADER、PATH、BODY 或 TCP_BODY。' },
    { name: 'conditions.key', detail: '要读取的参数名、请求头名、路径变量名或 JSON Path。' },
    { name: 'conditions.operator', detail: '比较方式：EQ、NOT_EQ、CONTAINS、REGEX、EXISTS、JSON_PATH。' },
    { name: 'conditions.value', detail: '比较目标值；EXISTS 不需要 value。' },
    { name: 'response', detail: '分支命中后的主响应对象。' },
    { name: 'response.status', detail: '分支响应状态码，例如错误分支常用 503。' },
    { name: 'response.headers', detail: '分支响应头对象，值支持模板变量。' },
    { name: 'response.body', detail: '分支响应体，常用于模拟错误 JSON 或异常报文。' },
    { name: 'response.delayMs', detail: '分支响应延迟毫秒数。' },
    { name: 'probability', detail: '无条件分支出现概率，范围 0 到 1；多个分支概率总和小于 1 时，剩余概率走默认响应。' },
    { name: 'responseVariants', detail: '响应变体数组。分支命中后会在主响应和变体响应之间选择。' },
    { name: 'responseVariantsEnabled', detail: '是否启用响应变体；关闭后保留变体定义但只返回主响应。' },
    { name: 'variantStrategy', detail: '响应变体策略：ROUND_ROBIN 轮询，RANDOM 随机。' },
  ];

  return <FieldReference title="分支 JSON 字段说明" fields={fields} />;
}

function FieldReference({ title, fields }: { title: string; fields: Array<{ name: string; detail: string }> }) {
  return (
    <div className="rounded-2xl border-[3px] border-clay-border bg-clay-cream p-3 shadow-[1px_2px_0_rgba(17,17,17,0.45)]">
      <strong className="text-sm font-black text-clay-ink">{title}</strong>
      <div className="mt-3 grid grid-cols-2 gap-2">
        {fields.map((field) => (
          <div key={field.name} className="rounded-xl border-[2px] border-clay-border bg-white px-3 py-2">
            <code className="block break-all text-[0.7rem] font-black text-clay-ink">{field.name}</code>
            <span className="mt-1 block text-[0.68rem] font-bold leading-snug text-clay-muted">{field.detail}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function ProtocolPicker({
  value,
  disabled,
  onChange,
}: {
  value: ProtocolType;
  disabled: boolean;
  onChange: (protocol: ProtocolType) => void;
}) {
  const options: ProtocolType[] = ['HTTP', 'TCP'];
  return (
    <SegmentedPicker<ProtocolType>
      ariaLabel="协议"
      value={value}
      disabled={disabled}
      title={disabled ? '启用中的配置需要先停用后才能修改协议' : undefined}
      options={options.map((option) => ({ value: option, label: option }))}
      onChange={onChange}
    />
  );
}

function SegmentedPicker<T extends string>({
  ariaLabel,
  value,
  options,
  onChange,
  disabled = false,
  title,
}: {
  ariaLabel: string;
  value: T;
  options: Array<{ value: T; label: string }>;
  onChange: (value: T) => void;
  disabled?: boolean;
  title?: string;
}) {
  return (
    <div
      className={cn(
        'inline-flex h-12 min-w-0 items-center gap-1 rounded-2xl border-[3px] border-clay-border bg-white p-1 shadow-[1px_2px_0_rgba(17,17,17,0.55)]',
        disabled && 'bg-clay-cream',
      )}
      role="radiogroup"
      aria-label={ariaLabel}
      title={title}
    >
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={selected}
            disabled={disabled}
            onClick={() => onChange(option.value)}
            className={cn(
              'h-full min-w-0 flex-1 rounded-xl border-[3px] border-transparent px-3 text-sm font-black text-clay-muted transition focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-clay-primary/35 disabled:cursor-not-allowed',
              selected && 'border-clay-border bg-clay-primary text-white',
              !selected && !disabled && 'hover:border-clay-border hover:bg-clay-cream hover:text-clay-ink',
              disabled && selected && 'bg-white text-clay-muted',
            )}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}

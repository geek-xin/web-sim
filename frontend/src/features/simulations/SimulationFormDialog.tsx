import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { X } from 'lucide-react';
import { toast } from 'sonner';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
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
  setErrorBranchesEnabled,
  validatePayload,
} from './sim-utils';
import type { HttpMatchMode, ProtocolType, SimulationBranch, SimulationConfig, SimulationConfigPayload, TcpFrameMode } from './types';

type FormMode = 'create' | 'edit' | 'copy';

interface SimulationFormDialogProps {
  open: boolean;
  mode: FormMode;
  config?: SimulationConfig | null;
  onOpenChange: (open: boolean) => void;
  onSubmit: (payload: SimulationConfigPayload) => Promise<void> | void;
}

export function SimulationFormDialog({ open, mode, config, onOpenChange, onSubmit }: SimulationFormDialogProps) {
  const [payload, setPayload] = useState<SimulationConfigPayload>(() => initialPayload(mode, config));
  const [branchesText, setBranchesText] = useState(() => JSON.stringify(initialPayload(mode, config).branches, null, 2));
  const [submitting, setSubmitting] = useState(false);
  const protocolLocked = mode === 'edit' && payload.enabled;
  const parsedBranches = useMemo(() => parseBranchesText(branchesText), [branchesText]);
  const canToggleErrorBranch = parsedBranches != null && hasErrorBranch(parsedBranches);
  const errorBranchEnabled = parsedBranches != null && hasEnabledErrorBranch(parsedBranches);

  useEffect(() => {
    if (!open) return;
    const next = initialPayload(mode, config);
    setPayload(next);
    setBranchesText(JSON.stringify(next.branches, null, 2));
  }, [config, mode, open]);

  const title = useMemo(() => {
    if (mode === 'edit') return '编辑模拟配置';
    if (mode === 'copy') return '复制模拟配置';
    return '创建模拟配置';
  }, [mode]);

  const handleProtocolChange = (protocol: ProtocolType) => {
    const next = withFormattedDefaultBody(defaultPayload(protocol));
    setPayload((current) => ({
      ...next,
      id: current.id,
      name: current.name || next.name,
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
            <div className="grid grid-cols-[minmax(0,1fr)_180px_140px] items-stretch gap-4">
              <Field label="名称" htmlFor="simulation-name">
                <Input
                  id="simulation-name"
                  value={payload.name}
                  onChange={(event) => setPayload((current) => ({ ...current, name: event.target.value }))}
                  placeholder="模拟配置名称"
                  required
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

              <div className="grid gap-2 text-sm font-black text-clay-ink">
                <span className="flex min-h-5 items-center">状态</span>
                <label className="flex h-12 items-center gap-3 rounded-2xl border-[3px] border-clay-border bg-white px-4 text-sm font-black shadow-[1px_2px_0_rgba(17,17,17,0.55)]">
                  <Checkbox
                    checked={payload.enabled}
                    onCheckedChange={(checked) => setPayload((current) => ({ ...current, enabled: checked === true }))}
                    aria-label="是否启用模拟配置"
                  />
                  已启用
                </label>
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

          <div className="grid grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] gap-5">
            <section className="grid content-start gap-4 rounded-[24px] border-[3px] border-clay-border bg-white p-4 shadow-clay-sm">
              <h3 className="text-lg font-black text-clay-ink">默认响应</h3>
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
            </section>

            <section className="grid content-start gap-4 rounded-[24px] border-[3px] border-clay-border bg-white p-4 shadow-clay-sm">
              <div className="grid gap-2">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="grid gap-2">
                    <label className="text-sm font-black text-clay-ink" htmlFor="branches-json">分支 JSON</label>
                    <p id="branches-json-help" className="text-xs font-bold text-clay-muted">
                      可直接编辑分支数组；支持条件、优先级、主响应、probability 出现概率、responseVariants 响应变体，以及 ROUND_ROBIN/RANDOM 交错策略。
                    </p>
                  </div>
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
                <Textarea
                  id="branches-json"
                  value={branchesText}
                  onChange={(event) => setBranchesText(event.target.value)}
                  rows={16}
                  aria-describedby="branches-json-help"
                />
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
    defaultResponse: {
      ...payload.defaultResponse,
      body: formatJsonText(payload.defaultResponse.body),
    },
  };
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

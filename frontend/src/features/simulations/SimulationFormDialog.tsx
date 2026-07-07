import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { toast } from 'sonner';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { cn } from '@/lib/utils';
import { configToPayload, defaultPayload, validatePayload } from './sim-utils';
import type { HttpMatchMode, ProtocolType, SimulationConfig, SimulationConfigPayload, TcpFrameMode } from './types';

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
    const next = defaultPayload(protocol);
    setPayload((current) => ({
      ...next,
      id: current.id,
      name: current.name || next.name,
      enabled: current.enabled,
    }));
    setBranchesText(JSON.stringify(next.branches, null, 2));
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
      <DialogContent className="w-[min(96vw,920px)]">
        <DialogHeader>
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={payload.protocol === 'HTTP' ? 'indigo' : 'mint'}>{payload.protocol}</Badge>
            <Badge variant={payload.enabled ? 'mint' : 'muted'}>{payload.enabled ? '已启用' : '已停用'}</Badge>
          </div>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>
            配置基础字段、匹配规则、默认响应和分支 JSON。
          </DialogDescription>
        </DialogHeader>

        <form className="grid gap-5" onSubmit={handleSubmit}>
          <section className="grid gap-4 rounded-[24px] border-[3px] border-clay-border bg-clay-cream p-4 shadow-clay-sm">
            <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_180px_140px]">
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
                <span>协议</span>
                <ProtocolPicker
                  value={payload.protocol}
                  disabled={protocolLocked}
                  onChange={handleProtocolChange}
                />
                {protocolLocked ? (
                  <span className="text-[0.68rem] font-bold text-clay-muted">先停用后可修改协议</span>
                ) : null}
              </div>

              <label className="flex items-center gap-3 self-end rounded-2xl border-[3px] border-clay-border bg-white px-4 py-3 text-sm font-black shadow-clay-sm">
                <Checkbox
                  checked={payload.enabled}
                  onCheckedChange={(checked) => setPayload((current) => ({ ...current, enabled: checked === true }))}
                  aria-label="是否启用模拟配置"
                />
                已启用
              </label>
            </div>
          </section>

          {payload.protocol === 'HTTP' ? (
            <section className="grid gap-4 rounded-[24px] border-[3px] border-clay-border bg-white p-4 shadow-clay-sm" aria-label="HTTP 匹配字段">
              <h3 className="text-lg font-black text-clay-ink">HTTP 匹配</h3>
              <div className="grid gap-4 md:grid-cols-[160px_minmax(0,1fr)_300px]">
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
              <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_160px_320px]">
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

          <section className="grid gap-4 rounded-[24px] border-[3px] border-clay-border bg-white p-4 shadow-clay-sm">
            <h3 className="text-lg font-black text-clay-ink">默认响应</h3>
            <div className="grid gap-4 md:grid-cols-[160px_minmax(0,1fr)]">
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
                  rows={3}
                />
              </Field>
            </div>
          </section>

          <Field label="分支 JSON" htmlFor="branches-json">
            <Textarea
              id="branches-json"
              value={branchesText}
              onChange={(event) => setBranchesText(event.target.value)}
              rows={12}
              aria-describedby="branches-json-help"
            />
            <p id="branches-json-help" className="text-xs font-bold text-clay-muted">
              可直接编辑分支数组；支持条件、优先级、主响应、responseVariants 响应变体，以及 ROUND_ROBIN/RANDOM 交错策略。
            </p>
          </Field>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={submitting}>
              取消
            </Button>
            <Button type="submit" variant="primary" disabled={submitting}>
              {submitting ? '保存中...' : '保存模拟配置'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function initialPayload(mode: FormMode, config?: SimulationConfig | null): SimulationConfigPayload {
  if (!config) return defaultPayload('HTTP');
  const payload = configToPayload(config);
  if (mode === 'copy') {
    const { id: _id, ...copy } = payload;
    return { ...copy, name: `${payload.name} 副本` };
  }
  return payload;
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

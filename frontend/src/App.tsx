import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Activity, AlertTriangle, Cable, FileJson, RefreshCw, ServerCog, ShieldCheck, Sparkles } from 'lucide-react';
import { toast, Toaster } from 'sonner';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { fetchJson, jsonRequest } from '@/lib/api';
import { DeleteConfirmDialog } from '@/features/simulations/DeleteConfirmDialog';
import { SimulationCard } from '@/features/simulations/SimulationCard';
import { SimulationDetailDrawer, type RawConfigViewState } from '@/features/simulations/SimulationDetailDrawer';
import { SimulationFormDialog } from '@/features/simulations/SimulationFormDialog';
import { SimulationToolbar, type EnabledFilter, type ProtocolFilter } from '@/features/simulations/SimulationToolbar';
import { SimulationLogDialog } from '@/features/logs/SimulationLogDialog';
import { displayEndpoint } from '@/features/simulations/sim-utils';
import type { SimulationConfig, SimulationConfigPayload } from '@/features/simulations/types';

declare global {
  interface Window {
    __WEB_SIM_CONFIG_DIR__?: string;
  }
}

type FormState = {
  open: boolean;
  mode: 'create' | 'edit' | 'copy';
  config?: SimulationConfig | null;
};

type DeleteState = {
  open: boolean;
  ids: string[];
  name?: string;
};

type DetailState = {
  open: boolean;
  config: SimulationConfig | null;
};

type RawConfigResponse = {
  fileName: string;
  content: string;
};

const EMPTY_RAW_STATE: RawConfigViewState = {
  fileName: null,
  content: null,
  loading: false,
  error: null,
};

export default function App() {
  const configDir = readConfigDir();
  const [simulations, setSimulations] = useState<SimulationConfig[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [protocolFilter, setProtocolFilter] = useState<ProtocolFilter>('ALL');
  const [enabledFilter, setEnabledFilter] = useState<EnabledFilter>('ALL');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(() => new Set());
  const [formState, setFormState] = useState<FormState>({ open: false, mode: 'create', config: null });
  const [deleteState, setDeleteState] = useState<DeleteState>({ open: false, ids: [] });
  const [detailState, setDetailState] = useState<DetailState>({ open: false, config: null });
  const [rawConfig, setRawConfig] = useState<RawConfigViewState>(EMPTY_RAW_STATE);
  const [logsOpen, setLogsOpen] = useState(false);
  const rawRequestSeq = useRef(0);

  const loadSimulations = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await fetchJson<SimulationConfig[]>('/admin/api/simulations');
      setSimulations(data);
      setSelectedIds((current) => new Set([...current].filter((id) => data.some((config) => config.id === id))));
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : '加载模拟配置失败。';
      setError(message);
      toast.error(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSimulations();
  }, [loadSimulations]);

  const loadRawConfig = useCallback(async (id: string) => {
    const requestSeq = rawRequestSeq.current + 1;
    rawRequestSeq.current = requestSeq;
    try {
      setRawConfig((current) => ({ ...current, loading: true, error: null }));
      const data = await fetchJson<RawConfigResponse>(`/admin/api/simulations/${id}/raw`);
      if (rawRequestSeq.current !== requestSeq) return;
      setRawConfig({
        fileName: data.fileName,
        content: data.content,
        loading: false,
        error: null,
      });
    } catch (rawError) {
      if (rawRequestSeq.current !== requestSeq) return;
      setRawConfig((current) => ({
        ...current,
        loading: false,
        error: rawError instanceof Error ? rawError.message : '加载原始 JSON 失败。',
      }));
    }
  }, []);

  useEffect(() => {
    if (!detailState.open || !detailState.config) {
      rawRequestSeq.current += 1;
      setRawConfig(EMPTY_RAW_STATE);
      return;
    }
    void loadRawConfig(detailState.config.id);
  }, [detailState.config?.id, detailState.open, loadRawConfig]);

  useEffect(() => {
    setDetailState((current) => {
      if (!current.open || !current.config) return current;
      const fresh = simulations.find((config) => config.id === current.config?.id);
      if (!fresh) return { open: false, config: null };
      return fresh === current.config ? current : { ...current, config: fresh };
    });
  }, [simulations]);

  const filteredSimulations = useMemo(() => {
    const query = search.trim().toLowerCase();
    return simulations.filter((config) => {
      if (protocolFilter !== 'ALL' && config.protocol !== protocolFilter) return false;
      if (enabledFilter === 'ENABLED' && !config.enabled) return false;
      if (enabledFilter === 'DISABLED' && config.enabled) return false;
      if (!query) return true;

      const haystack = [config.name, config.protocol, displayEndpoint(config), config.enabled ? '已启用 enabled' : '已停用 disabled']
        .join(' ')
        .toLowerCase();
      return haystack.includes(query);
    });
  }, [enabledFilter, protocolFilter, search, simulations]);

  const overviewCards = useMemo(() => buildOverviewCards(simulations), [simulations]);

  const handleSubmit = async (payload: SimulationConfigPayload) => {
    const editingId = formState.mode === 'edit' ? formState.config?.id : undefined;
    const saved = editingId
      ? await fetchJson<SimulationConfig>(`/admin/api/simulations/${editingId}`, jsonRequest(payload, 'PUT'))
      : await fetchJson<SimulationConfig>('/admin/api/simulations', jsonRequest(payload, 'POST'));

    setSimulations((current) => {
      const index = current.findIndex((item) => item.id === saved.id);
      if (index === -1) return [saved, ...current];
      const next = [...current];
      next[index] = saved;
      return next;
    });
    setDetailState((current) => (current.config?.id === saved.id ? { ...current, config: saved } : current));
    if (detailState.config?.id === saved.id) {
      void loadRawConfig(saved.id);
    }
    toast.success(editingId ? '模拟配置已更新。' : '模拟配置已创建。');
  };

  const handleView = (config: SimulationConfig) => {
    setDetailState({ open: true, config });
  };

  const handleEdit = (config: SimulationConfig) => {
    setFormState({ open: true, mode: 'edit', config });
  };

  const handleToggle = async (config: SimulationConfig) => {
    try {
      const updated = await fetchJson<SimulationConfig>(`/admin/api/simulations/${config.id}/toggle`, { method: 'POST' });
      setSimulations((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      toast.success(`${updated.name} 已${updated.enabled ? '启用' : '停用'}。`);
    } catch (toggleError) {
      const message = toggleError instanceof Error ? toggleError.message : '切换启停状态失败。';
      toast.error(message);
    }
  };

  const handleDeleteConfirmed = async () => {
    const ids = deleteState.ids;
    try {
      await Promise.all(ids.map((id) => fetchJson<void>(`/admin/api/simulations/${id}`, { method: 'DELETE' })));
      setSimulations((current) => current.filter((config) => !ids.includes(config.id)));
      setSelectedIds((current) => new Set([...current].filter((id) => !ids.includes(id))));
      setDeleteState({ open: false, ids: [] });
      toast.success(ids.length === 1 ? '模拟配置已删除。' : `已删除 ${ids.length} 个模拟配置。`);
    } catch (deleteError) {
      const message = deleteError instanceof Error ? deleteError.message : '删除模拟配置失败。';
      toast.error(message);
    }
  };

  const selectedCount = selectedIds.size;

  return (
    <main className="min-h-screen px-3 py-4 text-clay-ink sm:px-4 sm:py-5 lg:px-6">
      <div className="mx-auto grid max-w-[1536px] gap-5">
        <Hero configDir={configDir} onRefresh={loadSimulations} refreshing={loading} onCreate={() => setFormState({ open: true, mode: 'create', config: null })} onLogs={() => setLogsOpen(true)} />

        <section className="overview-grid grid gap-4" aria-label="模拟配置概览">
          {overviewCards.map((card) => (
            <OverviewCard key={card.label} {...card} />
          ))}
        </section>

        <section className="chunky-panel bg-white p-4 sm:p-5" aria-labelledby="simulation-workspace-heading">
          <SimulationToolbar
            headingId="simulation-workspace-heading"
            search={search}
            protocolFilter={protocolFilter}
            enabledFilter={enabledFilter}
            selectedCount={selectedCount}
            totalCount={simulations.length}
            visibleCount={filteredSimulations.length}
            onSearchChange={setSearch}
            onProtocolFilterChange={setProtocolFilter}
            onEnabledFilterChange={setEnabledFilter}
            onAdd={() => setFormState({ open: true, mode: 'create', config: null })}
            onBatchDelete={() => setDeleteState({ open: true, ids: [...selectedIds] })}
          />

          <div className="mt-5">
            {loading ? (
              <StateCard icon={<RefreshCw className="h-10 w-10 animate-spin" />} title="正在加载模拟配置" detail="正在从管理 API 读取后端 JSON 配置。" />
            ) : error ? (
              <StateCard icon={<AlertTriangle className="h-10 w-10" />} title="无法加载模拟配置" detail={error} action={<Button onClick={loadSimulations}>重试</Button>} />
            ) : filteredSimulations.length === 0 ? (
              <StateCard
                icon={<Sparkles className="h-10 w-10" />}
                title={simulations.length === 0 ? '暂无模拟配置' : '没有符合筛选条件的配置'}
                detail={simulations.length === 0 ? '创建一个 HTTP 或 TCP 模拟配置，开始模拟接口流量。' : '调整搜索、协议或启停筛选条件查看更多卡片。'}
                action={simulations.length === 0 ? <Button variant="primary" onClick={() => setFormState({ open: true, mode: 'create', config: null })}>创建模拟配置</Button> : undefined}
              />
            ) : (
              <div className="grid gap-4 xl:grid-cols-2 2xl:grid-cols-3">
                {filteredSimulations.map((config) => (
                  <SimulationCard
                    key={config.id}
                    config={config}
                    selected={selectedIds.has(config.id)}
                    onSelectedChange={(selected) => setSelectedIds((current) => toggleSelected(current, config.id, selected))}
                    onView={() => handleView(config)}
                    onEdit={() => handleEdit(config)}
                    onCopy={() => setFormState({ open: true, mode: 'copy', config })}
                    onToggle={() => void handleToggle(config)}
                    onDelete={() => setDeleteState({ open: true, ids: [config.id], name: config.name })}
                  />
                ))}
              </div>
            )}
          </div>
        </section>
      </div>

      <SimulationFormDialog
        open={formState.open}
        mode={formState.mode}
        config={formState.config}
        onOpenChange={(open) => setFormState((current) => ({ ...current, open }))}
        onSubmit={handleSubmit}
      />
      <SimulationDetailDrawer
        open={detailState.open}
        config={detailState.config}
        raw={rawConfig}
        onOpenChange={(open) => setDetailState((current) => ({ ...current, open }))}
        onEdit={handleEdit}
        onRefreshRaw={(config) => void loadRawConfig(config.id)}
      />
      <SimulationLogDialog open={logsOpen} onOpenChange={setLogsOpen} />
      <DeleteConfirmDialog
        open={deleteState.open}
        count={deleteState.ids.length}
        name={deleteState.name}
        onOpenChange={(open) => setDeleteState((current) => ({ ...current, open }))}
        onConfirm={() => void handleDeleteConfirmed()}
      />
      <Toaster richColors position="top-right" />
    </main>
  );
}

function Hero({
  configDir,
  onRefresh,
  refreshing,
  onCreate,
  onLogs,
}: {
  configDir: string;
  onRefresh: () => void;
  refreshing: boolean;
  onCreate: () => void;
  onLogs: () => void;
}) {
  const displayConfigDir = shortenConfigDir(configDir);

  return (
    <section className="chunky-panel hero-shell overflow-hidden bg-clay-paper p-5 sm:p-7" aria-labelledby="web-sim-hero-title">
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_420px] lg:items-stretch">
        <div className="flex min-h-[270px] flex-col justify-between gap-8">
          <div className="flex flex-wrap items-center gap-3">
            <Badge variant="orange">WEB SIM</Badge>
            <Badge variant="mint">HTTP + TCP</Badge>
          </div>
          <div className="grid gap-5">
            <h1 id="web-sim-hero-title" className="hero-title max-w-5xl text-5xl font-black text-clay-ink sm:text-7xl lg:text-8xl">
              <span className="hero-title-latin">报文</span>
              <span className="hero-title-cn">模拟器</span>
            </h1>
            <p className="hero-copy max-w-3xl text-lg font-extrabold text-clay-muted sm:text-xl">
              创建、筛选、复制、启停和删除 HTTP/TCP 模拟规则，配置以本地 JSON 文件保存。
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Button variant="primary" onClick={onCreate}>创建模拟配置</Button>
            <Button variant="outline" onClick={onRefresh} disabled={refreshing}>
              <RefreshCw className={refreshing ? 'h-4 w-4 animate-spin' : 'h-4 w-4'} />
              刷新
            </Button>
            <Button variant="outline" onClick={onLogs}>
              <Activity className="h-4 w-4" />
              日志
            </Button>
          </div>
        </div>

        <Card className="chunky-card-blue flex min-h-[270px] flex-col justify-between overflow-hidden p-5">
          <div className="grid gap-4">
            <div className="flex items-center justify-between gap-3">
              <div>
                <span className="text-xs font-black uppercase tracking-[0.2em] text-clay-muted">配置目录</span>
                <strong className="mt-2 block break-words text-xl font-black text-clay-ink" title={configDir}>
                  {displayConfigDir}
                </strong>
              </div>
              <span className="overview-stat-icon shrink-0 bg-white">
                <FileJson className="h-6 w-6" />
              </span>
            </div>
            <div className="chunky-code p-4">
              <code>{'{ "protocol": "HTTP", "enabled": true, "branches": [...] }'}</code>
            </div>
          </div>
          <div className="flow-steps mt-5" aria-label="模拟流程预览">
            <FlowStep label="匹配" detail="路径 / 报文" />
            <span className="flow-arrow">→</span>
            <FlowStep label="分支" detail="条件" />
            <span className="flow-arrow">→</span>
            <FlowStep label="响应" detail="模板" />
          </div>
        </Card>
      </div>
    </section>
  );
}

function OverviewCard({ label, value, detail, tone, icon }: { label: string; value: string; detail: string; tone: string; icon: ReactNode }) {
  return (
    <Card className={`${tone} overview-stat-card chunky-pressable p-5`}>
      <div className="flex items-start justify-between gap-4">
        <div className="grid gap-2">
          <span className="text-xs font-black uppercase tracking-[0.2em] text-clay-muted">{label}</span>
          <strong className="text-5xl font-black leading-none text-clay-ink">{value}</strong>
        </div>
        <span className="overview-stat-icon bg-white">{icon}</span>
      </div>
      <p className="mt-4 text-sm font-extrabold leading-snug text-clay-muted">{detail}</p>
    </Card>
  );
}

function StateCard({ icon, title, detail, action }: { icon: ReactNode; title: string; detail: string; action?: ReactNode }) {
  return (
    <Card className="glass-card-gold p-7 text-center lg:min-h-[340px]">
      <div className="mx-auto flex max-w-xl flex-col items-center gap-5">
        <span className="empty-state-icon">{icon}</span>
        <div className="grid gap-3">
          <h3 className="text-3xl font-black tracking-tight text-clay-ink">{title}</h3>
          <p className="text-base font-extrabold leading-relaxed text-clay-muted">{detail}</p>
        </div>
        {action}
      </div>
    </Card>
  );
}

function FlowStep({ label, detail }: { label: string; detail: string }) {
  return (
    <span className="flow-step rounded-2xl border-[3px] border-clay-border bg-white p-3 shadow-clay-sm">
      <strong className="text-sm font-black text-clay-ink">{label}</strong>
      <span className="text-xs font-bold text-clay-muted">{detail}</span>
    </span>
  );
}

function readConfigDir(): string {
  return window.__WEB_SIM_CONFIG_DIR__ || 'config/simulations';
}

function shortenConfigDir(path: string): string {
  if (!path) {
    return '等待后端配置路径';
  }
  const normalized = path.replaceAll('\\', '/');
  const parts = normalized.split('/').filter(Boolean);
  if (parts.length <= 4) {
    return path;
  }
  return `.../${parts.slice(-3).join('/')}`;
}

function toggleSelected(current: Set<string>, id: string, selected: boolean): Set<string> {
  const next = new Set(current);
  if (selected) {
    next.add(id);
  } else {
    next.delete(id);
  }
  return next;
}

function buildOverviewCards(simulations: SimulationConfig[]) {
  const totalCount = simulations.length;
  const httpCount = simulations.filter((config) => config.protocol === 'HTTP').length;
  const tcpCount = simulations.filter((config) => config.protocol === 'TCP').length;
  const enabledCount = simulations.filter((config) => config.enabled).length;

  return [
    {
      label: '配置总数',
      value: String(totalCount),
      detail: '从管理 API 加载的全部 HTTP 和 TCP 模拟配置。',
      tone: 'chunky-card-yellow',
      icon: <FileJson className="h-6 w-6" />,
    },
    {
      label: 'HTTP 模拟',
      value: String(httpCount),
      detail: '按路径和方法匹配的 HTTP 响应规则。',
      tone: 'chunky-card-mint',
      icon: <ServerCog className="h-6 w-6" />,
    },
    {
      label: 'TCP 端口',
      value: String(tcpCount),
      detail: '按行、长度头或十六进制报文配置的 TCP 模拟。',
      tone: 'chunky-card-pink',
      icon: <Cable className="h-6 w-6" />,
    },
    {
      label: '已启用',
      value: String(enabledCount),
      detail: '最近一次后端刷新后可运行的配置数量。',
      tone: 'chunky-card-blue',
      icon: <ShieldCheck className="h-6 w-6" />,
    },
  ];
}

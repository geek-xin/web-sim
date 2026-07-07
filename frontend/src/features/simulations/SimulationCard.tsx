import { Copy, Eye, Pencil, Power, Trash2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { formatDuration } from '@/lib/utils';
import { countBranches, displayEndpoint, protocolBadgeVariant } from './sim-utils';
import type { SimulationConfig } from './types';

export interface SimulationMetricsSummary {
  hits?: number | null;
  errors?: number | null;
  averageDurationMs?: number | null;
}

interface SimulationCardProps {
  config: SimulationConfig;
  selected: boolean;
  metricsSummary?: SimulationMetricsSummary;
  onSelectedChange: (selected: boolean) => void;
  onView: () => void;
  onEdit: () => void;
  onCopy: () => void;
  onToggle: () => void;
  onDelete: () => void;
}

export function SimulationCard({
  config,
  selected,
  metricsSummary,
  onSelectedChange,
  onView,
  onEdit,
  onCopy,
  onToggle,
  onDelete,
}: SimulationCardProps) {
  const branchCount = countBranches(config);
  const defaultStatus = config.defaultResponse?.status ?? '—';
  const hits = metricsSummary?.hits ?? 0;
  const errors = metricsSummary?.errors ?? 0;
  const avg = formatDuration(metricsSummary?.averageDurationMs ?? 0);

  return (
    <Card className="chunky-pressable overflow-hidden">
      <CardHeader className="gap-4 pb-3">
        <div className="flex items-start justify-between gap-3">
          <div className="flex min-w-0 items-start gap-3">
            <Checkbox
              checked={selected}
              onCheckedChange={(checked) => onSelectedChange(checked === true)}
              aria-label={`选择 ${config.name}`}
            />
            <div className="min-w-0">
              <div className="mb-2 flex flex-wrap items-center gap-2">
                <Badge variant={protocolBadgeVariant(config.protocol)}>{config.protocol}</Badge>
                <Badge variant={config.enabled ? 'mint' : 'muted'}>{config.enabled ? '已启用' : '已停用'}</Badge>
              </div>
              <CardTitle className="truncate text-2xl" title={config.name}>{config.name}</CardTitle>
            </div>
          </div>
          <Button variant="outline" size="icon" onClick={onView} aria-label={`查看 ${config.name}`}>
            <Eye className="h-4 w-4" />
          </Button>
        </div>
      </CardHeader>

      <CardContent className="grid gap-4">
        <div className="rounded-2xl border-[3px] border-clay-border bg-clay-cream p-3 shadow-clay-sm">
          <span className="text-xs font-black uppercase tracking-[0.18em] text-clay-muted">端点</span>
          <p className="mt-1 break-all font-mono text-sm font-black text-clay-ink">{displayEndpoint(config)}</p>
        </div>

        <dl className="grid grid-cols-2 gap-2 sm:grid-cols-5">
          <Stat label="分支" value={branchCount} tone="bg-clay-accent" />
          <Stat label="默认" value={defaultStatus} tone="bg-clay-cream" />
          <Stat label="命中" value={hits} tone="bg-clay-secondary" />
          <Stat label="错误" value={errors} tone="bg-clay-pink" />
          <Stat label="平均" value={avg} tone="bg-clay-gold" />
        </dl>

        <div className="flex flex-wrap justify-end gap-2 border-t-[3px] border-clay-border pt-4">
          <Button variant="outline" size="sm" onClick={onEdit} aria-label={`编辑 ${config.name}`}>
            <Pencil className="h-4 w-4" />
            编辑
          </Button>
          <Button variant="outline" size="sm" onClick={onCopy} aria-label={`复制 ${config.name}`}>
            <Copy className="h-4 w-4" />
            复制
          </Button>
          <Button variant={config.enabled ? 'orange' : 'primary'} size="sm" onClick={onToggle} aria-label={`${config.enabled ? '停用' : '启用'} ${config.name}`}>
            <Power className="h-4 w-4" />
            {config.enabled ? '停用' : '启用'}
          </Button>
          <Button variant="danger" size="sm" onClick={onDelete} aria-label={`删除 ${config.name}`}>
            <Trash2 className="h-4 w-4" />
            删除
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function Stat({ label, value, tone }: { label: string; value: string | number; tone: string }) {
  return (
    <div className={`min-h-[76px] rounded-2xl border-[3px] border-clay-border ${tone} px-3 py-2 shadow-[1px_2px_0_rgba(17,17,17,0.55)]`}>
      <dt className="text-[0.62rem] font-black uppercase tracking-[0.14em] text-clay-muted">{label}</dt>
      <dd className="mt-1 truncate text-base font-black text-clay-ink" title={String(value)}>{value}</dd>
    </div>
  );
}

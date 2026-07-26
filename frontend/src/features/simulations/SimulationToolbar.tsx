import { Download, Plus, RefreshCw, Search, Trash2, Upload } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';
import type { ProtocolType } from './types';

export type ProtocolFilter = 'ALL' | ProtocolType;
export type EnabledFilter = 'ALL' | 'ENABLED' | 'DISABLED';

interface SimulationToolbarProps {
  headingId: string;
  search: string;
  protocolFilter: ProtocolFilter;
  enabledFilter: EnabledFilter;
  selectedCount: number;
  totalCount: number;
  visibleCount: number;
  onSearchChange: (value: string) => void;
  onProtocolFilterChange: (value: ProtocolFilter) => void;
  onEnabledFilterChange: (value: EnabledFilter) => void;
  onRefresh: () => void;
  refreshing: boolean;
  onImport: () => void;
  importing: boolean;
  onExport: () => void;
  exporting: boolean;
  onAdd: () => void;
  onBatchDelete: () => void;
}

const protocolOptions: Array<{ value: ProtocolFilter; label: string }> = [
  { value: 'ALL', label: '全部协议' },
  { value: 'HTTP', label: 'HTTP' },
  { value: 'TCP', label: 'TCP' },
];

const enabledOptions: Array<{ value: EnabledFilter; label: string }> = [
  { value: 'ALL', label: '全部状态' },
  { value: 'ENABLED', label: '已启用' },
  { value: 'DISABLED', label: '已停用' },
];

export function SimulationToolbar({
  headingId,
  search,
  protocolFilter,
  enabledFilter,
  selectedCount,
  totalCount,
  visibleCount,
  onSearchChange,
  onProtocolFilterChange,
  onEnabledFilterChange,
  onRefresh,
  refreshing,
  onImport,
  importing,
  onExport,
  exporting,
  onAdd,
  onBatchDelete,
}: SimulationToolbarProps) {
  return (
    <div className="grid gap-4" aria-labelledby={headingId}>
      <div className="flex flex-row items-end justify-between gap-3">
        <div>
          <Badge variant="indigo">工作区</Badge>
          <h2 id={headingId} className="workspace-title mt-3 text-4xl text-clay-ink">
            模拟规则
          </h2>
          <p className="mt-2 max-w-3xl text-sm font-bold text-clay-muted">
            {totalCount} 个配置中当前显示 {visibleCount} 个。选择卡片可批量删除，打开卡片可查看、编辑或复制。
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={onRefresh} disabled={refreshing}>
            <RefreshCw className={refreshing ? 'h-4 w-4 animate-spin' : 'h-4 w-4'} />
            刷新
          </Button>
          <Button variant="outline" onClick={onImport} disabled={importing} aria-label="导入模拟配置 JSON 文件">
            <Upload className={importing ? 'h-4 w-4 animate-pulse' : 'h-4 w-4'} />
            导入
          </Button>
          <Button variant="outline" onClick={onExport} disabled={exporting} aria-label="导出全部模拟配置">
            <Download className={exporting ? 'h-4 w-4 animate-pulse' : 'h-4 w-4'} />
            导出
          </Button>
          <Button variant="danger" onClick={onBatchDelete} disabled={selectedCount === 0} aria-label={`删除已选择的 ${selectedCount} 个模拟配置`}>
            <Trash2 className="h-4 w-4" />
            删除所选 {selectedCount > 0 ? `(${selectedCount})` : ''}
          </Button>
          <Button variant="primary" onClick={onAdd} aria-label="创建新的模拟配置">
            <Plus className="h-4 w-4" />
            新建模拟
          </Button>
        </div>
      </div>

      <div className="toolbar-strip grid grid-cols-[minmax(240px,1fr)_auto_auto] gap-3 rounded-[24px] border-[3px] border-clay-border bg-clay-cream p-3 shadow-clay-sm">
        <label className="relative block">
          <span className="sr-only">搜索模拟配置</span>
          <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-clay-muted" />
          <Input
            className="pl-11"
            value={search}
            onChange={(event) => onSearchChange(event.target.value)}
            placeholder="搜索名称、端点或协议..."
            aria-label="搜索模拟配置"
          />
        </label>

        <SegmentedFilter
          label="协议筛选"
          options={protocolOptions}
          value={protocolFilter}
          onChange={onProtocolFilterChange}
        />

        <SegmentedFilter
          label="启停状态筛选"
          options={enabledOptions}
          value={enabledFilter}
          onChange={onEnabledFilterChange}
        />
      </div>
    </div>
  );
}

function SegmentedFilter<TValue extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: Array<{ value: TValue; label: string }>;
  value: TValue;
  onChange: (value: TValue) => void;
}) {
  return (
    <fieldset className="min-w-0">
      <legend className="sr-only">{label}</legend>
      <div className="inline-flex flex-wrap items-center gap-2 rounded-3xl border-[3px] border-clay-border bg-white p-2 shadow-clay-sm" role="group" aria-label={label}>
        {options.map((option) => {
          const selected = option.value === value;
          return (
            <button
              key={option.value}
              type="button"
              aria-pressed={selected}
              onClick={() => onChange(option.value)}
              className={cn(
                'cursor-pointer rounded-2xl border-[3px] border-transparent px-4 py-2 text-sm font-black text-clay-muted transition hover:border-clay-border hover:bg-clay-cream hover:text-clay-ink focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-clay-primary/35',
                selected && 'border-clay-border bg-clay-primary text-white shadow-clay-sm hover:bg-clay-primary hover:text-white',
              )}
            >
              {option.label}
            </button>
          );
        })}
      </div>
    </fieldset>
  );
}

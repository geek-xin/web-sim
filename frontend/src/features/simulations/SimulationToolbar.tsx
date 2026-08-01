import { useEffect, useId, useRef, useState } from 'react';
import { ChevronDown, Download, Plus, RefreshCw, Search, Tags, Trash2, Upload } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button, buttonVariants } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';
import { tagFilterOptions, type SimulationTagFilter } from './sim-utils';

interface SimulationToolbarProps {
  headingId: string;
  search: string;
  tagFilter: SimulationTagFilter;
  tagOptions: string[];
  selectedCount: number;
  totalCount: number;
  visibleCount: number;
  onSearchChange: (value: string) => void;
  onTagFilterChange: (value: SimulationTagFilter) => void;
  onRefresh: () => void;
  refreshing: boolean;
  onImport: () => void;
  importing: boolean;
  onExport: () => void;
  exporting: boolean;
  onAdd: () => void;
  onBatchDelete: () => void;
}

export function SimulationToolbar({
  headingId,
  search,
  tagFilter,
  tagOptions,
  selectedCount,
  totalCount,
  visibleCount,
  onSearchChange,
  onTagFilterChange,
  onRefresh,
  refreshing,
  onImport,
  importing,
  onExport,
  exporting,
  onAdd,
  onBatchDelete,
}: SimulationToolbarProps) {
  const hasFilter = search.trim() !== '' || tagFilter !== 'ALL';

  return (
    <div className="grid gap-4" aria-labelledby={headingId}>
      <div>
        <Badge variant="indigo">工作区</Badge>
        <h2 id={headingId} className="workspace-title mt-3 text-4xl text-clay-ink">
          模拟规则
        </h2>
        <p className="mt-2 max-w-3xl text-sm font-bold text-clay-muted">
          {hasFilter ? `${totalCount} 个配置中当前显示 ${visibleCount} 个。` : `当前共有 ${totalCount} 个配置。`}
          选择卡片可批量删除，打开卡片可查看、编辑或复制。
        </p>
      </div>

      <div className="grid grid-cols-[minmax(260px,1fr)_220px_auto] items-center gap-3 rounded-[24px] border-[3px] border-clay-border bg-clay-cream p-3 shadow-clay-sm">
        <label className="relative block min-w-0">
          <span className="sr-only">按名称搜索模拟配置</span>
          <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-clay-muted" />
          <Input
            className="pl-11 font-black"
            value={search}
            onChange={(event) => onSearchChange(event.target.value)}
            placeholder="搜索名称..."
            aria-label="按名称搜索模拟配置"
          />
        </label>
        <TagFilter
          value={tagFilter}
          options={tagFilterOptions(tagOptions)}
          onChange={onTagFilterChange}
        />
        <div className="flex flex-wrap items-center justify-end gap-2">
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
    </div>
  );
}

function TagFilter({
  value,
  options,
  onChange,
}: {
  value: SimulationTagFilter;
  options: Array<{ value: SimulationTagFilter; label: string }>;
  onChange: (value: SimulationTagFilter) => void;
}) {
  const [open, setOpen] = useState(false);
  const menuId = useId();
  const rootRef = useRef<HTMLDivElement | null>(null);
  const selectedOption = options.find((option) => option.value === value) ?? options[0];

  useEffect(() => {
    if (!open) return;

    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
      }
    };

    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open]);

  return (
    <div ref={rootRef} className="relative inline-flex min-w-0">
      <button
        type="button"
        className={cn(buttonVariants({ variant: 'outline' }), 'h-12 w-full justify-start overflow-hidden pl-5 pr-10')}
        aria-label="标签筛选"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen((current) => !current)}
      >
        <Tags className="h-4 w-4 shrink-0" />
        <span className="truncate">{selectedOption?.label ?? '全部'}</span>
      </button>
      <ChevronDown className={cn('pointer-events-none absolute right-4 top-1/2 h-4 w-4 -translate-y-1/2 text-clay-ink transition-transform', open && 'rotate-180')} />

      {open ? (
        <div
          id={menuId}
          role="listbox"
          aria-label="标签筛选"
          className="absolute right-0 top-[calc(100%+0.5rem)] z-40 grid max-h-72 w-full min-w-[220px] gap-2 overflow-auto rounded-2xl border-[3px] border-clay-border bg-white p-2 shadow-clay"
        >
          {options.map((option) => {
            const selected = option.value === value;
            return (
              <button
                key={option.value}
                type="button"
                role="option"
                aria-selected={selected}
                className={cn(
                  buttonVariants({ variant: selected ? 'primary' : 'outline', size: 'sm' }),
                  'w-full justify-start overflow-hidden px-3',
                )}
                onClick={() => {
                  onChange(option.value);
                  setOpen(false);
                }}
              >
                <span className="truncate">{option.label}</span>
              </button>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}

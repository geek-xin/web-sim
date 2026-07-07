import { AlertTriangle, Braces, FileJson, GitBranch, Pencil, RefreshCw } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import { formatDuration } from '@/lib/utils';
import { countBranches, displayEndpoint, protocolBadgeVariant } from './sim-utils';
import type { SimulationConfig } from './types';

export interface RawConfigViewState {
  fileName: string | null;
  content: string | null;
  loading: boolean;
  error: string | null;
}

interface SimulationDetailDrawerProps {
  open: boolean;
  config: SimulationConfig | null;
  raw: RawConfigViewState;
  onOpenChange: (open: boolean) => void;
  onEdit: (config: SimulationConfig) => void;
  onRefreshRaw: (config: SimulationConfig) => void;
}

export function SimulationDetailDrawer({ open, config, raw, onOpenChange, onEdit, onRefreshRaw }: SimulationDetailDrawerProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="left-auto right-0 !top-0 h-screen max-h-screen w-[min(96vw,780px)] translate-x-0 !translate-y-0 content-start gap-2 overflow-auto rounded-l-[32px] rounded-r-none border-y-0 border-r-0 p-4 sm:p-5">
        {config ? (
          <>
            <DialogHeader className="gap-0 pr-12">
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant={protocolBadgeVariant(config.protocol)}>{config.protocol}</Badge>
                <Badge variant={config.enabled ? 'mint' : 'muted'}>{config.enabled ? '已启用' : '已停用'}</Badge>
              </div>
              <DialogTitle className="mt-2 text-[1.65rem] leading-tight">{config.name}</DialogTitle>
              <DialogDescription className="mt-1">{displayEndpoint(config)}</DialogDescription>
            </DialogHeader>

            <Tabs defaultValue="overview" className="-mt-1 min-h-0">
              <TabsList className="p-1.5">
                <TabsTrigger value="overview" className="py-1.5">概览</TabsTrigger>
                <TabsTrigger value="edit" className="py-1.5">编辑</TabsTrigger>
                <TabsTrigger value="branches" className="py-1.5">分支</TabsTrigger>
                <TabsTrigger value="raw" className="py-1.5">原始 JSON</TabsTrigger>
              </TabsList>

              <TabsContent value="overview" className="mt-3">
                <OverviewTab config={config} />
              </TabsContent>

              <TabsContent value="edit" className="mt-3">
                <EditTab config={config} onEdit={() => onEdit(config)} />
              </TabsContent>

              <TabsContent value="branches" className="mt-3">
                <BranchesTab config={config} />
              </TabsContent>

              <TabsContent value="raw" className="mt-3">
                <RawJsonTab config={config} raw={raw} onRefresh={() => onRefreshRaw(config)} />
              </TabsContent>
            </Tabs>
          </>
        ) : (
          <div className="py-12 text-center">
            <span className="empty-state-icon mx-auto"><FileJson className="h-8 w-8" /></span>
            <p className="mt-4 font-black text-clay-muted">请选择一个模拟配置查看详情。</p>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

function OverviewTab({ config }: { config: SimulationConfig }) {
  const stats = [
    { label: '协议', value: config.protocol },
    { label: '端点', value: displayEndpoint(config) },
    { label: '启用状态', value: config.enabled ? '是' : '否' },
    { label: '分支数量', value: String(countBranches(config)) },
    { label: '默认状态码', value: String(config.defaultResponse?.status ?? '—') },
    { label: '默认延迟', value: formatDuration(config.defaultResponse?.delayMs ?? 0) },
  ];

  return (
    <div className="grid gap-4">
      <div className="grid gap-3 sm:grid-cols-2">
        {stats.map((stat) => (
          <Card key={stat.label} className="p-4">
            <span className="text-[0.65rem] font-black uppercase tracking-[0.16em] text-clay-muted">{stat.label}</span>
            <strong className="mt-1 block break-words text-lg font-black text-clay-ink">{stat.value}</strong>
          </Card>
        ))}
      </div>

      <Card className="grid gap-3 bg-clay-cream p-4">
        <h3 className="text-lg font-black text-clay-ink">默认响应</h3>
        <pre className="chunky-code max-h-64 overflow-auto p-4 text-xs"><code>{formatJson(config.defaultResponse)}</code></pre>
      </Card>
    </div>
  );
}

function EditTab({ onEdit }: { config: SimulationConfig; onEdit: () => void }) {
  return (
    <div className="grid gap-4">
      <Card className="grid gap-4 p-4">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-xl font-black text-clay-ink">编辑模拟配置</h3>
            <p className="mt-1 text-sm font-bold text-clay-muted">
              使用统一编辑器更新基础字段、匹配规则、默认响应和分支 JSON。
            </p>
          </div>
          <Button variant="primary" onClick={onEdit}>
            <Pencil className="h-4 w-4" />
            打开编辑器
          </Button>
        </div>
      </Card>
    </div>
  );
}

function BranchesTab({ config }: { config: SimulationConfig }) {
  const branches = config.branches || [];
  return (
    <div className="grid gap-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="text-xl font-black text-clay-ink">分支规则</h3>
          <p className="text-sm font-bold text-clay-muted">系统会按优先级先匹配分支规则；命中后可按响应变体策略交错返回正常或异常报文。</p>
        </div>
        <Badge variant="yellow"><GitBranch className="mr-1 h-3 w-3" />{branches.length} 个分支</Badge>
      </div>
      <Textarea readOnly value={formatJson(branches)} rows={22} aria-label="格式化后的分支 JSON" />
    </div>
  );
}

function RawJsonTab({ config, raw, onRefresh }: { config: SimulationConfig; raw: RawConfigViewState; onRefresh: () => void }) {
  return (
    <div className="grid gap-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="text-xl font-black text-clay-ink">原始 JSON</h3>
          <p className="text-sm font-bold text-clay-muted">{raw.fileName || `${config.id}.json`}</p>
        </div>
        <Button variant="outline" onClick={onRefresh} disabled={raw.loading}>
          <RefreshCw className={raw.loading ? 'h-4 w-4 animate-spin' : 'h-4 w-4'} />
          刷新原始内容
        </Button>
      </div>

      {raw.error ? (
        <Card className="glass-card-gold p-5">
          <div className="flex items-start gap-3">
            <AlertTriangle className="mt-1 h-5 w-5 text-clay-error" />
            <div>
              <h4 className="font-black text-clay-ink">无法加载原始 JSON</h4>
              <p className="text-sm font-bold text-clay-muted">{raw.error}</p>
            </div>
          </div>
        </Card>
      ) : raw.loading ? (
        <Card className="p-6 text-center">
          <RefreshCw className="mx-auto h-8 w-8 animate-spin" />
          <p className="mt-3 font-black text-clay-muted">正在加载原始配置...</p>
        </Card>
      ) : (
        <div className="grid gap-2">
          <Badge variant="muted"><Braces className="mr-1 h-3 w-3" />文件内容</Badge>
          <Textarea readOnly value={formatRawContent(raw.content)} rows={24} aria-label="原始模拟配置 JSON" />
        </div>
      )}
    </div>
  );
}

function formatJson(value: unknown): string {
  return JSON.stringify(value, null, 2);
}

function formatRawContent(content: string | null): string {
  if (!content) return '';
  try {
    return JSON.stringify(JSON.parse(content), null, 2);
  } catch (_error) {
    return content;
  }
}

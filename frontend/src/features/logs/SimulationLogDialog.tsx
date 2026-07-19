import { useEffect, useState, type ReactNode } from 'react';
import { AlertTriangle, Activity, Clock, Eye, Radio, RefreshCw, ServerCog, ShieldAlert } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { fetchJson } from '@/lib/api';
import { formatDuration } from '@/lib/utils';
import { parseSnapshotEvent, type SimulationLogEntry, type SimulationLogSnapshot } from './types';
import type { ProtocolType } from '@/features/simulations/types';

const LOG_PAGE_SIZE = 10;

interface SimulationLogDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  simulationFilter?: {
    id: string;
    name: string;
    protocol: ProtocolType;
  } | null;
}

export function SimulationLogDialog({ open, onOpenChange, simulationFilter }: SimulationLogDialogProps) {
  const [snapshot, setSnapshot] = useState<SimulationLogSnapshot | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [streamError, setStreamError] = useState<string | null>(null);
  const [selectedLog, setSelectedLog] = useState<SimulationLogEntry | null>(null);
  const [logPage, setLogPage] = useState(1);
  const [logPageInput, setLogPageInput] = useState('1');

  useEffect(() => {
    if (!open) return;

    let cancelled = false;
    setLoading(true);
    setError(null);

    fetchJson<SimulationLogSnapshot>('/admin/api/logs/snapshot')
      .then((data) => {
        if (!cancelled) setSnapshot(data);
      })
      .catch((snapshotError) => {
        if (!cancelled) setError(snapshotError instanceof Error ? snapshotError.message : '加载日志快照失败。');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [open]);

  useEffect(() => {
    if (open) {
      setLogPage(1);
      setLogPageInput('1');
    }
  }, [open, simulationFilter?.id]);

  useEffect(() => {
    if (!open || typeof EventSource === 'undefined') return;

    setStreamError(null);
    const source = new EventSource('/admin/api/logs/stream');
    const handleSnapshot = (event: Event) => {
      const next = parseSnapshotEvent((event as MessageEvent<string>).data);
      if (next) {
        setSnapshot(next);
        setError(null);
        setStreamError(null);
      }
    };

    source.addEventListener('snapshot', handleSnapshot);
    source.onerror = () => {
      setStreamError('实时日志流已断开，当前仍显示最近一次快照。');
    };

    return () => {
      source.removeEventListener('snapshot', handleSnapshot);
      source.close();
    };
  }, [open]);

  const visibleLogs = simulationFilter
    ? snapshot?.recentLogs.filter((log) => log.simulationId === simulationFilter.id) ?? []
    : snapshot?.recentLogs ?? [];
  const stats = buildStats(snapshot, visibleLogs, simulationFilter);
  const tableTitle = simulationFilter ? `${simulationFilter.name} 日志` : '最近日志';
  const emptyDetail = simulationFilter
    ? '向这个模拟配置发送请求后，这里会显示采样日志。'
    : '向模拟接口发送请求后，这里会显示采样日志。';
  const totalLogPages = Math.max(1, Math.ceil(visibleLogs.length / LOG_PAGE_SIZE));
  const currentLogPage = Math.min(logPage, totalLogPages);
  const pageStart = (currentLogPage - 1) * LOG_PAGE_SIZE;
  const pagedLogs = visibleLogs.slice(pageStart, pageStart + LOG_PAGE_SIZE);

  useEffect(() => {
    setLogPageInput(String(currentLogPage));
  }, [currentLogPage]);

  const jumpToLogPage = () => {
    const page = Number(logPageInput);
    if (!Number.isInteger(page)) {
      setLogPageInput(String(currentLogPage));
      return;
    }
    const nextPage = Math.max(1, Math.min(totalLogPages, page));
    setLogPage(nextPage);
    setLogPageInput(String(nextPage));
  };

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="flex max-h-[90vh] w-[1180px] flex-col overflow-x-auto overflow-y-hidden">
        <div className="flex min-h-0 min-w-[1120px] flex-1 flex-col gap-4">
          <DialogHeader className="shrink-0">
            <div className="flex items-center gap-2">
              <Badge variant="orange">实时日志</Badge>
              <Badge variant={streamError ? 'muted' : 'mint'}>{streamError ? '快照模式' : '实时更新'}</Badge>
              {simulationFilter ? <Badge variant="indigo">单配置</Badge> : null}
            </div>
            <DialogTitle>{simulationFilter ? '模拟配置请求日志' : '模拟请求日志'}</DialogTitle>
            <DialogDescription>
              {simulationFilter
                ? `展示 ${simulationFilter.name} 的运行时采样日志。弹窗打开时，表格会通过 SSE 快照流自动刷新。`
                : '展示运行时采样的 HTTP/TCP 指标。弹窗打开时，表格会通过 SSE 快照流自动刷新。'}
            </DialogDescription>
          </DialogHeader>

          <div className="grid shrink-0 grid-cols-5 gap-3">
            {stats.map((stat) => (
              <Card key={stat.label} className={`chunky-pressable p-4 ${stat.tone}`}>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <span className="text-[0.65rem] font-black uppercase tracking-[0.16em] text-clay-muted">{stat.label}</span>
                    <strong className="mt-1 block text-2xl font-black text-clay-ink">{stat.value}</strong>
                  </div>
                  <span className="overview-stat-icon h-10 w-10 bg-clay-cream">{stat.icon}</span>
                </div>
              </Card>
            ))}
          </div>

          <div className="flex min-h-0 flex-1 flex-col">
            {loading && !snapshot ? (
              <StateMessage icon={<RefreshCw className="h-8 w-8 animate-spin" />} title="正在加载日志" detail="正在获取最新指标快照。" />
            ) : error && !snapshot ? (
              <StateMessage
                icon={<AlertTriangle className="h-8 w-8" />}
                title="无法加载日志"
                detail={error}
                action={<Button variant="outline" onClick={() => onOpenChange(false)}>关闭</Button>}
              />
            ) : (
              <section className="flex min-h-0 flex-1 flex-col gap-3" aria-label="最近模拟请求日志">
              <div className="flex shrink-0 items-center justify-between gap-3">
                <h3 className="text-lg font-black text-clay-ink">{tableTitle}</h3>
                {streamError ? <p className="text-sm font-bold text-clay-muted">{streamError}</p> : <p className="text-sm font-bold text-clay-muted">每 2 秒自动刷新。</p>}
              </div>
              {visibleLogs.length ? (
                <div className="min-h-0 flex-1 overflow-hidden">
                  <Table className="min-w-[1120px]" wrapperClassName="overflow-x-auto">
                    <TableHeader className="[&_th]:sticky [&_th]:top-0 [&_th]:z-10 [&_th]:bg-clay-indigo">
                      <TableRow>
                        <TableHead className="w-16">序号</TableHead>
                        <TableHead className="w-36">时间</TableHead>
                        <TableHead>协议</TableHead>
                        <TableHead>状态</TableHead>
                        <TableHead>耗时</TableHead>
                        <TableHead>请求</TableHead>
                        <TableHead>响应</TableHead>
                        <TableHead>详情</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {pagedLogs.map((log, index) => (
                        <LogRow key={log.id} index={pageStart + index + 1} log={log} onView={() => setSelectedLog(log)} />
                      ))}
                    </TableBody>
                  </Table>
                  <div className="mt-3 flex shrink-0 items-center justify-between gap-3">
                    <p className="text-sm font-black text-clay-muted">
                      第 {currentLogPage} / {totalLogPages} 页，共 {visibleLogs.length} 条
                    </p>
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-black text-clay-muted">跳至</span>
                      <Input
                        className="h-10 w-20 px-3 py-1 text-center"
                        inputMode="numeric"
                        value={logPageInput}
                        onChange={(event) => setLogPageInput(event.target.value)}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') {
                            jumpToLogPage();
                          }
                        }}
                        aria-label="跳转页码"
                      />
                      <Button type="button" variant="outline" size="sm" onClick={jumpToLogPage}>跳转</Button>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={currentLogPage <= 1}
                        onClick={() => setLogPage((page) => Math.max(1, page - 1))}
                      >
                        上一页
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={currentLogPage >= totalLogPages}
                        onClick={() => setLogPage((page) => Math.min(totalLogPages, page + 1))}
                      >
                        下一页
                      </Button>
                    </div>
                  </div>
                </div>
              ) : (
                <StateMessage icon={<Activity className="h-8 w-8" />} title="暂无最近日志" detail={emptyDetail} />
              )}
              </section>
            )}
          </div>
        </div>
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(selectedLog)} onOpenChange={(nextOpen) => {
        if (!nextOpen) setSelectedLog(null);
      }}>
        <DialogContent className="w-[760px]">
          <DialogHeader>
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant={selectedLog?.protocol === 'HTTP' ? 'indigo' : 'mint'}>{selectedLog?.protocol || '日志'}</Badge>
              <Badge variant={(selectedLog?.status ?? 0) >= 400 ? 'muted' : 'mint'}>{selectedLog?.status ?? '—'}</Badge>
            </div>
            <DialogTitle>请求详情</DialogTitle>
            <DialogDescription>{selectedLog ? formatTimestamp(selectedLog.timestamp) : '查看单条采样请求的请求与响应摘要。'}</DialogDescription>
          </DialogHeader>
          {selectedLog ? <LogDetail log={selectedLog} /> : null}
        </DialogContent>
      </Dialog>
    </>
  );
}

function LogRow({ index, log, onView }: { index: number; log: SimulationLogEntry; onView: () => void }) {
  return (
    <TableRow>
      <TableCell>{index}</TableCell>
      <TableCell className="w-36 whitespace-nowrap">{formatTimestamp(log.timestamp)}</TableCell>
      <TableCell><Badge variant={log.protocol === 'HTTP' ? 'indigo' : 'mint'}>{log.protocol}</Badge></TableCell>
      <TableCell>
        <span className={`inline-flex min-w-14 items-center justify-center rounded-xl border-[3px] border-clay-border px-3 py-1 text-xs font-black shadow-clay-sm ${log.status >= 400 ? 'bg-clay-error text-white' : 'bg-clay-secondary text-clay-ink'}`}>
          {log.status}
        </span>
      </TableCell>
      <TableCell>{formatDuration(log.durationMs)}</TableCell>
      <TableCell className="max-w-[220px] truncate font-mono text-xs" title={log.requestSummary || ''}>{log.requestSummary || '—'}</TableCell>
      <TableCell className="max-w-[220px] truncate font-mono text-xs" title={log.responseSummary || ''}>{log.responseSummary || '—'}</TableCell>
      <TableCell>
        <Button variant="outline" size="sm" onClick={onView}>
          <Eye className="h-4 w-4" />
          详情
        </Button>
      </TableCell>
    </TableRow>
  );
}

function LogDetail({ log }: { log: SimulationLogEntry }) {
  return (
    <div className="grid gap-4">
      <div className="grid grid-cols-2 gap-3">
        <DetailItem label="模拟配置" value={log.simulationName || log.simulationId || '未知'} />
        <DetailItem label="时间" value={formatTimestamp(log.timestamp)} />
        <DetailItem label="协议" value={log.protocol} />
        <DetailItem label="状态" value={String(log.status)} />
        <DetailItem label="耗时" value={formatDuration(log.durationMs)} />
        <DetailItem label="日志 ID" value={log.id} />
      </div>
      <DetailCode title="请求" value={log.requestSummary || '—'} />
      <DetailCode title="响应" value={log.responseSummary || '—'} />
    </div>
  );
}

function DetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border-[3px] border-clay-border bg-white p-3 shadow-clay-sm">
      <span className="text-[0.65rem] font-black uppercase tracking-[0.16em] text-clay-muted">{label}</span>
      <strong className="mt-1 block break-words text-sm font-black text-clay-ink">{value}</strong>
    </div>
  );
}

function DetailCode({ title, value }: { title: string; value: string }) {
  return (
    <section className="grid gap-2">
      <h3 className="font-black text-clay-ink">{title}</h3>
      <pre className="chunky-code max-h-56 overflow-auto p-4 text-xs"><code>{formatMaybeJson(value)}</code></pre>
    </section>
  );
}

function StateMessage({ icon, title, detail, action }: { icon: ReactNode; title: string; detail: string; action?: ReactNode }) {
  return (
    <Card className="glass-card-gold p-6 text-center">
      <div className="mx-auto flex max-w-lg flex-col items-center gap-3">
        <span className="empty-state-icon h-16 w-16">{icon}</span>
        <div className="grid gap-2">
          <h3 className="text-2xl font-black text-clay-ink">{title}</h3>
          <p className="text-sm font-bold text-clay-muted">{detail}</p>
        </div>
        {action}
      </div>
    </Card>
  );
}

function buildStats(
  snapshot: SimulationLogSnapshot | null,
  visibleLogs: SimulationLogEntry[],
  filter?: { id: string; name: string; protocol: ProtocolType } | null,
) {
  if (filter) {
    const summary = snapshot?.simulationMetrics?.[filter.id];
    const total = summary?.hits ?? visibleLogs.length;
    const errors = summary?.errors ?? visibleLogs.filter((log) => log.status >= 400).length;
    const averageDurationMs = summary?.averageDurationMs ?? (total > 0
      ? visibleLogs.reduce((sum, log) => sum + log.durationMs, 0) / total
      : 0);
    const httpCount = filter.protocol === 'HTTP' ? total : 0;
    const tcpCount = filter.protocol === 'TCP' ? total : 0;
    return [
      { label: '总请求', value: total, icon: <Activity className="h-5 w-5" />, tone: 'bg-clay-gold' },
      { label: 'HTTP', value: httpCount, icon: <ServerCog className="h-5 w-5" />, tone: 'bg-clay-secondary' },
      { label: 'TCP', value: tcpCount, icon: <Radio className="h-5 w-5" />, tone: 'bg-clay-accent' },
      { label: '错误', value: errors, icon: <ShieldAlert className="h-5 w-5" />, tone: 'bg-clay-pink' },
      { label: '平均耗时', value: formatDuration(averageDurationMs, { fractionDigits: 3 }), icon: <Clock className="h-5 w-5" />, tone: 'bg-clay-cream' },
    ];
  }

  return [
    { label: '总请求', value: snapshot?.totalRequests ?? 0, icon: <Activity className="h-5 w-5" />, tone: 'bg-clay-gold' },
    { label: 'HTTP', value: snapshot?.httpRequests ?? 0, icon: <ServerCog className="h-5 w-5" />, tone: 'bg-clay-secondary' },
    { label: 'TCP', value: snapshot?.tcpRequests ?? 0, icon: <Radio className="h-5 w-5" />, tone: 'bg-clay-accent' },
    { label: '错误', value: snapshot?.errorRequests ?? 0, icon: <ShieldAlert className="h-5 w-5" />, tone: 'bg-clay-pink' },
    { label: '平均耗时', value: formatDuration(snapshot?.averageDurationMs ?? 0, { fractionDigits: 3 }), icon: <Clock className="h-5 w-5" />, tone: 'bg-clay-cream' },
  ];
}

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return timestamp;
  return date.toLocaleString();
}

function formatMaybeJson(value: string): string {
  const text = value.trim();
  if (!text) return value;
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch (_error) {
    return value;
  }
}

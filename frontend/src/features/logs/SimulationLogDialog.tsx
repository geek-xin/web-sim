import { useEffect, useState, type ReactNode } from 'react';
import { AlertTriangle, Activity, Clock, Radio, RefreshCw, ServerCog, ShieldAlert } from 'lucide-react';
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { fetchJson } from '@/lib/api';
import { formatDuration } from '@/lib/utils';
import { parseSnapshotEvent, type SimulationLogEntry, type SimulationLogSnapshot } from './types';

interface SimulationLogDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function SimulationLogDialog({ open, onOpenChange }: SimulationLogDialogProps) {
  const [snapshot, setSnapshot] = useState<SimulationLogSnapshot | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [streamError, setStreamError] = useState<string | null>(null);

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

  const stats = buildStats(snapshot);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[min(96vw,1040px)]">
        <DialogHeader>
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="orange">实时日志</Badge>
            <Badge variant={streamError ? 'muted' : 'mint'}>{streamError ? '快照模式' : '实时更新'}</Badge>
          </div>
          <DialogTitle>模拟请求日志</DialogTitle>
          <DialogDescription>
            展示运行时采样的 HTTP/TCP 指标。弹窗打开时，表格会通过 SSE 快照流自动刷新。
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-4">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
            {stats.map((stat) => (
              <Card key={stat.label} className="chunky-pressable p-4">
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
            <section className="grid gap-3" aria-label="最近模拟请求日志">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <h3 className="text-lg font-black text-clay-ink">最近日志</h3>
                {streamError ? <p className="text-sm font-bold text-clay-muted">{streamError}</p> : <p className="text-sm font-bold text-clay-muted">每 2 秒自动刷新。</p>}
              </div>
              {snapshot?.recentLogs.length ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>时间</TableHead>
                      <TableHead>协议</TableHead>
                      <TableHead>模拟配置</TableHead>
                      <TableHead>状态</TableHead>
                      <TableHead>耗时</TableHead>
                      <TableHead>请求</TableHead>
                      <TableHead>响应</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {snapshot.recentLogs.map((log) => (
                      <LogRow key={log.id} log={log} />
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <StateMessage icon={<Activity className="h-8 w-8" />} title="暂无最近日志" detail="向模拟接口发送请求后，这里会显示采样日志。" />
              )}
            </section>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

function LogRow({ log }: { log: SimulationLogEntry }) {
  return (
    <TableRow>
      <TableCell className="whitespace-nowrap">{formatTimestamp(log.timestamp)}</TableCell>
      <TableCell><Badge variant={log.protocol === 'HTTP' ? 'indigo' : 'mint'}>{log.protocol}</Badge></TableCell>
      <TableCell className="max-w-[180px] truncate" title={log.simulationName || log.simulationId || '未知模拟配置'}>{log.simulationName || log.simulationId || '未知'}</TableCell>
      <TableCell>{log.status}</TableCell>
      <TableCell>{formatDuration(log.durationMs)}</TableCell>
      <TableCell className="max-w-[220px] truncate font-mono text-xs" title={log.requestSummary || ''}>{log.requestSummary || '—'}</TableCell>
      <TableCell className="max-w-[220px] truncate font-mono text-xs" title={log.responseSummary || ''}>{log.responseSummary || '—'}</TableCell>
    </TableRow>
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

function buildStats(snapshot: SimulationLogSnapshot | null) {
  return [
    { label: '总请求', value: snapshot?.totalRequests ?? 0, icon: <Activity className="h-5 w-5" /> },
    { label: 'HTTP', value: snapshot?.httpRequests ?? 0, icon: <ServerCog className="h-5 w-5" /> },
    { label: 'TCP', value: snapshot?.tcpRequests ?? 0, icon: <Radio className="h-5 w-5" /> },
    { label: '错误', value: snapshot?.errorRequests ?? 0, icon: <ShieldAlert className="h-5 w-5" /> },
    { label: '平均耗时', value: formatDuration(snapshot?.averageDurationMs ?? 0), icon: <Clock className="h-5 w-5" /> },
  ];
}

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return timestamp;
  return date.toLocaleString();
}

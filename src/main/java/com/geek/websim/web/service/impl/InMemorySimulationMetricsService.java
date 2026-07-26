package com.geek.websim.web.service.impl;

import com.geek.websim.config.SimulationProperties;
import com.geek.websim.web.model.dto.SimulationLogEntry;
import com.geek.websim.web.model.dto.SimulationLogSnapshot;
import com.geek.websim.web.model.dto.SimulationMetricsSummary;
import com.geek.websim.web.model.enums.ProtocolType;
import com.geek.websim.web.service.SimulationLogWriter;
import com.geek.websim.web.service.SimulationMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

@Service
public class InMemorySimulationMetricsService implements SimulationMetricsService {
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder httpRequests = new LongAdder();
    private final LongAdder tcpRequests = new LongAdder();
    private final LongAdder errorRequests = new LongAdder();
    private final LongAdder totalDurationMs = new LongAdder();
    private final Map<String, MetricsCounter> simulationMetrics = new ConcurrentHashMap<>();
    private final ArrayDeque<SimulationLogEntry> recentLogs = new ArrayDeque<>();
    private final double sampleRate;
    private final int recentLogSize;
    private final SimulationLogWriter logWriter;

    @Autowired
    public InMemorySimulationMetricsService(SimulationProperties properties,
                                            ObjectProvider<SimulationLogWriter> logWriterProvider) {
        this(properties == null ? 0.01 : properties.logSampleRate(),
                properties == null ? 50_000 : properties.recentLogSize(),
                logWriterProvider == null ? null : logWriterProvider.getIfAvailable());
    }

    public InMemorySimulationMetricsService(SimulationProperties properties) {
        this(properties == null ? 0.01 : properties.logSampleRate(),
                properties == null ? 50_000 : properties.recentLogSize(),
                null);
    }

    public InMemorySimulationMetricsService(double sampleRate, int recentLogSize) {
        this(sampleRate, recentLogSize, null);
    }

    public InMemorySimulationMetricsService(double sampleRate, int recentLogSize, SimulationLogWriter logWriter) {
        this.sampleRate = Math.max(0, Math.min(1, sampleRate));
        this.recentLogSize = Math.max(0, recentLogSize);
        this.logWriter = logWriter;
        restoreRetainedLogs();
    }

    @Override
    public void record(ProtocolType protocol,
                       int status,
                       long durationMs,
                       Supplier<SimulationLogEntry> sampledEntrySupplier) {
        record(null, protocol, status, durationMs, sampledEntrySupplier);
    }

    @Override
    public void record(String simulationId,
                       ProtocolType protocol,
                       int status,
                       long durationMs,
                       Supplier<SimulationLogEntry> sampledEntrySupplier) {
        totalRequests.increment();
        long normalizedDuration = Math.max(0, durationMs);
        totalDurationMs.add(normalizedDuration);
        if (protocol == ProtocolType.HTTP) {
            httpRequests.increment();
        } else if (protocol == ProtocolType.TCP) {
            tcpRequests.increment();
        }
        if (isError(protocol, status)) {
            errorRequests.increment();
        }
        recordSimulationMetrics(simulationId, protocol, status, normalizedDuration);
        boolean shouldSampleRecentLog = shouldSample();
        boolean shouldWriteTextLog = logWriter != null;
        if ((shouldSampleRecentLog || shouldWriteTextLog) && sampledEntrySupplier != null) {
            SimulationLogEntry entry = sampledEntrySupplier.get();
            if (entry == null) {
                return;
            }
            if (shouldWriteTextLog) {
                logWriter.append(entry);
            }
            if (!shouldSampleRecentLog && !shouldWriteTextLog) {
                return;
            }
            synchronized (recentLogs) {
                recentLogs.addFirst(entry);
                while (recentLogs.size() > recentLogSize) {
                    recentLogs.removeLast();
                }
            }
        }
    }

    @Override
    public SimulationLogSnapshot snapshot() {
        long total = totalRequests.sum();
        List<SimulationLogEntry> logs;
        synchronized (recentLogs) {
            logs = new ArrayList<>(recentLogs);
        }
        return SimulationLogSnapshot.builder()
                .totalRequests(total)
                .httpRequests(httpRequests.sum())
                .tcpRequests(tcpRequests.sum())
                .errorRequests(errorRequests.sum())
                .averageDurationMs(total == 0 ? 0.0 : (double) totalDurationMs.sum() / total)
                .simulationMetrics(simulationMetricsSnapshot())
                .recentLogs(logs)
                .build();
    }

    private void recordSimulationMetrics(String simulationId, ProtocolType protocol, int status, long durationMs) {
        if (simulationId == null || simulationId.isBlank()) {
            return;
        }
        MetricsCounter counter = simulationMetrics.computeIfAbsent(simulationId, ignored -> new MetricsCounter());
        counter.hits.increment();
        counter.totalDurationMs.add(durationMs);
        if (isError(protocol, status)) {
            counter.errors.increment();
        }
    }

    private void restoreRetainedLogs() {
        if (logWriter == null) {
            return;
        }
        List<SimulationLogEntry> retainedLogs = logWriter.loadRetainedLogs();
        if (retainedLogs == null || retainedLogs.isEmpty()) {
            return;
        }
        retainedLogs.stream()
                .filter(java.util.Objects::nonNull)
                .forEach(this::restoreLogEntry);
    }

    private void restoreLogEntry(SimulationLogEntry entry) {
        long normalizedDuration = Math.max(0, entry.getDurationMs());
        totalRequests.increment();
        totalDurationMs.add(normalizedDuration);
        ProtocolType protocol = entry.getProtocol();
        if (protocol == ProtocolType.HTTP) {
            httpRequests.increment();
        } else if (protocol == ProtocolType.TCP) {
            tcpRequests.increment();
        }
        if (isError(protocol, entry.getStatus())) {
            errorRequests.increment();
        }
        recordSimulationMetrics(entry.getSimulationId(), protocol, entry.getStatus(), normalizedDuration);
        if (recentLogSize > 0) {
            synchronized (recentLogs) {
                recentLogs.addFirst(entry);
                while (recentLogs.size() > recentLogSize) {
                    recentLogs.removeLast();
                }
            }
        }
    }

    private Map<String, SimulationMetricsSummary> simulationMetricsSnapshot() {
        Map<String, SimulationMetricsSummary> snapshot = new LinkedHashMap<>();
        simulationMetrics.forEach((simulationId, counter) -> {
            long hits = counter.hits.sum();
            snapshot.put(simulationId, SimulationMetricsSummary.builder()
                    .hits(hits)
                    .errors(counter.errors.sum())
                    .averageDurationMs(hits == 0 ? 0.0 : (double) counter.totalDurationMs.sum() / hits)
                    .build());
        });
        return snapshot;
    }

    private boolean isError(ProtocolType protocol, int status) {
        if (status >= 400) {
            return true;
        }
        return protocol == ProtocolType.TCP && status <= 0;
    }

    private boolean shouldSample() {
        if (recentLogSize <= 0 || sampleRate <= 0) {
            return false;
        }
        return sampleRate >= 1 || ThreadLocalRandom.current().nextDouble() < sampleRate;
    }

    private static final class MetricsCounter {
        private final LongAdder hits = new LongAdder();
        private final LongAdder errors = new LongAdder();
        private final LongAdder totalDurationMs = new LongAdder();
    }
}

package com.geek.websim.web.service.impl;

import com.geek.websim.config.SimulationProperties;
import com.geek.websim.web.model.dto.SimulationLogEntry;
import com.geek.websim.web.model.dto.SimulationLogSnapshot;
import com.geek.websim.web.model.enums.ProtocolType;
import com.geek.websim.web.service.SimulationMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
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
    private final ArrayDeque<SimulationLogEntry> recentLogs = new ArrayDeque<>();
    private final double sampleRate;
    private final int recentLogSize;

    @Autowired
    public InMemorySimulationMetricsService(SimulationProperties properties) {
        this(properties == null ? 0.01 : properties.logSampleRate(),
                properties == null ? 200 : properties.recentLogSize());
    }

    public InMemorySimulationMetricsService(double sampleRate, int recentLogSize) {
        this.sampleRate = Math.max(0, Math.min(1, sampleRate));
        this.recentLogSize = Math.max(0, recentLogSize);
    }

    @Override
    public void record(ProtocolType protocol,
                       int status,
                       long durationMs,
                       Supplier<SimulationLogEntry> sampledEntrySupplier) {
        totalRequests.increment();
        totalDurationMs.add(Math.max(0, durationMs));
        if (protocol == ProtocolType.HTTP) {
            httpRequests.increment();
        } else if (protocol == ProtocolType.TCP) {
            tcpRequests.increment();
        }
        if (isError(protocol, status)) {
            errorRequests.increment();
        }
        if (shouldSample() && sampledEntrySupplier != null) {
            SimulationLogEntry entry = sampledEntrySupplier.get();
            if (entry == null) {
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
                .recentLogs(logs)
                .build();
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
}

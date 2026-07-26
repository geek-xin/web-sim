package com.geek.websim.web.service;

import com.geek.websim.web.model.dto.SimulationLogEntry;
import com.geek.websim.web.model.dto.SimulationLogSnapshot;
import com.geek.websim.web.model.enums.ProtocolType;
import com.geek.websim.web.service.impl.InMemorySimulationMetricsService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySimulationMetricsServiceTest {

    @Test
    void recordsHttpAndTcpCountersErrorsAndRecentLogs() {
        InMemorySimulationMetricsService service = new InMemorySimulationMetricsService(1.0, 10);

        service.record(entry("http-ok", ProtocolType.HTTP, 200, 20));
        service.record(entry("http-error", ProtocolType.HTTP, 500, 40));
        service.record(entry("tcp-ok", ProtocolType.TCP, 200, 60));

        SimulationLogSnapshot snapshot = service.snapshot();

        assertThat(snapshot.getTotalRequests()).isEqualTo(3);
        assertThat(snapshot.getHttpRequests()).isEqualTo(2);
        assertThat(snapshot.getTcpRequests()).isEqualTo(1);
        assertThat(snapshot.getErrorRequests()).isEqualTo(1);
        assertThat(snapshot.getAverageDurationMs()).isEqualTo(40.0);
        assertThat(snapshot.getSimulationMetrics()).containsOnlyKeys(
                "http-ok-simulation",
                "http-error-simulation",
                "tcp-ok-simulation");
        assertThat(snapshot.getRecentLogs()).hasSize(3)
                .extracting(SimulationLogEntry::getId)
                .containsExactly("tcp-ok", "http-error", "http-ok");
    }

    @Test
    void recordsCountersWithoutInvokingSupplierWhenSamplingDisabled() {
        InMemorySimulationMetricsService service = new InMemorySimulationMetricsService(0.0, 10);
        AtomicInteger supplierInvocations = new AtomicInteger();

        service.record(ProtocolType.HTTP, 200, 10, () -> {
            supplierInvocations.incrementAndGet();
            return entry("http-ok", ProtocolType.HTTP, 200, 10);
        });
        service.record(ProtocolType.TCP, 0, 30, () -> {
            supplierInvocations.incrementAndGet();
            return entry("tcp-error", ProtocolType.TCP, 0, 30);
        });

        SimulationLogSnapshot snapshot = service.snapshot();

        assertThat(snapshot.getTotalRequests()).isEqualTo(2);
        assertThat(snapshot.getHttpRequests()).isEqualTo(1);
        assertThat(snapshot.getTcpRequests()).isEqualTo(1);
        assertThat(snapshot.getErrorRequests()).isEqualTo(1);
        assertThat(snapshot.getAverageDurationMs()).isEqualTo(20.0);
        assertThat(snapshot.getSimulationMetrics()).isEmpty();
        assertThat(snapshot.getRecentLogs()).isEmpty();
        assertThat(supplierInvocations).hasValue(0);
    }

    @Test
    void recordsPerSimulationCountersEvenWhenSamplingDisabled() {
        InMemorySimulationMetricsService service = new InMemorySimulationMetricsService(0.0, 10);

        service.record("sim-a", ProtocolType.HTTP, 200, 10, () -> entry("a1", ProtocolType.HTTP, 200, 10));
        service.record("sim-a", ProtocolType.HTTP, 500, 30, () -> entry("a2", ProtocolType.HTTP, 500, 30));
        service.record("sim-b", ProtocolType.TCP, 200, 60, () -> entry("b1", ProtocolType.TCP, 200, 60));

        SimulationLogSnapshot snapshot = service.snapshot();

        assertThat(snapshot.getRecentLogs()).isEmpty();
        assertThat(snapshot.getSimulationMetrics()).containsKeys("sim-a", "sim-b");
        assertThat(snapshot.getSimulationMetrics().get("sim-a").getHits()).isEqualTo(2);
        assertThat(snapshot.getSimulationMetrics().get("sim-a").getErrors()).isEqualTo(1);
        assertThat(snapshot.getSimulationMetrics().get("sim-a").getAverageDurationMs()).isEqualTo(20.0);
        assertThat(snapshot.getSimulationMetrics().get("sim-b").getHits()).isEqualTo(1);
        assertThat(snapshot.getSimulationMetrics().get("sim-b").getErrors()).isZero();
        assertThat(snapshot.getSimulationMetrics().get("sim-b").getAverageDurationMs()).isEqualTo(60.0);
    }

    @Test
    void writesAndKeepsTextLogEntriesEvenWhenRecentLogSamplingIsDisabled() {
        CapturingLogWriter logWriter = new CapturingLogWriter();
        InMemorySimulationMetricsService service = new InMemorySimulationMetricsService(0.0, 10, logWriter);
        AtomicInteger supplierInvocations = new AtomicInteger();

        service.record("sim-a", ProtocolType.HTTP, 200, 10, () -> {
            supplierInvocations.incrementAndGet();
            return entry("a1", ProtocolType.HTTP, 200, 10);
        });

        SimulationLogSnapshot snapshot = service.snapshot();

        assertThat(snapshot.getTotalRequests()).isEqualTo(1);
        assertThat(snapshot.getRecentLogs()).extracting(SimulationLogEntry::getId)
                .containsExactly("a1");
        assertThat(supplierInvocations).hasValue(1);
        assertThat(logWriter.entries).hasSize(1)
                .extracting(SimulationLogEntry::getId)
                .containsExactly("a1");
    }

    @Test
    void restoresRetainedTextLogsIntoSnapshotAfterRestart() {
        CapturingLogWriter logWriter = new CapturingLogWriter(List.of(
                entry("old", ProtocolType.HTTP, 200, 10),
                entry("new-error", ProtocolType.HTTP, 503, 30)
        ));

        InMemorySimulationMetricsService service = new InMemorySimulationMetricsService(0.0, 10, logWriter);

        SimulationLogSnapshot snapshot = service.snapshot();

        assertThat(snapshot.getTotalRequests()).isEqualTo(2);
        assertThat(snapshot.getHttpRequests()).isEqualTo(2);
        assertThat(snapshot.getErrorRequests()).isEqualTo(1);
        assertThat(snapshot.getAverageDurationMs()).isEqualTo(20.0);
        assertThat(snapshot.getRecentLogs()).extracting(SimulationLogEntry::getId)
                .containsExactly("new-error", "old");
    }

    private SimulationLogEntry entry(String id, ProtocolType protocol, int status, long durationMs) {
        return SimulationLogEntry.builder()
                .id(id)
                .simulationId(id + "-simulation")
                .simulationName(id + " simulation")
                .protocol(protocol)
                .status(status)
                .durationMs(durationMs)
                .requestSummary("request " + id)
                .responseSummary("response " + id)
                .timestamp(Instant.parse("2026-07-07T00:00:00Z"))
                .build();
    }

    private static final class CapturingLogWriter implements SimulationLogWriter {
        private final List<SimulationLogEntry> entries = new ArrayList<>();

        private CapturingLogWriter() {
        }

        private CapturingLogWriter(List<SimulationLogEntry> retainedEntries) {
            entries.addAll(retainedEntries);
        }

        @Override
        public void append(SimulationLogEntry entry) {
            entries.add(entry);
        }

        @Override
        public List<SimulationLogEntry> loadRetainedLogs() {
            return List.copyOf(entries);
        }

        @Override
        public void cleanupExpiredLogs() {
        }
    }
}

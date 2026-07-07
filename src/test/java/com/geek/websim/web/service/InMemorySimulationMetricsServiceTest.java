package com.geek.websim.web.service;

import com.geek.websim.web.model.dto.SimulationLogEntry;
import com.geek.websim.web.model.dto.SimulationLogSnapshot;
import com.geek.websim.web.model.enums.ProtocolType;
import com.geek.websim.web.service.impl.InMemorySimulationMetricsService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
        assertThat(snapshot.getRecentLogs()).isEmpty();
        assertThat(supplierInvocations).hasValue(0);
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
}

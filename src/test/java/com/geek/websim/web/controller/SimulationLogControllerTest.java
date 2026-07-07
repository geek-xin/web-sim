package com.geek.websim.web.controller;

import com.geek.websim.web.model.dto.SimulationLogEntry;
import com.geek.websim.web.model.dto.SimulationLogSnapshot;
import com.geek.websim.web.model.enums.ProtocolType;
import com.geek.websim.web.service.SimulationMetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

class SimulationLogControllerTest {
    private static final ParameterizedTypeReference<ServerSentEvent<SimulationLogSnapshot>> SNAPSHOT_EVENT =
            new ParameterizedTypeReference<>() {
            };

    private final FakeMetricsService metricsService = new FakeMetricsService();
    private final WebTestClient webTestClient = WebTestClient
            .bindToController(new SimulationLogController(metricsService))
            .build();

    @Test
    void snapshotReturnsResultWrappedMetricsSnapshot() {
        webTestClient.get()
                .uri("/admin/api/logs/snapshot")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.totalRequests").isEqualTo(7)
                .jsonPath("$.data.httpRequests").isEqualTo(5)
                .jsonPath("$.data.tcpRequests").isEqualTo(2)
                .jsonPath("$.data.errorRequests").isEqualTo(1);
    }

    @Test
    void streamEmitsSnapshotServerSentEvent() {
        FluxExchangeResult<ServerSentEvent<SimulationLogSnapshot>> result = webTestClient.get()
                .uri("/admin/api/logs/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(SNAPSHOT_EVENT);

        StepVerifier.create(result.getResponseBody().take(1))
                .assertNext(event -> {
                    org.assertj.core.api.Assertions.assertThat(event.event()).isEqualTo("snapshot");
                    org.assertj.core.api.Assertions.assertThat(event.data()).isNotNull();
                    org.assertj.core.api.Assertions.assertThat(event.data().getTotalRequests()).isEqualTo(7);
                })
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }

    private static final class FakeMetricsService implements SimulationMetricsService {
        @Override
        public void record(ProtocolType protocol,
                           int status,
                           long durationMs,
                           Supplier<SimulationLogEntry> sampledEntrySupplier) {
        }

        @Override
        public SimulationLogSnapshot snapshot() {
            return SimulationLogSnapshot.builder()
                    .totalRequests(7)
                    .httpRequests(5)
                    .tcpRequests(2)
                    .errorRequests(1)
                    .averageDurationMs(12.5)
                    .recentLogs(List.of())
                    .build();
        }
    }
}

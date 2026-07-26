package com.geek.websim.web.controller;

import com.geek.websim.common.result.Result;
import com.geek.websim.web.model.dto.SimulationLogSnapshot;
import com.geek.websim.web.model.dto.SimulationConfigDto;
import com.geek.websim.web.model.entity.HttpRule;
import com.geek.websim.web.model.entity.RequestTemplate;
import com.geek.websim.web.model.entity.SimulationBranch;
import com.geek.websim.web.model.entity.SimulationCondition;
import com.geek.websim.web.model.entity.SimulationConfig;
import com.geek.websim.web.model.entity.SimulationResponse;
import com.geek.websim.web.model.enums.ConditionOperator;
import com.geek.websim.web.model.enums.ConditionSource;
import com.geek.websim.web.model.enums.HttpMatchMode;
import com.geek.websim.web.model.enums.ProtocolType;
import com.geek.websim.web.service.SimulationRuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpSimulationMetricsLoadTest {
    private static final Path CONFIG_DIR = createTempConfigDir();
    private static final Path LOG_DIR = createTempLogDir();
    private static final ParameterizedTypeReference<Result<SimulationConfig>> CONFIG_RESULT =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<Result<SimulationLogSnapshot>> SNAPSHOT_RESULT =
            new ParameterizedTypeReference<>() {
            };

    @Autowired
    WebTestClient webTestClient;

    @Autowired
    SimulationRuntimeService runtimeService;

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void simulationProperties(DynamicPropertyRegistry registry) {
        registry.add("web-sim.config-dir", () -> CONFIG_DIR.toString());
        registry.add("web-sim.log-dir", () -> LOG_DIR.toString());
        registry.add("web-sim.log-sample-rate", () -> 1.0);
        registry.add("web-sim.recent-log-size", () -> 10_000);
    }

    @BeforeEach
    void clearPersistedConfigsAndRuntimeSnapshot() throws IOException {
        try (var files = Files.list(CONFIG_DIR)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(this::deleteConfigFile);
        }
        runtimeService.publish(runtimeService.compile());
    }

    @Test
    @Timeout(value = 5, unit = java.util.concurrent.TimeUnit.MINUTES)
    void simulatesTenThousandRequestsAtConcurrencyTwoHundredAndVerifiesMetrics() {
        Result<SimulationConfig> createdResult = webTestClient.post()
                .uri("/admin/api/simulations")
                .bodyValue(loadTestConfig())
                .exchange()
                .expectStatus().isOk()
                .expectBody(CONFIG_RESULT)
                .returnResult()
                .getResponseBody();
        assertThat(createdResult).isNotNull();
        String simulationId = createdResult.getData().getId();

        WebClient client = WebClient.builder()
                .exchangeStrategies(largeBodyStrategies())
                .baseUrl("http://127.0.0.1:" + port)
                .build();

        List<Integer> statuses = reactor.core.publisher.Flux.range(1, 10_000)
                .flatMap(i -> client.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/load/{id}")
                                .queryParam("status", i % 10 == 0 ? "fail" : "ok")
                                .build(i))
                        .exchangeToMono(response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .thenReturn(response.statusCode().value())), 200)
                .collectList()
                .block(Duration.ofMinutes(5));

        assertThat(statuses).hasSize(10_000);
        assertThat(statuses).filteredOn(code -> code == 200).hasSize(9_000);
        assertThat(statuses).filteredOn(code -> code == 500).hasSize(1_000);

        Result<SimulationLogSnapshot> result = client.get()
                .uri("/admin/api/logs/snapshot")
                .retrieve()
                .bodyToMono(SNAPSHOT_RESULT)
                .block(Duration.ofMinutes(1));

        assertThat(result).isNotNull();
        SimulationLogSnapshot snapshot = result.getData();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getTotalRequests()).isEqualTo(10_000);
        assertThat(snapshot.getHttpRequests()).isEqualTo(10_000);
        assertThat(snapshot.getTcpRequests()).isZero();
        assertThat(snapshot.getErrorRequests()).isEqualTo(1_000);
        assertThat(snapshot.getAverageDurationMs()).isPositive();
        assertThat(snapshot.getSimulationMetrics()).containsKey(simulationId);
        assertThat(snapshot.getSimulationMetrics().get(simulationId).getHits()).isEqualTo(10_000);
        assertThat(snapshot.getSimulationMetrics().get(simulationId).getErrors()).isEqualTo(1_000);
        assertThat(snapshot.getSimulationMetrics().get(simulationId).getAverageDurationMs()).isPositive();
        assertThat(snapshot.getRecentLogs()).hasSize(10_000);
        assertThat(snapshot.getRecentLogs())
                .allSatisfy(entry -> assertThat(entry.getSimulationId()).isEqualTo(simulationId));
        assertThat(snapshot.getRecentLogs())
                .filteredOn(entry -> entry.getStatus() >= 400)
                .hasSize(1_000);
    }

    private SimulationConfigDto loadTestConfig() {
        return SimulationConfigDto.builder()
                .name("load-test")
                .protocol(ProtocolType.HTTP)
                .enabled(true)
                .http(HttpRule.builder()
                        .method("GET")
                        .path("/api/load/{id}")
                        .matchMode(HttpMatchMode.TEMPLATE)
                        .build())
                .requestTemplate(new RequestTemplate())
                .branches(List.of(
                        SimulationBranch.builder()
                                .name("fail")
                                .priority(10)
                                .conditions(List.of(SimulationCondition.builder()
                                        .source(ConditionSource.QUERY)
                                        .key("status")
                                        .operator(ConditionOperator.EQ)
                                        .value("fail")
                                        .build()))
                                .response(SimulationResponse.builder()
                                        .status(500)
                                        .headers(Map.of("Content-Type", "application/json"))
                                        .body("{\"error\":\"failed {{path.id}}\"}")
                                        .delayMs(1L)
                                        .build())
                                .build()))
                .defaultResponse(SimulationResponse.builder()
                        .status(200)
                        .headers(Map.of("Content-Type", "application/json"))
                        .body("{\"id\":\"{{path.id}}\",\"status\":\"ok\"}")
                        .delayMs(1L)
                        .build())
                .build();
    }

    private static Path createTempConfigDir() {
        try {
            return Files.createTempDirectory("web-sim-load-test-");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Path createTempLogDir() {
        try {
            return Files.createTempDirectory("web-sim-load-test-logs-");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static ExchangeStrategies largeBodyStrategies() {
        return ExchangeStrategies.builder()
                .codecs(HttpSimulationMetricsLoadTest::configureLargeBuffer)
                .build();
    }

    private static void configureLargeBuffer(ClientCodecConfigurer configurer) {
        configurer.defaultCodecs().maxInMemorySize(20 * 1024 * 1024);
    }

    private void deleteConfigFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

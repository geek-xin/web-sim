package com.geek.websim.web.controller;

import com.geek.websim.common.result.Result;
import com.geek.websim.web.model.dto.SimulationConfigDto;
import com.geek.websim.web.model.entity.HttpRule;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpSimulationControllerTest {
    private static final Path CONFIG_DIR = createTempConfigDir();
    private static final ParameterizedTypeReference<Result<SimulationConfig>> CONFIG_RESULT = new ParameterizedTypeReference<>() {
    };

    @Autowired
    WebTestClient webTestClient;

    @Autowired
    SimulationRuntimeService runtimeService;

    @DynamicPropertySource
    static void simulationProperties(DynamicPropertyRegistry registry) {
        registry.add("web-sim.config-dir", () -> CONFIG_DIR.toString());
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
    void adminCreatedHttpSimulationServesMatchingRequestsAndStopsAfterDisable() {
        SimulationConfig created = webTestClient.post()
                .uri("/admin/api/simulations")
                .bodyValue(usersConfig(true))
                .exchange()
                .expectStatus().isOk()
                .expectBody(CONFIG_RESULT)
                .returnResult()
                .getResponseBody()
                .getData();

        assertThat(created.getId()).isNotBlank();

        webTestClient.get()
                .uri("/api/users/u1?status=ok")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body).contains("u1"));

        webTestClient.get()
                .uri("/api/users/u1?status=fail")
                .exchange()
                .expectStatus().isEqualTo(500);

        webTestClient.get()
                .uri("/api/unknown")
                .exchange()
                .expectStatus().isNotFound();

        SimulationConfigDto disabled = usersConfig(false);
        disabled.setId(created.getId());
        webTestClient.put()
                .uri("/admin/api/simulations/{id}", created.getId())
                .bodyValue(disabled)
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/api/users/u1?status=ok")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void administratorPathIsNotTreatedAsReservedAdminPrefix() {
        webTestClient.post()
                .uri("/admin/api/simulations")
                .bodyValue(simpleHttpConfig("administrator", "/administrator", 200, "administrator ok"))
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/administrator")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body).contains("administrator ok"));
    }

    @Test
    void adminAssetPathIsNotCapturedBySimulatorCatchAll() {
        webTestClient.post()
                .uri("/admin/api/simulations")
                .bodyValue(simpleHttpConfig("admin asset", "/admin/assets/__missing-test.js", 200, "simulated admin asset"))
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/admin/assets/__missing-test.js")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .consumeWith(result -> assertThat(responseBody(result)).doesNotContain("simulated admin asset"));
    }

    private SimulationConfigDto usersConfig(boolean enabled) {
        return SimulationConfigDto.builder()
                .name("用户查询")
                .protocol(ProtocolType.HTTP)
                .enabled(enabled)
                .http(HttpRule.builder()
                        .method("GET")
                        .path("/api/users/{id}")
                        .matchMode(HttpMatchMode.TEMPLATE)
                        .build())
                .branches(List.of(
                        branch("ok", 0, "ok", 200, "{\"id\":\"{{path.id}}\",\"status\":\"ok\"}"),
                        branch("fail", 1, "fail", 500, "{\"error\":\"failed {{path.id}}\"}")
                ))
                .defaultResponse(SimulationResponse.builder()
                        .status(404)
                        .headers(Map.of("Content-Type", "application/json"))
                        .body("{\"error\":\"default\"}")
                        .build())
                .build();
    }

    private SimulationConfigDto simpleHttpConfig(String name, String path, int status, String body) {
        return SimulationConfigDto.builder()
                .name(name)
                .protocol(ProtocolType.HTTP)
                .enabled(true)
                .http(HttpRule.builder()
                        .method("GET")
                        .path(path)
                        .matchMode(HttpMatchMode.EXACT)
                        .build())
                .defaultResponse(SimulationResponse.builder()
                        .status(status)
                        .headers(Map.of("Content-Type", "text/plain"))
                        .body(body)
                        .build())
                .build();
    }

    private SimulationBranch branch(String name,
                                    int priority,
                                    String conditionValue,
                                    int status,
                                    String body) {
        return SimulationBranch.builder()
                .name(name)
                .priority(priority)
                .conditions(List.of(SimulationCondition.builder()
                        .source(ConditionSource.QUERY)
                        .key("status")
                        .operator(ConditionOperator.EQ)
                        .value(conditionValue)
                        .build()))
                .response(SimulationResponse.builder()
                        .status(status)
                        .headers(Map.of("Content-Type", "application/json"))
                        .body(body)
                        .build())
                .build();
    }

    private static Path createTempConfigDir() {
        try {
            return Files.createTempDirectory("web-sim-http-test-");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private void deleteConfigFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String responseBody(org.springframework.test.web.reactive.server.EntityExchangeResult<byte[]> result) {
        byte[] body = result.getResponseBodyContent();
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }
}

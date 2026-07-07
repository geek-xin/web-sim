package com.geek.websim.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geek.websim.common.enums.ErrorCodeEnum;
import com.geek.websim.common.exception.BusinessException;
import com.geek.websim.web.model.entity.*;
import com.geek.websim.web.model.enums.*;
import com.geek.websim.web.service.impl.SimulationConfigServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationConfigServiceImplTest {
    @TempDir Path tempDir;

    @Test
    void createPersistsSimulationWithGeneratedId() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();

        SimulationConfig saved = service.create(httpConfig("用户查询", "/api/users/{id}"));

        assertThat(saved.getId()).startsWith("sim-");
        assertThat(service.listAll()).extracting(SimulationConfig::getName).containsExactly("用户查询");
        assertThat(tempDir.resolve(saved.getId() + ".json")).exists();
    }

    @Test
    void updateRejectsDuplicateName() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();
        SimulationConfig one = service.create(httpConfig("用户查询", "/api/users/{id}"));
        service.create(httpConfig("订单查询", "/api/orders/{id}"));

        SimulationConfig renamed = httpConfig("订单查询", "/api/users/{id}");
        assertThatThrownBy(() -> service.update(one.getId(), renamed)).isInstanceOf(BusinessException.class);
    }

    @Test
    void createRejectsDuplicateTcpBinding() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();
        service.create(tcpConfig("TCP A", 19001));

        assertThatThrownBy(() -> service.create(tcpConfig("TCP B", 19001))).isInstanceOf(BusinessException.class);
    }

    @Test
    void createRejectsDuplicateTcpPortWithDifferentHost() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();
        service.create(tcpConfig("TCP A", "127.0.0.1", 19001));

        assertThatThrownBy(() -> service.create(tcpConfig("TCP B", "0.0.0.0", 19001)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void restoreWritesConfigWithExactIdWhenFileIsMissing() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();
        SimulationConfig saved = service.create(httpConfig("原配置", "/old"));
        service.delete(saved.getId());

        SimulationConfig restored = service.restore(saved.getId(), httpConfig("恢复配置", "/restored"));

        assertThat(restored.getId()).isEqualTo(saved.getId());
        assertThat(tempDir.resolve(saved.getId() + ".json")).exists();
        assertThat(service.getById(saved.getId()).getName()).isEqualTo("恢复配置");
    }

    @Test
    void restoreRejectsDuplicateNameFromAnotherConfig() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();
        SimulationConfig one = service.create(httpConfig("配置一", "/one"));
        service.create(httpConfig("配置二", "/two"));

        assertThatThrownBy(() -> service.restore(one.getId(), httpConfig("配置二", "/one-restored")))
                .isInstanceOf(BusinessException.class);
    }


    @Test
    void getByIdRejectsMissingConfig() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();

        assertThatThrownBy(() -> service.getById("missing"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCodeEnum.NOT_FOUND));
    }

    @Test
    void failedCreateDoesNotMutateInput() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();
        SimulationConfig config = httpConfig("bad http", "api/users");
        config.getHttp().setMethod("");

        assertThatThrownBy(() -> service.create(config)).isInstanceOf(BusinessException.class);

        assertThat(config.getHttp().getMethod()).isEmpty();
        assertThat(config.getHttp().getPath()).isEqualTo("api/users");
    }

    @Test
    void createTrimsHttpPathBeforeValidation() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();
        SimulationConfig config = httpConfig("trimmed path", " /foo ");

        SimulationConfig saved = service.create(config);

        assertThat(saved.getHttp().getPath()).isEqualTo("/foo");
        assertThat(service.getById(saved.getId()).getHttp().getPath()).isEqualTo("/foo");
    }

    @Test
    void createRejectsInvalidDefaultResponseStatus() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();
        SimulationConfig config = httpConfig("bad status", "/bad-status");
        config.getDefaultResponse().setStatus(99);

        assertThatThrownBy(() -> service.create(config))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCodeEnum.BAD_REQUEST));
    }

    @Test
    void createRejectsInvalidBranchResponseStatus() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();
        SimulationConfig config = httpConfig("bad branch status", "/bad-branch-status");
        config.setBranches(List.of(SimulationBranch.builder()
                .name("bad")
                .response(SimulationResponse.builder().status(1000).body("bad").build())
                .build()));

        assertThatThrownBy(() -> service.create(config))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCodeEnum.BAD_REQUEST));
    }

    @Test
    void createRejectsInvalidResponseVariantStatus() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();
        SimulationConfig config = httpConfig("bad variant status", "/bad-variant-status");
        config.setBranches(List.of(SimulationBranch.builder()
                .name("flaky")
                .response(SimulationResponse.builder().status(200).body("ok").build())
                .responseVariants(List.of(SimulationResponse.builder().status(1000).body("bad").build()))
                .build()));

        assertThatThrownBy(() -> service.create(config))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCodeEnum.BAD_REQUEST));
    }



    @Test
    void createRejectsEqConditionMissingValue() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();
        SimulationConfig config = httpConfig("missing condition value", "/missing-condition-value");
        config.setBranches(List.of(SimulationBranch.builder()
                .name("missing value")
                .priority(0)
                .conditions(List.of(SimulationCondition.builder()
                        .source(ConditionSource.QUERY)
                        .key("status")
                        .operator(ConditionOperator.EQ)
                        .value(" ")
                        .build()))
                .response(SimulationResponse.builder().status(200).body("ok").build())
                .build()));

        assertThatThrownBy(() -> service.create(config))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCodeEnum.BAD_REQUEST));
    }

    @Test
    void createRejectsBlankHeaderNames() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();
        SimulationConfig config = httpConfig("blank header", "/blank-header");
        config.getDefaultResponse().setHeaders(Map.of(" ", "bad"));

        assertThatThrownBy(() -> service.create(config))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCodeEnum.BAD_REQUEST));
    }

    @Test
    void createRejectsGeneratedAndHopByHopResponseHeaders() {
        SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
        service.initDefaultConfigs();
        SimulationConfig config = httpConfig("generated header", "/generated-header");
        config.getDefaultResponse().setHeaders(Map.of("Content-Length", "12"));

        assertThatThrownBy(() -> service.create(config))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCodeEnum.BAD_REQUEST));
    }

    @Test
    void createRejectsUnsafeHeaderNames() {
        SimulationConfigImplTestSupport.assertCreateRejectsHeader(
                tempDir,
                "unsafe header name",
                Map.of("X-Bad\r\nInjected", "bad"));
    }

    @Test
    void createRejectsUnsafeHeaderValues() {
        SimulationConfigImplTestSupport.assertCreateRejectsHeader(
                tempDir,
                "unsafe header value",
                Map.of("X-Good", "ok\r\nX-Injected: yes"));
    }

    @Test
    void createRejectsTransferEncodingHeaderCaseInsensitively() {
        SimulationConfigImplTestSupport.assertCreateRejectsHeader(
                tempDir,
                "transfer encoding",
                Map.of("transfer-encoding", "chunked"));
    }

    private SimulationConfig httpConfig(String name, String path) {
        return SimulationConfig.builder()
                .name(name)
                .protocol(ProtocolType.HTTP)
                .enabled(true)
                .http(HttpRule.builder().method("GET").path(path).matchMode(HttpMatchMode.TEMPLATE).build())
                .branches(List.of())
                .defaultResponse(SimulationResponse.builder().status(404).body("not matched").build())
                .build();
    }

    private SimulationConfig tcpConfig(String name, int port) {
        return tcpConfig(name, "127.0.0.1", port);
    }

    private SimulationConfig tcpConfig(String name, String host, int port) {
        return SimulationConfig.builder()
                .name(name)
                .protocol(ProtocolType.TCP)
                .enabled(true)
                .tcp(TcpRule.builder().host(host).port(port).frameMode(TcpFrameMode.LINE).build())
                .branches(List.of())
                .defaultResponse(SimulationResponse.builder().status(200).body("OK\n").build())
                .build();
    }

    private static class SimulationConfigImplTestSupport {
        private static void assertCreateRejectsHeader(Path tempDir, String name, Map<String, String> headers) {
            SimulationConfigServiceImpl service = new SimulationConfigServiceImpl(new ObjectMapper(), tempDir);
            service.initDefaultConfigs();
            SimulationConfig config = SimulationConfig.builder()
                    .name(name)
                    .protocol(ProtocolType.HTTP)
                    .enabled(true)
                    .http(HttpRule.builder().method("GET").path("/" + name.replace(" ", "-")).matchMode(HttpMatchMode.EXACT).build())
                    .defaultResponse(SimulationResponse.builder().status(200).headers(headers).body("body").build())
                    .build();

            assertThatThrownBy(() -> service.create(config))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(ErrorCodeEnum.BAD_REQUEST));
        }
    }
}

package com.geek.websim.runtime;

import com.geek.websim.web.model.entity.HttpRule;
import com.geek.websim.web.model.entity.SimulationBranch;
import com.geek.websim.web.model.entity.SimulationCondition;
import com.geek.websim.web.model.entity.SimulationConfig;
import com.geek.websim.web.model.entity.SimulationResponse;
import com.geek.websim.web.model.entity.TcpRule;
import com.geek.websim.web.model.enums.ConditionOperator;
import com.geek.websim.web.model.enums.ConditionSource;
import com.geek.websim.web.model.enums.HttpMatchMode;
import com.geek.websim.web.model.enums.ProtocolType;
import com.geek.websim.web.model.enums.ResponseVariantStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationMatcherTest {

    @Test
    void httpTemplateMatchesAndExtractsPathVariables() {
        SimulationConfig config = httpConfig("users", "GET", "/api/users/{id}", HttpMatchMode.TEMPLATE,
                response("default"),
                branch("path branch", 0, response("user"),
                        condition(ConditionSource.PATH, "id", ConditionOperator.EQ, "u1")));
        SimulationMatcher matcher = matcher(config);

        Optional<SimulationMatchResult> result = matcher.matchHttp(SimulationRequest.builder()
                .method("GET")
                .path("/api/users/u1")
                .build());

        assertThat(result).isPresent();
        assertThat(result.get().getConfig()).isNotSameAs(config);
        assertThat(result.get().getConfig().getId()).isEqualTo(config.getId());
        assertThat(result.get().getBranch().getName()).isEqualTo("path branch");
        assertThat(result.get().getResponse().getBody()).isEqualTo("user");
        assertThat(result.get().getPathVariables()).containsEntry("id", "u1");
    }

    @Test
    void httpExactDoesNotMatchLongerPath() {
        SimulationMatcher matcher = matcher(httpConfig("exact", "GET", "/api/users", HttpMatchMode.EXACT,
                response("default")));

        assertThat(matcher.matchHttp(SimulationRequest.builder()
                .method("GET")
                .path("/api/users/u1")
                .build())).isEmpty();
    }

    @Test
    void httpPrefixMatchesNestedPath() {
        SimulationMatcher matcher = matcher(httpConfig("prefix", "GET", "/api/users", HttpMatchMode.PREFIX,
                response("nested")));

        Optional<SimulationMatchResult> result = matcher.matchHttp(SimulationRequest.builder()
                .method("GET")
                .path("/api/users/u1/orders")
                .build());

        assertThat(result).isPresent();
        assertThat(result.get().getResponse().getBody()).isEqualTo("nested");
    }

    @Test
    void httpPrefixRequiresSegmentBoundary() {
        SimulationMatcher matcher = matcher(httpConfig("prefix", "GET", "/api/users", HttpMatchMode.PREFIX,
                response("nested")));

        assertThat(matcher.matchHttp(SimulationRequest.builder()
                .method("GET")
                .path("/api/users2")
                .build())).isEmpty();
    }

    @Test
    void rootHttpPrefixMatchesAllAbsolutePaths() {
        SimulationMatcher matcher = matcher(httpConfig("root", "GET", "/", HttpMatchMode.PREFIX,
                response("root")));

        assertThat(matcher.matchHttp(SimulationRequest.builder()
                .method("GET")
                .path("/api/users")
                .build())).isPresent();
    }

    @Test
    void lowerPriorityMatchingBranchIsSelectedFirst() {
        SimulationConfig config = httpConfig("priority", "ANY", "/api/priority", HttpMatchMode.EXACT,
                response("default"),
                branch("later", 20, response("later"),
                        condition(ConditionSource.QUERY, "group", ConditionOperator.EQ, "a")),
                branch("earlier", 10, response("earlier"),
                        condition(ConditionSource.QUERY, "group", ConditionOperator.EQ, "a")));
        SimulationMatcher matcher = matcher(config);

        Optional<SimulationMatchResult> result = matcher.matchHttp(SimulationRequest.builder()
                .method("POST")
                .path("/api/priority")
                .query(Map.of("group", "a"))
                .build());

        assertThat(result).isPresent();
        assertThat(result.get().getBranch().getName()).isEqualTo("earlier");
        assertThat(result.get().getResponse().getBody()).isEqualTo("earlier");
    }

    @Test
    void unconditionalBranchesInterleaveWithDefaultResponseWithoutRequestConditions() {
        SimulationBranch errorBranch = branch("error", 0, SimulationResponse.builder()
                .status(503)
                .body("error")
                .build());
        SimulationMatcher matcher = matcher(httpConfig("interleave", "GET", "/api/interleave", HttpMatchMode.EXACT,
                SimulationResponse.builder()
                        .status(200)
                        .body("ok")
                        .build(),
                errorBranch));

        SimulationRequest request = SimulationRequest.builder()
                .method("GET")
                .path("/api/interleave")
                .build();

        assertThat(matcher.matchHttp(request).map(result -> result.getResponse().getStatus())).contains(200);
        assertThat(matcher.matchHttp(request).map(result -> result.getResponse().getStatus())).contains(503);
        assertThat(matcher.matchHttp(request).map(result -> result.getResponse().getStatus())).contains(200);
        assertThat(matcher.matchHttp(request).map(result -> result.getResponse().getStatus())).contains(503);
    }

    @Test
    void unconditionalBranchProbabilityControlsWhetherBranchCanAppear() {
        SimulationBranch alwaysError = branch("always error", 0, SimulationResponse.builder()
                .status(503)
                .body("error")
                .build());
        alwaysError.setProbability(1.0);
        SimulationMatcher alwaysMatcher = matcher(httpConfig("always", "GET", "/api/probability", HttpMatchMode.EXACT,
                SimulationResponse.builder().status(200).body("ok").build(),
                alwaysError));
        SimulationRequest request = SimulationRequest.builder()
                .method("GET")
                .path("/api/probability")
                .build();

        assertThat(alwaysMatcher.matchHttp(request).map(result -> result.getResponse().getStatus())).contains(503);
        assertThat(alwaysMatcher.matchHttp(request).map(result -> result.getResponse().getStatus())).contains(503);

        SimulationBranch neverError = branch("never error", 0, SimulationResponse.builder()
                .status(503)
                .body("error")
                .build());
        neverError.setProbability(0.0);
        SimulationMatcher neverMatcher = matcher(httpConfig("never", "GET", "/api/probability", HttpMatchMode.EXACT,
                SimulationResponse.builder().status(200).body("ok").build(),
                neverError));

        assertThat(neverMatcher.matchHttp(request).map(result -> result.getResponse().getStatus())).contains(200);
        assertThat(neverMatcher.matchHttp(request).map(result -> result.getResponse().getStatus())).contains(200);
    }

    @Test
    void matchingBranchCyclesPrimaryAndVariantResponses() {
        SimulationBranch branch = branch("flaky", 0, response("ok"),
                condition(ConditionSource.QUERY, "branch", ConditionOperator.EQ, "flaky"));
        branch.setResponseVariants(List.of(
                SimulationResponse.builder().status(500).body("error").build(),
                SimulationResponse.builder().status(429).body("busy").build()));
        branch.setVariantStrategy(ResponseVariantStrategy.ROUND_ROBIN);
        SimulationMatcher matcher = matcher(httpConfig("flaky", "GET", "/api/flaky", HttpMatchMode.EXACT,
                response("default"),
                branch));

        SimulationRequest request = SimulationRequest.builder()
                .method("GET")
                .path("/api/flaky")
                .query(Map.of("branch", "flaky"))
                .build();

        assertThat(matcher.matchHttp(request).map(result -> result.getResponse().getBody())).contains("ok");
        assertThat(matcher.matchHttp(request).map(result -> result.getResponse().getBody())).contains("error");
        assertThat(matcher.matchHttp(request).map(result -> result.getResponse().getBody())).contains("busy");
        assertThat(matcher.matchHttp(request).map(result -> result.getResponse().getBody())).contains("ok");
    }

    @Test
    void branchWithNullResponseIsSkipped() {
        SimulationConfig config = httpConfig("invalid-branch", "GET", "/api/branch", HttpMatchMode.EXACT,
                response("default"),
                branch("invalid", 0, null),
                branch("valid", 10, response("valid"),
                        condition(ConditionSource.QUERY, "branch", ConditionOperator.EQ, "valid")));
        SimulationMatcher matcher = matcher(config);

        Optional<SimulationMatchResult> result = matcher.matchHttp(SimulationRequest.builder()
                .method("GET")
                .path("/api/branch")
                .query(Map.of("branch", "valid"))
                .build());

        assertThat(result).isPresent();
        assertThat(result.get().getBranch().getName()).isEqualTo("valid");
        assertThat(result.get().getResponse().getBody()).isEqualTo("valid");
    }

    @Test
    void snapshotFreezesConfigAgainstLaterCallerMutations() {
        SimulationConfig config = httpConfig("immutable", "GET", "/api/users/{id}", HttpMatchMode.TEMPLATE,
                response("default"),
                branch("before", 0, response("before"),
                        condition(ConditionSource.PATH, "id", ConditionOperator.EQ, "u1")));
        SimulationMatcher matcher = matcher(config);

        config.getHttp().setPath("/mutated");
        config.getBranches().get(0).setResponse(response("mutated"));
        config.getDefaultResponse().setBody("mutated-default");

        Optional<SimulationMatchResult> result = matcher.matchHttp(SimulationRequest.builder()
                .method("GET")
                .path("/api/users/u1")
                .build());

        assertThat(result).isPresent();
        assertThat(result.get().getResponse().getBody()).isEqualTo("before");
        assertThat(result.get().getConfig().getHttp().getPath()).isEqualTo("/api/users/{id}");
    }

    @Test
    void templateResultIncludesExistingAndExtractedPathVariables() {
        SimulationMatcher matcher = matcher(httpConfig("merged-vars", "GET", "/api/users/{id}", HttpMatchMode.TEMPLATE,
                response("default"),
                branch("tenant branch", 0, response("tenant"),
                        condition(ConditionSource.PATH, "tenant", ConditionOperator.EQ, "t1"),
                        condition(ConditionSource.PATH, "id", ConditionOperator.EQ, "u1"))));

        Optional<SimulationMatchResult> result = matcher.matchHttp(SimulationRequest.builder()
                .method("GET")
                .path("/api/users/u1")
                .pathVariables(Map.of("tenant", "t1"))
                .build());

        assertThat(result).isPresent();
        assertThat(result.get().getBranch().getName()).isEqualTo("tenant branch");
        assertThat(result.get().getPathVariables()).containsEntry("tenant", "t1").containsEntry("id", "u1");
    }

    @Test
    void disabledConfigsAreNotInSnapshot() {
        SimulationConfig enabled = httpConfig("enabled", "GET", "/enabled", HttpMatchMode.EXACT, response("enabled"));
        SimulationConfig disabled = httpConfig("disabled", "GET", "/disabled", HttpMatchMode.EXACT, response("disabled"));
        disabled.setEnabled(false);

        SimulationRuleSnapshot snapshot = SimulationRuleSnapshot.from(List.of(enabled, disabled));
        SimulationMatcher matcher = new SimulationMatcher(snapshot);

        assertThat(snapshot.getHttpRules()).hasSize(1);
        assertThat(snapshot.getHttpRules().get(0).getConfig()).isNotSameAs(enabled);
        assertThat(snapshot.getHttpRules().get(0).getConfig().getId()).isEqualTo(enabled.getId());
        assertThat(matcher.matchHttp(SimulationRequest.builder()
                .method("GET")
                .path("/disabled")
                .build())).isEmpty();
    }

    @Test
    void tcpRuleMatchesByPortAndBodyCondition() {
        SimulationConfig config = tcpConfig("tcp", 9000, response("default"),
                branch("tcp branch", 0, response("pong"),
                        condition(ConditionSource.TCP_BODY, null, ConditionOperator.CONTAINS, "PING")));
        SimulationMatcher matcher = matcher(config);

        Optional<SimulationMatchResult> result = matcher.matchTcp(9000, SimulationRequest.builder()
                .tcpBody("client PING")
                .build());

        assertThat(result).isPresent();
        assertThat(result.get().getConfig()).isNotSameAs(config);
        assertThat(result.get().getConfig().getId()).isEqualTo(config.getId());
        assertThat(result.get().getBranch().getName()).isEqualTo("tcp branch");
        assertThat(result.get().getResponse().getBody()).isEqualTo("pong");
        assertThat(matcher.matchTcp(9001, SimulationRequest.builder().tcpBody("client PING").build())).isEmpty();
    }

    private SimulationMatcher matcher(SimulationConfig... configs) {
        return new SimulationMatcher(SimulationRuleSnapshot.from(List.of(configs)));
    }

    private SimulationConfig httpConfig(String id, String method, String path, HttpMatchMode matchMode,
                                        SimulationResponse defaultResponse, SimulationBranch... branches) {
        return SimulationConfig.builder()
                .id(id)
                .name(id)
                .protocol(ProtocolType.HTTP)
                .enabled(true)
                .http(HttpRule.builder()
                        .method(method)
                        .path(path)
                        .matchMode(matchMode)
                        .build())
                .branches(List.of(branches))
                .defaultResponse(defaultResponse)
                .build();
    }

    private SimulationConfig tcpConfig(String id, int port, SimulationResponse defaultResponse, SimulationBranch... branches) {
        return SimulationConfig.builder()
                .id(id)
                .name(id)
                .protocol(ProtocolType.TCP)
                .enabled(true)
                .tcp(TcpRule.builder()
                        .port(port)
                        .build())
                .branches(List.of(branches))
                .defaultResponse(defaultResponse)
                .build();
    }

    private SimulationBranch branch(String name, int priority, SimulationResponse response, SimulationCondition... conditions) {
        return SimulationBranch.builder()
                .name(name)
                .priority(priority)
                .conditions(List.of(conditions))
                .response(response)
                .build();
    }

    private SimulationCondition condition(ConditionSource source, String key, ConditionOperator operator, String value) {
        return SimulationCondition.builder()
                .source(source)
                .key(key)
                .operator(operator)
                .value(value)
                .build();
    }

    private SimulationResponse response(String body) {
        return SimulationResponse.builder()
                .status(200)
                .body(body)
                .build();
    }
}

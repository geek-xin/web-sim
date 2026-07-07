package com.geek.websim.runtime;

import com.geek.websim.web.model.entity.SimulationCondition;
import com.geek.websim.web.model.enums.ConditionOperator;
import com.geek.websim.web.model.enums.ConditionSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BranchMatcherTest {

    private final BranchMatcher matcher = new BranchMatcher();

    @Test
    void matchesEqQueryCondition() {
        SimulationRequest request = SimulationRequest.builder()
                .query(Map.of("status", "paid"))
                .build();

        assertThat(matcher.matches(List.of(condition(ConditionSource.QUERY, "status", ConditionOperator.EQ, "paid")), request))
                .isTrue();
    }

    @Test
    void matchesEqPathCondition() {
        SimulationRequest request = SimulationRequest.builder()
                .pathVariables(Map.of("id", "u1"))
                .build();

        assertThat(matcher.matches(List.of(condition(ConditionSource.PATH, "id", ConditionOperator.EQ, "u1")), request))
                .isTrue();
    }

    @Test
    void matchesContainsTcpBodyCondition() {
        SimulationRequest request = SimulationRequest.builder()
                .tcpBody("hello device-42")
                .build();

        assertThat(matcher.matches(List.of(condition(ConditionSource.TCP_BODY, null, ConditionOperator.CONTAINS, "device")), request))
                .isTrue();
    }

    @Test
    void matchesRegexBodyCondition() {
        SimulationRequest request = SimulationRequest.builder()
                .body("order-12345")
                .build();

        assertThat(matcher.matches(List.of(condition(ConditionSource.BODY, null, ConditionOperator.REGEX, "order-\\d+")), request))
                .isTrue();
    }

    @Test
    void matchesExistsHeaderCondition() {
        SimulationRequest request = SimulationRequest.builder()
                .headers(Map.of("X-Trace", "abc"))
                .build();

        assertThat(matcher.matches(List.of(condition(ConditionSource.HEADER, "X-Trace", ConditionOperator.EXISTS, null)), request))
                .isTrue();
    }

    @Test
    void notEqFailsWhenValueIsEqual() {
        SimulationRequest request = SimulationRequest.builder()
                .query(Map.of("mode", "test"))
                .build();

        assertThat(matcher.matches(List.of(condition(ConditionSource.QUERY, "mode", ConditionOperator.NOT_EQ, "test")), request))
                .isFalse();
    }

    @Test
    void matchesJsonPathBodyTopLevelKey() {
        SimulationRequest request = SimulationRequest.builder()
                .body("{\"state\":\"ready\"}")
                .build();

        assertThat(matcher.matches(List.of(condition(ConditionSource.BODY, "state", ConditionOperator.JSON_PATH, "ready")), request))
                .isTrue();
    }

    @Test
    void matchesJsonPathTcpBodyTopLevelKey() {
        SimulationRequest request = SimulationRequest.builder()
                .tcpBody("{\"command\":\"PING\"}")
                .build();

        assertThat(matcher.matches(List.of(condition(ConditionSource.TCP_BODY, "command", ConditionOperator.JSON_PATH, "PING")), request))
                .isTrue();
    }


    @Test
    void missingQueryKeyEqDoesNotMatch() {
        SimulationRequest request = SimulationRequest.builder()
                .query(Map.of("present", "value"))
                .build();

        assertThat(matcher.matches(List.of(condition(ConditionSource.QUERY, "missing", ConditionOperator.EQ, "value")), request))
                .isFalse();
    }

    @Test
    void missingQueryKeyNotEqDoesNotMatch() {
        SimulationRequest request = SimulationRequest.builder()
                .query(Map.of("present", "value"))
                .build();

        assertThat(matcher.matches(List.of(condition(ConditionSource.QUERY, "missing", ConditionOperator.NOT_EQ, "other")), request))
                .isFalse();
    }

    @Test
    void nullSourceDoesNotMatch() {
        SimulationRequest request = SimulationRequest.builder()
                .query(Map.of("status", "paid"))
                .build();

        assertThat(matcher.matches(List.of(condition(null, "status", ConditionOperator.EQ, "paid")), request))
                .isFalse();
    }


    @Test
    void bodyExistsFalseWhenBodyIsNull() {
        SimulationRequest request = SimulationRequest.builder().build();

        assertThat(matcher.matches(List.of(condition(ConditionSource.BODY, null, ConditionOperator.EXISTS, null)), request))
                .isFalse();
    }

    @Test
    void bodyNotEqFalseWhenBodyIsNull() {
        SimulationRequest request = SimulationRequest.builder().build();

        assertThat(matcher.matches(List.of(condition(ConditionSource.BODY, null, ConditionOperator.NOT_EQ, "anything")), request))
                .isFalse();
    }

    @Test
    void tcpBodyExistsFalseWhenTcpBodyIsNull() {
        SimulationRequest request = SimulationRequest.builder().build();

        assertThat(matcher.matches(List.of(condition(ConditionSource.TCP_BODY, null, ConditionOperator.EXISTS, null)), request))
                .isFalse();
    }

    @Test
    void tcpBodyNotEqFalseWhenTcpBodyIsNull() {
        SimulationRequest request = SimulationRequest.builder().build();

        assertThat(matcher.matches(List.of(condition(ConditionSource.TCP_BODY, null, ConditionOperator.NOT_EQ, "anything")), request))
                .isFalse();
    }

    private SimulationCondition condition(ConditionSource source, String key, ConditionOperator operator, String value) {
        return SimulationCondition.builder()
                .source(source)
                .key(key)
                .operator(operator)
                .value(value)
                .build();
    }
}

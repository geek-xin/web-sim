package com.geek.websim.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geek.websim.web.model.entity.SimulationCondition;
import com.geek.websim.web.model.enums.ConditionOperator;
import com.geek.websim.web.model.enums.ConditionSource;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class BranchMatcher {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public boolean matches(List<SimulationCondition> conditions, SimulationRequest request) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        if (request == null) {
            return false;
        }
        for (SimulationCondition condition : conditions) {
            if (!matches(condition, request)) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(SimulationCondition condition, SimulationRequest request) {
        if (condition == null || condition.getOperator() == null) {
            return false;
        }
        if (condition.getOperator() == ConditionOperator.JSON_PATH) {
            return matchesJsonPath(condition, request);
        }

        ResolvedValue resolved = resolve(condition.getSource(), condition.getKey(), request);
        return switch (condition.getOperator()) {
            case EQ -> resolved.present() && Objects.equals(resolved.value(), condition.getValue());
            case NOT_EQ -> resolved.present() && !Objects.equals(resolved.value(), condition.getValue());
            case CONTAINS -> resolved.present() && resolved.value() != null && condition.getValue() != null
                    && resolved.value().contains(condition.getValue());
            case REGEX -> resolved.present() && matchesRegex(resolved.value(), condition.getValue());
            case EXISTS -> resolved.present();
            case JSON_PATH -> false;
        };
    }

    private boolean matchesJsonPath(SimulationCondition condition, SimulationRequest request) {
        if (condition.getSource() == null || isBlank(condition.getKey())) {
            return false;
        }
        String json = switch (condition.getSource()) {
            case BODY -> request.getBody();
            case TCP_BODY -> request.getTcpBody();
            case QUERY, HEADER, PATH -> null;
        };
        if (json == null) {
            return false;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json).path(condition.getKey());
            if (node.isMissingNode()) {
                return false;
            }
            return Objects.equals(node.asText(), condition.getValue());
        } catch (Exception ignored) {
            return false;
        }
    }

    private ResolvedValue resolve(ConditionSource source, String key, SimulationRequest request) {
        if (source == null) {
            return ResolvedValue.missing();
        }
        return switch (source) {
            case QUERY -> lookup(request.getQuery(), key);
            case HEADER -> lookup(request.getHeaders(), key);
            case PATH -> lookup(request.getPathVariables(), key);
            case BODY -> isBlank(key) ? bodyValue(request.getBody()) : ResolvedValue.missing();
            case TCP_BODY -> isBlank(key) ? bodyValue(request.getTcpBody()) : ResolvedValue.missing();
        };
    }

    private ResolvedValue bodyValue(String body) {
        return body == null ? ResolvedValue.missing() : ResolvedValue.present(body);
    }

    private ResolvedValue lookup(Map<String, String> values, String key) {
        if (values == null || isBlank(key) || !values.containsKey(key)) {
            return ResolvedValue.missing();
        }
        return ResolvedValue.present(values.get(key));
    }

    private boolean matchesRegex(String resolved, String regex) {
        if (resolved == null || regex == null) {
            return false;
        }
        try {
            return Pattern.matches(regex, resolved);
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ResolvedValue(boolean present, String value) {
        static ResolvedValue present(String value) {
            return new ResolvedValue(true, value);
        }

        static ResolvedValue missing() {
            return new ResolvedValue(false, null);
        }
    }
}

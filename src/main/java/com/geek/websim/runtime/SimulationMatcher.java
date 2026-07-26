package com.geek.websim.runtime;

import com.geek.websim.web.model.entity.HttpRule;
import com.geek.websim.web.model.entity.SimulationBranch;
import com.geek.websim.web.model.entity.SimulationResponse;
import com.geek.websim.web.model.enums.HttpMatchMode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SimulationMatcher {
    private final SimulationRuleSnapshot snapshot;
    private final BranchMatcher branchMatcher;

    public SimulationMatcher(SimulationRuleSnapshot snapshot) {
        this(snapshot, new BranchMatcher());
    }

    public SimulationMatcher(SimulationRuleSnapshot snapshot, BranchMatcher branchMatcher) {
        this.snapshot = snapshot == null ? SimulationRuleSnapshot.empty() : snapshot;
        this.branchMatcher = branchMatcher == null ? new BranchMatcher() : branchMatcher;
    }

    public Optional<SimulationMatchResult> matchHttp(SimulationRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        for (CompiledSimulationRule rule : snapshot.getHttpRules()) {
            Optional<Map<String, String>> pathVariables = matchHttpRule(rule, request);
            if (pathVariables.isPresent()) {
                SimulationRequest requestForBranchMatching = requestWithPathVariables(request, pathVariables.get());
                return Optional.of(resultFor(rule, requestForBranchMatching, requestForBranchMatching.getPathVariables()));
            }
        }
        return Optional.empty();
    }

    public Optional<SimulationMatchResult> matchTcp(int port, SimulationRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        List<CompiledSimulationRule> rules = snapshot.getTcpRulesByPort().getOrDefault(port, List.of());
        for (CompiledSimulationRule rule : rules) {
            return Optional.of(resultFor(rule, request, Map.of()));
        }
        return Optional.empty();
    }

    private Optional<Map<String, String>> matchHttpRule(CompiledSimulationRule rule, SimulationRequest request) {
        HttpRule http = rule.getConfig().getHttp();
        if (http == null || http.getMatchMode() == null || !methodMatches(http.getMethod(), request.getMethod())) {
            return Optional.empty();
        }
        String expectedPath = http.getPath();
        String actualPath = request.getPath();
        if (expectedPath == null || actualPath == null) {
            return Optional.empty();
        }
        HttpMatchMode mode = http.getMatchMode();
        return switch (mode) {
            case EXACT -> expectedPath.equals(actualPath) ? Optional.of(Map.of()) : Optional.empty();
            case PREFIX -> prefixMatches(expectedPath, actualPath) ? Optional.of(Map.of()) : Optional.empty();
            case TEMPLATE -> rule.getPathTemplate() == null ? Optional.empty() : rule.getPathTemplate().matchVariables(actualPath);
        };
    }

    private boolean prefixMatches(String expectedPath, String actualPath) {
        if ("/".equals(expectedPath)) {
            return actualPath.startsWith("/");
        }
        return actualPath.equals(expectedPath)
                || (actualPath.startsWith(expectedPath)
                && actualPath.length() > expectedPath.length()
                && actualPath.charAt(expectedPath.length()) == '/');
    }

    private boolean methodMatches(String ruleMethod, String requestMethod) {
        if (ruleMethod == null || ruleMethod.isBlank() || "ANY".equalsIgnoreCase(ruleMethod)) {
            return true;
        }
        return requestMethod != null && ruleMethod.equalsIgnoreCase(requestMethod);
    }

    private SimulationMatchResult resultFor(CompiledSimulationRule rule,
                                            SimulationRequest requestForBranchMatching,
                                            Map<String, String> pathVariables) {
        MatchedBranch matchedBranch = firstMatchingConditionalBranch(rule, requestForBranchMatching).orElseGet(() ->
                defaultOrUnconditionalBranch(rule).orElse(null));
        SimulationBranch branch = matchedBranch == null ? null : matchedBranch.branch();
        SimulationResponse response = matchedBranch == null
                ? rule.getConfig().getDefaultResponse()
                : rule.selectResponse(matchedBranch.index());
        return SimulationMatchResult.builder()
                .config(rule.getConfig())
                .branch(branch)
                .response(response)
                .pathVariables(pathVariables == null ? Map.of() : Map.copyOf(pathVariables))
                .build();
    }

    private Optional<MatchedBranch> firstMatchingConditionalBranch(CompiledSimulationRule rule, SimulationRequest request) {
        List<SimulationBranch> branches = rule.getRuntimeBranches();
        for (int index = 0; index < branches.size(); index++) {
            SimulationBranch branch = branches.get(index);
            if (hasConditions(branch) && branchMatcher.matches(branch.getConditions(), request)) {
                return Optional.of(new MatchedBranch(index, branch));
            }
        }
        return Optional.empty();
    }

    private Optional<MatchedBranch> defaultOrUnconditionalBranch(CompiledSimulationRule rule) {
        List<SimulationBranch> branches = rule.getRuntimeBranches();
        List<Integer> unconditionalBranchIndexes = new java.util.ArrayList<>();
        for (int index = 0; index < branches.size(); index++) {
            SimulationBranch branch = branches.get(index);
            if (!hasConditions(branch)) {
                unconditionalBranchIndexes.add(index);
            }
        }
        int selectedIndex = rule.selectDefaultOrUnconditionalBranch(unconditionalBranchIndexes);
        return selectedIndex < 0 ? Optional.empty() : Optional.of(new MatchedBranch(selectedIndex, branches.get(selectedIndex)));
    }

    private boolean hasConditions(SimulationBranch branch) {
        return branch != null && branch.getConditions() != null && !branch.getConditions().isEmpty();
    }

    private SimulationRequest requestWithPathVariables(SimulationRequest request, Map<String, String> pathVariables) {
        if (pathVariables == null || pathVariables.isEmpty()) {
            return request;
        }
        Map<String, String> mergedPathVariables = new LinkedHashMap<>();
        if (request.getPathVariables() != null) {
            mergedPathVariables.putAll(request.getPathVariables());
        }
        mergedPathVariables.putAll(pathVariables);
        return SimulationRequest.builder()
                .protocol(request.getProtocol())
                .method(request.getMethod())
                .path(request.getPath())
                .query(request.getQuery() == null ? Map.of() : request.getQuery())
                .headers(request.getHeaders() == null ? Map.of() : request.getHeaders())
                .pathVariables(Map.copyOf(mergedPathVariables))
                .body(request.getBody())
                .tcpBody(request.getTcpBody())
                .build();
    }

    private record MatchedBranch(int index, SimulationBranch branch) {
    }
}

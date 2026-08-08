package com.geek.websim.runtime;

import com.geek.websim.runtime.http.PathTemplate;
import com.geek.websim.web.model.entity.HttpRule;
import com.geek.websim.web.model.entity.RequestTemplate;
import com.geek.websim.web.model.entity.SimulationBranch;
import com.geek.websim.web.model.entity.SimulationCondition;
import com.geek.websim.web.model.entity.SimulationConfig;
import com.geek.websim.web.model.entity.SimulationResponse;
import com.geek.websim.web.model.entity.TcpRule;
import com.geek.websim.web.model.enums.HttpMatchMode;
import com.geek.websim.web.model.enums.ProtocolType;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class CompiledSimulationRule {
    private final SimulationConfig config;
    private final PathTemplate pathTemplate;
    private final List<SimulationBranch> sortedBranches;

    public CompiledSimulationRule(SimulationConfig config) {
        SimulationConfig frozenConfig = deepCopy(config);
        this.config = Objects.requireNonNull(frozenConfig, "config");
        this.pathTemplate = compileTemplate(frozenConfig);
        this.sortedBranches = sortedBranches(frozenConfig);
    }

    public CompiledSimulationRule(SimulationConfig config, PathTemplate pathTemplate, List<SimulationBranch> sortedBranches) {
        SimulationConfig frozenConfig = deepCopy(config);
        this.config = Objects.requireNonNull(frozenConfig, "config");
        this.pathTemplate = pathTemplate == null ? compileTemplate(frozenConfig) : pathTemplate;
        this.sortedBranches = sortedBranches == null ? sortedBranches(frozenConfig) : freezeBranches(sortedBranches);
    }


    public SimulationConfig getConfig() {
        return deepCopy(config);
    }

    public PathTemplate getPathTemplate() {
        return pathTemplate;
    }

    public List<SimulationBranch> getSortedBranches() {
        return copyBranches(sortedBranches);
    }

    List<SimulationBranch> getRuntimeBranches() {
        return sortedBranches;
    }

    SimulationResponse selectResponse(int branchIndex) {
        if (branchIndex < 0 || branchIndex >= sortedBranches.size()) {
            return null;
        }
        return copyResponse(sortedBranches.get(branchIndex).getResponse());
    }

    int selectDefaultOrUnconditionalBranch(List<Integer> branchIndexes) {
        if (branchIndexes == null || branchIndexes.isEmpty()) {
            return -1;
        }
        return selectByProbability(branchIndexes);
    }

    private static PathTemplate compileTemplate(SimulationConfig config) {
        if (config == null
                || config.getProtocol() != ProtocolType.HTTP
                || config.getHttp() == null
                || config.getHttp().getMatchMode() != HttpMatchMode.TEMPLATE) {
            return null;
        }
        return PathTemplate.compile(config.getHttp().getPath());
    }

    private static List<SimulationBranch> sortedBranches(SimulationConfig config) {
        if (config == null || config.getBranches() == null) {
            return List.of();
        }
        return config.getBranches().stream()
                .filter(CompiledSimulationRule::hasUsableBranchResponse)
                .sorted(Comparator.comparingInt(SimulationBranch::getPriority))
                .toList();
    }

    private static List<SimulationBranch> freezeBranches(List<SimulationBranch> branches) {
        if (branches == null || branches.isEmpty()) {
            return List.of();
        }
        return branches.stream()
                .filter(CompiledSimulationRule::hasUsableBranchResponse)
                .map(CompiledSimulationRule::copyBranch)
                .sorted(Comparator.comparingInt(SimulationBranch::getPriority))
                .toList();
    }

    private static boolean hasUsableBranchResponse(SimulationBranch branch) {
        return branch != null && branch.getResponse() != null;
    }

    private int selectByProbability(List<Integer> branchIndexes) {
        double random = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0;
        for (Integer branchIndex : branchIndexes) {
            SimulationBranch branch = sortedBranches.get(branchIndex);
            cumulative += branchProbability(branch);
            if (random < cumulative) {
                return branchIndex;
            }
        }
        return -1;
    }

    private double branchProbability(SimulationBranch branch) {
        if (branch == null) {
            return 0;
        }
        return Math.max(0, Math.min(1, branch.getPriority() / 100.0));
    }

    private static SimulationConfig deepCopy(SimulationConfig source) {
        if (source == null) {
            return null;
        }
        return SimulationConfig.builder()
                .id(source.getId())
                .name(source.getName())
                .tags(source.getTags() == null ? List.of() : List.copyOf(source.getTags()))
                .protocol(source.getProtocol())
                .enabled(source.isEnabled())
                .http(copyHttp(source.getHttp()))
                .tcp(copyTcp(source.getTcp()))
                .requestTemplate(copyRequestTemplate(source.getRequestTemplate()))
                .branches(copyBranches(source.getBranches()))
                .defaultResponse(copyResponse(source.getDefaultResponse()))
                .build();
    }

    private static HttpRule copyHttp(HttpRule source) {
        if (source == null) {
            return null;
        }
        return HttpRule.builder()
                .method(source.getMethod())
                .path(source.getPath())
                .matchMode(source.getMatchMode())
                .build();
    }

    private static TcpRule copyTcp(TcpRule source) {
        if (source == null) {
            return null;
        }
        return TcpRule.builder()
                .host(source.getHost())
                .port(source.getPort())
                .frameMode(source.getFrameMode())
                .build();
    }

    private static RequestTemplate copyRequestTemplate(RequestTemplate source) {
        if (source == null) {
            return new RequestTemplate(freezeMap(null), freezeMap(null), null);
        }
        return RequestTemplate.builder()
                .headers(freezeMap(source.getHeaders()))
                .query(freezeMap(source.getQuery()))
                .body(source.getBody())
                .build();
    }

    private static List<SimulationBranch> copyBranches(List<SimulationBranch> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .filter(CompiledSimulationRule::hasUsableBranchResponse)
                .map(CompiledSimulationRule::copyBranch)
                .toList();
    }

    private static SimulationBranch copyBranch(SimulationBranch source) {
        return SimulationBranch.builder()
                .name(source.getName())
                .priority(source.getPriority())
                .conditions(copyConditions(source.getConditions()))
                .response(copyResponse(source.getResponse()))
                .build();
    }

    private static List<SimulationCondition> copyConditions(List<SimulationCondition> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .filter(Objects::nonNull)
                .map(condition -> SimulationCondition.builder()
                        .source(condition.getSource())
                        .key(condition.getKey())
                        .operator(condition.getOperator())
                        .value(condition.getValue())
                        .build())
                .toList();
    }

    private static SimulationResponse copyResponse(SimulationResponse source) {
        if (source == null) {
            return null;
        }
        return SimulationResponse.builder()
                .status(source.getStatus())
                .headers(freezeMap(source.getHeaders()))
                .body(source.getBody())
                .delayMs(source.getDelayMs())
                .build();
    }

    private static Map<String, String> freezeMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}

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
import com.geek.websim.web.model.enums.ResponseVariantStrategy;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class CompiledSimulationRule {
    private final SimulationConfig config;
    private final PathTemplate pathTemplate;
    private final List<SimulationBranch> sortedBranches;
    private final List<AtomicLong> branchResponseSequences;
    private final AtomicLong defaultBranchSequence = new AtomicLong();

    public CompiledSimulationRule(SimulationConfig config) {
        SimulationConfig frozenConfig = deepCopy(config);
        this.config = Objects.requireNonNull(frozenConfig, "config");
        this.pathTemplate = compileTemplate(frozenConfig);
        this.sortedBranches = sortedBranches(frozenConfig);
        this.branchResponseSequences = responseSequences(this.sortedBranches.size());
    }

    public CompiledSimulationRule(SimulationConfig config, PathTemplate pathTemplate, List<SimulationBranch> sortedBranches) {
        SimulationConfig frozenConfig = deepCopy(config);
        this.config = Objects.requireNonNull(frozenConfig, "config");
        this.pathTemplate = pathTemplate == null ? compileTemplate(frozenConfig) : pathTemplate;
        this.sortedBranches = sortedBranches == null ? sortedBranches(frozenConfig) : freezeBranches(sortedBranches);
        this.branchResponseSequences = responseSequences(this.sortedBranches.size());
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
        SimulationBranch branch = sortedBranches.get(branchIndex);
        List<SimulationResponse> responses = responseCycle(branch);
        if (responses.isEmpty()) {
            return null;
        }
        if (responses.size() == 1) {
            return copyResponse(responses.get(0));
        }
        int selectedIndex = selectedResponseIndex(branch, branchIndex, responses.size());
        return copyResponse(responses.get(selectedIndex));
    }

    int selectDefaultOrUnconditionalBranch(List<Integer> branchIndexes) {
        if (branchIndexes == null || branchIndexes.isEmpty()) {
            return -1;
        }
        if (branchIndexes.stream().anyMatch(index -> probability(sortedBranches.get(index)) != null)) {
            return selectByProbability(branchIndexes);
        }
        long sequence = defaultBranchSequence.getAndIncrement();
        int selectedSlot = Math.floorMod(sequence, branchIndexes.size() + 1);
        return selectedSlot == 0 ? -1 : branchIndexes.get(selectedSlot - 1);
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

    private static List<AtomicLong> responseSequences(int size) {
        if (size <= 0) {
            return List.of();
        }
        return java.util.stream.IntStream.range(0, size)
                .mapToObj(ignored -> new AtomicLong())
                .toList();
    }

    private int selectedResponseIndex(SimulationBranch branch, int branchIndex, int responseCount) {
        ResponseVariantStrategy strategy = branch.getVariantStrategy() == null
                ? ResponseVariantStrategy.ROUND_ROBIN
                : branch.getVariantStrategy();
        if (strategy == ResponseVariantStrategy.RANDOM) {
            return ThreadLocalRandom.current().nextInt(responseCount);
        }
        long sequence = branchResponseSequences.get(branchIndex).getAndIncrement();
        return Math.floorMod(sequence, responseCount);
    }

    private int selectByProbability(List<Integer> branchIndexes) {
        double random = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0;
        for (Integer branchIndex : branchIndexes) {
            SimulationBranch branch = sortedBranches.get(branchIndex);
            cumulative += probability(branch) == null ? 0 : probability(branch);
            if (random < cumulative) {
                return branchIndex;
            }
        }
        return -1;
    }

    private Double probability(SimulationBranch branch) {
        if (branch == null || branch.getProbability() == null) {
            return null;
        }
        return Math.max(0, Math.min(1, branch.getProbability()));
    }

    private static List<SimulationResponse> responseCycle(SimulationBranch branch) {
        if (branch == null || branch.getResponse() == null) {
            return List.of();
        }
        List<SimulationResponse> responses = new java.util.ArrayList<>();
        responses.add(branch.getResponse());
        if (branch.getResponseVariants() != null) {
            branch.getResponseVariants().stream()
                    .filter(Objects::nonNull)
                    .forEach(responses::add);
        }
        return responses;
    }

    private static SimulationConfig deepCopy(SimulationConfig source) {
        if (source == null) {
            return null;
        }
        return SimulationConfig.builder()
                .id(source.getId())
                .name(source.getName())
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
                .responseVariants(copyResponses(source.getResponseVariants()))
                .variantStrategy(source.getVariantStrategy())
                .probability(source.getProbability())
                .build();
    }

    private static List<SimulationResponse> copyResponses(List<SimulationResponse> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .filter(Objects::nonNull)
                .map(CompiledSimulationRule::copyResponse)
                .toList();
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

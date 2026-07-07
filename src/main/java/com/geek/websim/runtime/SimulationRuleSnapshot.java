package com.geek.websim.runtime;

import com.geek.websim.web.model.entity.HttpRule;
import com.geek.websim.web.model.entity.SimulationConfig;
import com.geek.websim.web.model.entity.TcpRule;
import com.geek.websim.web.model.enums.ProtocolType;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
public class SimulationRuleSnapshot {
    private final List<CompiledSimulationRule> httpRules;
    private final Map<Integer, List<CompiledSimulationRule>> tcpRulesByPort;

    public SimulationRuleSnapshot(List<CompiledSimulationRule> httpRules,
                                  Map<Integer, List<CompiledSimulationRule>> tcpRulesByPort) {
        this.httpRules = List.copyOf(httpRules == null ? List.of() : httpRules);
        this.tcpRulesByPort = copyTcpRules(tcpRulesByPort);
    }

    public static SimulationRuleSnapshot empty() {
        return new SimulationRuleSnapshot(List.of(), Map.of());
    }

    public static SimulationRuleSnapshot from(List<SimulationConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return empty();
        }

        List<CompiledSimulationRule> httpRules = new ArrayList<>();
        Map<Integer, List<CompiledSimulationRule>> tcpRulesByPort = new LinkedHashMap<>();

        for (SimulationConfig config : configs) {
            if (config == null || !config.isEnabled() || config.getProtocol() == null || config.getDefaultResponse() == null) {
                continue;
            }
            if (config.getProtocol() == ProtocolType.HTTP) {
                compileHttp(config).ifPresent(httpRules::add);
            } else if (config.getProtocol() == ProtocolType.TCP) {
                compileTcp(config).ifPresent(rule -> tcpRulesByPort
                        .computeIfAbsent(config.getTcp().getPort(), ignored -> new ArrayList<>())
                        .add(rule));
            }
        }

        return new SimulationRuleSnapshot(httpRules, tcpRulesByPort);
    }

    private static java.util.Optional<CompiledSimulationRule> compileHttp(SimulationConfig config) {
        HttpRule http = config.getHttp();
        if (http == null || isBlank(http.getPath()) || !http.getPath().startsWith("/") || http.getMatchMode() == null) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(new CompiledSimulationRule(config));
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static java.util.Optional<CompiledSimulationRule> compileTcp(SimulationConfig config) {
        TcpRule tcp = config.getTcp();
        if (tcp == null || tcp.getPort() == null || tcp.getPort() < 1 || tcp.getPort() > 65_535) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new CompiledSimulationRule(config));
    }

    private static Map<Integer, List<CompiledSimulationRule>> copyTcpRules(Map<Integer, List<CompiledSimulationRule>> rulesByPort) {
        if (rulesByPort == null || rulesByPort.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<CompiledSimulationRule>> copied = new LinkedHashMap<>();
        rulesByPort.forEach((port, rules) -> copied.put(port, List.copyOf(rules == null ? List.of() : rules)));
        return Collections.unmodifiableMap(copied);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

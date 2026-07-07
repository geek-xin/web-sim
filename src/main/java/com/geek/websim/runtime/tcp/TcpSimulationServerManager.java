package com.geek.websim.runtime.tcp;

import com.geek.websim.runtime.CompiledSimulationRule;
import com.geek.websim.runtime.ResponseRenderer;
import com.geek.websim.runtime.SimulationMatchResult;
import com.geek.websim.runtime.SimulationMatcher;
import com.geek.websim.runtime.SimulationRequest;
import com.geek.websim.runtime.SimulationRuleSnapshot;
import com.geek.websim.web.model.dto.SimulationLogEntry;
import com.geek.websim.web.model.entity.SimulationConfig;
import com.geek.websim.web.model.entity.SimulationResponse;
import com.geek.websim.web.model.entity.TcpRule;
import com.geek.websim.web.model.enums.ProtocolType;
import com.geek.websim.web.service.SimulationMetricsService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpServer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
public class TcpSimulationServerManager {
    private static final String DEFAULT_HOST = "127.0.0.1";
    static final String FRAME_TOO_LARGE_RESPONSE = "ERR frame too large\n";
    private static final String NO_MATCH_RESPONSE = "ERR no simulation\n";
    private static final int MAX_FRAME_CHARS = 8 * 1024;
    private static final int SUMMARY_LIMIT = 512;
    private static final Duration SERVER_LIFECYCLE_TIMEOUT = Duration.ofSeconds(2);

    private final Map<Integer, DisposableServer> servers = new ConcurrentHashMap<>();
    private final Map<Integer, String> serverHosts = new ConcurrentHashMap<>();
    private final SimulationMetricsService metricsService;
    private final ResponseRenderer responseRenderer;
    private volatile SimulationRuleSnapshot currentSnapshot = SimulationRuleSnapshot.empty();

    public TcpSimulationServerManager() {
        this(null, new ResponseRenderer());
    }

    @Autowired
    public TcpSimulationServerManager(SimulationMetricsService metricsService) {
        this(metricsService, new ResponseRenderer());
    }

    TcpSimulationServerManager(ResponseRenderer responseRenderer) {
        this(null, responseRenderer);
    }

    TcpSimulationServerManager(SimulationMetricsService metricsService, ResponseRenderer responseRenderer) {
        this.metricsService = metricsService;
        this.responseRenderer = responseRenderer == null ? new ResponseRenderer() : responseRenderer;
    }

    /**
     * Synchronously reconciles TCP listeners with the supplied immutable snapshot.
     * New listeners are bound first, then removed listeners are stopped, and only
     * after both phases complete successfully is the snapshot published to TCP
     * handlers. This preserves the last coherent TCP runtime if either binding a
     * requested port or stopping a removed port fails.
     */
    public synchronized void refreshServers(SimulationRuleSnapshot snapshot) {
        SimulationRuleSnapshot nextSnapshot = snapshot == null ? SimulationRuleSnapshot.empty() : snapshot;
        Map<Integer, List<CompiledSimulationRule>> tcpRulesByPort = nextSnapshot.getTcpRulesByPort();
        Map<Integer, String> desiredHosts = desiredHosts(tcpRulesByPort);
        Set<Integer> desiredPorts = desiredHosts.keySet();
        List<Integer> portsToRemove = servers.keySet().stream()
                .filter(port -> !desiredPorts.contains(port))
                .toList();
        Map<Integer, DisposableServer> newlyStarted = new LinkedHashMap<>();

        try {
            rejectUnsupportedHostChanges(desiredHosts);
            for (Map.Entry<Integer, String> desired : desiredHosts.entrySet()) {
                Integer port = desired.getKey();
                if (!servers.containsKey(port)) {
                    DisposableServer server = bindServer(desired.getValue(), port);
                    servers.put(port, server);
                    serverHosts.put(port, desired.getValue());
                    newlyStarted.put(port, server);
                }
            }
        } catch (RuntimeException e) {
            cleanupNewServers(newlyStarted.keySet());
            throw e;
        }

        try {
            portsToRemove.forEach(this::stopServer);
        } catch (RuntimeException e) {
            cleanupNewServersAfterStopFailure(newlyStarted.keySet(), e);
            throw e;
        }

        currentSnapshot = nextSnapshot;
    }

    public synchronized void stopAll() {
        servers.keySet().stream().toList().forEach(this::stopServer);
    }

    @PreDestroy
    void preDestroy() {
        stopAll();
    }

    boolean hasServer(int port) {
        return servers.containsKey(port);
    }

    private Map<Integer, String> desiredHosts(Map<Integer, List<CompiledSimulationRule>> tcpRulesByPort) {
        Map<Integer, String> desiredHosts = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<CompiledSimulationRule>> entry : tcpRulesByPort.entrySet()) {
            Integer port = entry.getKey();
            if (port != null) {
                desiredHosts.put(port, hostFor(entry.getValue()));
            }
        }
        return desiredHosts;
    }

    private void rejectUnsupportedHostChanges(Map<Integer, String> desiredHosts) {
        for (Map.Entry<Integer, String> desired : desiredHosts.entrySet()) {
            Integer port = desired.getKey();
            String currentHost = serverHosts.get(port);
            if (servers.containsKey(port) && currentHost != null && !currentHost.equals(desired.getValue())) {
                throw new IllegalStateException("Cannot change TCP host for active port " + port
                        + " without stopping the existing listener");
            }
        }
    }

    private DisposableServer bindServer(String host, int port) {
        return TcpServer.create()
                .host(host)
                .port(port)
                .handle((inbound, outbound) -> outbound
                        .sendString(lineFrames(inbound.receive().asString(StandardCharsets.UTF_8))
                                .map(frame -> responseFor(port, frame)))
                        .then())
                .bindNow(SERVER_LIFECYCLE_TIMEOUT);
    }

    private void cleanupNewServers(Set<Integer> ports) {
        for (Integer port : new HashSet<>(ports)) {
            stopServer(port);
        }
    }

    private void cleanupNewServersAfterStopFailure(Set<Integer> ports, RuntimeException original) {
        for (Integer port : new HashSet<>(ports)) {
            try {
                stopServer(port);
            } catch (RuntimeException cleanupFailure) {
                original.addSuppressed(cleanupFailure);
            }
        }
    }

    private void stopServer(Integer port) {
        DisposableServer server = servers.get(port);
        if (server != null) {
            server.disposeNow(SERVER_LIFECYCLE_TIMEOUT);
        }
        servers.remove(port);
        serverHosts.remove(port);
    }

    private Flux<String> lineFrames(Flux<String> chunks) {
        return chunks.concatMapIterable(new Function<String, Iterable<String>>() {
            private final LineFrameParser parser = new LineFrameParser(MAX_FRAME_CHARS);

            @Override
            public Iterable<String> apply(String chunk) {
                return parser.accept(chunk);
            }
        });
    }

    private String responseFor(int port, String frame) {
        long startNanos = System.nanoTime();
        if (FRAME_TOO_LARGE_RESPONSE.equals(frame)) {
            recordTcp("unmatched", "unmatched", 400, startNanos,
                    () -> tcpRequestSummary(port, frame), () -> summary(FRAME_TOO_LARGE_RESPONSE));
            return FRAME_TOO_LARGE_RESPONSE;
        }
        SimulationRuleSnapshot snapshot = currentSnapshot;
        SimulationRequest request = SimulationRequest.builder()
                .protocol(ProtocolType.TCP)
                .tcpBody(frame)
                .build();
        try {
            Optional<SimulationMatchResult> result = new SimulationMatcher(snapshot).matchTcp(port, request);
            if (result.isEmpty()) {
                recordTcp("unmatched", "unmatched", 404, startNanos,
                        () -> tcpRequestSummary(port, frame), () -> summary(NO_MATCH_RESPONSE));
                return NO_MATCH_RESPONSE;
            }

            SimulationMatchResult match = result.get();
            String response = responseRenderer.renderBody(match.getResponse(), request);
            SimulationConfig config = match.getConfig();
            recordTcp(config == null ? "unknown" : config.getId(),
                    config == null ? "unknown" : config.getName(),
                    status(match.getResponse()),
                    startNanos,
                    () -> tcpRequestSummary(port, frame),
                    () -> summary(response));
            return response;
        } catch (RuntimeException e) {
            recordTcp("unmatched", "unmatched", 500, startNanos,
                    () -> tcpRequestSummary(port, frame), () -> summary(e.getMessage()));
            throw e;
        }
    }

    private String hostFor(List<CompiledSimulationRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return DEFAULT_HOST;
        }
        TcpRule tcp = rules.get(0).getConfig().getTcp();
        if (tcp == null || tcp.getHost() == null || tcp.getHost().isBlank()) {
            return DEFAULT_HOST;
        }
        return tcp.getHost().trim();
    }

    private int status(SimulationResponse response) {
        Integer status = response == null ? null : response.getStatus();
        return status == null ? 200 : status;
    }

    private void recordTcp(String simulationId,
                           String simulationName,
                           int status,
                           long startNanos,
                           Supplier<String> requestSummarySupplier,
                           Supplier<String> responseSummarySupplier) {
        if (metricsService == null) {
            return;
        }
        long durationMs = elapsedMillis(startNanos);
        metricsService.record(ProtocolType.TCP, status, durationMs, () -> SimulationLogEntry.builder()
                .id(UUID.randomUUID().toString())
                .simulationId(blankToDefault(simulationId, "unknown"))
                .simulationName(blankToDefault(simulationName, "unknown"))
                .protocol(ProtocolType.TCP)
                .status(status)
                .durationMs(durationMs)
                .requestSummary(safeGet(requestSummarySupplier))
                .responseSummary(safeGet(responseSummarySupplier))
                .timestamp(Instant.now())
                .build());
    }

    private String tcpRequestSummary(int port, String frame) {
        StringBuilder summary = new StringBuilder(SUMMARY_LIMIT);
        appendBounded(summary, "TCP port=");
        appendBounded(summary, String.valueOf(port));
        appendBounded(summary, " body=");
        appendBounded(summary, frame);
        return summary.toString();
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
    }

    private String safeGet(Supplier<String> supplier) {
        return supplier == null ? "" : supplier.get();
    }

    private String summary(String value) {
        StringBuilder summary = new StringBuilder(SUMMARY_LIMIT);
        appendBounded(summary, value);
        return summary.toString();
    }

    private void appendBounded(StringBuilder target, String value) {
        if (value == null) {
            return;
        }
        if (target.length() >= SUMMARY_LIMIT) {
            return;
        }
        int remaining = SUMMARY_LIMIT - target.length();
        int charsToCopy = Math.min(value.length(), remaining);
        for (int i = 0; i < charsToCopy; i++) {
            char ch = value.charAt(i);
            target.append(ch == '\n' || ch == '\r' || ch == '\t' ? ' ' : ch);
        }
        if (charsToCopy < value.length() && target.length() == SUMMARY_LIMIT && SUMMARY_LIMIT > 0) {
            target.setCharAt(SUMMARY_LIMIT - 1, '…');
        }
    }

    static final class LineFrameParser {
        private final int maxFrameChars;
        private final StringBuilder buffer = new StringBuilder();
        private boolean discardingOversizedFrame;

        LineFrameParser(int maxFrameChars) {
            if (maxFrameChars < 1) {
                throw new IllegalArgumentException("maxFrameChars must be positive");
            }
            this.maxFrameChars = maxFrameChars;
        }

        List<String> accept(String chunk) {
            List<String> frames = new ArrayList<>();
            if (chunk == null || chunk.isEmpty()) {
                return frames;
            }

            int start = 0;
            while (start < chunk.length()) {
                int newline = chunk.indexOf('\n', start);
                int end = newline >= 0 ? newline : chunk.length();
                String segment = chunk.substring(start, end);
                if (discardingOversizedFrame) {
                    if (newline >= 0) {
                        discardingOversizedFrame = false;
                    }
                } else if (buffer.length() + segment.length() > maxFrameChars) {
                    buffer.setLength(0);
                    frames.add(FRAME_TOO_LARGE_RESPONSE);
                    discardingOversizedFrame = newline < 0;
                } else {
                    buffer.append(segment);
                    if (newline >= 0) {
                        frames.add(trimCrlf(buffer.toString()));
                        buffer.setLength(0);
                    }
                }

                if (newline < 0) {
                    break;
                }
                start = newline + 1;
            }
            return frames;
        }

        private String trimCrlf(String frame) {
            if (frame.endsWith("\r")) {
                return frame.substring(0, frame.length() - 1);
            }
            return frame;
        }
    }
}

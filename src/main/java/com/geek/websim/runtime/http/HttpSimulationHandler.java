package com.geek.websim.runtime.http;

import com.geek.websim.common.constants.CommonConstants;
import com.geek.websim.config.SimulationProperties;
import com.geek.websim.runtime.ResponseRenderer;
import com.geek.websim.runtime.SimulationMatchResult;
import com.geek.websim.runtime.SimulationMatcher;
import com.geek.websim.runtime.SimulationRequest;
import com.geek.websim.web.model.dto.SimulationLogEntry;
import com.geek.websim.web.model.entity.SimulationConfig;
import com.geek.websim.web.model.entity.SimulationResponse;
import com.geek.websim.web.model.enums.ProtocolType;
import com.geek.websim.web.service.SimulationMetricsService;
import com.geek.websim.web.service.SimulationRuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Component
public class HttpSimulationHandler {
    private static final String NO_MATCH_BODY = "{\"error\":\"未匹配模拟规则\"}";
    private static final String BODY_TOO_LARGE_BODY = "{\"error\":\"请求体过大\"}";
    private static final int SUMMARY_LIMIT = 512;
    private static final Pattern RESPONSE_HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

    private final SimulationRuntimeService runtimeService;
    private final SimulationProperties properties;
    private final SimulationMetricsService metricsService;
    private final ResponseRenderer responseRenderer;

    public HttpSimulationHandler(SimulationRuntimeService runtimeService, SimulationProperties properties) {
        this(runtimeService, properties, null, new ResponseRenderer());
    }

    @Autowired
    public HttpSimulationHandler(SimulationRuntimeService runtimeService,
                                 SimulationProperties properties,
                                 SimulationMetricsService metricsService) {
        this(runtimeService, properties, metricsService, new ResponseRenderer());
    }

    public HttpSimulationHandler(SimulationRuntimeService runtimeService,
                                 SimulationProperties properties,
                                 ResponseRenderer responseRenderer) {
        this(runtimeService, properties, null, responseRenderer);
    }

    private HttpSimulationHandler(SimulationRuntimeService runtimeService,
                                  SimulationProperties properties,
                                  SimulationMetricsService metricsService,
                                  ResponseRenderer responseRenderer) {
        this.runtimeService = runtimeService;
        this.properties = properties;
        this.metricsService = metricsService;
        this.responseRenderer = responseRenderer == null ? new ResponseRenderer() : responseRenderer;
    }

    public Mono<Void> handle(ServerWebExchange exchange) {
        long startNanos = System.nanoTime();
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (isReservedPath(path)) {
            recordHttp("unmatched", "unmatched", HttpStatus.NOT_FOUND.value(), startNanos,
                    () -> httpRequestSummary(exchange, path, null), () -> summary(NO_MATCH_BODY));
            return writeJson(exchange, HttpStatus.NOT_FOUND, NO_MATCH_BODY);
        }

        return DataBufferUtils.join(exchange.getRequest().getBody(), properties.maxBodyBytes())
                .map(this::readBody)
                .defaultIfEmpty("")
                .flatMap(body -> simulate(exchange, path, body, startNanos))
                .onErrorResume(DataBufferLimitException.class,
                        ignored -> {
                            recordHttp("unmatched", "unmatched", HttpStatus.PAYLOAD_TOO_LARGE.value(), startNanos,
                                    () -> httpRequestSummary(exchange, path, null), () -> summary(BODY_TOO_LARGE_BODY));
                            return writeJson(exchange, HttpStatus.PAYLOAD_TOO_LARGE, BODY_TOO_LARGE_BODY);
                        });
    }

    private Mono<Void> simulate(ServerWebExchange exchange, String path, String body, long startNanos) {
        SimulationRequest request = SimulationRequest.builder()
                .protocol(ProtocolType.HTTP)
                .method(exchange.getRequest().getMethod().name())
                .path(path)
                .query(firstValues(exchange.getRequest().getQueryParams()))
                .headers(firstValues(exchange.getRequest().getHeaders()))
                .body(body)
                .build();

        return new SimulationMatcher(runtimeService.current()).matchHttp(request)
                .map(match -> writeMatchedResponse(exchange, request, match, startNanos))
                .orElseGet(() -> {
                    recordHttp("unmatched", "unmatched", HttpStatus.NOT_FOUND.value(), startNanos,
                            () -> httpRequestSummary(exchange, path, body), () -> summary(NO_MATCH_BODY));
                    return writeJson(exchange, HttpStatus.NOT_FOUND, NO_MATCH_BODY);
                });
    }

    private Mono<Void> writeMatchedResponse(ServerWebExchange exchange,
                                            SimulationRequest request,
                                            SimulationMatchResult match,
                                            long startNanos) {
        SimulationResponse response = match.getResponse();
        request.setPathVariables(match.getPathVariables());
        Mono<Void> writer = Mono.defer(() -> {
            Map<String, String> renderedHeaders = responseRenderer.renderHeaders(response, request);
            renderedHeaders.forEach((name, value) -> addResponseHeader(exchange, name, value));
            HttpStatusCode status = statusCode(response);
            exchange.getResponse().setStatusCode(status);
            String renderedBody = responseRenderer.renderBody(response, request);
            SimulationConfig config = match.getConfig();
            recordHttp(config == null ? "unknown" : config.getId(),
                    config == null ? "unknown" : config.getName(),
                    status.value(),
                    startNanos,
                    () -> requestSummary(request),
                    () -> summary(renderedBody));
            return writeBody(exchange, renderedBody);
        });

        Long delayMs = response == null ? null : response.getDelayMs();
        if (delayMs != null && delayMs > 0) {
            return Mono.delay(Duration.ofMillis(delayMs)).then(writer);
        }
        return writer;
    }

    private String readBody(DataBuffer dataBuffer) {
        try {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            DataBufferUtils.release(dataBuffer);
        }
    }

    private Map<String, String> firstValues(Map<String, ? extends Iterable<String>> values) {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((name, allValues) -> {
            if (allValues == null) {
                return;
            }
            for (String value : allValues) {
                result.put(name, value);
                return;
            }
        });
        return result;
    }

    private HttpStatusCode statusCode(SimulationResponse response) {
        Integer status = response == null ? null : response.getStatus();
        return HttpStatusCode.valueOf(status == null ? 200 : status);
    }

    private boolean isReservedPath(String path) {
        if (path == null) {
            return false;
        }
        return CommonConstants.RESERVED_PATH_PREFIXES.stream().anyMatch(prefix -> isReservedPath(path, prefix));
    }

    private boolean isReservedPath(String path, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return false;
        }
        if ("/favicon.ico".equals(prefix)) {
            return path.equals(prefix);
        }
        if (!prefix.startsWith("/")) {
            return path.equals(prefix);
        }
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private void addResponseHeader(ServerWebExchange exchange, String name, String value) {
        if (!isSafeResponseHeaderName(name) || !isSafeResponseHeaderValue(value)) {
            return;
        }
        exchange.getResponse().getHeaders().add(name, value == null ? "" : value);
    }

    private boolean isSafeResponseHeaderName(String name) {
        return name != null
                && !name.isBlank()
                && RESPONSE_HEADER_NAME.matcher(name).matches()
                && !isGeneratedOrHopByHopHeader(name);
    }

    private boolean isSafeResponseHeaderValue(String value) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 0x20 || ch == 0x7f) {
                return false;
            }
        }
        return true;
    }

    private boolean isGeneratedOrHopByHopHeader(String name) {
        if (name == null) {
            return false;
        }
        String trimmedName = name.trim();
        return HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(trimmedName)
                || HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(trimmedName);
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatusCode status, String json) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return writeBody(exchange, json);
    }

    private Mono<Void> writeBody(ServerWebExchange exchange, String body) {
        byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.setContentLength(bytes.length);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private void recordHttp(String simulationId,
                            String simulationName,
                            int status,
                            long startNanos,
                            Supplier<String> requestSummarySupplier,
                            Supplier<String> responseSummarySupplier) {
        if (metricsService == null) {
            return;
        }
        long durationMs = elapsedMillis(startNanos);
        metricsService.record(blankToDefault(simulationId, "unknown"), ProtocolType.HTTP, status, durationMs, () -> SimulationLogEntry.builder()
                .id(UUID.randomUUID().toString())
                .simulationId(blankToDefault(simulationId, "unknown"))
                .simulationName(blankToDefault(simulationName, "unknown"))
                .protocol(ProtocolType.HTTP)
                .status(status)
                .durationMs(durationMs)
                .requestSummary(safeGet(requestSummarySupplier))
                .responseSummary(safeGet(responseSummarySupplier))
                .timestamp(Instant.now())
                .build());
    }

    private String requestSummary(SimulationRequest request) {
        if (request == null) {
            return "";
        }
        StringBuilder summary = new StringBuilder(SUMMARY_LIMIT);
        appendBounded(summary, request.getMethod());
        appendBounded(summary, " ");
        appendBounded(summary, request.getPath());
        if (request.getBody() != null && !request.getBody().isEmpty()) {
            appendBounded(summary, " body=");
            appendBounded(summary, request.getBody());
        }
        return summary.toString();
    }

    private String httpRequestSummary(ServerWebExchange exchange, String path, String body) {
        String method = exchange.getRequest().getMethod() == null ? "HTTP" : exchange.getRequest().getMethod().name();
        StringBuilder summary = new StringBuilder(SUMMARY_LIMIT);
        appendBounded(summary, method);
        appendBounded(summary, " ");
        appendBounded(summary, path);
        if (body != null && !body.isEmpty()) {
            appendBounded(summary, " body=");
            appendBounded(summary, body);
        }
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
}

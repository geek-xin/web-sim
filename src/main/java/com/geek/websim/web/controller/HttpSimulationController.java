package com.geek.websim.web.controller;

import com.geek.websim.common.constants.CommonConstants;
import com.geek.websim.runtime.http.HttpSimulationHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class HttpSimulationController implements WebFilter {
    private final HttpSimulationHandler handler;

    public HttpSimulationController(HttpSimulationHandler handler) {
        this.handler = handler;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (isReservedPath(path)) {
            return chain.filter(exchange);
        }
        return handler.handle(exchange);
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
}

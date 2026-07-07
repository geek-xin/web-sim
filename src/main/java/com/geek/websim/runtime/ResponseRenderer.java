package com.geek.websim.runtime;

import com.geek.websim.web.model.entity.SimulationResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResponseRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^}]+)}}");

    private final RandomValueProvider randomValueProvider;

    public ResponseRenderer() {
        this(new RandomValueProvider());
    }

    public ResponseRenderer(RandomValueProvider randomValueProvider) {
        this.randomValueProvider = randomValueProvider;
    }

    public String renderBody(SimulationResponse response, SimulationRequest request) {
        if (response == null || response.getBody() == null) {
            return "";
        }
        return render(response.getBody(), request);
    }

    public Map<String, String> renderHeaders(SimulationResponse response, SimulationRequest request) {
        if (response == null || response.getHeaders() == null) {
            return Map.of();
        }
        Map<String, String> rendered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
            rendered.put(entry.getKey(), render(entry.getValue(), request));
        }
        return rendered;
    }

    private String render(String template, SimulationRequest request) {
        if (template == null) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(resolve(matcher.group(1).trim(), request)));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String resolve(String expression, SimulationRequest request) {
        if (expression.startsWith("random.")) {
            return randomValueProvider.resolve(expression);
        }
        if (request == null) {
            return "";
        }
        if (expression.startsWith("request.header.")) {
            return value(request.getHeaders(), expression.substring("request.header.".length()));
        }
        if (expression.startsWith("query.")) {
            return value(request.getQuery(), expression.substring("query.".length()));
        }
        if (expression.startsWith("path.")) {
            return value(request.getPathVariables(), expression.substring("path.".length()));
        }
        if (expression.equals("tcp.body")) {
            return request.getTcpBody() == null ? "" : request.getTcpBody();
        }
        return "";
    }

    private String value(Map<String, String> values, String key) {
        if (values == null || key == null) {
            return "";
        }
        return values.getOrDefault(key, "");
    }
}

package com.geek.websim.runtime.http;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PathTemplate {
    private final List<Segment> segments;

    private PathTemplate(List<Segment> segments) {
        this.segments = segments;
    }

    public static PathTemplate compile(String pattern) {
        if (pattern == null || pattern.isBlank() || !pattern.startsWith("/")) {
            throw new IllegalArgumentException("Path template must be non-blank and start with '/'");
        }

        String[] parts = split(pattern);
        List<Segment> segments = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.startsWith("{") && part.endsWith("}") && part.length() > 2) {
                segments.add(Segment.variable(part.substring(1, part.length() - 1)));
            } else {
                segments.add(Segment.literal(part));
            }
        }
        return new PathTemplate(List.copyOf(segments));
    }

    public Map<String, String> match(String path) {
        return matchVariables(path).orElse(Map.of());
    }

    public Optional<Map<String, String>> matchVariables(String path) {
        if (path == null || !path.startsWith("/")) {
            return Optional.empty();
        }

        String[] parts = split(path);
        if (parts.length != segments.size()) {
            return Optional.empty();
        }

        Map<String, String> variables = new LinkedHashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            String value = parts[i];
            if (segment.variable()) {
                variables.put(segment.value(), value);
            } else if (!segment.value().equals(value)) {
                return Optional.empty();
            }
        }
        return Optional.of(variables);
    }

    private static String[] split(String path) {
        if (path.equals("/")) {
            return new String[0];
        }
        return path.substring(1).split("/", -1);
    }

    private record Segment(boolean variable, String value) {
        static Segment variable(String name) {
            return new Segment(true, name);
        }

        static Segment literal(String value) {
            return new Segment(false, value);
        }
    }
}

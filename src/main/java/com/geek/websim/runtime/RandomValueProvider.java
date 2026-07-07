package com.geek.websim.runtime;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RandomValueProvider {
    private static final List<String> NAMES = List.of("张三", "李四", "Alice", "Bob");

    public String resolve(String expression) {
        if (expression == null) {
            return "";
        }
        if (expression.equals("random.uuid")) {
            return UUID.randomUUID().toString();
        }
        if (expression.startsWith("random.int:")) {
            return randomInt(expression.substring("random.int:".length()));
        }
        if (expression.startsWith("random.float:")) {
            return randomFloat(expression.substring("random.float:".length()));
        }
        if (expression.equals("random.bool")) {
            return Boolean.toString(ThreadLocalRandom.current().nextBoolean());
        }
        if (expression.equals("random.timestamp")) {
            return Instant.now().toString();
        }
        if (expression.startsWith("random.pick:")) {
            return randomPick(expression.substring("random.pick:".length()));
        }
        if (expression.equals("random.name")) {
            return NAMES.get(ThreadLocalRandom.current().nextInt(NAMES.size()));
        }
        return "";
    }

    private String randomInt(String bounds) {
        try {
            String[] parts = bounds.split(",", 2);
            long min = Long.parseLong(parts[0].trim());
            long max = Long.parseLong(parts[1].trim());
            if (max < min) {
                return "";
            }
            if (min == max) {
                return Long.toString(min);
            }
            return Long.toString(ThreadLocalRandom.current().nextLong(min, max + 1));
        } catch (Exception ignored) {
            return "";
        }
    }

    private String randomFloat(String bounds) {
        try {
            String[] parts = bounds.split(",", 2);
            double min = Double.parseDouble(parts[0].trim());
            double max = Double.parseDouble(parts[1].trim());
            if (max < min) {
                return "";
            }
            return Double.toString(ThreadLocalRandom.current().nextDouble(min, max));
        } catch (Exception ignored) {
            return "";
        }
    }

    private String randomPick(String values) {
        String[] options = values.split(",");
        if (options.length == 0) {
            return "";
        }
        return options[ThreadLocalRandom.current().nextInt(options.length)].trim();
    }
}

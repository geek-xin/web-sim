package com.geek.websim.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "web-sim")
public record SimulationProperties(
        String configDir,
        int maxBodyBytes,
        double logSampleRate,
        int recentLogSize,
        String logDir,
        int logRetentionDays,
        boolean hotReloadEnabled,
        long hotReloadDebounceMs,
        Tcp tcp
) {
    public SimulationProperties(String configDir,
                                int maxBodyBytes,
                                double logSampleRate,
                                int recentLogSize,
                                boolean hotReloadEnabled,
                                long hotReloadDebounceMs,
                                Tcp tcp) {
        this(configDir, maxBodyBytes, logSampleRate, recentLogSize,
                "logs/simulations", 7, hotReloadEnabled, hotReloadDebounceMs, tcp);
    }

    @ConstructorBinding
    public SimulationProperties {
        if (configDir == null || configDir.isBlank()) {
            configDir = "config/simulations";
        }
        if (maxBodyBytes <= 0) {
            maxBodyBytes = 1_048_576;
        }
        if (logSampleRate < 0 || logSampleRate > 1) {
            logSampleRate = 0.01;
        }
        if (recentLogSize <= 0) {
            recentLogSize = 50_000;
        }
        if (logDir == null || logDir.isBlank()) {
            logDir = "logs/simulations";
        }
        if (logRetentionDays <= 0) {
            logRetentionDays = 7;
        }
        if (hotReloadDebounceMs <= 0) {
            hotReloadDebounceMs = 500;
        }
        if (tcp == null) {
            tcp = new Tcp("0.0.0.0", 65_536);
        }
    }

    public record Tcp(String defaultHost, int maxFrameBytes) {
        public Tcp {
            if (defaultHost == null || defaultHost.isBlank()) {
                defaultHost = "0.0.0.0";
            }
            if (maxFrameBytes <= 0) {
                maxFrameBytes = 65_536;
            }
        }
    }
}

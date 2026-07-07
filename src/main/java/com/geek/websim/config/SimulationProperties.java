package com.geek.websim.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "web-sim")
public record SimulationProperties(
        String configDir,
        int maxBodyBytes,
        double logSampleRate,
        int recentLogSize,
        Tcp tcp
) {
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
            recentLogSize = 200;
        }
        if (tcp == null) {
            tcp = new Tcp("127.0.0.1", 65_536);
        }
    }

    public record Tcp(String defaultHost, int maxFrameBytes) {
        public Tcp {
            if (defaultHost == null || defaultHost.isBlank()) {
                defaultHost = "127.0.0.1";
            }
            if (maxFrameBytes <= 0) {
                maxFrameBytes = 65_536;
            }
        }
    }
}

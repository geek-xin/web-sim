package com.geek.websim.web.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationLogSnapshot {
    private long totalRequests;
    private long httpRequests;
    private long tcpRequests;
    private long errorRequests;
    private double averageDurationMs;
    private Map<String, SimulationMetricsSummary> simulationMetrics;
    private List<SimulationLogEntry> recentLogs;
}

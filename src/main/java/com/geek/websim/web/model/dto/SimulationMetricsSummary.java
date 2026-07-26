package com.geek.websim.web.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationMetricsSummary {
    private long hits;
    private long errors;
    private double averageDurationMs;
}

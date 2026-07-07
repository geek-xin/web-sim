package com.geek.websim.web.model.dto;

import com.geek.websim.web.model.enums.ProtocolType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationLogEntry {
    private String id;
    private String simulationId;
    private String simulationName;
    private ProtocolType protocol;
    private int status;
    private long durationMs;
    private String requestSummary;
    private String responseSummary;
    private Instant timestamp;
}

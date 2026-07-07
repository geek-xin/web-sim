package com.geek.websim.runtime;

import com.geek.websim.web.model.entity.SimulationBranch;
import com.geek.websim.web.model.entity.SimulationConfig;
import com.geek.websim.web.model.entity.SimulationResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationMatchResult {
    private SimulationConfig config;
    private SimulationBranch branch;
    private SimulationResponse response;
    @Builder.Default
    private Map<String, String> pathVariables = Map.of();
}

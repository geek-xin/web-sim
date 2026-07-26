package com.geek.websim.web.model.dto;

import com.geek.websim.web.model.entity.SimulationConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationConfigImportResponse {
    private int importedCount;
    private int createdCount;
    private int updatedCount;
    private List<SimulationConfig> configs;
}

package com.geek.websim.web.model.dto;

import com.geek.websim.web.model.entity.SimulationConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationConfigImportRequest {
    @NotEmpty(message = "导入配置不能为空")
    @Valid
    private List<SimulationConfigDto> configs;

    public List<SimulationConfig> toEntities() {
        return configs.stream()
                .map(SimulationConfigDto::toEntity)
                .toList();
    }
}

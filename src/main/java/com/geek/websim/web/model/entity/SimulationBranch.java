package com.geek.websim.web.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import com.geek.websim.web.model.enums.ResponseVariantStrategy;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationBranch {
    private String name;
    private int priority;
    @Builder.Default
    private List<SimulationCondition> conditions = new ArrayList<>();
    private SimulationResponse response;
    @Builder.Default
    private List<SimulationResponse> responseVariants = new ArrayList<>();
    private ResponseVariantStrategy variantStrategy;
    private Double probability;
}

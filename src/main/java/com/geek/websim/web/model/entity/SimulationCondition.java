package com.geek.websim.web.model.entity;

import com.geek.websim.web.model.enums.ConditionOperator;
import com.geek.websim.web.model.enums.ConditionSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationCondition {
    private ConditionSource source;
    private String key;
    private ConditionOperator operator;
    private String value;
}

package com.geek.websim.web.model.dto;

import com.geek.websim.web.model.entity.HttpRule;
import com.geek.websim.web.model.entity.RequestTemplate;
import com.geek.websim.web.model.entity.SimulationBranch;
import com.geek.websim.web.model.entity.SimulationConfig;
import com.geek.websim.web.model.entity.SimulationResponse;
import com.geek.websim.web.model.entity.TcpRule;
import com.geek.websim.web.model.enums.ProtocolType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationConfigDto {
    private String id;
    @NotBlank(message = "模拟名称不能为空")
    private String name;
    @NotNull(message = "协议不能为空")
    private ProtocolType protocol;
    private boolean enabled;
    @Valid
    private HttpRule http;
    @Valid
    private TcpRule tcp;
    @Builder.Default
    @Valid
    private RequestTemplate requestTemplate = new RequestTemplate();
    @Builder.Default
    @Valid
    private List<SimulationBranch> branches = new ArrayList<>();
    @NotNull(message = "默认响应不能为空")
    @Valid
    private SimulationResponse defaultResponse;

    public SimulationConfig toEntity() {
        return SimulationConfig.builder()
                .id(id)
                .name(name)
                .protocol(protocol)
                .enabled(enabled)
                .http(http)
                .tcp(tcp)
                .requestTemplate(requestTemplate)
                .branches(branches)
                .defaultResponse(defaultResponse)
                .build();
    }
}

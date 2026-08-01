package com.geek.websim.web.model.entity;

import com.geek.websim.web.model.enums.ProtocolType;
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
public class SimulationConfig {
    private String id;
    @NotBlank(message = "模拟名称不能为空")
    private String name;
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    @NotNull(message = "协议不能为空")
    private ProtocolType protocol;
    private boolean enabled;
    private HttpRule http;
    private TcpRule tcp;
    @Builder.Default
    private RequestTemplate requestTemplate = new RequestTemplate();
    @Builder.Default
    private List<SimulationBranch> branches = new ArrayList<>();
    @NotNull(message = "默认响应不能为空")
    private SimulationResponse defaultResponse;
}

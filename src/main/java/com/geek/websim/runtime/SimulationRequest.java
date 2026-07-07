package com.geek.websim.runtime;

import com.geek.websim.web.model.enums.ProtocolType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationRequest {
    private ProtocolType protocol;
    private String method;
    private String path;
    @Builder.Default
    private Map<String, String> query = Map.of();
    @Builder.Default
    private Map<String, String> headers = Map.of();
    @Builder.Default
    private Map<String, String> pathVariables = Map.of();
    private String body;
    private String tcpBody;
}

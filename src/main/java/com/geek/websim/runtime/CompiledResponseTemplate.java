package com.geek.websim.runtime;

import com.geek.websim.web.model.entity.SimulationResponse;

public record CompiledResponseTemplate(String template) {

    public static CompiledResponseTemplate of(String template) {
        return new CompiledResponseTemplate(template);
    }

    public String render(ResponseRenderer renderer, SimulationResponse response, SimulationRequest request) {
        SimulationResponse renderedResponse = SimulationResponse.builder()
                .status(response == null ? null : response.getStatus())
                .headers(response == null ? null : response.getHeaders())
                .body(template)
                .delayMs(response == null ? null : response.getDelayMs())
                .build();
        return renderer.renderBody(renderedResponse, request);
    }
}

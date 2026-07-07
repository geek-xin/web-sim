package com.geek.websim.runtime;

import com.geek.websim.web.model.entity.SimulationResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseRendererTest {

    @Test
    void renderedBodyReplacesRequestAndRandomPlaceholders() {
        ResponseRenderer renderer = new ResponseRenderer(new RandomValueProvider());
        SimulationRequest request = SimulationRequest.builder()
                .pathVariables(Map.of("id", "u1"))
                .query(Map.of("trace", "q-123"))
                .headers(Map.of("X-Trace-Id", "h-456"))
                .tcpBody("PING")
                .build();
        SimulationResponse response = SimulationResponse.builder()
                .body("id={{path.id}}, trace={{query.trace}}, header={{request.header.X-Trace-Id}}, tcp={{tcp.body}}, requestId={{random.uuid}}")
                .build();

        String rendered = renderer.renderBody(response, request);

        assertThat(rendered).contains("id=u1", "trace=q-123", "header=h-456", "tcp=PING");
        assertThat(rendered).doesNotContain("{{random.uuid}}");
        assertThat(rendered).doesNotContain("{{");
        assertThat(Pattern.compile("requestId=[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                .matcher(rendered).find()).isTrue();
    }

    @Test
    void randomIntSupportsInclusiveIntegerMaxValue() {
        assertThat(new RandomValueProvider().resolve("random.int:2147483647,2147483647"))
                .isEqualTo("2147483647");
    }

}

package com.geek.websim.runtime.http;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PathTemplateTest {

    @Test
    void matchesLiteralAndVariableSegments() {
        PathTemplate template = PathTemplate.compile("/api/users/{id}/orders/{orderId}");

        assertThat(template.match("/api/users/u1/orders/o9"))
                .containsEntry("id", "u1")
                .containsEntry("orderId", "o9");
        assertThat(template.match("/api/users/u1/orders")).isEmpty();
    }

    @Test
    void matchVariablesIsPresentWithEmptyMapForLiteralMatch() {
        PathTemplate template = PathTemplate.compile("/health");

        assertThat(template.matchVariables("/health")).isPresent().contains(Map.of());
    }

    @Test
    void matchVariablesIsEmptyForLiteralMismatch() {
        PathTemplate template = PathTemplate.compile("/health");

        assertThat(template.matchVariables("/ready")).isEmpty();
    }

}

package com.geek.websim.web.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestTemplate {
    @Builder.Default
    private Map<String, String> headers = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, String> query = new LinkedHashMap<>();
    private String body;
}

package com.geek.websim.web.model.entity;

import com.geek.websim.web.model.enums.HttpMatchMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpRule {
    private String method;
    private String path;
    private HttpMatchMode matchMode;
}

package com.geek.websim.web.model.entity;

import com.geek.websim.web.model.enums.TcpFrameMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TcpRule {
    private String host;
    private Integer port;
    private TcpFrameMode frameMode;
}

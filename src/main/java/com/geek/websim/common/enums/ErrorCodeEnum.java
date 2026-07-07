package com.geek.websim.common.enums;

import lombok.Getter;

@Getter
public enum ErrorCodeEnum {
    BAD_REQUEST("400", "请求参数不正确"),
    NOT_FOUND("404", "资源不存在"),
    DUPLICATE_NAME("409_NAME", "名称已存在"),
    DUPLICATE_BINDING("409_BINDING", "监听地址已被占用"),
    CONFIG_IO_ERROR("CONFIG_IO_ERROR", "配置文件读写失败"),
    RUNTIME_REFRESH_ERROR("RUNTIME_REFRESH_ERROR", "运行时刷新失败"),
    INTERNAL_ERROR("500", "系统异常");

    private final String code;
    private final String message;

    ErrorCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }
}

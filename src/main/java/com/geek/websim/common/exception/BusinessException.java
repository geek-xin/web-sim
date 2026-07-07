package com.geek.websim.common.exception;

import com.geek.websim.common.enums.ErrorCodeEnum;
import lombok.Getter;

import java.util.Objects;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCodeEnum errorCode;

    public BusinessException(ErrorCodeEnum errorCode, String message) {
        super(resolveMessage(errorCode, message));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    private static String resolveMessage(ErrorCodeEnum errorCode, String message) {
        ErrorCodeEnum requiredErrorCode = Objects.requireNonNull(errorCode, "errorCode");
        return message == null || message.isBlank() ? requiredErrorCode.getMessage() : message;
    }
}

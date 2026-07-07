package com.geek.websim.common.exception;

import com.geek.websim.common.enums.ErrorCodeEnum;
import com.geek.websim.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    @ResponseBody
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(statusFor(ex.getErrorCode()))
                .body(Result.failure(ex.getErrorCode().getCode(), ex.getMessage()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseBody
    public ResponseEntity<Result<Void>> handleValidation(WebExchangeBindException ex) {
        String message = ex.getAllErrors().stream()
                .map(this::formatValidationError)
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = ErrorCodeEnum.BAD_REQUEST.getMessage();
        }
        return ResponseEntity.badRequest().body(Result.failure(ErrorCodeEnum.BAD_REQUEST.getCode(), message));
    }

    @ExceptionHandler(ServerWebInputException.class)
    @ResponseBody
    public ResponseEntity<Result<Void>> handleInput(ServerWebInputException ex) {
        return ResponseEntity.badRequest().body(Result.failure(ErrorCodeEnum.BAD_REQUEST.getCode(), ex.getReason()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ResponseEntity<Result<Void>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String message = ex.getReason() == null ? status.toString() : ex.getReason();
        return ResponseEntity.status(status)
                .body(Result.failure(String.valueOf(status.value()), message));
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<Result<Void>> handleUnknown(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failure(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()), ErrorCodeEnum.INTERNAL_ERROR.getMessage()));
    }

    private HttpStatus statusFor(ErrorCodeEnum errorCode) {
        if (errorCode == ErrorCodeEnum.BAD_REQUEST) {
            return HttpStatus.BAD_REQUEST;
        }
        if (errorCode == ErrorCodeEnum.NOT_FOUND) {
            return HttpStatus.NOT_FOUND;
        }
        if (errorCode == ErrorCodeEnum.DUPLICATE_NAME || errorCode == ErrorCodeEnum.DUPLICATE_BINDING) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String formatValidationError(ObjectError error) {
        if (error instanceof FieldError fieldError) {
            return formatFieldError(fieldError);
        }
        return error.getDefaultMessage();
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}

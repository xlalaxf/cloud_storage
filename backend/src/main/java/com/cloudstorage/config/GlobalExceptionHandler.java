package com.cloudstorage.config;

import com.cloudstorage.dto.ApiResponse;
import com.cloudstorage.service.AppException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AppException.class)
    ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    ResponseEntity<ApiResponse<Void>> handleValidation(Exception ex) {
        String message = "参数不合法";
        if (ex instanceof MethodArgumentNotValidException validation) {
            message = validation.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + " " + error.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        } else if (ex instanceof BindException bindException) {
            message = bindException.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + " " + error.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        }
        return ResponseEntity.badRequest().body(ApiResponse.fail(message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> handleMaxUpload() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiResponse.fail("上传文件超过限制"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(ex.getMessage()));
    }
}

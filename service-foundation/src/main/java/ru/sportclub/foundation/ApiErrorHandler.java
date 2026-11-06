package ru.sportclub.foundation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiErrorHandler {
    public static String requestId(HttpServletRequest request) {
        String value = MDC.get("requestId");
        if (value == null) value = request.getHeader("X-Request-ID");
        return value != null && value.matches("[A-Za-z0-9._-]{1,64}") ? value : UUID.randomUUID().toString();
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> domain(ApiException error, HttpServletRequest request) {
        return response(error.getStatus(), error.getCode(), error.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException error, HttpServletRequest request) {
        var fields = new java.util.LinkedHashMap<String, String>();
        error.getBindingResult().getFieldErrors().forEach(field ->
                fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        String message = fields.entrySet().stream().map(field -> field.getKey() + ": " + field.getValue())
                .collect(java.util.stream.Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_ERROR", "message", message,
                "fields", fields, "requestId", requestId(request)));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class})
    ResponseEntity<Map<String, String>> malformed(Exception error, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Проверьте формат введённых данных.", request);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, String>> database(DataAccessException error, HttpServletRequest request) {
        org.slf4j.LoggerFactory.getLogger(ApiErrorHandler.class).error("Database request failed; requestId={}", requestId(request));
        return response(HttpStatus.SERVICE_UNAVAILABLE, "DATA_UNAVAILABLE", "Данные временно недоступны. Повторите позже.", request);
    }

    private ResponseEntity<Map<String, String>> response(HttpStatus status, String code, String message,
                                                        HttpServletRequest request) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message,
                "requestId", requestId(request)));
    }
}


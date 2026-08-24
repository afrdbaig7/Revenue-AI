package com.recoverai.common.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts exceptions into the structured {@link ApiError} format. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest req) {
    return ResponseEntity.status(ex.getHttpStatus())
        .body(new ApiError(ex.getCode().name(), ex.getMessage(), correlationId(req), ex.getDetails()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
    Map<String, Object> details = new HashMap<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
      details.put(fe.getField(), fe.getDefaultMessage());
    }
    return ResponseEntity.badRequest()
        .body(new ApiError(ErrorCode.VALIDATION_ERROR.name(), "Request validation failed", correlationId(req), details));
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest req) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ApiError(ErrorCode.UNAUTHENTICATED.name(), "Authentication required", correlationId(req), null));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex, HttpServletRequest req) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ApiError(ErrorCode.FORBIDDEN.name(), "Insufficient permissions", correlationId(req), null));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
    // Log full error server-side; return a safe, generic message to the client.
    org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).error("Unhandled error", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ApiError(ErrorCode.INTERNAL_ERROR.name(), "Internal server error", correlationId(req), null));
  }

  private String correlationId(HttpServletRequest req) {
    String cid = req.getHeader("X-Correlation-Id");
    if (cid == null || cid.isBlank()) {
      cid = MDC.get("correlationId");
    }
    return cid;
  }
}

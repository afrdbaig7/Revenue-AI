package com.recoverai.common.api;

import lombok.Getter;

/** Base exception carrying a stable error code for clients. */
@Getter
public class ApiException extends RuntimeException {

  private final ErrorCode code;
  private final int httpStatus;
  private final java.util.Map<String, Object> details;

  public ApiException(ErrorCode code, String message, int httpStatus) {
    this(code, message, httpStatus, null);
  }

  public ApiException(ErrorCode code, String message, int httpStatus, java.util.Map<String, Object> details) {
    super(message);
    this.code = code;
    this.httpStatus = httpStatus;
    this.details = details;
  }

  public static ApiException notFound(String message) {
    return new ApiException(ErrorCode.NOT_FOUND, message, 404);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(ErrorCode.FORBIDDEN, message, 403);
  }

  public static ApiException badRequest(String message) {
    return new ApiException(ErrorCode.VALIDATION_ERROR, message, 400);
  }

  public static ApiException conflict(String message) {
    return new ApiException(ErrorCode.CONFLICT, message, 409);
  }
}

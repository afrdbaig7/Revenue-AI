package com.recoverai.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Structured error response. Never exposes stack traces or SQL. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    String code,
    String message,
    String correlationId,
    java.util.Map<String, Object> details) {}

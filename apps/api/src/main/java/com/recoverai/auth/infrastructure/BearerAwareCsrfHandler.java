package com.recoverai.auth.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.common.api.ApiError;
import com.recoverai.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * CSRF request handler that skips validation for stateless API clients presenting a
 * Bearer token (CSRF only protects cookie-based ambient authority). Browser requests
 * (cookies) still require the X-CSRF-Token header matching the CSRF cookie.
 */
public class BearerAwareCsrfHandler extends CsrfTokenRequestAttributeHandler {

  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    String auth = request.getHeader("Authorization");
    if (auth != null && auth.startsWith("Bearer ")) {
      return csrfToken.getToken(); // stateless client — nothing to protect via CSRF
    }
    return super.resolveCsrfTokenValue(request, csrfToken);
  }

  /** Writes a structured 401/403 JSON body for security failures. */
  public static void writeJson(HttpServletResponse response, ObjectMapper mapper, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    mapper.writeValue(response.getWriter(), new ApiError(code, message, null, null));
  }
}

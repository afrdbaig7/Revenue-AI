package com.recoverai.auth.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.auth.application.JwtService;
import com.recoverai.common.api.ApiError;
import com.recoverai.common.api.ErrorCode;
import com.recoverai.common.tenant.CurrentUser;
import com.recoverai.common.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests from either the {@code ra_access} HttpOnly cookie (browser) or
 * an {@code Authorization: Bearer} header (API clients), then populates the tenant
 * context from the token claims — tenant ids from clients are never trusted.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String token = extractToken(request);
    if (token != null) {
      Claims claims = jwtService.parse(token);
      if (claims != null) {
        UUID userId = UUID.fromString(claims.getSubject());
        UUID orgId = UUID.fromString(claims.get("orgId", String.class));
        String email = claims.get("email", String.class);
        String name = claims.get("name", String.class);
        String role = claims.get("role", String.class);

        CurrentUser principal = new CurrentUser(userId, orgId, email, name, role);
        var authentication =
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        TenantContext.setOrgId(orgId);
        MDC.put("tenantId", orgId.toString());
      } else {
        writeUnauthorized(response);
        return;
      }
    }
    try {
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
      MDC.remove("tenantId");
    }
  }

  private String extractToken(HttpServletRequest request) {
    String bearer = request.getHeader("Authorization");
    if (bearer != null && bearer.startsWith("Bearer ")) {
      return bearer.substring(7);
    }
    if (request.getCookies() != null) {
      return Arrays.stream(request.getCookies())
          .filter(c -> c.getName().equals("ra_access"))
          .map(Cookie::getValue)
          .findFirst()
          .orElse(null);
    }
    return null;
  }

  private void writeUnauthorized(HttpServletResponse response) throws IOException {
    response.setStatus(401);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getWriter(),
        new ApiError(ErrorCode.UNAUTHENTICATED.name(), "Invalid or expired access token", null, null));
  }
}

package com.recoverai.auth.api;

import com.recoverai.auth.api.AuthDtos.LoginRequest;
import com.recoverai.auth.api.AuthDtos.LoginResponse;
import com.recoverai.auth.api.AuthDtos.MeResponse;
import com.recoverai.auth.api.AuthDtos.RefreshResponse;
import com.recoverai.auth.api.AuthDtos.UserView;
import com.recoverai.auth.application.AuthService;
import com.recoverai.auth.application.AuthService.TokenPair;
import com.recoverai.common.config.RecoverAiProperties;
import com.recoverai.common.tenant.CurrentUser;
import com.recoverai.tenant.domain.Organization;
import com.recoverai.tenant.infrastructure.OrganizationRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth endpoints. Browser flow: access token in HttpOnly cookie (15 min), rotating
 * refresh token in HttpOnly cookie (7 days), CSRF cookie + header for state changes.
 * API clients may instead use Authorization: Bearer (CSRF then skipped).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  public static final String ACCESS_COOKIE = "ra_access";
  public static final String REFRESH_COOKIE = "ra_refresh";
  private static final String CSRF_COOKIE = "ra_csrf";

  private final AuthService authService;
  private final RecoverAiProperties props;
  private final OrganizationRepository organizations;
  private final com.recoverai.common.util.RateLimiter rateLimiter;

  @PostMapping("/login")
  public LoginResponse login(
      @Valid @RequestBody LoginRequest req, HttpServletRequest http, HttpServletResponse response) {
    // Brute-force protection: per-IP burst limit + per-account limit (Redis-backed).
    String ip = http.getRemoteAddr();
    if (!rateLimiter.tryAcquire("login:ip:" + ip, 30, java.time.Duration.ofMinutes(1))
        || !rateLimiter.tryAcquire("login:email:" + req.email().toLowerCase(), 5, java.time.Duration.ofMinutes(1))) {
      throw new com.recoverai.common.api.ApiException(
          com.recoverai.common.api.ErrorCode.RATE_LIMITED, "Too many login attempts. Try again later.", 429);
    }
    TokenPair pair = authService.login(req.email(), req.password());
    setAuthCookies(response, pair.accessToken(), pair.refreshToken(), pair.accessTtlSeconds());
    return new LoginResponse(
        pair.accessToken(), pair.refreshToken(), pair.accessTtlSeconds(), toView(pair.principal()));
  }

  @PostMapping("/refresh")
  public RefreshResponse refresh(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie,
      HttpServletRequest http,
      HttpServletResponse response) {
    TokenPair pair = authService.refresh(refreshCookie);
    setAuthCookies(response, pair.accessToken(), pair.refreshToken(), pair.accessTtlSeconds());
    return new RefreshResponse(
        pair.accessToken(), pair.refreshToken(), pair.accessTtlSeconds(), toView(pair.principal()));
  }

  @PostMapping("/logout")
  public void logout(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie, HttpServletResponse response) {
    authService.logout(refreshCookie);
    clearAuthCookies(response);
  }

  @GetMapping("/me")
  public MeResponse me(Authentication authentication) {
    CurrentUser user = (CurrentUser) authentication.getPrincipal();
    List<String> permissions = permissionsFor(user.role());
    String orgName = organizations
        .findById(user.orgId())
        .map(Organization::getName)
        .orElse(user.orgId().toString());
    return new MeResponse(
        new UserView(user.userId(), user.orgId(), user.email(), user.fullName(), user.role()),
        permissions,
        orgName);
  }

  /** Exposes the CSRF token to the browser (cookie is set by the CSRF filter). */
  @GetMapping("/csrf")
  public AuthDtos.CsrfResponse csrf(HttpServletRequest request) {
    Cookie cookie = request.getCookies() == null
        ? null
        : Arrays.stream(request.getCookies()).filter(c -> c.getName().equals(CSRF_COOKIE)).findFirst().orElse(null);
    return new AuthDtos.CsrfResponse(cookie == null ? "" : cookie.getValue());
  }

  private void setAuthCookies(HttpServletResponse response, String access, String refresh, long ttlSeconds) {
    boolean secure = props.jwt().secureCookies();
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(ACCESS_COOKIE, access)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(ttlSeconds)
            .build()
            .toString());
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(REFRESH_COOKIE, refresh)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/api/v1/auth")
            .maxAge(props.jwt().refreshTtl().toSeconds())
            .build()
            .toString());
  }

  private void clearAuthCookies(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(ACCESS_COOKIE, "").maxAge(0).path("/").build().toString());
    response.addHeader(
        HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, "").maxAge(0).path("/api/v1/auth").build().toString());
  }

  private static UserView toView(AuthService.UserPrincipal p) {
    return new UserView(p.userId(), p.orgId(), p.email(), p.fullName(), p.role());
  }

  public static List<String> permissionsFor(String role) {
    return switch (role) {
      case "OWNER" -> List.of("*");
      case "ADMIN" -> List.of("integrations:write", "policies:write", "users:write", "recovery:manage", "recovery:approve");
      case "OPERATOR" -> List.of("recovery:manage", "recovery:approve", "recovery:escalate");
      case "ANALYST" -> List.of("analytics:read", "audit:read", "export");
      default -> List.of("analytics:read");
    };
  }
}

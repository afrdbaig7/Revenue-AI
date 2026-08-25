package com.recoverai.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request/response DTOs for the auth API. Tokens are also delivered via HttpOnly cookies. */
public final class AuthDtos {

  private AuthDtos() {}

  public record LoginRequest(
      @NotBlank @Email String email,
      @NotBlank @Size(min = 8, max = 128) String password) {}

  public record LoginResponse(
      @JsonProperty("accessToken") String accessToken,
      @JsonProperty("refreshToken") String refreshToken,
      @JsonProperty("expiresIn") long expiresIn,
      @JsonProperty("user") UserView user) {}

  public record UserView(UUID userId, UUID orgId, String email, String fullName, String role) {}

  public record RefreshResponse(
      @JsonProperty("accessToken") String accessToken,
      @JsonProperty("refreshToken") String refreshToken,
      @JsonProperty("expiresIn") long expiresIn,
      @JsonProperty("user") UserView user) {}

  public record MeResponse(
      @JsonProperty("user") UserView user,
      @JsonProperty("permissions") java.util.List<String> permissions,
      @JsonProperty("orgName") String orgName) {}

  public record CsrfResponse(@JsonProperty("csrfToken") String csrfToken) {}
}

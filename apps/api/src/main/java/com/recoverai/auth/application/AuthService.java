package com.recoverai.auth.application;

import com.recoverai.auth.domain.Membership;
import com.recoverai.auth.domain.RefreshToken;
import com.recoverai.auth.domain.Role;
import com.recoverai.auth.domain.User;
import com.recoverai.auth.infrastructure.MembershipRepository;
import com.recoverai.auth.infrastructure.RefreshTokenRepository;
import com.recoverai.auth.infrastructure.UserRepository;
import com.recoverai.common.api.ApiException;
import com.recoverai.common.api.ErrorCode;
import com.recoverai.common.config.RecoverAiProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication service: email/password login, JWT access tokens, opaque rotating
 * refresh tokens (hashed at rest, family-based reuse detection), logout/revocation.
 */
@Service
@Slf4j
public class AuthService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserRepository users;
  private final MembershipRepository memberships;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RecoverAiProperties props;

  public AuthService(
      UserRepository users,
      MembershipRepository memberships,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      RecoverAiProperties props) {
    this.users = users;
    this.memberships = memberships;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.props = props;
  }

  /** Login result: access token, opaque refresh token, and principal metadata. */
  public record TokenPair(String accessToken, String refreshToken, long accessTtlSeconds, UserPrincipal principal) {}

  public record UserPrincipal(UUID userId, UUID orgId, String email, String fullName, String role) {}

  @Transactional
  public TokenPair login(String email, String rawPassword) {
    User user = users
        .findByEmail(email.toLowerCase().trim())
        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid credentials", 401));

    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid credentials", 401);
    }
    if (!"ACTIVE".equals(user.getStatus())) {
      throw new ApiException(ErrorCode.FORBIDDEN, "Account is disabled", 403);
    }

    Membership membership = memberships.findByUserId(user.getId()).stream()
        .findFirst()
        .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "No organization membership", 403));

    user.setLastLoginAt(Instant.now());
    users.save(user);

    return issueTokens(user, membership);
  }

  @Transactional
  public TokenPair refresh(String opaqueToken) {
    if (opaqueToken == null || opaqueToken.isBlank()) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED, "Missing refresh token", 401);
    }
    String hash = sha256Hex(opaqueToken);
    RefreshToken stored = refreshTokens
        .findByTokenHash(hash)
        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Unknown refresh token", 401));

    if (stored.isRevoked()) {
      // Reuse detection: a presented token that was already rotated/revoked is a theft
      // signal — revoke the whole family.
      refreshTokens.revokeFamily(stored.getFamilyId(), Instant.now());
      log.warn("Refresh token reuse detected; family {} revoked", stored.getFamilyId());
      throw new ApiException(ErrorCode.UNAUTHENTICATED, "Refresh token revoked", 401);
    }
    if (stored.getExpiresAt().isBefore(Instant.now())) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED, "Refresh token expired", 401);
    }

    User user = users.findById(stored.getUserId()).orElseThrow();
    Membership membership = memberships
        .findByOrgIdAndUserId(stored.getOrgId(), stored.getUserId())
        .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "Membership missing", 403));

    // Rotate: revoke current, issue new token in the same family.
    stored.setRevokedAt(Instant.now());
    refreshTokens.save(stored);

    String newOpaque = newOpaqueToken();
    RefreshToken next =
        new RefreshToken(stored.getOrgId(), user.getId(), sha256Hex(newOpaque), stored.getFamilyId(), refreshExpiry());
    next.setReplacedBy(stored.getId());
    refreshTokens.save(next);

    String access = jwtService.issueAccessToken(
        user.getId(), membership.getOrgId(), user.getEmail(), user.getFullName(), membership.getRole().name());

    return new TokenPair(access, newOpaque, props.jwt().accessTtl().toSeconds(), toPrincipal(user, membership));
  }

  @Transactional
  public void logout(String opaqueToken) {
    if (opaqueToken == null || opaqueToken.isBlank()) {
      return;
    }
    refreshTokens
        .findByTokenHash(sha256Hex(opaqueToken))
        .ifPresent(t -> {
          t.setRevokedAt(Instant.now());
          refreshTokens.save(t);
        });
  }

  private TokenPair issueTokens(User user, Membership membership) {
    String access = jwtService.issueAccessToken(
        user.getId(), membership.getOrgId(), user.getEmail(), user.getFullName(), membership.getRole().name());
    String opaque = newOpaqueToken();
    RefreshToken stored = new RefreshToken(membership.getOrgId(), user.getId(), sha256Hex(opaque), UUID.randomUUID(), refreshExpiry());
    refreshTokens.save(stored);
    return new TokenPair(access, opaque, props.jwt().accessTtl().toSeconds(), toPrincipal(user, membership));
  }

  private Instant refreshExpiry() {
    return Instant.now().plus(props.jwt().refreshTtl());
  }

  private static UserPrincipal toPrincipal(User user, Membership membership) {
    return new UserPrincipal(
        user.getId(), membership.getOrgId(), user.getEmail(), user.getFullName(), membership.getRole().name());
  }

  private static String newOpaqueToken() {
    byte[] bytes = new byte[48];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  public static String sha256Hex(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public List<Membership> membershipsOf(UUID userId) {
    return memberships.findByUserId(userId);
  }
}

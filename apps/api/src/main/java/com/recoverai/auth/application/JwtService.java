package com.recoverai.auth.application;

import com.recoverai.common.config.RecoverAiProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Issues and verifies short-lived JWT access tokens (HS256, derived key). */
@Service
public class JwtService {

  private final RecoverAiProperties props;
  private final SecretKey key;

  public JwtService(RecoverAiProperties props) {
    this.props = props;
    this.key = deriveKey(props.encryption().key());
  }

  private static SecretKey deriveKey(String secret) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
      return Keys.hmacShaKeyFor(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  public String issueAccessToken(UUID userId, UUID orgId, String email, String fullName, String role) {
    Instant now = Instant.now();
    Instant exp = now.plus(props.jwt().accessTtl());
    return Jwts.builder()
        .issuer(props.jwt().issuer())
        .subject(userId.toString())
        .claim("orgId", orgId.toString())
        .claim("email", email)
        .claim("name", fullName)
        .claim("role", role)
        .issuedAt(Date.from(now))
        .expiration(Date.from(exp))
        .signWith(key)
        .compact();
  }

  /** @return parsed claims or {@code null} when the token is invalid/expired. */
  public Claims parse(String token) {
    try {
      return Jwts.parser().verifyWith(key).requireIssuer(props.jwt().issuer()).build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (Exception e) {
      return null;
    }
  }
}

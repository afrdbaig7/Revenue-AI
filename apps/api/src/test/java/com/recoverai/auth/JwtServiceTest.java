package com.recoverai.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.recoverai.auth.application.JwtService;
import com.recoverai.common.config.RecoverAiProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Access tokens: short-lived, signed, parseable, and reject tampering. */
class JwtServiceTest {

  private final RecoverAiProperties props = new RecoverAiProperties(
      true,
      "inline",
      new RecoverAiProperties.Temporal(false, "localhost:7233", "recoverai"),
      new RecoverAiProperties.Razorpay("", "", "", "https://api.razorpay.com", true, 3000, 8000, "api.razorpay.com,localhost"),
      new RecoverAiProperties.Ai("http://localhost:8100", 8000, true),
      new RecoverAiProperties.Jwt("recoverai-test", 15, 7, false),
      new RecoverAiProperties.Encryption("test-master-key-for-jwt-derivation-0123456789"),
      new RecoverAiProperties.Scheduling(1000, 5000, 30000, "0 0 * * * *"));

  private final JwtService jwt = new JwtService(props);

  @Test
  void issuesAndParsesTokens() {
    UUID userId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    String token = jwt.issueAccessToken(userId, orgId, "demo@recoverai.dev", "Demo User", "OWNER");
    var claims = jwt.parse(token);
    assertThat(claims).isNotNull();
    assertThat(claims.getSubject()).isEqualTo(userId.toString());
    assertThat(claims.get("orgId", String.class)).isEqualTo(orgId.toString());
    assertThat(claims.get("role", String.class)).isEqualTo("OWNER");
  }

  @Test
  void rejectsTamperedToken() {
    String token = jwt.issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), "a@b.c", "A", "VIEWER");
    String tampered = token.substring(0, token.length() - 4) + "abcd";
    assertThat(jwt.parse(tampered)).isNull();
  }

  @Test
  void rejectsGarbage() {
    assertThat(jwt.parse("not-a-jwt")).isNull();
    assertThat(jwt.parse(null)).isNull();
  }
}

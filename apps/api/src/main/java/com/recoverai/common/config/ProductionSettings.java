package com.recoverai.common.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Production must not start with repository defaults. Error messages never contain secret values. */
@Component
@Profile("production")
public class ProductionSettings {
  private final RecoverAiProperties props;
  @Value("${recoverai.jwt.signing-key:}") private String signingKey;
  @Value("${recoverai.ai.internal-api-key:}") private String internalKey;
  @Value("${recoverai.process-role:all}") private String role;

  public ProductionSettings(RecoverAiProperties props) { this.props = props; }

  @PostConstruct void validate() {
    requireSecret(props.encryption().key(), "ENCRYPTION_KEY");
    if (!role.equals("worker")) {
      requireSecret(signingKey, "JWT_SIGNING_KEY");
      if (signingKey.equals(props.encryption().key())) throw new IllegalStateException("Use separate signing and encryption keys");
      if (!props.jwt().secureCookies()) throw new IllegalStateException("Secure cookies are required in production");
    }
    if (props.ai().enabled()) requireSecret(internalKey, "INTERNAL_API_KEY");
  }

  private void requireSecret(String value, String name) {
    if (value == null || value.length() < 32 || value.toLowerCase().contains("change_me") || value.contains("dev-only")) {
      throw new IllegalStateException("Configure a strong " + name + " before starting production");
    }
  }
}

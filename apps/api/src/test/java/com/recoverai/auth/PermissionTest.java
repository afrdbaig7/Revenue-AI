package com.recoverai.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.recoverai.auth.api.AuthController;
import org.junit.jupiter.api.Test;

/** RBAC permission mapping — sensitive actions must be scoped to trusted roles. */
class PermissionTest {

  @Test
  void viewerIsReadOnly() {
    assertThat(AuthController.permissionsFor("VIEWER")).containsExactly("analytics:read");
  }

  @Test
  void analystReadsAnalyticsAndAudit() {
    var permissions = AuthController.permissionsFor("ANALYST");
    assertThat(permissions).contains("analytics:read", "audit:read", "export");
  }

  @Test
  void operatorManagesAndApprovesRecovery() {
    var permissions = AuthController.permissionsFor("OPERATOR");
    assertThat(permissions).contains("recovery:manage", "recovery:approve");
    assertThat(permissions).doesNotContain("policies:write");
  }

  @Test
  void adminConfiguresButOwnerIsUniversal() {
    var admin = AuthController.permissionsFor("ADMIN");
    assertThat(admin).contains("integrations:write", "policies:write", "users:write", "recovery:approve");
    assertThat(AuthController.permissionsFor("OWNER")).containsExactly("*");
  }
}

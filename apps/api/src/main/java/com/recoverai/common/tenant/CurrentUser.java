package com.recoverai.common.tenant;

import java.util.UUID;

/** Authenticated principal information used for RBAC + tenant scoping + audit actors. */
public record CurrentUser(UUID userId, UUID orgId, String email, String fullName, String role) {

  public boolean hasRole(String... roles) {
    for (String r : roles) {
      if (r.equalsIgnoreCase(role)) {
        return true;
      }
    }
    return false;
  }
}

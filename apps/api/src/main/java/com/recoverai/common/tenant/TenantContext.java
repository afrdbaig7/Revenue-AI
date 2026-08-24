package com.recoverai.common.tenant;

import java.util.UUID;

/**
 * Tenant context resolved from the authenticated principal. Never trust tenant IDs from
 * client input — controllers resolve {@code orgId} from here.
 */
public final class TenantContext {

  private static final ThreadLocal<UUID> ORG = new ThreadLocal<>();

  private TenantContext() {}

  public static void setOrgId(UUID orgId) {
    ORG.set(orgId);
  }

  public static UUID orgId() {
    return ORG.get();
  }

  public static void clear() {
    ORG.remove();
  }
}

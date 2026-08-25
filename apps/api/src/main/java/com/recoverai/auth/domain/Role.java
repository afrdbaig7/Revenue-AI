package com.recoverai.auth.domain;

/** RBAC roles. Sensitive actions are authorized server-side with these. */
public enum Role {
  OWNER,
  ADMIN,
  OPERATOR,
  ANALYST,
  VIEWER
}

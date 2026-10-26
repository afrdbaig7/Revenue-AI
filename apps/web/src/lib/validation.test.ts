import { expect, it } from "vitest";
import { policySchema } from "./validation";

const policy = { maxRetries: 3, maxContactAttempts: 2, maxDiscountPercent: 10, recoveryWindowHours: 72,
  minimumRecoverableAmount: 100, contactCooldownHours: 12, requireApprovalAboveAmount: 10000,
  allowWhatsApp: true, allowEmail: true, allowSms: true, allowDiscounts: false, allowPaymentLinks: true, allowDelayedRetry: true };

it("rejects policy values outside server limits", () => {
  for (const patch of [{ maxRetries: 11 }, { maxDiscountPercent: -1 }, { recoveryWindowHours: 0 }, { minimumRecoverableAmount: NaN }, { contactCooldownHours: 169 }]) {
    expect(policySchema.safeParse({ ...policy, ...patch }).success).toBe(false);
  }
});

it("strips server metadata from writable policy values", () => {
  expect(policySchema.parse({ ...policy, id: "id", version: 5, orgId: "other" })).toEqual(policy);
});

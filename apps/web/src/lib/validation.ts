import { z } from "zod";

export const policySchema = z.object({
  maxRetries: z.number().int().min(0).max(10),
  maxContactAttempts: z.number().int().min(0).max(10),
  maxDiscountPercent: z.number().int().min(0).max(50),
  recoveryWindowHours: z.number().int().min(1).max(720),
  minimumRecoverableAmount: z.number().int().nonnegative().safe(),
  contactCooldownHours: z.number().int().min(0).max(168),
  requireApprovalAboveAmount: z.number().int().nonnegative().safe(),
  allowWhatsApp: z.boolean(), allowEmail: z.boolean(), allowSms: z.boolean(),
  allowDiscounts: z.boolean(), allowPaymentLinks: z.boolean(), allowDelayedRetry: z.boolean(),
});

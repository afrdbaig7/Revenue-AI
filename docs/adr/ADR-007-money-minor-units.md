# ADR-007: Money in Integer Minor Units

**Status:** Accepted

## Context
Floating point cannot represent decimal money exactly (0.1 + 0.2 ≠ 0.3); rounding
errors in revenue recovery are unacceptable and unauditable.

## Decision
All monetary values are stored and transmitted as **`BIGINT` in the smallest currency
unit** (paise for INR) with an explicit `CHAR(3)` currency column. Conversion to/from
decimal happens only at presentation boundaries (UI formatting, CSV export) using
`BigDecimal` with `HALF_UP` and 2 decimal places. `Money` value type (amount_minor +
currency) is used in domain logic; arithmetic is `long`/`BigDecimal` — never `double`.

## Alternatives considered
- `DECIMAL(19,4)`: acceptable, but integer minor units eliminate scale ambiguity and
  match payment-provider integer payloads (Razorpay amounts are in paise).
- Float/double: rejected outright.

## Consequences
- No rounding surprises; provider integration maps directly (Razorpay `amount` = paise).
- JSON API carries `amountMinor` (long) + `currency`; UI formats with `Intl.NumberFormat`.

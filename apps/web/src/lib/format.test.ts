import { describe, expect, it } from "vitest";
import { formatINR, formatPercent, timeAgo } from "./format";

describe("formatINR — money in integer minor units (paise)", () => {
  it("formats rupees from paise", () => {
    expect(formatINR(349900, { noDecimals: true })).toBe("₹3,499");
  });

  it("handles paise remainder", () => {
    expect(formatINR(149950)).toContain("₹1,499");
  });

  it("uses Indian digit grouping (lakh/crore)", () => {
    expect(formatINR(28430000, { noDecimals: true })).toBe("₹2,84,300");
    expect(formatINR(52400000, { noDecimals: true })).toBe("₹5,24,000");
  });

  it("compact mode works", () => {
    expect(formatINR(28430000, { compact: true })).toContain("₹");
  });
});

describe("formatPercent", () => {
  it("formats with one decimal", () => {
    expect(formatPercent(54.3)).toBe("54.3%");
  });
});

describe("timeAgo", () => {
  it("handles null", () => {
    expect(timeAgo(null)).toBe("—");
  });
});

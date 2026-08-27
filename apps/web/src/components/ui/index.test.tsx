import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { Button, Modal, Progress, StatusBadge } from ".";

afterEach(cleanup);

describe("RecoverAI UI primitives", () => {
  it("renders status text in addition to its semantic color", () => {
    render(<StatusBadge status="AWAITING_APPROVAL" />);
    expect(screen.getByText("Awaiting Approval").textContent).toBe("Awaiting Approval");
  });

  it("clamps progress values to the valid percentage range", () => {
    render(<Progress value={125} label="Recovery progress" />);
    expect(screen.getByRole("progressbar").getAttribute("aria-valuenow")).toBe("100");
  });

  it("disables a button while it is loading", () => {
    render(<Button loading>Saving policy</Button>);
    const button = screen.getByRole("button", { name: "Saving policy" });
    expect(button.hasAttribute("disabled")).toBe(true);
    expect(button.getAttribute("aria-busy")).toBe("true");
  });

  it("closes an accessible modal with Escape", () => {
    const onClose = vi.fn();
    render(<Modal open onClose={onClose} title="Approve proposal">Review this action.</Modal>);
    expect(screen.getByRole("dialog").getAttribute("aria-modal")).toBe("true");
    fireEvent.keyDown(document, { key: "Escape" });
    expect(onClose).toHaveBeenCalledOnce();
  });
});

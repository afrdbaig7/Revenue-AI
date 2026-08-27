# ADR-009: Custom UI Primitives

**Status:** Accepted

## Context
We are building a fintech SaaS dashboard for RecoverAI, which involves dense data layouts, highly specific visual requirements for AI diagnostics, financial metrics, and timeline events. The interface needs to feel snappy, strictly adhere to our bespoke design language, and avoid the overhead of unused generic components.

## Decision
**Use custom-built UI primitives tailored to the fintech domain.**

- We will maintain our own UI primitives (in `components/ui.tsx` or similar domain-specific component files) rather than importing a heavy component library.
- Custom components will be designed specifically for the data density required by fintech interfaces (e.g., `StatCard`, `StatusBadge`, `SyntheticBanner`).
- We prioritize complete control over DOM structure, styling, and accessibility for our specific use cases.

## Alternatives considered
- **shadcn/ui + Radix UI:** While flexible, it generates significant boilerplate for generic use cases that we don't need, and adapting it to dense fintech layouts requires almost as much work as building custom primitives.
- **Material UI / Ant Design:** Too visually opinionated; overriding their styles to match our bespoke fintech design language would lead to bloated CSS and poor performance.

## Consequences
- **Full design control:** We can perfectly match the design specifications without fighting framework constraints.
- **Lighter bundle:** We only ship the exact code needed for our UI primitives.
- **Fintech-specific components:** We can optimize for our exact data models and user flows out of the box.
- **Maintenance:** The team assumes full responsibility for maintaining, updating, and ensuring accessibility of these custom primitives.

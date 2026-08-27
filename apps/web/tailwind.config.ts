import type { Config } from "tailwindcss";

export default {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: {
          950: "var(--color-ink)",
          900: "#113451",
          800: "#193F5C",
          700: "#284E68",
          500: "var(--color-text)",
        },
        brand: {
          50: "#F3F2FF",
          100: "#E8E6FF",
          500: "var(--color-brand)",
          600: "var(--color-brand)",
          700: "var(--color-brand-hover)",
        },
        mint: {
          50: "#EAFBF4",
          400: "#18BE83",
          500: "var(--color-recovered)",
          600: "var(--color-recovered-strong)",
        },
        amber: {
          50: "#FFF8E8",
          100: "#FDECC4",
          400: "#fbbf24",
          500: "var(--color-warning)",
          700: "#92400E",
        },
        danger: {
          50: "#FFF1F4",
          100: "#FFDCE5",
          500: "var(--color-danger)",
          600: "#C51237",
          700: "#9F1239",
        },
      },
      fontFamily: {
        sans: ["var(--font-inter)", "ui-sans-serif", "system-ui", "sans-serif"],
        mono: ["ui-monospace", "SFMono-Regular", "Menlo", "monospace"],
      },
      boxShadow: {
        card: "var(--shadow-card)",
        pop: "var(--shadow-popover)",
      },
    },
  },
  plugins: [],
} satisfies Config;

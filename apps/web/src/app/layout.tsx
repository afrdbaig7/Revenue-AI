import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { Providers } from "./providers";

const inter = Inter({ subsets: ["latin"], variable: "--font-inter" });

export const metadata: Metadata = {
  title: "RecoverAI — AI Revenue Recovery Engine",
  description:
    "Detect revenue at risk, diagnose payment failures, and execute policy-bounded recovery workflows with measurable results.",
  keywords: [
    "Revenue Recovery",
    "Payment Failure Recovery",
    "Razorpay",
    "Fintech",
    "Expected Value Engine",
    "Payment Reliability",
    "AI Recovery",
  ],
  authors: [{ name: "RecoverAI Engineering Team" }],
  openGraph: {
    title: "RecoverAI — AI Revenue Recovery & Payment Reliability Engine",
    description:
      "Detect revenue at risk, diagnose payment failures, and execute policy-bounded recovery workflows with measurable results.",
    type: "website",
    siteName: "RecoverAI",
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={inter.variable}>
      <body className="font-sans antialiased text-slate-800 bg-[#F6F9FC] selection:bg-brand-500/20 selection:text-ink-950">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}

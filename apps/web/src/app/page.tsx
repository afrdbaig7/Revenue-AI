import {
  LandingNav,
  HeroSection,
  ProofGrid,
  RecoveryLoop,
  TrustBoundarySection,
  MoneyRecoveredSection,
  ExperimentSection,
  JourneySection,
  SafetyGrid,
  ArchitectureSection,
  LandingFooter,
} from "@/components/landing";

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-[#F6F9FC] text-slate-800 selection:bg-brand-500/20 selection:text-ink-950">
      <LandingNav />
      <main>
        <HeroSection />
        <ProofGrid />
        <RecoveryLoop />
        <TrustBoundarySection />
        <MoneyRecoveredSection />
        <ExperimentSection />
        <JourneySection />
        <SafetyGrid />
        <ArchitectureSection />
      </main>
      <LandingFooter />
    </div>
  );
}

"use client";

export function BackgroundMesh() {
  return (
    <div
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 -z-10 overflow-hidden"
    >
      {/* Subtle top ambient gradients */}
      <div className="absolute -top-40 left-1/2 h-[600px] w-[1000px] -translate-x-1/2 opacity-30 blur-[100px] sm:opacity-40">
        <div className="h-full w-full bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-[#635bff]/40 via-[#00d4b2]/20 to-transparent" />
      </div>
      <div className="absolute top-20 -left-40 h-[450px] w-[500px] rounded-full bg-[#ff62a5]/15 blur-[90px]" />
      <div className="absolute top-40 -right-40 h-[500px] w-[600px] rounded-full bg-[#ff8a00]/10 blur-[110px]" />
      
      {/* Light dot grid pattern overlay */}
      <div 
        className="absolute inset-0 opacity-[0.035]"
        style={{
          backgroundImage: `radial-gradient(#0a2540 1px, transparent 1px)`,
          backgroundSize: "24px 24px"
        }}
      />
    </div>
  );
}

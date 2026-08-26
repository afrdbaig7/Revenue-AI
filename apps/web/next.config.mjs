/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: "standalone",
  // The dashboard NEVER calls the API cross-origin from the browser. All /api/v1
  // traffic is same-origin and proxied server-side by this Next.js process to the
  // control plane (API_PROXY_TARGET, default localhost:8080). This works in the
  // sandbox live preview, local dev, and production behind the ingress.
  async rewrites() {
    const target = process.env.API_PROXY_TARGET || "http://api:8080";
    return [
      {
        source: "/api/v1/:path*",
        destination: `${target}/api/v1/:path*`,
      },
    ];
  },
};

export default nextConfig;

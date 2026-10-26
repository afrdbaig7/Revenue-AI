/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: "standalone",
  // The App Router proxy reads API_PROXY_TARGET at runtime.
};
export default nextConfig;

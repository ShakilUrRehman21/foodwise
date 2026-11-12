import type { NextConfig } from "next";

// The browser talks only to this app; /api/* is proxied to the Spring Boot backend (same origin, no CORS).
// API_PROXY_TARGET is read at build time: http://localhost:8080 for local dev, http://api:8080 inside docker compose.
const apiTarget = (process.env.API_PROXY_TARGET ?? "http://localhost:8080").replace(/\/$/, "");

const nextConfig: NextConfig = {
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiTarget}/api/:path*` }];
  },
};

export default nextConfig;

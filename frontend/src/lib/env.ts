/**
 * Public runtime configuration (inlined at build time by Next.js).
 * Empty API_URL (the default) means same-origin: requests go to /api/* and Next.js proxies them to the backend.
 */
export const API_URL = (process.env.NEXT_PUBLIC_API_URL ?? "").replace(/\/$/, "");
export const CLERK_PUBLISHABLE_KEY = process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY ?? "";
/** Clerk is used when a publishable key is configured; otherwise the backend's local dev sign-in is used. */
export const AUTH_MODE: "clerk" | "dev" = CLERK_PUBLISHABLE_KEY ? "clerk" : "dev";

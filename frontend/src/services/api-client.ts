import { API_URL } from "@/lib/env";
import type { ApiEnvelope } from "./types";

/** Error raised for any non-2xx API response, carrying the backend's error envelope. */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly details: Record<string, string[]> = {},
  ) {
    super(message);
    this.name = "ApiError";
  }

  /** First validation message for a field (supports prefixes like "items[0].expiryDate"). */
  field(name: string): string | undefined {
    return this.details[name]?.[0];
  }
}

type TokenGetter = () => Promise<string | null>;
let tokenGetter: TokenGetter = async () => null;
let onUnauthorized: () => void = () => {};

/** Wired by the auth provider so non-React code can attach the bearer token. */
export function configureApiAuth(getter: TokenGetter, unauthorized: () => void) {
  tokenGetter = getter;
  onUnauthorized = unauthorized;
}

type Query = Record<string, string | number | boolean | null | undefined>;

interface RequestOptions {
  query?: Query;
  body?: unknown;
  form?: FormData;
  signal?: AbortSignal;
  raw?: boolean;
}

function buildUrl(path: string, query?: Query) {
  const base = API_URL || (typeof window !== "undefined" ? window.location.origin : "http://localhost:3000");
  const url = new URL(base + path);
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== null && v !== "") url.searchParams.set(k, String(v));
    }
  }
  return url.toString();
}

async function request<T>(method: string, path: string, opts: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  const token = await tokenGetter();
  if (token) headers.Authorization = `Bearer ${token}`;
  let body: BodyInit | undefined;
  if (opts.form) {
    body = opts.form;
  } else if (opts.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(opts.body);
  }

  let res: Response;
  try {
    res = await fetch(buildUrl(path, opts.query), { method, headers, body, signal: opts.signal, cache: "no-store" });
  } catch (e) {
    if ((e as Error).name === "AbortError") throw e;
    throw new ApiError(0, "NETWORK_ERROR", "Can't reach FoodWise right now. Check your connection and try again.");
  }

  if (res.status === 401) onUnauthorized();
  if (opts.raw && res.ok) return res as unknown as T;

  let envelope: ApiEnvelope<T> | null = null;
  try {
    envelope = (await res.json()) as ApiEnvelope<T>;
  } catch {
    envelope = null;
  }
  if (!res.ok || !envelope?.success) {
    const err = envelope?.error;
    throw new ApiError(
      res.status,
      err?.code ?? "HTTP_" + res.status,
      err?.message ?? `Request failed (${res.status}).`,
      err?.details ?? {},
    );
  }
  return envelope.data as T;
}

export const api = {
  get: <T>(path: string, query?: Query, signal?: AbortSignal) => request<T>("GET", path, { query, signal }),
  post: <T>(path: string, body?: unknown, query?: Query) => request<T>("POST", path, { body, query }),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, { body }),
  del: <T>(path: string, query?: Query) => request<T>("DELETE", path, { query }),
  upload: <T>(path: string, form: FormData, query?: Query) => request<T>("POST", path, { form, query }),
  /** Authenticated download (e.g. CSV export or scan image) returning the raw Response. */
  download: (path: string) => request<Response>("GET", path, { raw: true }),
};

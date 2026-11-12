import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "@/services/api-client";

/** GETs retry up to 3 times with exponential backoff (never for 4xx); mutations never auto-retry (SYSTEM_DESIGN 11.2). */
export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: true,
        retry: (count, error) => {
          if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false;
          return count < 3;
        },
        retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 8000),
      },
      mutations: { retry: false },
    },
  });
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const firstDetail = Object.values(error.details ?? {})[0]?.[0];
    return error.code === "VALIDATION_ERROR" && firstDetail ? firstDetail : error.message;
  }
  if (error instanceof Error) return error.message;
  return "Something went wrong.";
}

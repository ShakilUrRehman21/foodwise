"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { errorMessage } from "@/lib/query";
import { useKitchen } from "./use-kitchen";

/** Query keys are namespaced by kitchen so a single invalidation refreshes every dependent screen. */
export const kq = (kitchenId: string, ...parts: unknown[]) => ["k", kitchenId, ...parts] as const;

interface Options<TData> {
  success?: string | ((data: TData) => string | undefined);
  /** Show errors as toasts (default true). Set false when the caller renders field errors itself. */
  toastErrors?: boolean;
  onSuccess?: (data: TData) => void;
}

/**
 * Mutation scoped to the active kitchen. On success it invalidates all of that kitchen's queries
 * (inventory changes ripple into dashboard, expiry, recipes and analytics).
 */
export function useKitchenMutation<TVars, TData>(fn: (kitchenId: string, vars: TVars) => Promise<TData>, opts: Options<TData> = {}) {
  const { kitchenId } = useKitchen();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: TVars) => fn(kitchenId, vars),
    onSuccess: async (data) => {
      await qc.invalidateQueries({ queryKey: ["k", kitchenId] });
      const msg = typeof opts.success === "function" ? opts.success(data) : opts.success;
      if (msg) toast.success(msg);
      opts.onSuccess?.(data);
    },
    onError: (error) => {
      if (opts.toastErrors !== false) toast.error(errorMessage(error));
    },
  });
}

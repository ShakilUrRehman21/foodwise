"use client";

import { useQuery } from "@tanstack/react-query";
import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { kitchensApi, usersApi } from "@/services/endpoints";
import type { Kitchen, Profile } from "@/services/types";
import { ACTIVE_KITCHEN_KEY } from "@/lib/constants";

interface KitchenContextValue {
  profile: Profile;
  kitchens: Kitchen[];
  kitchen: Kitchen;
  kitchenId: string;
  setKitchenId: (id: string) => void;
}

const KitchenContext = createContext<KitchenContextValue | null>(null);

export function useKitchen(): KitchenContextValue {
  const ctx = useContext(KitchenContext);
  if (!ctx) throw new Error("useKitchen must be used inside <KitchenProvider>");
  return ctx;
}

export const profileKey = ["me"] as const;
export const kitchensKey = ["kitchens"] as const;

export function useProfileQuery(enabled = true) {
  return useQuery({ queryKey: profileKey, queryFn: usersApi.me, enabled });
}

export function useKitchensQuery(enabled = true) {
  return useQuery({ queryKey: kitchensKey, queryFn: kitchensApi.list, enabled });
}

function readActive(): string | null {
  try {
    return window.localStorage.getItem(ACTIVE_KITCHEN_KEY);
  } catch {
    return null;
  }
}

/** Provides the signed-in profile and the active kitchen (remembered per browser). */
export function KitchenProvider({
  profile,
  kitchens,
  children,
}: {
  profile: Profile;
  kitchens: Kitchen[];
  children: React.ReactNode;
}) {
  const [activeId, setActiveId] = useState<string | null>(null);

  useEffect(() => {
    setActiveId(readActive());
  }, []);

  const setKitchenId = useCallback((id: string) => {
    setActiveId(id);
    try {
      window.localStorage.setItem(ACTIVE_KITCHEN_KEY, id);
    } catch {
      /* storage unavailable */
    }
  }, []);

  const kitchen = kitchens.find((k) => k.id === activeId) ?? kitchens[0];
  const value = useMemo(
    () => ({ profile, kitchens, kitchen, kitchenId: kitchen.id, setKitchenId }),
    [profile, kitchens, kitchen, setKitchenId],
  );
  return <KitchenContext.Provider value={value}>{children}</KitchenContext.Provider>;
}

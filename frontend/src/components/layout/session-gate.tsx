"use client";

import { usePathname, useRouter } from "next/navigation";
import React, { useEffect } from "react";
import { ErrorState, PageLoader } from "@/components/shared/states";
import { KitchenProvider, useKitchensQuery, useProfileQuery } from "@/hooks/use-kitchen";
import { useAppAuth } from "@/lib/auth";

/**
 * Client-side navigation guard: requires sign-in, loads profile + kitchens, and sends first-time users to onboarding.
 * (The API enforces auth independently; this only shapes navigation.)
 */
export function SessionGate({ children, requireOnboarded = true }: { children: React.ReactNode; requireOnboarded?: boolean }) {
  const { isLoaded, isSignedIn } = useAppAuth();
  const router = useRouter();
  const pathname = usePathname();
  const profile = useProfileQuery(isLoaded && isSignedIn);
  const kitchens = useKitchensQuery(isLoaded && isSignedIn);

  useEffect(() => {
    if (isLoaded && !isSignedIn) router.replace(`/sign-in?redirect=${encodeURIComponent(pathname)}`);
  }, [isLoaded, isSignedIn, router, pathname]);

  useEffect(() => {
    if (requireOnboarded && profile.data && !profile.data.onboardingCompleted) router.replace("/onboarding");
  }, [requireOnboarded, profile.data, router]);

  if (!isLoaded || !isSignedIn || profile.isPending || kitchens.isPending) return <PageLoader />;
  if (profile.error || kitchens.error) {
    return (
      <div className="mx-auto max-w-lg p-6">
        <ErrorState
          error={profile.error ?? kitchens.error}
          onRetry={() => {
            void profile.refetch();
            void kitchens.refetch();
          }}
        />
      </div>
    );
  }
  if (requireOnboarded && !profile.data.onboardingCompleted) return <PageLoader />;
  if (!kitchens.data.length) return <PageLoader />;

  return (
    <KitchenProvider profile={profile.data} kitchens={kitchens.data}>
      {children}
    </KitchenProvider>
  );
}

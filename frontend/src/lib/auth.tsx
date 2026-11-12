"use client";

import { ClerkProvider, useAuth } from "@clerk/nextjs";
import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { configureApiAuth } from "@/services/api-client";
import { usersApi } from "@/services/endpoints";
import { AUTH_MODE, CLERK_PUBLISHABLE_KEY } from "@/lib/env";
import { DEV_TOKEN_KEY } from "@/lib/constants";

interface AuthContextValue {
  mode: "clerk" | "dev";
  isLoaded: boolean;
  isSignedIn: boolean;
  signOut: () => Promise<void>;
  /** Local development sign-in (only in dev mode). */
  devSignIn: (email: string, firstName?: string) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function useAppAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAppAuth must be used inside <AuthProvider>");
  return ctx;
}

function redirectToSignIn() {
  if (typeof window !== "undefined" && !window.location.pathname.startsWith("/sign-in")) {
    const next = encodeURIComponent(window.location.pathname + window.location.search);
    window.location.assign(`/sign-in?redirect=${next}`);
  }
}

// ---------------------------------------------------------------- Clerk
function ClerkBridge({ children }: { children: React.ReactNode }) {
  const { isLoaded, isSignedIn, getToken, signOut } = useAuth();
  configureApiAuth(
    async () => (isSignedIn ? await getToken() : null),
    () => {
      void signOut().finally(redirectToSignIn);
    },
  );
  const value = useMemo<AuthContextValue>(
    () => ({
      mode: "clerk",
      isLoaded,
      isSignedIn: !!isSignedIn,
      signOut: async () => {
        await signOut();
      },
      devSignIn: async () => {
        throw new Error("Dev sign-in is disabled when Clerk is configured.");
      },
    }),
    [isLoaded, isSignedIn, signOut],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// ---------------------------------------------------------------- Dev (no Clerk keys configured)
interface StoredToken {
  token: string;
  expiresAt: string;
}

function readToken(): StoredToken | null {
  try {
    const raw = window.localStorage.getItem(DEV_TOKEN_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredToken;
    return new Date(parsed.expiresAt).getTime() > Date.now() ? parsed : null;
  } catch {
    return null;
  }
}

function DevAuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<StoredToken | null>(null);
  const [isLoaded, setLoaded] = useState(false);

  useEffect(() => {
    setToken(readToken());
    setLoaded(true);
  }, []);

  const clear = useCallback(() => {
    try {
      window.localStorage.removeItem(DEV_TOKEN_KEY);
    } catch {
      /* storage unavailable */
    }
    setToken(null);
  }, []);

  configureApiAuth(
    async () => token?.token ?? readToken()?.token ?? null,
    () => {
      clear();
      redirectToSignIn();
    },
  );

  const devSignIn = useCallback(async (email: string, firstName?: string) => {
    configureApiAuth(async () => null, () => {});
    const result = await usersApi.devToken({ email, firstName: firstName || undefined });
    try {
      window.localStorage.setItem(DEV_TOKEN_KEY, JSON.stringify(result));
    } catch {
      /* private mode: token lives in memory for this tab */
    }
    setToken(result);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ mode: "dev", isLoaded, isSignedIn: !!token, signOut: async () => clear(), devSignIn }),
    [isLoaded, token, clear, devSignIn],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  if (AUTH_MODE === "clerk") {
    return (
      <ClerkProvider
        publishableKey={CLERK_PUBLISHABLE_KEY}
        signInUrl="/sign-in"
        signUpUrl="/sign-up"
        signInFallbackRedirectUrl="/dashboard"
        signUpFallbackRedirectUrl="/onboarding"
        appearance={{ variables: { colorPrimary: "#668568", borderRadius: "10px" } }}
      >
        <ClerkBridge>{children}</ClerkBridge>
      </ClerkProvider>
    );
  }
  return <DevAuthProvider>{children}</DevAuthProvider>;
}

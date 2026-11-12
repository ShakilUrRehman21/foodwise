"use client";

import { SignIn, SignUp } from "@clerk/nextjs";
import { Leaf } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { useAppAuth } from "@/lib/auth";
import { errorMessage } from "@/lib/query";

function DevForm({ variant }: { variant: "sign-in" | "sign-up" }) {
  const { devSignIn, isSignedIn, isLoaded } = useAppAuth();
  const router = useRouter();
  const params = useSearchParams();
  const [email, setEmail] = useState("");
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const target = params.get("redirect") || "/dashboard";

  useEffect(() => {
    if (isLoaded && isSignedIn) router.replace(target.startsWith("/") ? target : "/dashboard");
  }, [isLoaded, isSignedIn, router, target]);

  return (
    <Card className="w-full max-w-sm p-6">
      <h1 className="text-xl font-bold text-[#26372C]">{variant === "sign-in" ? "Welcome back" : "Create your kitchen"}</h1>
      <p className="mb-5 mt-1 text-sm text-[#788278]">Local development sign-in (Clerk is not configured).</p>
      <form
        className="space-y-3"
        onSubmit={async (e) => {
          e.preventDefault();
          setError(null);
          setPending(true);
          try {
            await devSignIn(email.trim(), name.trim() || undefined);
          } catch (err) {
            setError(errorMessage(err));
          } finally {
            setPending(false);
          }
        }}
      >
        <Field label="Email" required>
          <Input type="email" required autoFocus value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
        </Field>
        <Field label="First name">
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Rohan" />
        </Field>
        {error && <p className="text-sm text-[#C04334]">{error}</p>}
        <Button type="submit" className="w-full" disabled={pending}>
          {pending ? "Signing in…" : "Continue"}
        </Button>
      </form>
    </Card>
  );
}

export function AuthScreen({ variant }: { variant: "sign-in" | "sign-up" }) {
  const { mode } = useAppAuth();
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 bg-[#F8F7F2] p-4">
      <Link href="/" className="flex items-center gap-2">
        <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#668568] text-white">
          <Leaf className="h-5 w-5" />
        </span>
        <span className="text-lg font-bold text-[#26372C]">FoodWise</span>
      </Link>
      {mode === "clerk" ? (
        variant === "sign-in" ? <SignIn /> : <SignUp />
      ) : (
        <Suspense>
          <DevForm variant={variant} />
        </Suspense>
      )}
      <p className="text-sm text-[#788278]">
        {variant === "sign-in" ? "New here? " : "Already have an account? "}
        <Link className="font-semibold text-[#668568] hover:underline" href={variant === "sign-in" ? "/sign-up" : "/sign-in"}>
          {variant === "sign-in" ? "Create an account" : "Sign in"}
        </Link>
      </p>
    </div>
  );
}

"use client";

import { Button } from "@/components/ui/button";

export default function AppError({ error, reset }: { error: Error; reset: () => void }) {
  return (
    <div className="mx-auto max-w-md py-16 text-center">
      <h2 className="text-xl font-bold text-[#26372C]">Something went wrong</h2>
      <p className="mt-2 text-sm text-[#788278]">{error.message || "This screen failed to load."}</p>
      <Button className="mt-5" onClick={reset}>
        Try again
      </Button>
    </div>
  );
}

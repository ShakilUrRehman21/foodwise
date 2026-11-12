"use client";

import { QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "sonner";
import React, { useState } from "react";
import { AuthProvider } from "@/lib/auth";
import { createQueryClient } from "@/lib/query";

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(createQueryClient);
  return (
    <AuthProvider>
      <QueryClientProvider client={queryClient}>
        {children}
        <Toaster position="top-center" richColors closeButton toastOptions={{ style: { fontFamily: "inherit" } }} />
      </QueryClientProvider>
    </AuthProvider>
  );
}

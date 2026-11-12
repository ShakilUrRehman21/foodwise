import { AppShell } from "@/components/layout/app-shell";
import { SessionGate } from "@/components/layout/session-gate";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <SessionGate>
      <AppShell>{children}</AppShell>
    </SessionGate>
  );
}

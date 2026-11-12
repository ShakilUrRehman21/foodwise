import { StatusBadge } from "@/components/ui/badge";
import { expiryLabel } from "@/lib/format";
import type { ExpiryStatus } from "@/services/types";

export function ExpiryBadge({ status, days, className }: { status: ExpiryStatus; days: number; className?: string }) {
  return <StatusBadge status={status} label={expiryLabel(status, days)} className={className} />;
}

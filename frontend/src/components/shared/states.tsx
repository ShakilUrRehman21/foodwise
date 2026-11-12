import { AlertCircle, Loader2, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { errorMessage } from "@/lib/query";
import { cn } from "@/lib/utils";

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn("h-5 w-5 animate-spin text-[#668568]", className)} aria-label="Loading" />;
}

export function PageLoader() {
  return (
    <div className="flex min-h-[50vh] items-center justify-center">
      <Spinner className="h-7 w-7" />
    </div>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn("animate-pulse rounded-xl bg-[#ECE9E1]", className)} />;
}

export function ListSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div className="space-y-3">
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} className="h-20 w-full" />
      ))}
    </div>
  );
}

export function ErrorState({ error, onRetry, className }: { error: unknown; onRetry?: () => void; className?: string }) {
  return (
    <div className={cn("flex items-center justify-between gap-3 rounded-xl border border-[#F8D8D3] bg-[#FCEDE8] px-4 py-3", className)}>
      <div className="flex items-center gap-2 text-sm text-[#C04334]">
        <AlertCircle className="h-4 w-4 shrink-0" />
        <span>{errorMessage(error) || "Could not load this. Please retry."}</span>
      </div>
      {onRetry && (
        <Button size="sm" variant="secondary" onClick={onRetry}>
          <RefreshCw className="h-3.5 w-3.5" /> Retry
        </Button>
      )}
    </div>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: React.ReactNode }) {
  return (
    <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-[1.625rem] font-bold leading-tight tracking-tight text-[#26372C]">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-[#788278]">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

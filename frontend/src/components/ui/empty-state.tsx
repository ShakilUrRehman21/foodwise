import React from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils";

interface EmptyStateProps {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  description: string;
  actionLabel?: string;
  actionHref?: string;
  onAction?: () => void;
  secondaryLabel?: string;
  onSecondaryAction?: () => void;
  tips?: string[];
  className?: string;
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  actionLabel,
  onAction,
  secondaryLabel,
  onSecondaryAction,
  tips,
  className,
}: EmptyStateProps) {
  return (
    <Card className={cn("p-8 sm:p-12 text-center max-w-2xl mx-auto border-dashed border-2 border-[#E6E3DA] bg-white/70 backdrop-blur-xs", className)}>
      <div className="mx-auto h-16 w-16 sm:h-20 sm:w-20 rounded-3xl bg-[#EEF3EE] border border-[#D5E8D8] flex items-center justify-center text-[#668568] shadow-xs mb-5">
        <Icon className="h-8 w-8 sm:h-10 sm:w-10 stroke-[1.75]" />
      </div>

      <h3 className="text-xl sm:text-2xl font-bold text-[#26372C] tracking-tight">{title}</h3>
      <p className="mt-2 text-sm sm:text-base text-[#788278] max-w-md mx-auto leading-relaxed">
        {description}
      </p>

      {(actionLabel || secondaryLabel) && (
        <div className="mt-6 flex flex-wrap items-center justify-center gap-3">
          {actionLabel && (
            <Button onClick={onAction} className="h-11 px-5 font-semibold">
              {actionLabel}
            </Button>
          )}
          {secondaryLabel && (
            <Button variant="secondary" onClick={onSecondaryAction} className="h-11 px-5">
              {secondaryLabel}
            </Button>
          )}
        </div>
      )}

      {tips && tips.length > 0 && (
        <div className="mt-8 pt-6 border-t border-[#F1EFEA] text-left">
          <p className="text-xs font-semibold uppercase tracking-wider text-[#668568] mb-2.5">
            💡 Kitchen Wisdom:
          </p>
          <ul className="space-y-1.5 text-xs text-[#788278]">
            {tips.map((tip, index) => (
              <li key={index} className="flex items-start gap-2">
                <span className="text-[#668568] font-bold">•</span>
                <span>{tip}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}


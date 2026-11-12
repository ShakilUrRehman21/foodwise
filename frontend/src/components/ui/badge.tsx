import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-semibold tracking-wide uppercase transition-colors",
  {
    variants: {
      variant: {
        default:
          "bg-[#EEF3EE] text-[#26372C] border border-[#E6E3DA]",
        primary:
          "bg-[#668568] text-white",
        secondary:
          "bg-[#F1EFEA] text-[#788278] border border-[#E6E3DA]",
        outline:
          "border border-[#E6E3DA] text-[#26372C] bg-white",
        fresh:
          "bg-[#EAF3EC] text-[#2D6B3C] border border-[#D5E8D8]",
        warning:
          "bg-[#FDF4E7] text-[#B47823] border border-[#F9E5C9]",
        urgent:
          "bg-[#FCEDE8] text-[#C04334] border border-[#F8D8D3]",
        expired:
          "bg-[#F2F1ED] text-[#8C8A84] border border-[#E6E3DA]",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {
  dot?: boolean;
  pulsate?: boolean;
}

function Badge({ className, variant, dot, pulsate, children, ...props }: BadgeProps) {
  return (
    <div className={cn(badgeVariants({ variant }), className)} {...props}>
      {dot && (
        <span
          className={cn(
            "h-1.5 w-1.5 rounded-full",
            variant === "fresh" && "bg-[#2D6B3C]",
            variant === "warning" && "bg-[#B47823]",
            variant === "urgent" && "bg-[#C04334]",
            variant === "expired" && "bg-[#8C8A84]",
            (!variant || variant === "default" || variant === "secondary") && "bg-[#788278]",
            variant === "primary" && "bg-white",
            pulsate && "animate-pulse"
          )}
        />
      )}
      {children}
    </div>
  );
}

export interface StatusBadgeProps {
  status: "fresh" | "use_soon" | "expiring_today" | "expired";
  label?: string;
  className?: string;
}

function StatusBadge({ status, label, className }: StatusBadgeProps) {
  switch (status) {
    case "fresh":
      return (
        <Badge variant="fresh" dot className={className}>
          {label ?? "Fresh"}
        </Badge>
      );
    case "use_soon":
      return (
        <Badge variant="warning" dot className={className}>
          {label ?? "Use Soon"}
        </Badge>
      );
    case "expiring_today":
      return (
        <Badge variant="urgent" dot pulsate className={className}>
          {label ?? "Expires Today"}
        </Badge>
      );
    case "expired":
      return (
        <Badge variant="expired" dot className={className}>
          {label ?? "Overdue"}
        </Badge>
      );
  }
}

export { Badge, StatusBadge, badgeVariants };


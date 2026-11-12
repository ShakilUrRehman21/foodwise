import * as React from "react";
import { cn } from "@/lib/utils";

export function Label({ className, ...props }: React.LabelHTMLAttributes<HTMLLabelElement>) {
  return <label className={cn("text-xs font-semibold text-[#26372C]", className)} {...props} />;
}

export function Field({
  label,
  hint,
  error,
  required,
  children,
  className,
  htmlFor,
}: {
  label: string;
  hint?: React.ReactNode;
  error?: string;
  required?: boolean;
  children: React.ReactNode;
  className?: string;
  htmlFor?: string;
}) {
  return (
    <div className={cn("space-y-1.5", className)}>
      <Label htmlFor={htmlFor}>
        {label}
        {required && <span className="text-[#C04334]"> *</span>}
      </Label>
      {children}
      {error ? (
        <p className="text-xs text-[#C04334]" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p className="text-xs text-[#788278]">{hint}</p>
      ) : null}
    </div>
  );
}

export const Select = React.forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>(
  ({ className, ...props }, ref) => (
    <select
      ref={ref}
      className={cn(
        "flex h-11 w-full rounded-[10px] border border-[#E6E3DA] bg-white px-3 text-sm text-[#26372C] shadow-sm focus-visible:outline-none focus-visible:border-[#668568] focus-visible:ring-2 focus-visible:ring-[#668568]/20 disabled:opacity-50",
        className,
      )}
      {...props}
    />
  ),
);
Select.displayName = "Select";

export const Textarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) => (
    <textarea
      ref={ref}
      className={cn(
        "flex min-h-20 w-full rounded-[10px] border border-[#E6E3DA] bg-white px-3.5 py-2.5 text-sm text-[#26372C] shadow-sm placeholder:text-[#788278] focus-visible:outline-none focus-visible:border-[#668568] focus-visible:ring-2 focus-visible:ring-[#668568]/20",
        className,
      )}
      {...props}
    />
  ),
);
Textarea.displayName = "Textarea";

/** Segmented single-choice control (e.g. Fridge / Freezer / Pantry). */
export function Segmented<T extends string>({
  value,
  onChange,
  options,
  className,
  size = "md",
}: {
  value: T;
  onChange: (v: T) => void;
  options: { value: T; label: React.ReactNode }[];
  className?: string;
  size?: "sm" | "md";
}) {
  return (
    <div role="radiogroup" className={cn("inline-flex w-full rounded-full bg-[#F1EFEA] p-1 border border-[#E6E3DA]", className)}>
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="radio"
          aria-checked={value === o.value}
          onClick={() => onChange(o.value)}
          className={cn(
            "flex-1 rounded-full font-semibold transition-colors whitespace-nowrap",
            size === "sm" ? "px-2.5 py-1 text-xs" : "px-3 py-2 text-sm",
            value === o.value ? "bg-white text-[#26372C] shadow-sm" : "text-[#788278] hover:text-[#26372C]",
          )}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

export function Chip({
  active,
  className,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { active?: boolean }) {
  return (
    <button
      type="button"
      className={cn(
        "inline-flex items-center gap-1 rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors whitespace-nowrap",
        active ? "bg-[#668568] border-[#556F57] text-white" : "bg-white border-[#E6E3DA] text-[#26372C] hover:bg-[#EEF3EE]",
        className,
      )}
      {...props}
    />
  );
}

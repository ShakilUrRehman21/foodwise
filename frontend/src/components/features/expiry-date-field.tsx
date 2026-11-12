"use client";

import { Badge } from "@/components/ui/badge";
import { Chip, Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { addDays, isoDate } from "@/lib/format";

const QUICK = [
  { label: "Today", days: 0 },
  { label: "Tomorrow", days: 1 },
  { label: "+3 days", days: 3 },
  { label: "+1 week", days: 7 },
  { label: "+1 month", days: 30 },
  { label: "+6 months", days: 182 },
];

/**
 * Expiry date input. FoodWise never invents dates (PRD FR-EXP-001): the field starts empty unless validated
 * catalog guidance produced an estimate, which is labelled "Estimated". Quick chips are explicit user choices.
 */
export function ExpiryDateField({
  value,
  onChange,
  estimated,
  error,
  from,
  label = "Expiry date",
  quick = QUICK,
}: {
  value: string;
  onChange: (value: string, fromEstimate?: boolean) => void;
  estimated?: boolean;
  error?: string;
  from?: string;
  label?: string;
  quick?: { label: string; days: number }[];
}) {
  const base = from ?? isoDate();
  return (
    <Field
      label={label}
      required
      error={error}
      hint={estimated ? "Estimated from approved storage guidance — adjust if the pack says otherwise." : "Check the pack or use your best judgement."}
    >
      <div className="flex items-center gap-2">
        <Input type="date" value={value} min={base} onChange={(e) => onChange(e.target.value)} aria-invalid={!!error} />
        {estimated && value && <Badge variant="warning">Estimated</Badge>}
      </div>
      <div className="flex flex-wrap gap-1.5 pt-1">
        {quick.map((q) => (
          <Chip key={q.label} active={value === addDays(base, q.days)} onClick={() => onChange(addDays(base, q.days))}>
            {q.label}
          </Chip>
        ))}
      </div>
    </Field>
  );
}

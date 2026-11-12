"use client";

import { AlertTriangle, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Select } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { INVENTORY_UNITS, LOCATIONS } from "@/lib/constants";
import { addDays, isoDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { InventoryUnit, StorageLocation } from "@/services/types";

export interface ReviewRow {
  key: string;
  itemName: string;
  catalogItemId: string | null;
  categoryId: string | null;
  quantity: string;
  unit: InventoryUnit;
  storageLocation: StorageLocation;
  expiryDate: string;
  expiryEstimated: boolean;
  priceInr: string;
  lowConfidence: boolean;
  rawText: string;
}

/** Editable review rows (FR-SCAN-004). Low-confidence rows are highlighted and every row needs an expiry date. */
export function ReviewTable({
  rows,
  onChange,
  errors,
}: {
  rows: ReviewRow[];
  onChange: (rows: ReviewRow[]) => void;
  errors: Record<string, string>;
}) {
  const update = (key: string, patch: Partial<ReviewRow>) => onChange(rows.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  return (
    <div className="space-y-2">
      {rows.map((r, i) => (
        <Card key={r.key} className={cn("space-y-3 p-3.5", r.lowConfidence && "border-[#F9E5C9] bg-[#FFFBF4]")}>
          <div className="flex items-start gap-2">
            <div className="flex-1 space-y-1">
              <Input value={r.itemName} aria-label="Item name" onChange={(e) => update(r.key, { itemName: e.target.value, catalogItemId: null })} />
              <p className="flex items-center gap-1.5 truncate text-[11px] text-[#788278]">
                {r.lowConfidence && <AlertTriangle className="h-3 w-3 text-[#B47823]" />}
                {r.lowConfidence ? "Please check — " : ""}read as “{r.rawText}”
              </p>
              {errors[`items[${i}].name`] && <p className="text-xs text-[#C04334]">{errors[`items[${i}].name`]}</p>}
            </div>
            <button
              type="button"
              aria-label={`Remove ${r.itemName}`}
              className="rounded-full p-2 text-[#788278] hover:bg-[#FCEDE8] hover:text-[#C04334]"
              onClick={() => onChange(rows.filter((x) => x.key !== r.key))}
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-[5rem_5.5rem_7rem_1fr_6rem]">
            <Input type="number" min="0" step="any" aria-label="Quantity" value={r.quantity} onChange={(e) => update(r.key, { quantity: e.target.value })} />
            <Select aria-label="Unit" value={r.unit} onChange={(e) => update(r.key, { unit: e.target.value as InventoryUnit })}>
              {INVENTORY_UNITS.map((u) => (
                <option key={u}>{u}</option>
              ))}
            </Select>
            <Select aria-label="Location" value={r.storageLocation} onChange={(e) => update(r.key, { storageLocation: e.target.value as StorageLocation })}>
              {LOCATIONS.map((l) => (
                <option key={l.value} value={l.value}>
                  {l.label}
                </option>
              ))}
            </Select>
            <div className="col-span-2 sm:col-span-1">
              <div className="flex items-center gap-1">
                <Input
                  type="date"
                  aria-label="Expiry date"
                  min={isoDate()}
                  value={r.expiryDate}
                  aria-invalid={!!errors[`items[${i}].expiryDate`]}
                  className={cn(!r.expiryDate && "border-[#E9AC55]")}
                  onChange={(e) => update(r.key, { expiryDate: e.target.value, expiryEstimated: false })}
                />
                {r.expiryEstimated && <Badge variant="warning">Est.</Badge>}
              </div>
              <div className="mt-1 flex gap-1">
                {[3, 7, 30].map((d) => (
                  <button key={d} type="button" className="rounded-full bg-[#F1EFEA] px-2 py-0.5 text-[11px] font-semibold text-[#26372C] hover:bg-[#EEF3EE]" onClick={() => update(r.key, { expiryDate: addDays(isoDate(), d), expiryEstimated: false })}>
                    +{d}d
                  </button>
                ))}
              </div>
              {errors[`items[${i}].expiryDate`] && <p className="text-xs text-[#C04334]">{errors[`items[${i}].expiryDate`]}</p>}
            </div>
            <Input type="number" min="0" step="any" aria-label="Price in rupees" placeholder="₹" value={r.priceInr} onChange={(e) => update(r.key, { priceInr: e.target.value })} />
          </div>
        </Card>
      ))}
    </div>
  );
}

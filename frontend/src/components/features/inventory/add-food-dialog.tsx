"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Field, Segmented, Select } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { CatalogCombobox } from "@/components/features/catalog-combobox";
import { ExpiryDateField } from "@/components/features/expiry-date-field";
import { useKitchenMutation } from "@/hooks/use-kitchen-mutation";
import { INVENTORY_UNITS, LOCATIONS } from "@/lib/constants";
import { addDays, isoDate } from "@/lib/format";
import { ApiError } from "@/services/api-client";
import { inventoryApi, type CreateBatchInput } from "@/services/endpoints";
import type { CatalogItem, InventoryUnit, StorageLocation } from "@/services/types";

const DRAFT_KEY = "foodwise.addFoodDraft";

interface FormState {
  name: string;
  item: CatalogItem | null;
  location: StorageLocation;
  quantity: string;
  unit: InventoryUnit;
  expiryDate: string;
  estimated: boolean;
  costInr: string;
  purchaseDate: string;
}

const empty = (): FormState => ({
  name: "",
  item: null,
  location: "fridge",
  quantity: "1",
  unit: "pcs",
  expiryDate: "",
  estimated: false,
  costInr: "",
  purchaseDate: isoDate(),
});

function shelfLife(item: CatalogItem | null, loc: StorageLocation): number | null {
  if (!item) return null;
  return loc === "fridge" ? item.shelfLifeFridgeDays : loc === "freezer" ? item.shelfLifeFreezerDays : item.shelfLifeRoomTempDays;
}

/** Manual single-item entry (UI Screen 6). Unsaved input survives reloads/offline via a local draft (NFR-REL-002). */
export function AddFoodDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const [f, setF] = useState<FormState>(empty);
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!open) return;
    try {
      const raw = window.localStorage.getItem(DRAFT_KEY);
      if (raw) setF({ ...empty(), ...(JSON.parse(raw) as FormState) });
    } catch {
      /* ignore */
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    try {
      window.localStorage.setItem(DRAFT_KEY, JSON.stringify(f));
    } catch {
      /* ignore */
    }
  }, [f, open]);

  const set = (patch: Partial<FormState>) => setF((prev) => ({ ...prev, ...patch }));

  const applyEstimate = (item: CatalogItem | null, loc: StorageLocation, prev: FormState): Partial<FormState> => {
    const days = shelfLife(item, loc);
    if (days !== null) return { expiryDate: addDays(prev.purchaseDate, days), estimated: true };
    // No validated guidance: keep a user-entered date, clear a previous estimate - never invent one.
    return prev.estimated ? { expiryDate: "", estimated: false } : {};
  };

  const create = useKitchenMutation((kid, body: CreateBatchInput) => inventoryApi.create(kid, body), { toastErrors: false });

  const submit = async (addAnother: boolean) => {
    const errs: Record<string, string> = {};
    if (!f.name.trim()) errs.name = "What did you buy?";
    const qty = Number(f.quantity);
    if (!(qty > 0)) errs.quantity = "Enter a quantity above 0.";
    if (!f.expiryDate) errs.expiryDate = "Please choose an expiry date.";
    setErrors(errs);
    if (Object.keys(errs).length) return;
    try {
      const batch = await create.mutateAsync({
        catalogItemId: f.item?.id ?? null,
        name: f.name.trim(),
        storageLocation: f.location,
        quantity: qty,
        unit: f.unit,
        purchaseDate: f.purchaseDate,
        expiryDate: f.expiryDate,
        costInr: f.costInr ? Number(f.costInr) : null,
      });
      toast.success(`${batch.name} added to ${batch.storageLocation}.`);
      try {
        window.localStorage.removeItem(DRAFT_KEY);
      } catch {
        /* ignore */
      }
      setF(empty());
      setErrors({});
      if (!addAnother) onOpenChange(false);
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.details).length) {
        setErrors(Object.fromEntries(Object.entries(e.details).map(([k, v]) => [k, v[0]])));
      } else {
        toast.error(e instanceof Error ? e.message : "Could not add the item.");
      }
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add food</DialogTitle>
          <DialogDescription>Log something you bought or already have.</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <Field label="Item" required error={errors.name} htmlFor="add-name">
            <CatalogCombobox
              id="add-name"
              autoFocus
              value={f.name}
              onTextChange={(name) => set({ name, item: f.item && f.item.nameEn === name ? f.item : null })}
              onSelect={(item) =>
                setF((prev) => ({
                  ...prev,
                  name: item.nameEn,
                  item,
                  location: item.defaultLocation,
                  unit: item.defaultUnit,
                  ...applyEstimate(item, item.defaultLocation, prev),
                }))
              }
            />
          </Field>
          <Field label="Where is it kept?">
            <Segmented
              value={f.location}
              onChange={(location) => setF((prev) => ({ ...prev, location, ...applyEstimate(prev.item, location, prev) }))}
              options={LOCATIONS.map((l) => ({ value: l.value, label: `${l.emoji} ${l.label}` }))}
            />
          </Field>
          <div className="grid grid-cols-[1fr_7rem] gap-3">
            <Field label="Quantity" required error={errors.quantity}>
              <Input type="number" inputMode="decimal" min="0" step="any" value={f.quantity} onChange={(e) => set({ quantity: e.target.value })} />
            </Field>
            <Field label="Unit" error={errors.unit}>
              <Select value={f.unit} onChange={(e) => set({ unit: e.target.value as InventoryUnit })}>
                {INVENTORY_UNITS.map((u) => (
                  <option key={u} value={u}>
                    {u}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <ExpiryDateField
            value={f.expiryDate}
            estimated={f.estimated}
            error={errors.expiryDate}
            from={f.purchaseDate}
            onChange={(expiryDate) => set({ expiryDate, estimated: false })}
          />
          <div className="grid grid-cols-2 gap-3">
            <Field label="Price (₹)" hint="Optional — powers spend & waste stats." error={errors.costInr}>
              <Input type="number" inputMode="decimal" min="0" step="any" placeholder="0" value={f.costInr} onChange={(e) => set({ costInr: e.target.value })} />
            </Field>
            <Field label="Bought on" error={errors.purchaseDate}>
              <Input type="date" max={isoDate()} value={f.purchaseDate} onChange={(e) => set({ purchaseDate: e.target.value })} />
            </Field>
          </div>
        </div>
        <DialogFooter className="gap-2">
          <Button variant="secondary" disabled={create.isPending} onClick={() => submit(true)}>
            Save &amp; add another
          </Button>
          <Button disabled={create.isPending} onClick={() => submit(false)}>
            {create.isPending ? "Saving…" : "Save item"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Chip, Field, Segmented, Select } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { ExpiryDateField } from "@/components/features/expiry-date-field";
import { useKitchenMutation } from "@/hooks/use-kitchen-mutation";
import { LOCATIONS, WASTE_REASONS } from "@/lib/constants";
import { formatInr, formatQty } from "@/lib/format";
import { inventoryApi } from "@/services/endpoints";
import type { Batch, StorageLocation, WasteReason } from "@/services/types";

/** "Use some" — partial consumption with quick fractions (FR-INV-004). */
export function UseDialog({ batch, onClose }: { batch: Batch | null; onClose: () => void }) {
  const [amount, setAmount] = useState("");
  const use = useKitchenMutation((kid, v: { id: string; delta: number }) => inventoryApi.use(kid, v.id, v.delta), {
    success: (b) => (b.status === "consumed" ? `${b.name} used up.` : `${b.name}: ${formatQty(b.currentQuantity, b.unit)} left.`),
    onSuccess: onClose,
  });
  const consume = useKitchenMutation((kid, id: string) => inventoryApi.consume(kid, id), {
    success: (b) => `${b.name} marked as used up.`,
    onSuccess: onClose,
  });
  if (!batch) return null;
  const q = Number(batch.currentQuantity);
  const fractions = [0.25, 0.5].map((f) => Math.round(q * f * 100) / 100).filter((v) => v > 0 && v < q);
  return (
    <Dialog open={!!batch} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Use {batch.name}</DialogTitle>
          <DialogDescription>You have {formatQty(batch.currentQuantity, batch.unit)}.</DialogDescription>
        </DialogHeader>
        <Field label={`How much did you use? (${batch.unit})`}>
          <Input type="number" inputMode="decimal" min="0" max={q} step="any" autoFocus value={amount} onChange={(e) => setAmount(e.target.value)} />
          <div className="flex flex-wrap gap-1.5 pt-1">
            {fractions.map((v) => (
              <Chip key={v} onClick={() => setAmount(String(v))}>
                {formatQty(v, batch.unit)}
              </Chip>
            ))}
            {batch.unit === "pcs" && q >= 1 && <Chip onClick={() => setAmount("1")}>1 pcs</Chip>}
          </div>
        </Field>
        <DialogFooter className="gap-2">
          <Button variant="secondary" disabled={consume.isPending} onClick={() => consume.mutate(batch.id)}>
            Used it all
          </Button>
          <Button
            disabled={!(Number(amount) > 0) || use.isPending}
            onClick={() => (Number(amount) >= q ? consume.mutate(batch.id) : use.mutate({ id: batch.id, delta: Number(amount) }))}
          >
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Discard & log waste with a reason; shows the rupee loss (FR-ANA-002). */
export function DiscardDialog({ batch, onClose }: { batch: Batch | null; onClose: () => void }) {
  const [reason, setReason] = useState<WasteReason | "">("");
  const discard = useKitchenMutation(
    (kid, v: { id: string; reason?: WasteReason }) => inventoryApi.discard(kid, v.id, v.reason),
    {
      success: (r) => (Number(r.estimatedLossInr) > 0 ? `Logged ${formatInr(r.estimatedLossInr)} of waste.` : "Logged as waste."),
      onSuccess: () => {
        setReason("");
        onClose();
      },
    },
  );
  if (!batch) return null;
  const defaultReason: WasteReason = batch.expiryStatus === "expired" ? "expired" : "spoiled";
  return (
    <Dialog open={!!batch} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Throw away {batch.name}?</DialogTitle>
          <DialogDescription>We&apos;ll log it as waste so you can see patterns over time.</DialogDescription>
        </DialogHeader>
        <Field label="Why?">
          <Select value={reason || defaultReason} onChange={(e) => setReason(e.target.value as WasteReason)}>
            {WASTE_REASONS.map((r) => (
              <option key={r.value} value={r.value}>
                {r.label}
              </option>
            ))}
          </Select>
        </Field>
        <DialogFooter className="gap-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="destructive" disabled={discard.isPending} onClick={() => discard.mutate({ id: batch.id, reason: reason || defaultReason })}>
            Discard &amp; log waste
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/**
 * Move between fridge / freezer / pantry. The expiry date is kept unless the user sets a new one
 * (or validated guidance exists server-side) - freezing never silently extends shelf life.
 */
export function MoveDialog({ batch, initial, onClose }: { batch: Batch | null; initial?: StorageLocation; onClose: () => void }) {
  const [location, setLocation] = useState<StorageLocation | null>(null);
  const [expiry, setExpiry] = useState("");
  const move = useKitchenMutation(
    (kid, v: { id: string; location: StorageLocation; expiryDate?: string }) => inventoryApi.move(kid, v.id, v.location, v.expiryDate),
    {
      success: (b) => `${b.name} moved to ${b.storageLocation}.`,
      onSuccess: () => {
        setLocation(null);
        setExpiry("");
        onClose();
      },
    },
  );
  if (!batch) return null;
  const target = location ?? initial ?? (batch.storageLocation === "freezer" ? "fridge" : "freezer");
  return (
    <Dialog open={!!batch} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Move {batch.name}</DialogTitle>
          <DialogDescription>Currently in the {batch.storageLocation}.</DialogDescription>
        </DialogHeader>
        <Segmented value={target} onChange={setLocation} options={LOCATIONS.map((l) => ({ value: l.value, label: `${l.emoji} ${l.label}` }))} />
        <div className="rounded-xl bg-[#F8F7F2] p-3">
          <ExpiryDateField
            label="New expiry date (optional)"
            value={expiry}
            onChange={setExpiry}
            quick={[
              { label: "+1 week", days: 7 },
              { label: "+1 month", days: 30 },
              { label: "+3 months", days: 91 },
            ]}
          />
          <p className="mt-2 text-xs text-[#788278]">Leave empty to keep the current date ({batch.expiryDate}).</p>
        </div>
        <DialogFooter className="gap-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button disabled={move.isPending || target === batch.storageLocation} onClick={() => move.mutate({ id: batch.id, location: target, expiryDate: expiry || undefined })}>
            Move to {target}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Edit name, expiry date or price. */
export function EditDialog({ batch, onClose }: { batch: Batch | null; onClose: () => void }) {
  const [name, setName] = useState<string | null>(null);
  const [expiry, setExpiry] = useState<string | null>(null);
  const [cost, setCost] = useState<string | null>(null);
  const save = useKitchenMutation(
    (kid, v: { id: string; body: { name?: string; expiryDate?: string; costInr?: number } }) => inventoryApi.update(kid, v.id, v.body),
    {
      success: "Saved.",
      onSuccess: () => {
        setName(null);
        setExpiry(null);
        setCost(null);
        onClose();
      },
    },
  );
  if (!batch) return null;
  return (
    <Dialog open={!!batch} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit {batch.name}</DialogTitle>
        </DialogHeader>
        <Field label="Name">
          <Input value={name ?? batch.name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <ExpiryDateField value={expiry ?? batch.expiryDate} from={batch.purchaseDate} onChange={setExpiry} estimated={expiry === null && batch.expiryEstimated} />
        <Field label="Price (₹)">
          <Input type="number" min="0" step="any" value={cost ?? (batch.costInr ?? "")} onChange={(e) => setCost(e.target.value)} />
        </Field>
        <DialogFooter className="gap-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            disabled={save.isPending}
            onClick={() =>
              save.mutate({
                id: batch.id,
                body: {
                  name: name ?? undefined,
                  expiryDate: expiry ?? undefined,
                  costInr: cost !== null && cost !== "" ? Number(cost) : undefined,
                },
              })
            }
          >
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

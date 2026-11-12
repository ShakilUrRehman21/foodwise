"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Camera, CheckCircle2, ImageUp, Plus, ReceiptText, ShoppingBasket } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { toast } from "sonner";
import { ReviewTable, type ReviewRow } from "@/components/features/scan/review-table";
import { ErrorState, PageHeader, Spinner } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Field, Segmented } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { useKitchen } from "@/hooks/use-kitchen";
import { formatInr } from "@/lib/format";
import { errorMessage } from "@/lib/query";
import { ApiError } from "@/services/api-client";
import { scanApi } from "@/services/endpoints";
import type { ScanSession } from "@/services/types";

type Mode = "receipt" | "grocery_haul";

function toRows(s: ScanSession): ReviewRow[] {
  return s.items.map((c, i) => ({
    key: `${s.id}-${i}`,
    itemName: c.itemName,
    catalogItemId: c.catalogItemId,
    categoryId: c.categoryId,
    quantity: String(c.quantity),
    unit: c.unit,
    storageLocation: c.suggestedLocation,
    expiryDate: c.expiryDate ?? "",
    expiryEstimated: c.expiryEstimated,
    priceInr: c.priceInr !== null ? String(c.priceInr) : "",
    lowConfidence: c.lowConfidence,
    rawText: c.rawText,
  }));
}

export default function ScanPage() {
  const { kitchenId } = useKitchen();
  const qc = useQueryClient();
  const router = useRouter();
  const fileRef = useRef<HTMLInputElement>(null);
  const [mode, setMode] = useState<Mode>("receipt");
  const [preview, setPreview] = useState<string | null>(null);
  const [session, setSession] = useState<ScanSession | null>(null);
  const [rows, setRows] = useState<ReviewRow[]>([]);
  const [store, setStore] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});

  const upload = useMutation({
    mutationFn: (file: File) => scanApi.upload(kitchenId, file, mode),
    onSuccess: (s) => {
      setSession(s);
      setRows(toRows(s));
      setStore(s.storeName ?? "");
      if (s.status === "pending" && !s.items.length) toast.info("We couldn't find any items. Try a clearer photo or add them manually.");
    },
  });

  const confirm = useMutation({
    mutationFn: () =>
      scanApi.confirm(kitchenId, session!.id, {
        storeName: store || null,
        purchaseDate: session!.purchaseDate,
        items: rows.map((r) => ({
          itemName: r.itemName.trim(),
          catalogItemId: r.catalogItemId,
          categoryId: r.categoryId,
          quantity: Number(r.quantity),
          unit: r.unit,
          storageLocation: r.storageLocation,
          expiryDate: r.expiryDate,
          priceInr: r.priceInr ? Number(r.priceInr) : null,
        })),
      }),
    onSuccess: async (res) => {
      await qc.invalidateQueries({ queryKey: ["k", kitchenId] });
      toast.success(`Added ${res.created.length} items to your kitchen.`);
      router.push("/inventory");
    },
    onError: (e) => {
      if (e instanceof ApiError && Object.keys(e.details).length) {
        setErrors(Object.fromEntries(Object.entries(e.details).map(([k, v]) => [k, v[0]])));
      }
      toast.error(errorMessage(e));
    },
  });

  const reset = () => {
    setSession(null);
    setRows([]);
    setPreview(null);
    setErrors({});
    upload.reset();
    if (fileRef.current) fileRef.current.value = "";
  };

  const onFile = (file: File | undefined) => {
    if (!file) return;
    if (file.size > 10 * 1024 * 1024) {
      toast.error("That photo is over 10 MB. Try a smaller one.");
      return;
    }
    setPreview(URL.createObjectURL(file));
    upload.mutate(file);
  };

  const submit = () => {
    const errs: Record<string, string> = {};
    rows.forEach((r, i) => {
      if (!r.itemName.trim()) errs[`items[${i}].name`] = "Name is required.";
      if (!r.expiryDate) errs[`items[${i}].expiryDate`] = "Set an expiry date.";
    });
    setErrors(errs);
    if (Object.keys(errs).length) {
      toast.error("Every item needs a name and an expiry date.");
      return;
    }
    confirm.mutate();
  };

  const total = rows.reduce((s, r) => s + (Number(r.priceInr) || 0), 0);

  return (
    <>
      <PageHeader title="Scan groceries" subtitle="Photograph a bill and review what we found before anything is added." />

      {!session && (
        <div className="mx-auto max-w-xl space-y-4">
          <Segmented
            value={mode}
            onChange={setMode}
            options={[
              { value: "receipt", label: "🧾 Receipt" },
              { value: "grocery_haul", label: "🧺 Grocery haul (beta)" },
            ]}
          />
          <Card className="flex flex-col items-center gap-4 border-2 border-dashed p-8 text-center">
            {upload.isPending ? (
              <>
                {preview && <img src={preview} alt="Your upload" className="max-h-56 rounded-xl object-contain opacity-70" />}
                <div className="flex items-center gap-2 text-sm text-[#26372C]">
                  <Spinner /> Reading your {mode === "receipt" ? "bill" : "photo"}…
                </div>
              </>
            ) : (
              <>
                <span className="flex h-16 w-16 items-center justify-center rounded-3xl bg-[#EEF3EE] text-[#668568]">
                  {mode === "receipt" ? <ReceiptText className="h-8 w-8" /> : <ShoppingBasket className="h-8 w-8" />}
                </span>
                <div>
                  <p className="font-semibold text-[#26372C]">{mode === "receipt" ? "Snap your grocery bill" : "Spread groceries on the counter"}</p>
                  <p className="mt-1 text-sm text-[#788278]">
                    {mode === "receipt" ? "Flat, well-lit, whole bill in frame." : "Experimental — always double-check the results."}
                  </p>
                </div>
                <input ref={fileRef} type="file" accept="image/jpeg,image/png" capture="environment" className="hidden" onChange={(e) => onFile(e.target.files?.[0])} />
                <div className="flex flex-wrap justify-center gap-2">
                  <Button onClick={() => fileRef.current?.click()}>
                    <Camera className="h-4 w-4" /> Take or choose photo
                  </Button>
                </div>
                <p className="text-xs text-[#788278]">Processed on FoodWise servers with local OCR. Photos are not shared with third parties.</p>
              </>
            )}
            {upload.error && <ErrorState error={upload.error} className="w-full" />}
          </Card>
          <p className="text-center text-sm text-[#788278]">
            No bill handy?{" "}
            <Link className="font-semibold text-[#668568] hover:underline" href="/inventory?add=1">
              Add items manually
            </Link>
          </p>
        </div>
      )}

      {session?.status === "failed" && (
        <Card className="mx-auto max-w-xl space-y-4 p-6 text-center">
          <ImageUp className="mx-auto h-10 w-10 text-[#B47823]" />
          <p className="font-semibold text-[#26372C]">We couldn&apos;t read that one</p>
          <p className="text-sm text-[#788278]">{session.errorMessage}</p>
          <div className="flex justify-center gap-2">
            <Button onClick={reset}>Try another photo</Button>
            <Button variant="secondary" asChild>
              <Link href="/inventory?add=1">Add manually</Link>
            </Button>
          </div>
        </Card>
      )}

      {session?.status === "pending" && (
        <div className="grid gap-6 lg:grid-cols-[18rem_1fr]">
          <div className="space-y-3">
            {preview && <img src={preview} alt="Scanned bill" className="w-full rounded-2xl border border-[#E6E3DA] object-contain lg:max-h-[70vh]" />}
            <Field label="Store">
              <Input value={store} placeholder="e.g. Blinkit" onChange={(e) => setStore(e.target.value)} />
            </Field>
            {session.detectedTotalInr !== null && <p className="text-xs text-[#788278]">Bill total read as {formatInr(session.detectedTotalInr, true)}</p>}
          </div>
          <div className="space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm text-[#26372C]">
                <strong>{rows.length}</strong> items found · {formatInr(total)}
              </p>
              <div className="flex items-center gap-2">
                {session.experimental && <Badge variant="warning">Experimental</Badge>}
                {session.lowConfidence && <Badge variant="warning">Low confidence — please check</Badge>}
              </div>
            </div>
            <ReviewTable rows={rows} onChange={setRows} errors={errors} />
            <Button
              variant="ghost"
              size="sm"
              onClick={() =>
                setRows([
                  ...rows,
                  { key: crypto.randomUUID(), itemName: "", catalogItemId: null, categoryId: null, quantity: "1", unit: "pcs", storageLocation: "fridge", expiryDate: "", expiryEstimated: false, priceInr: "", lowConfidence: false, rawText: "added by you" },
                ])
              }
            >
              <Plus className="h-4 w-4" /> Add a missed item
            </Button>
            <div className="sticky bottom-20 flex gap-2 rounded-2xl border border-[#E6E3DA] bg-white p-3 shadow-lg lg:bottom-4">
              <Button
                variant="secondary"
                onClick={() => {
                  void scanApi.reject(kitchenId, session.id).catch(() => undefined);
                  reset();
                }}
              >
                Discard scan
              </Button>
              <Button className="flex-1" disabled={!rows.length || confirm.isPending} onClick={submit}>
                <CheckCircle2 className="h-4 w-4" /> {confirm.isPending ? "Adding…" : `Add ${rows.length} items to kitchen`}
              </Button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

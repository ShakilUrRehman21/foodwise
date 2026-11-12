"use client";

import { useQuery } from "@tanstack/react-query";
import { Lightbulb, Plus, Soup } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { ExpiryDateField } from "@/components/features/expiry-date-field";
import { ExpiryBadge } from "@/components/shared/expiry-badge";
import { ErrorState, ListSkeleton, PageHeader } from "@/components/shared/states";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { Field, Segmented, Select } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { useKitchen } from "@/hooks/use-kitchen";
import { kq, useKitchenMutation } from "@/hooks/use-kitchen-mutation";
import { INVENTORY_UNITS, LEFTOVER_TYPES } from "@/lib/constants";
import { formatQty, relativeTime } from "@/lib/format";
import { leftoversApi } from "@/services/endpoints";
import type { InventoryUnit, Leftover, LeftoverType, RepurposeIdea, StorageLocation } from "@/services/types";

function LogDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const [dish, setDish] = useState("");
  const [type, setType] = useState<LeftoverType>("dal");
  const [qty, setQty] = useState("1");
  const [unit, setUnit] = useState<InventoryUnit>("pcs");
  const [expiry, setExpiry] = useState("");
  const [location, setLocation] = useState<StorageLocation>("fridge");
  const [note, setNote] = useState("");
  const [err, setErr] = useState<Record<string, string>>({});
  const log = useKitchenMutation((kid, _: void) =>
    leftoversApi.log(kid, { dishName: dish.trim(), leftoverType: type, quantity: Number(qty), unit, expiryDate: expiry, storageLocation: location, containerNote: note || undefined }),
    {
      success: "Leftover saved.",
      onSuccess: () => {
        setDish("");
        setExpiry("");
        setNote("");
        onOpenChange(false);
      },
    },
  );
  const submit = () => {
    const e: Record<string, string> = {};
    if (!dish.trim()) e.dish = "What did you cook?";
    if (!(Number(qty) > 0)) e.qty = "Enter an amount.";
    if (!expiry) e.expiry = "Choose when it should be eaten by.";
    setErr(e);
    if (!Object.keys(e).length) log.mutate();
  };
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Log a leftover</DialogTitle>
          <DialogDescription>Track cooked food so it gets eaten, not forgotten.</DialogDescription>
        </DialogHeader>
        <Field label="Dish" required error={err.dish}>
          <Input autoFocus placeholder="e.g. Dal tadka" value={dish} onChange={(e) => setDish(e.target.value)} />
        </Field>
        <Field label="Type" hint="Used to suggest ways to reuse it.">
          <Select value={type} onChange={(e) => setType(e.target.value as LeftoverType)}>
            {LEFTOVER_TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </Select>
        </Field>
        <div className="grid grid-cols-[1fr_7rem] gap-3">
          <Field label="Amount" required error={err.qty}>
            <Input type="number" min="0" step="any" value={qty} onChange={(e) => setQty(e.target.value)} />
          </Field>
          <Field label="Unit">
            <Select value={unit} onChange={(e) => setUnit(e.target.value as InventoryUnit)}>
              {INVENTORY_UNITS.map((u) => (
                <option key={u}>{u}</option>
              ))}
            </Select>
          </Field>
        </div>
        <Field label="Stored in">
          <Segmented value={location} onChange={setLocation} options={[{ value: "fridge", label: "❄️ Fridge" }, { value: "freezer", label: "🧊 Freezer" }]} />
        </Field>
        <ExpiryDateField
          label="Eat by"
          value={expiry}
          onChange={setExpiry}
          error={err.expiry}
          quick={[
            { label: "Today", days: 0 },
            { label: "Tomorrow", days: 1 },
            { label: "+2 days", days: 2 },
          ]}
        />
        <p className="text-xs text-[#788278]">FoodWise doesn&apos;t guess how long cooked food stays safe — use your judgement and follow food-safety guidance.</p>
        <Field label="Container note">
          <Input placeholder="e.g. steel dabba, top shelf" value={note} onChange={(e) => setNote(e.target.value)} />
        </Field>
        <DialogFooter>
          <Button disabled={log.isPending} onClick={submit}>
            Save leftover
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function IdeasDialog({ leftover, onClose }: { leftover: Leftover | null; onClose: () => void }) {
  const { kitchenId } = useKitchen();
  const { data } = useQuery({
    queryKey: kq(kitchenId, "ideas", leftover?.batch.id),
    queryFn: () => leftoversApi.ideas(kitchenId, leftover!.batch.id),
    enabled: !!leftover,
  });
  return (
    <Dialog open={!!leftover} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Ideas for {leftover?.batch.name}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          {data?.map((idea: RepurposeIdea) => (
            <Card key={idea.id} className="p-4">
              <p className="font-semibold text-[#26372C]">{idea.title}</p>
              <p className="mt-1 text-sm text-[#788278]">{idea.description}</p>
              <ol className="mt-2 list-decimal space-y-1 pl-5 text-sm text-[#26372C]">
                {idea.steps.map((s) => (
                  <li key={s}>{s}</li>
                ))}
              </ol>
              {idea.recipeSlug && (
                <Link href={`/recipes/${idea.recipeSlug}`} className="mt-2 inline-block text-sm font-semibold text-[#668568] hover:underline">
                  See full recipe →
                </Link>
              )}
            </Card>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  );
}

function LeftoversInner() {
  const { kitchenId } = useKitchen();
  const params = useSearchParams();
  const router = useRouter();
  const [logOpen, setLogOpen] = useState(false);
  const [ideas, setIdeas] = useState<Leftover | null>(null);
  const { data, isPending, error, refetch } = useQuery({ queryKey: kq(kitchenId, "leftovers"), queryFn: () => leftoversApi.list(kitchenId) });
  const eat = useKitchenMutation((kid, id: string) => leftoversApi.consume(kid, id), { success: "Marked as eaten." });
  const toss = useKitchenMutation((kid, id: string) => leftoversApi.discard(kid, id), { success: "Logged as waste." });

  useEffect(() => {
    if (params.get("log")) {
      setLogOpen(true);
      router.replace("/leftovers");
    }
  }, [params, router]);

  return (
    <>
      <PageHeader
        title="Leftovers"
        subtitle="Your dabbas in the fridge — eat them or turn them into something new."
        actions={
          <Button onClick={() => setLogOpen(true)}>
            <Plus className="h-4 w-4" /> Log leftover
          </Button>
        }
      />
      {isPending ? (
        <ListSkeleton />
      ) : error ? (
        <ErrorState error={error} onRetry={() => refetch()} />
      ) : !data.length ? (
        <EmptyState icon={Soup} title="No leftovers tracked" description="Cooked extra dal or rice? Log it so it gets eaten in time." actionLabel="Log leftover" onAction={() => setLogOpen(true)} />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {data.map((l) => (
            <Card key={l.batch.id} className="flex flex-col gap-3 p-4">
              <div>
                <p className="font-semibold text-[#26372C]">{l.batch.name}</p>
                <p className="text-xs text-[#788278]">
                  {formatQty(l.batch.currentQuantity, l.batch.unit)} · cooked {l.batch.cookedAt ? relativeTime(l.batch.cookedAt) : "—"}
                  {l.batch.leftoverNotes ? ` · ${l.batch.leftoverNotes}` : ""}
                </p>
              </div>
              <ExpiryBadge status={l.batch.expiryStatus} days={l.batch.daysUntilExpiry} />
              {l.topIdea && (
                <button type="button" onClick={() => setIdeas(l)} className="flex items-start gap-2 rounded-xl bg-[#FDF4E7] p-3 text-left text-sm text-[#26372C] hover:bg-[#F9E5C9]">
                  <Lightbulb className="mt-0.5 h-4 w-4 shrink-0 text-[#B47823]" />
                  <span>
                    Try <strong>{l.topIdea.title}</strong> — {l.topIdea.description}
                  </span>
                </button>
              )}
              <div className="mt-auto flex gap-2">
                <Button size="sm" className="flex-1" disabled={eat.isPending} onClick={() => eat.mutate(l.batch.id)}>
                  Eaten
                </Button>
                <Button size="sm" variant="secondary" onClick={() => setIdeas(l)}>
                  Ideas
                </Button>
                <Button size="sm" variant="destructive" disabled={toss.isPending} onClick={() => toss.mutate(l.batch.id)}>
                  Discard
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}
      <LogDialog open={logOpen} onOpenChange={setLogOpen} />
      <IdeasDialog leftover={ideas} onClose={() => setIdeas(null)} />
    </>
  );
}

export default function LeftoversPage() {
  return (
    <Suspense>
      <LeftoversInner />
    </Suspense>
  );
}

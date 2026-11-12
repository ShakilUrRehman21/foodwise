"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, CalendarPlus, Check, ChefHat, Clock, Heart, Minus, Plus, ShoppingCart, Timer, Users } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { ErrorState, ListSkeleton } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Field, Segmented, Select } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { useKitchen } from "@/hooks/use-kitchen";
import { kq, useKitchenMutation } from "@/hooks/use-kitchen-mutation";
import { MEAL_SLOTS } from "@/lib/constants";
import { formatQty, isoDate } from "@/lib/format";
import { mealPlanApi, recipesApi, shoppingApi } from "@/services/endpoints";
import type { CookResult, IngredientMatch, MealSlot } from "@/services/types";
import { cn } from "@/lib/utils";

function StepTimer({ text }: { text: string }) {
  const m = text.match(/(\d+)(?:\s*-\s*(\d+))?\s*min/i);
  const [left, setLeft] = useState<number | null>(null);
  useEffect(() => {
    if (left === null || left <= 0) return;
    const t = setTimeout(() => setLeft(left - 1), 1000);
    if (left === 1) toast.success("Timer done!");
    return () => clearTimeout(t);
  }, [left]);
  if (!m) return null;
  const minutes = Number(m[2] ?? m[1]);
  if (left !== null && left > 0) {
    return (
      <button type="button" onClick={() => setLeft(null)} className="mt-1 inline-flex items-center gap-1 rounded-full bg-[#FDF4E7] px-2.5 py-1 text-xs font-semibold text-[#B47823]">
        <Timer className="h-3 w-3" /> {Math.floor(left / 60)}:{String(left % 60).padStart(2, "0")} · stop
      </button>
    );
  }
  return (
    <button type="button" onClick={() => setLeft(minutes * 60)} className="mt-1 inline-flex items-center gap-1 rounded-full bg-[#EEF3EE] px-2.5 py-1 text-xs font-semibold text-[#668568]">
      <Timer className="h-3 w-3" /> Start {minutes} min timer
    </button>
  );
}

function IngredientLine({ ing }: { ing: IngredientMatch }) {
  const tone =
    ing.status === "available" ? "text-[#2D6B3C]" : ing.status === "insufficient" ? "text-[#B47823]" : ing.status === "missing" ? "text-[#C04334]" : "text-[#788278]";
  const icon = ing.status === "available" ? "✓" : ing.status === "insufficient" ? "◐" : ing.status === "missing" ? "✗" : "·";
  return (
    <li className="flex items-start justify-between gap-3 py-2 text-sm">
      <span className="flex gap-2">
        <span className={cn("w-4 font-bold", tone)}>{icon}</span>
        <span className="text-[#26372C]">
          {ing.name}
          {ing.optional && <span className="text-[#788278]"> (optional)</span>}
          {ing.expiryStatus && (ing.expiryStatus === "expiring_today" || ing.expiryStatus === "use_soon") && (
            <Badge variant="warning" className="ml-2">
              use soon
            </Badge>
          )}
        </span>
      </span>
      <span className="shrink-0 text-right text-[#788278]">
        {formatQty(ing.requiredQuantity, ing.unit)}
        {ing.status === "insufficient" && ing.availableQuantity !== null && <span className="block text-xs">have {formatQty(ing.availableQuantity, ing.unit)}</span>}
        {ing.pantryStaple && <span className="block text-xs">staple</span>}
      </span>
    </li>
  );
}

export default function RecipeDetailPage() {
  const { slug } = useParams<{ slug: string }>();
  const { kitchenId } = useKitchen();
  const [servings, setServings] = useState<number | null>(null);
  const [cookOpen, setCookOpen] = useState(false);
  const [planOpen, setPlanOpen] = useState(false);
  const [mode, setMode] = useState<"STRICT" | "AVAILABLE_ONLY">("STRICT");
  const [result, setResult] = useState<CookResult | null>(null);
  const [planDate, setPlanDate] = useState(isoDate());
  const [planSlot, setPlanSlot] = useState<MealSlot>("dinner");

  const { data, isPending, error, refetch } = useQuery({
    queryKey: kq(kitchenId, "recipe", slug, servings),
    queryFn: () => recipesApi.detail(kitchenId, slug, servings ?? undefined),
    placeholderData: (prev) => prev,
  });
  const serves = servings ?? data?.servingsRequested ?? 2;

  const fav = useKitchenMutation((kid, on: boolean) => recipesApi.favorite(kid, data!.summary.id, on));
  const cook = useKitchenMutation((kid, _: void) => recipesApi.cook(kid, data!.summary.id, { servings: serves, mode }), {
    success: "Inventory updated. Enjoy your meal!",
    onSuccess: (r) => {
      setCookOpen(false);
      setResult(r);
    },
  });
  const addMissing = useKitchenMutation((kid, ids: string[]) => shoppingApi.fromRecipe(kid, data!.summary.id, ids), {
    success: (r) => (r.added ? `Added ${r.added} item${r.added > 1 ? "s" : ""} to your shopping list.` : "Already on your shopping list."),
  });
  const addLow = useKitchenMutation((kid, ids: string[]) => shoppingApi.fromRecipe(kid, data!.summary.id, ids), {
    success: "Added to your shopping list.",
    onSuccess: () => setResult(null),
  });
  const plan = useKitchenMutation(
    (kid, _: void) => mealPlanApi.upsert(kid, { planDate, mealSlot: planSlot, recipeId: data!.summary.id }),
    { success: "Added to your meal plan.", onSuccess: () => setPlanOpen(false) },
  );

  if (isPending) return <ListSkeleton rows={5} />;
  if (error) return <ErrorState error={error} onRetry={() => refetch()} />;

  const s = data.summary;
  const missing = data.ingredients.filter((i) => !i.optional && (i.status === "missing" || i.status === "insufficient") && i.catalogItemId);
  const tracked = data.ingredients.filter((i) => !i.pantryStaple && i.catalogItemId && i.status !== "missing");

  return (
    <div className="mx-auto max-w-4xl space-y-5">
      <Link href="/recipes" className="inline-flex items-center gap-1 text-sm font-semibold text-[#668568] hover:underline">
        <ArrowLeft className="h-4 w-4" /> Recipes
      </Link>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-[1.75rem] font-bold leading-tight tracking-tight text-[#26372C]">{s.title}</h1>
          <p className="mt-1 text-sm text-[#788278]">{s.description}</p>
          <p className="mt-2 flex flex-wrap items-center gap-3 text-sm text-[#788278]">
            <span><Clock className="mr-1 inline h-4 w-4" />{s.prepTimeMinutes + s.cookTimeMinutes} min</span>
            <span className="capitalize">{s.difficulty}</span>
            <span className="capitalize">{s.dietaryType}</span>
            <span>{s.cuisine}</span>
          </p>
        </div>
        <Button variant="secondary" size="icon" aria-label={s.favorite ? "Remove from favourites" : "Save to favourites"} onClick={() => fav.mutate(!s.favorite)}>
          <Heart className={cn("h-5 w-5", s.favorite && "fill-[#D76F65] text-[#D76F65]")} />
        </Button>
      </div>

      <div className="grid gap-5 lg:grid-cols-[1fr_1.3fr]">
        <Card className="p-5">
          <div className="mb-2 flex items-center justify-between">
            <h2 className="font-semibold text-[#26372C]">Ingredients</h2>
            <div className="flex items-center gap-2">
              <Users className="h-4 w-4 text-[#788278]" />
              <Button variant="secondary" size="icon" className="h-8 w-8" aria-label="Fewer servings" disabled={serves <= 1} onClick={() => setServings(serves - 1)}>
                <Minus className="h-3.5 w-3.5" />
              </Button>
              <span className="w-6 text-center text-sm font-semibold">{serves}</span>
              <Button variant="secondary" size="icon" className="h-8 w-8" aria-label="More servings" disabled={serves >= 20} onClick={() => setServings(serves + 1)}>
                <Plus className="h-3.5 w-3.5" />
              </Button>
            </div>
          </div>
          <p className="mb-1 text-xs text-[#788278]">{s.matchPercent}% of the key ingredients are in your kitchen.</p>
          <ul className="divide-y divide-[#F1EFEA]">
            {data.ingredients.map((i) => (
              <IngredientLine key={i.name} ing={i} />
            ))}
          </ul>
          {missing.length > 0 && (
            <Button variant="secondary" className="mt-3 w-full" disabled={addMissing.isPending} onClick={() => addMissing.mutate(missing.map((m) => m.catalogItemId!))}>
              <ShoppingCart className="h-4 w-4" /> Add {missing.length} missing to shopping list
            </Button>
          )}
        </Card>

        <Card className="p-5">
          <h2 className="mb-3 font-semibold text-[#26372C]">Method</h2>
          <ol className="space-y-4">
            {data.instructions.map((step, i) => (
              <li key={i} className="flex gap-3">
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-[#EEF3EE] text-sm font-bold text-[#668568]">{i + 1}</span>
                <div className="text-sm leading-relaxed text-[#26372C]">
                  {step}
                  <div>
                    <StepTimer text={step} />
                  </div>
                </div>
              </li>
            ))}
          </ol>
        </Card>
      </div>

      <div className="sticky bottom-20 flex gap-2 rounded-2xl border border-[#E6E3DA] bg-white p-3 shadow-lg lg:bottom-4">
        <Button variant="secondary" onClick={() => setPlanOpen(true)}>
          <CalendarPlus className="h-4 w-4" /> Add to plan
        </Button>
        <Button className="flex-1" onClick={() => setCookOpen(true)}>
          <ChefHat className="h-4 w-4" /> I cooked this
        </Button>
      </div>

      <Dialog open={cookOpen} onOpenChange={setCookOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Cooked {s.title}?</DialogTitle>
            <DialogDescription>We&apos;ll deduct ingredients for {serves} serving{serves > 1 ? "s" : ""}, using what expires first.</DialogDescription>
          </DialogHeader>
          <ul className="max-h-56 space-y-1 overflow-y-auto rounded-xl bg-[#F8F7F2] p-3 text-sm">
            {tracked.length ? (
              tracked.map((i) => (
                <li key={i.name} className="flex justify-between">
                  <span>{i.name}</span>
                  <span className="text-[#788278]">−{formatQty(i.requiredQuantity, i.unit)}</span>
                </li>
              ))
            ) : (
              <li className="text-[#788278]">None of the tracked ingredients are in your inventory.</li>
            )}
          </ul>
          {missing.length > 0 && (
            <Field label="Some ingredients are short or missing">
              <Segmented
                size="sm"
                value={mode}
                onChange={setMode}
                options={[
                  { value: "STRICT", label: "Stop if short" },
                  { value: "AVAILABLE_ONLY", label: "Deduct what I have" },
                ]}
              />
            </Field>
          )}
          <DialogFooter className="gap-2">
            <Button variant="secondary" onClick={() => setCookOpen(false)}>
              Cancel
            </Button>
            <Button disabled={cook.isPending} onClick={() => cook.mutate()}>
              <Check className="h-4 w-4" /> Update inventory
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!result} onOpenChange={(o) => !o && setResult(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Inventory updated</DialogTitle>
            <DialogDescription>{result?.deductions.length ?? 0} item batches updated.</DialogDescription>
          </DialogHeader>
          {result && result.skipped.length > 0 && (
            <p className="text-xs text-[#788278]">Not deducted: {result.skipped.map((x) => `${x.ingredient} (${x.reason})`).join("; ")}</p>
          )}
          {result && result.lowStock.length > 0 ? (
            <>
              <p className="text-sm text-[#26372C]">You&apos;re running low on {result.lowStock.map((l) => l.name).join(", ")}. Add to your shopping list?</p>
              <DialogFooter className="gap-2">
                <Button variant="secondary" onClick={() => setResult(null)}>
                  Not now
                </Button>
                <Button onClick={() => addLow.mutate(result.lowStock.map((l) => l.catalogItemId))}>Add to list</Button>
              </DialogFooter>
            </>
          ) : (
            <DialogFooter>
              <Button onClick={() => setResult(null)}>Done</Button>
            </DialogFooter>
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={planOpen} onOpenChange={setPlanOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Plan {s.title}</DialogTitle>
          </DialogHeader>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Day">
              <Input type="date" min={isoDate()} value={planDate} onChange={(e) => setPlanDate(e.target.value)} />
            </Field>
            <Field label="Meal">
              <Select value={planSlot} onChange={(e) => setPlanSlot(e.target.value as MealSlot)}>
                {MEAL_SLOTS.map((m) => (
                  <option key={m.value} value={m.value}>
                    {m.label}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <DialogFooter>
            <Button disabled={plan.isPending} onClick={() => plan.mutate()}>
              Add to plan
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

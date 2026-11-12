"use client";

import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, X } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { ErrorState, ListSkeleton } from "@/components/shared/states";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { kq, useKitchenMutation } from "@/hooks/use-kitchen-mutation";
import { useKitchen } from "@/hooks/use-kitchen";
import { MEAL_SLOTS } from "@/lib/constants";
import { addDays, formatDate, isoDate } from "@/lib/format";
import { mealPlanApi } from "@/services/endpoints";
import type { MealSlot } from "@/services/types";

function monday(d = new Date()) {
  const x = new Date(d);
  x.setDate(x.getDate() - ((x.getDay() + 6) % 7));
  return isoDate(x);
}

export function MealPlanWeek() {
  const { kitchenId } = useKitchen();
  const [start, setStart] = useState(monday());
  const [adding, setAdding] = useState<{ date: string; slot: MealSlot } | null>(null);
  const [title, setTitle] = useState("");
  const { data, isPending, error, refetch } = useQuery({ queryKey: kq(kitchenId, "plan", start), queryFn: () => mealPlanApi.week(kitchenId, start) });
  const upsert = useKitchenMutation((kid, v: { planDate: string; mealSlot: MealSlot; customMealTitle: string }) => mealPlanApi.upsert(kid, v), {
    success: "Added to plan.",
    onSuccess: () => {
      setAdding(null);
      setTitle("");
    },
  });
  const remove = useKitchenMutation((kid, id: string) => mealPlanApi.remove(kid, id));
  const days = Array.from({ length: 7 }, (_, i) => addDays(start, i));
  const today = isoDate();

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <Button variant="ghost" size="sm" onClick={() => setStart(addDays(start, -7))}>
          <ChevronLeft className="h-4 w-4" /> Prev
        </Button>
        <p className="text-sm font-semibold text-[#26372C]">
          {formatDate(days[0])} – {formatDate(days[6])}
        </p>
        <Button variant="ghost" size="sm" onClick={() => setStart(addDays(start, 7))}>
          Next <ChevronRight className="h-4 w-4" />
        </Button>
      </div>
      <p className="text-xs text-[#788278]">Tip: open any recipe and use “Add to plan”, or type a quick meal below.</p>
      {isPending ? (
        <ListSkeleton rows={3} />
      ) : error ? (
        <ErrorState error={error} onRetry={() => refetch()} />
      ) : (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {days.map((d) => (
            <Card key={d} className={`p-4 ${d === today ? "border-[#668568]" : ""}`}>
              <p className="mb-2 text-sm font-semibold text-[#26372C]">
                {formatDate(d, { weekday: "short", day: "numeric", month: "short" })} {d === today && <span className="text-[#668568]">· Today</span>}
              </p>
              <ul className="space-y-1.5">
                {MEAL_SLOTS.map((s) => {
                  const p = data.find((x) => x.planDate === d && x.mealSlot === s.value);
                  const isAdding = adding?.date === d && adding.slot === s.value;
                  return (
                    <li key={s.value} className="flex items-center gap-2 text-sm">
                      <span className="w-20 shrink-0 text-xs text-[#788278]">{s.label}</span>
                      {p ? (
                        <span className="flex min-w-0 flex-1 items-center justify-between gap-1">
                          {p.recipeSlug ? (
                            <Link href={`/recipes/${p.recipeSlug}`} className="truncate font-medium text-[#26372C] hover:underline">
                              {p.title}
                            </Link>
                          ) : (
                            <span className="truncate font-medium text-[#26372C]">{p.title}</span>
                          )}
                          <span className="flex items-center gap-1">
                            {p.status === "cooked" && <span className="text-xs text-[#2D6B3C]">✓</span>}
                            <button type="button" aria-label="Remove" className="rounded p-0.5 text-[#788278] hover:text-[#C04334]" onClick={() => remove.mutate(p.id)}>
                              <X className="h-3.5 w-3.5" />
                            </button>
                          </span>
                        </span>
                      ) : isAdding ? (
                        <form
                          className="flex flex-1 gap-1"
                          onSubmit={(e) => {
                            e.preventDefault();
                            if (title.trim()) upsert.mutate({ planDate: d, mealSlot: s.value, customMealTitle: title.trim() });
                          }}
                        >
                          <Input autoFocus className="h-8 text-xs" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Dal chawal" />
                          <Button size="sm" className="h-8" type="submit">
                            Add
                          </Button>
                        </form>
                      ) : (
                        <button type="button" className="text-xs font-semibold text-[#668568] hover:underline" onClick={() => setAdding({ date: d, slot: s.value })}>
                          + add
                        </button>
                      )}
                    </li>
                  );
                })}
              </ul>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

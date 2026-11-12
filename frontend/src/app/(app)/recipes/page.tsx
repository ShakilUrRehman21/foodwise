"use client";

import { useQuery } from "@tanstack/react-query";
import { Search, UtensilsCrossed, X } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { MealPlanWeek } from "@/components/features/recipes/meal-plan-week";
import { RecipeCard } from "@/components/features/recipes/recipe-card";
import { ErrorState, ListSkeleton, PageHeader } from "@/components/shared/states";
import { EmptyState } from "@/components/ui/empty-state";
import { Chip, Segmented, Select } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { useKitchen } from "@/hooks/use-kitchen";
import { kq } from "@/hooks/use-kitchen-mutation";
import { recipesApi, type RecipeQuery } from "@/services/endpoints";

type Filter = "all" | "READY_ONLY" | "EXPIRING" | "QUICK" | "FAVORITES";

function RecipesInner() {
  const { kitchenId } = useKitchen();
  const params = useSearchParams();
  const router = useRouter();
  const ingredient = params.get("ingredient") ?? undefined;
  const ingredientName = params.get("name");
  const [view, setView] = useState<"discover" | "plan">("discover");
  const [filter, setFilter] = useState<Filter>("all");
  const [diet, setDiet] = useState("");
  const [q, setQ] = useState("");

  const query: RecipeQuery = {
    diet: diet || undefined,
    filter: filter === "all" || filter === "QUICK" ? undefined : filter,
    maxCookTime: filter === "QUICK" ? 25 : undefined,
    ingredient,
    q: q.trim() || undefined,
  };
  const { data, isPending, error, refetch } = useQuery({
    queryKey: kq(kitchenId, "recipes", query),
    queryFn: () => recipesApi.recommendations(kitchenId, query),
    enabled: view === "discover",
  });

  return (
    <>
      <PageHeader title="Recipes" subtitle="Ranked by what you have — and what's about to expire." />
      <Segmented
        className="mb-4 max-w-sm"
        value={view}
        onChange={setView}
        options={[
          { value: "discover", label: "Discover" },
          { value: "plan", label: "This week" },
        ]}
      />
      {view === "plan" ? (
        <MealPlanWeek />
      ) : (
        <>
          <div className="mb-4 space-y-3">
            {ingredient && (
              <div className="flex items-center gap-2 rounded-xl bg-[#EEF3EE] px-3 py-2 text-sm text-[#26372C]">
                Recipes using <strong>{ingredientName ?? "this item"}</strong>
                <button type="button" aria-label="Clear ingredient filter" className="ml-auto rounded-full p-1 hover:bg-white" onClick={() => router.replace("/recipes")}>
                  <X className="h-4 w-4" />
                </button>
              </div>
            )}
            <div className="grid gap-2 sm:grid-cols-[1fr_12rem]">
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[#788278]" />
                <Input className="pl-9" placeholder="Search recipes or ingredients" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search recipes" />
              </div>
              <Select value={diet} onChange={(e) => setDiet(e.target.value)} aria-label="Diet">
                <option value="">My diet preference</option>
                <option value="all">All recipes</option>
                <option value="vegetarian">Vegetarian</option>
                <option value="eggetarian">Eggetarian</option>
                <option value="vegan">Vegan</option>
                <option value="jain">Jain</option>
                <option value="omnivore">Non-veg included</option>
              </Select>
            </div>
            <div className="flex gap-2 overflow-x-auto pb-1">
              {(
                [
                  ["all", "All"],
                  ["READY_ONLY", "Ready now"],
                  ["EXPIRING", "Uses expiring"],
                  ["QUICK", "Under 25 min"],
                  ["FAVORITES", "Favourites"],
                ] as [Filter, string][]
              ).map(([v, label]) => (
                <Chip key={v} active={filter === v} onClick={() => setFilter(v)}>
                  {label}
                </Chip>
              ))}
            </div>
          </div>
          {isPending ? (
            <ListSkeleton />
          ) : error ? (
            <ErrorState error={error} onRetry={() => refetch()} />
          ) : !data.length ? (
            <EmptyState icon={UtensilsCrossed} title="No recipes match" description="Try a different filter, or add more items to your kitchen." />
          ) : (
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {data.map((r) => (
                <RecipeCard key={r.id} recipe={r} />
              ))}
            </div>
          )}
        </>
      )}
    </>
  );
}

export default function RecipesPage() {
  return (
    <Suspense>
      <RecipesInner />
    </Suspense>
  );
}

"use client";

import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, Camera, ChefHat, Plus, ShoppingCart, Soup, UtensilsCrossed } from "lucide-react";
import Link from "next/link";
import { useBatchActions } from "@/components/features/inventory/batch-action-host";
import { ExpiryBadge } from "@/components/shared/expiry-badge";
import { ErrorState, ListSkeleton } from "@/components/shared/states";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { useKitchen } from "@/hooks/use-kitchen";
import { kq } from "@/hooks/use-kitchen-mutation";
import { formatInr, formatQty, greeting } from "@/lib/format";
import { dashboardApi } from "@/services/endpoints";

function Stat({ label, value, href }: { label: string; value: React.ReactNode; href: string }) {
  return (
    <Link href={href} className="rounded-2xl border border-[#E6E3DA] bg-white p-4 transition-shadow hover:shadow-sm">
      <p className="text-xs font-medium text-[#788278]">{label}</p>
      <p className="mt-1 text-2xl font-bold text-[#26372C]">{value}</p>
    </Link>
  );
}

export default function DashboardPage() {
  const { kitchenId, profile, kitchen } = useKitchen();
  const { onAction, dialogs } = useBatchActions();
  const { data, isPending, error, refetch } = useQuery({ queryKey: kq(kitchenId, "pulse"), queryFn: () => dashboardApi.pulse(kitchenId) });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-[1.625rem] font-bold tracking-tight text-[#26372C]">
          {greeting()}
          {profile.firstName ? `, ${profile.firstName}` : ""}
        </h1>
        <p className="text-sm text-[#788278]">Here&apos;s what&apos;s happening in {kitchen.name}.</p>
      </div>

      {isPending ? (
        <ListSkeleton rows={3} />
      ) : error ? (
        <ErrorState error={error} onRetry={() => refetch()} />
      ) : data.counts.total === 0 ? (
        <EmptyState
          icon={ChefHat}
          title="Let's stock your kitchen"
          description="Scan your latest grocery bill or add a few items. FoodWise will then tell you what to cook before it goes bad."
          tips={["Start with what spoils fastest: milk, paneer, vegetables.", "Add the price to see how much you save each month."]}
          actionLabel="Scan a bill"
          onAction={() => (window.location.href = "/scan")}
          secondaryLabel="Add manually"
          onSecondaryAction={() => (window.location.href = "/inventory?add=1")}
        />
      ) : (
        <>
          {(data.expiringToday > 0 || data.expired > 0) && (
            <Link
              href="/expiry"
              className="flex items-center gap-3 rounded-2xl border border-[#F8D8D3] bg-[#FCEDE8] p-4 text-[#C04334] transition-shadow hover:shadow-sm"
            >
              <AlertTriangle className="h-5 w-5 shrink-0" />
              <span className="text-sm font-semibold">
                {data.expiringToday > 0 &&
                  `${data.expiringToday} item${data.expiringToday > 1 ? "s expire" : " expires"} today${data.valueAtRiskInr > 0 ? ` (${formatInr(data.valueAtRiskInr)} at risk)` : ""}. `}
                {data.expired > 0 && `${data.expired} already expired.`}
              </span>
            </Link>
          )}

          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Button asChild variant="secondary" className="h-auto flex-col gap-1.5 py-3">
              <Link href="/scan">
                <Camera className="h-5 w-5 text-[#668568]" /> Scan bill
              </Link>
            </Button>
            <Button asChild variant="secondary" className="h-auto flex-col gap-1.5 py-3">
              <Link href="/inventory?add=1">
                <Plus className="h-5 w-5 text-[#668568]" /> Add food
              </Link>
            </Button>
            <Button asChild variant="secondary" className="h-auto flex-col gap-1.5 py-3">
              <Link href="/leftovers?log=1">
                <Soup className="h-5 w-5 text-[#668568]" /> Log leftover
              </Link>
            </Button>
            <Button asChild variant="secondary" className="h-auto flex-col gap-1.5 py-3">
              <Link href="/recipes">
                <UtensilsCrossed className="h-5 w-5 text-[#668568]" /> What to cook
              </Link>
            </Button>
          </div>

          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Stat label="Items tracked" value={data.counts.total} href="/inventory" />
            <Stat label="Use soon" value={data.useSoon + data.expiringToday} href="/expiry" />
            <Stat label="Saved this month" value={formatInr(data.monthRescuedInr)} href="/analytics" />
            <Stat label="Wasted this month" value={formatInr(data.monthWasteInr)} href="/analytics" />
          </div>

          <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
            <section>
              <div className="mb-2 flex items-center justify-between">
                <h2 className="text-lg font-semibold text-[#26372C]">Use these soon</h2>
                <Link href="/expiry" className="text-sm font-semibold text-[#668568] hover:underline">
                  View all
                </Link>
              </div>
              {data.urgent.length === 0 ? (
                <Card className="p-5 text-sm text-[#788278]">Nothing urgent. Everything is fresh 🎉</Card>
              ) : (
                <div className="space-y-2">
                  {data.urgent.map((b) => (
                    <Card key={b.id} className="flex items-center justify-between gap-3 p-3.5 hover:shadow-sm">
                      <div className="min-w-0">
                        <p className="truncate font-semibold text-[#26372C]">{b.name}</p>
                        <div className="mt-1 flex items-center gap-2 text-xs text-[#788278]">
                          <ExpiryBadge status={b.expiryStatus} days={b.daysUntilExpiry} />
                          <span>{formatQty(b.currentQuantity, b.unit)}</span>
                        </div>
                      </div>
                      <div className="flex gap-1.5">
                        {b.catalogItemId && (
                          <Button size="sm" onClick={() => onAction("cook", b)}>
                            Cook
                          </Button>
                        )}
                        <Button size="sm" variant="secondary" onClick={() => onAction("use", b)}>
                          Used
                        </Button>
                      </div>
                    </Card>
                  ))}
                </div>
              )}
            </section>

            <aside className="space-y-4">
              {data.suggestion && (
                <Card className="p-5">
                  <p className="text-xs font-semibold uppercase tracking-wide text-[#668568]">Tonight&apos;s idea</p>
                  <h3 className="mt-1 text-lg font-bold text-[#26372C]">{data.suggestion.title}</h3>
                  <p className="mt-1 text-sm text-[#788278]">
                    {data.suggestion.matchPercent}% of ingredients in stock · {data.suggestion.totalTimeMinutes} min
                  </p>
                  {data.suggestion.usesExpiring.length > 0 && (
                    <p className="mt-2 text-sm text-[#B47823]">Uses {data.suggestion.usesExpiring.join(", ")} before it expires.</p>
                  )}
                  <Button asChild className="mt-4 w-full">
                    <Link href={`/recipes/${data.suggestion.slug}`}>View recipe</Link>
                  </Button>
                </Card>
              )}
              {data.todaysMeals.length > 0 && (
                <Card className="p-5">
                  <p className="mb-2 text-sm font-semibold text-[#26372C]">Today&apos;s plan</p>
                  <ul className="space-y-1.5 text-sm">
                    {data.todaysMeals.map((m) => (
                      <li key={m.id} className="flex justify-between gap-2">
                        <span className="capitalize text-[#788278]">{m.mealSlot}</span>
                        <span className="truncate font-medium text-[#26372C]">
                          {m.title} {m.status === "cooked" && "✓"}
                        </span>
                      </li>
                    ))}
                  </ul>
                </Card>
              )}
              {data.lowStockCount > 0 && (
                <Link href="/shopping" className="flex items-center gap-3 rounded-2xl border border-[#E6E3DA] bg-white p-4 hover:shadow-sm">
                  <ShoppingCart className="h-5 w-5 text-[#668568]" />
                  <span className="text-sm text-[#26372C]">
                    <strong>{data.lowStockCount}</strong> item{data.lowStockCount > 1 ? "s are" : " is"} running low. Add to your list →
                  </span>
                </Link>
              )}
            </aside>
          </div>
        </>
      )}
      {dialogs}
    </div>
  );
}

"use client";

import { useQuery } from "@tanstack/react-query";
import { PartyPopper } from "lucide-react";
import { useBatchActions } from "@/components/features/inventory/batch-action-host";
import { ExpiryBadge } from "@/components/shared/expiry-badge";
import { ErrorState, ListSkeleton, PageHeader } from "@/components/shared/states";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { useKitchen } from "@/hooks/use-kitchen";
import { kq } from "@/hooks/use-kitchen-mutation";
import { formatInr, formatQty } from "@/lib/format";
import { expiryApi } from "@/services/endpoints";
import type { Batch } from "@/services/types";

export default function ExpiryPage() {
  const { kitchenId } = useKitchen();
  const { onAction, dialogs } = useBatchActions();
  const { data, isPending, error, refetch } = useQuery({ queryKey: kq(kitchenId, "expiry"), queryFn: () => expiryApi.timeline(kitchenId) });

  const groups: { title: string; tone: string; items: Batch[] }[] = data
    ? [
        { title: "Expiring today", tone: "text-[#C04334]", items: data.expiringToday },
        { title: "Next 3 days", tone: "text-[#B47823]", items: data.nextThreeDays },
        { title: "This week", tone: "text-[#2D6B3C]", items: data.thisWeek },
        { title: "Expired", tone: "text-[#8C8A84]", items: data.expired },
      ]
    : [];
  const nothing = data && groups.every((g) => !g.items.length);

  return (
    <>
      <PageHeader
        title="Expiry"
        subtitle={
          data && data.valueAtRiskInr > 0
            ? `${formatInr(data.valueAtRiskInr)} of food needs using in the next few days.`
            : "Use, freeze or log what's running out of time."
        }
      />
      {isPending ? (
        <ListSkeleton />
      ) : error ? (
        <ErrorState error={error} onRetry={() => refetch()} />
      ) : nothing ? (
        <EmptyState icon={PartyPopper} title="Nothing urgent" description="Nothing is expiring this week. Check back tomorrow." />
      ) : (
        <div className="space-y-6">
          {groups
            .filter((g) => g.items.length)
            .map((g) => (
              <section key={g.title}>
                <h2 className={`mb-2 text-sm font-semibold uppercase tracking-wide ${g.tone}`}>
                  {g.title} ({g.items.length})
                </h2>
                <div className="space-y-2">
                  {g.items.map((b) => (
                    <Card key={b.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between hover:shadow-sm">
                      <div className="min-w-0">
                        <p className="truncate font-semibold text-[#26372C]">{b.name}</p>
                        <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-[#788278]">
                          <ExpiryBadge status={b.expiryStatus} days={b.daysUntilExpiry} />
                          <span className="capitalize">
                            {formatQty(b.currentQuantity, b.unit)} · {b.storageLocation}
                          </span>
                        </div>
                      </div>
                      <div className="flex flex-wrap gap-2">
                        {b.expiryStatus !== "expired" && !b.leftover && b.catalogItemId && (
                          <Button size="sm" onClick={() => onAction("cook", b)}>
                            Cook
                          </Button>
                        )}
                        <Button size="sm" variant="secondary" onClick={() => onAction("use", b)}>
                          Used
                        </Button>
                        {b.expiryStatus !== "expired" && b.storageLocation !== "freezer" && (
                          <Button size="sm" variant="secondary" onClick={() => onAction("move", b, "freezer")}>
                            Freeze
                          </Button>
                        )}
                        <Button size="sm" variant="destructive" onClick={() => onAction("discard", b)}>
                          Discard
                        </Button>
                      </div>
                    </Card>
                  ))}
                </div>
              </section>
            ))}
        </div>
      )}
      {dialogs}
    </>
  );
}

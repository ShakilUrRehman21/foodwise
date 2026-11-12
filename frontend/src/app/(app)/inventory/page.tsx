"use client";

import { useQuery } from "@tanstack/react-query";
import { Boxes, Camera, Plus, Search } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useMemo, useState } from "react";
import { AddFoodDialog } from "@/components/features/inventory/add-food-dialog";
import { useBatchActions } from "@/components/features/inventory/batch-action-host";
import { BatchCard } from "@/components/features/inventory/batch-card";
import { ErrorState, ListSkeleton, PageHeader } from "@/components/shared/states";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Segmented, Select } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { useKitchen } from "@/hooks/use-kitchen";
import { kq } from "@/hooks/use-kitchen-mutation";
import { catalogApi, inventoryApi, type InventoryFilter } from "@/services/endpoints";
import type { StorageLocation } from "@/services/types";

type Tab = "all" | StorageLocation;

function InventoryInner() {
  const { kitchenId } = useKitchen();
  const router = useRouter();
  const params = useSearchParams();
  const [tab, setTab] = useState<Tab>("all");
  const [q, setQ] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [sort, setSort] = useState<NonNullable<InventoryFilter["sort"]>>("expiry_asc");
  const [addOpen, setAddOpen] = useState(false);
  const { onAction, dialogs } = useBatchActions();

  useEffect(() => {
    if (params.get("add")) {
      setAddOpen(true);
      router.replace("/inventory");
    }
  }, [params, router]);

  const { data, isPending, error, refetch } = useQuery({
    queryKey: kq(kitchenId, "inventory", sort),
    queryFn: () => inventoryApi.list(kitchenId, { sort }),
  });
  const categories = useQuery({ queryKey: ["categories"], queryFn: catalogApi.categories, staleTime: Infinity });

  // Filtering happens client-side for instant feedback (inventory is small; the API supports the same filters).
  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return (data ?? []).filter(
      (b) =>
        (tab === "all" || b.storageLocation === tab) &&
        (!categoryId || b.categoryId === categoryId) &&
        (!needle || b.name.toLowerCase().includes(needle) || (b.nameHi ?? "").toLowerCase().includes(needle)),
    );
  }, [data, tab, q, categoryId]);

  const count = (loc: Tab) => (data ?? []).filter((b) => loc === "all" || b.storageLocation === loc).length;

  return (
    <>
      <PageHeader
        title="Inventory"
        subtitle="Everything in your fridge, freezer and pantry."
        actions={
          <>
            <Button variant="secondary" asChild>
              <Link href="/scan">
                <Camera className="h-4 w-4" /> Scan bill
              </Link>
            </Button>
            <Button onClick={() => setAddOpen(true)}>
              <Plus className="h-4 w-4" /> Add food
            </Button>
          </>
        }
      />
      <div className="mb-4 space-y-3">
        <Segmented
          value={tab}
          onChange={setTab}
          options={(["all", "fridge", "freezer", "pantry"] as Tab[]).map((t) => ({
            value: t,
            label: `${t[0].toUpperCase()}${t.slice(1)} (${count(t)})`,
          }))}
        />
        <div className="grid gap-2 sm:grid-cols-[1fr_12rem_12rem]">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[#788278]" />
            <Input className="pl-9" placeholder="Search paneer, dal, milk…" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search inventory" />
          </div>
          <Select value={categoryId} onChange={(e) => setCategoryId(e.target.value)} aria-label="Category">
            <option value="">All categories</option>
            {categories.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </Select>
          <Select value={sort} onChange={(e) => setSort(e.target.value as typeof sort)} aria-label="Sort">
            <option value="expiry_asc">Expiring soonest</option>
            <option value="expiry_desc">Expiring latest</option>
            <option value="added_desc">Recently added</option>
            <option value="name_asc">Name (A–Z)</option>
          </Select>
        </div>
      </div>

      {isPending ? (
        <ListSkeleton />
      ) : error ? (
        <ErrorState error={error} onRetry={() => refetch()} />
      ) : !data.length ? (
        <EmptyState
          icon={Boxes}
          title="Your kitchen is a blank slate"
          description="Scan a grocery bill or add a few items to start tracking what you have."
          actionLabel="Add food"
          onAction={() => setAddOpen(true)}
          secondaryLabel="Scan a bill"
          onSecondaryAction={() => router.push("/scan")}
        />
      ) : !filtered.length ? (
        <p className="py-12 text-center text-sm text-[#788278]">Nothing matches those filters.</p>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {filtered.map((b) => (
            <BatchCard key={b.id} batch={b} onAction={onAction} />
          ))}
        </div>
      )}
      <AddFoodDialog open={addOpen} onOpenChange={setAddOpen} />
      {dialogs}
    </>
  );
}

export default function InventoryPage() {
  return (
    <Suspense>
      <InventoryInner />
    </Suspense>
  );
}

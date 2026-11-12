"use client";

import { useQuery } from "@tanstack/react-query";
import { PackageCheck, ShoppingCart, Sparkles, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import { CatalogCombobox } from "@/components/features/catalog-combobox";
import { ErrorState, ListSkeleton, PageHeader } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { Field, Select } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { useKitchen } from "@/hooks/use-kitchen";
import { kq, useKitchenMutation } from "@/hooks/use-kitchen-mutation";
import { LOCATIONS } from "@/lib/constants";
import { addDays, formatInr, formatQty, isoDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import { shoppingApi, type AddShoppingInput } from "@/services/endpoints";
import type { CatalogItem, ShoppingItem, ShoppingList, StorageLocation } from "@/services/types";

interface ImportRow {
  itemId: string;
  name: string;
  storageLocation: StorageLocation;
  expiryDate: string;
  costInr: string;
}

function ImportDialog({ list, open, onOpenChange }: { list: ShoppingList; open: boolean; onOpenChange: (o: boolean) => void }) {
  const checked = list.items.filter((i) => i.checked);
  const [rows, setRows] = useState<ImportRow[]>([]);
  const [store, setStore] = useState("");
  const init = () =>
    setRows(
      checked.map((i) => ({
        itemId: i.id,
        name: i.name,
        storageLocation: i.suggestedLocation,
        expiryDate: i.shelfLifeDays !== null ? addDays(isoDate(), i.shelfLifeDays) : "",
        costInr: i.estimatedCostInr !== null ? String(i.estimatedCostInr) : "",
      })),
    );
  const doImport = useKitchenMutation(
    (kid, _: void) =>
      shoppingApi.importToInventory(kid, list.id, {
        storeName: store || undefined,
        items: rows.map((r) => ({ itemId: r.itemId, storageLocation: r.storageLocation, expiryDate: r.expiryDate, costInr: r.costInr ? Number(r.costInr) : null })),
      }),
    { success: (r) => `Added ${r.created.length} items to your kitchen.`, onSuccess: () => onOpenChange(false) },
  );
  const upd = (id: string, patch: Partial<ImportRow>) => setRows(rows.map((r) => (r.itemId === id ? { ...r, ...patch } : r)));
  const missingDates = rows.some((r) => !r.expiryDate);
  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        if (o) init();
        onOpenChange(o);
      }}
    >
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Add bought items to kitchen</DialogTitle>
          <DialogDescription>Set where each item goes and when it expires.</DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          {rows.map((r) => (
            <div key={r.itemId} className="grid grid-cols-2 gap-2 rounded-xl bg-[#F8F7F2] p-3 sm:grid-cols-[1fr_7rem_9rem_6rem] sm:items-center">
              <span className="col-span-2 text-sm font-semibold text-[#26372C] sm:col-span-1">{r.name}</span>
              <Select aria-label="Location" value={r.storageLocation} onChange={(e) => upd(r.itemId, { storageLocation: e.target.value as StorageLocation })}>
                {LOCATIONS.map((l) => (
                  <option key={l.value} value={l.value}>
                    {l.label}
                  </option>
                ))}
              </Select>
              <Input type="date" aria-label="Expiry date" min={isoDate()} value={r.expiryDate} className={cn(!r.expiryDate && "border-[#E9AC55]")} onChange={(e) => upd(r.itemId, { expiryDate: e.target.value })} />
              <Input type="number" min="0" step="any" aria-label="Price" placeholder="₹" value={r.costInr} onChange={(e) => upd(r.itemId, { costInr: e.target.value })} />
            </div>
          ))}
        </div>
        <Field label="Store (optional)">
          <Input value={store} onChange={(e) => setStore(e.target.value)} placeholder="e.g. local kirana" />
        </Field>
        <DialogFooter>
          <Button disabled={missingDates || doImport.isPending || !rows.length} onClick={() => doImport.mutate()}>
            {missingDates ? "Set all expiry dates" : `Add ${rows.length} items`}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default function ShoppingPage() {
  const { kitchenId } = useKitchen();
  const [text, setText] = useState("");
  const [picked, setPicked] = useState<CatalogItem | null>(null);
  const [qty, setQty] = useState("");
  const [inStore, setInStore] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const { data, isPending, error, refetch } = useQuery({ queryKey: kq(kitchenId, "shopping"), queryFn: () => shoppingApi.get(kitchenId) });

  const add = useKitchenMutation((kid, body: AddShoppingInput) => shoppingApi.add(kid, body), {
    success: (r) => (r.alreadyOnList ? "Already on your list." : undefined),
    onSuccess: () => {
      setText("");
      setPicked(null);
      setQty("");
    },
  });
  const low = useKitchenMutation((kid, _: void) => shoppingApi.addLowStock(kid), {
    success: (r) => (r.added ? `Added ${r.added} running-low item${r.added > 1 ? "s" : ""}.` : "Nothing new is running low."),
  });
  const check = useKitchenMutation((kid, v: { id: string; checked: boolean }) => shoppingApi.check(kid, v.id, v.checked), { toastErrors: true });
  const remove = useKitchenMutation((kid, id: string) => shoppingApi.remove(kid, id));
  const clear = useKitchenMutation((kid, _: void) => shoppingApi.clearChecked(kid), { success: "Cleared checked items." });

  const groups = useMemo(() => {
    const m = new Map<string, ShoppingItem[]>();
    for (const i of data?.items ?? []) {
      const key = i.categoryName ?? "Other";
      m.set(key, [...(m.get(key) ?? []), i]);
    }
    return [...m.entries()];
  }, [data]);

  const submitAdd = () => {
    if (!text.trim()) return;
    add.mutate({
      catalogItemId: picked?.id ?? null,
      name: text.trim(),
      quantity: qty ? Number(qty) : undefined,
      unit: picked?.defaultUnit,
    });
  };

  return (
    <>
      <PageHeader
        title="Shopping list"
        subtitle="Shared with everyone in your kitchen."
        actions={
          <>
            <Button variant="secondary" onClick={() => low.mutate()} disabled={low.isPending}>
              <Sparkles className="h-4 w-4" /> Add running-low items
            </Button>
            <Button variant={inStore ? "default" : "secondary"} onClick={() => setInStore(!inStore)}>
              {inStore ? "Exit store mode" : "In-store mode"}
            </Button>
          </>
        }
      />
      {!inStore && (
        <form
          className="mb-5 grid gap-2 sm:grid-cols-[1fr_7rem_auto]"
          onSubmit={(e) => {
            e.preventDefault();
            submitAdd();
          }}
        >
          <CatalogCombobox value={text} placeholder="Add an item, e.g. tomatoes" onTextChange={(t) => { setText(t); setPicked(null); }} onSelect={(i) => { setText(i.nameEn); setPicked(i); }} />
          <Input type="number" min="0" step="any" placeholder={picked ? `qty (${picked.defaultUnit})` : "qty"} value={qty} onChange={(e) => setQty(e.target.value)} aria-label="Quantity" />
          <Button type="submit" disabled={!text.trim() || add.isPending}>
            Add
          </Button>
        </form>
      )}
      {isPending ? (
        <ListSkeleton />
      ) : error ? (
        <ErrorState error={error} onRetry={() => refetch()} />
      ) : !data.items.length ? (
        <EmptyState icon={ShoppingCart} title="Your list is empty" description="Add items above, or let FoodWise add what's running low." />
      ) : (
        <>
          {data.checkedCount > 0 && (
            <Card className="mb-4 flex flex-wrap items-center justify-between gap-2 border-[#D5E8D8] bg-[#EEF3EE] p-3">
              <span className="text-sm text-[#26372C]">
                <strong>{data.checkedCount}</strong> item{data.checkedCount > 1 ? "s" : ""} in your basket
              </span>
              <div className="flex gap-2">
                <Button size="sm" variant="secondary" onClick={() => clear.mutate()}>
                  Clear checked
                </Button>
                <Button size="sm" onClick={() => setImportOpen(true)}>
                  <PackageCheck className="h-4 w-4" /> Add to kitchen
                </Button>
              </div>
            </Card>
          )}
          <div className="space-y-5">
            {groups.map(([cat, items]) => (
              <section key={cat}>
                <h2 className="mb-2 text-xs font-semibold uppercase tracking-wide text-[#788278]">{cat}</h2>
                <Card className="divide-y divide-[#F1EFEA] p-0">
                  {items.map((i) => (
                    <div key={i.id} className={cn("flex items-center gap-3 px-4", inStore ? "py-4" : "py-3")}>
                      <input
                        type="checkbox"
                        aria-label={`Bought ${i.name}`}
                        checked={i.checked}
                        onChange={(e) => check.mutate({ id: i.id, checked: e.target.checked })}
                        className={cn("accent-[#668568]", inStore ? "h-6 w-6" : "h-5 w-5")}
                      />
                      <div className="min-w-0 flex-1">
                        <p className={cn("truncate text-[#26372C]", inStore ? "text-lg font-semibold" : "text-sm font-medium", i.checked && "text-[#A0A79F] line-through")}>{i.name}</p>
                        <p className="text-xs text-[#788278]">
                          {formatQty(i.quantity, i.unit)}
                          {i.estimatedCostInr !== null && ` · ~${formatInr(i.estimatedCostInr)}`}
                          {i.linkedRecipeTitle && ` · for ${i.linkedRecipeTitle}`}
                        </p>
                      </div>
                      {i.addedReason === "auto_low_stock" && <Badge variant="warning">Low</Badge>}
                      {!inStore && (
                        <button type="button" aria-label={`Remove ${i.name}`} className="rounded-full p-2 text-[#788278] hover:text-[#C04334]" onClick={() => remove.mutate(i.id)}>
                          <Trash2 className="h-4 w-4" />
                        </button>
                      )}
                    </div>
                  ))}
                </Card>
              </section>
            ))}
          </div>
          {data.estimatedTotalInr > 0 && <p className="mt-4 text-right text-sm text-[#788278]">Estimated total {formatInr(data.estimatedTotalInr)}</p>}
          <ImportDialog list={data} open={importOpen} onOpenChange={setImportOpen} />
        </>
      )}
    </>
  );
}

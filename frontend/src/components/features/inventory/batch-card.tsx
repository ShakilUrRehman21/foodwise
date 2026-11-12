"use client";

import { MoreVertical } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { ExpiryBadge } from "@/components/shared/expiry-badge";
import { formatDate, formatInr, formatQty } from "@/lib/format";
import type { Batch } from "@/services/types";

export type BatchAction = "use" | "discard" | "move" | "edit" | "cook";

export function BatchCard({ batch, onAction }: { batch: Batch; onAction: (action: BatchAction, batch: Batch) => void }) {
  return (
    <Card className="flex flex-col gap-3 p-4 hover:shadow-sm">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 className="truncate text-base font-semibold text-[#26372C]">
            {batch.name}
            {batch.nameHi && <span className="font-normal text-[#788278]"> · {batch.nameHi}</span>}
          </h3>
          <p className="mt-0.5 text-xs capitalize text-[#788278]">
            {batch.storageLocation}
            {batch.categoryName ? ` · ${batch.categoryName}` : ""}
            {batch.costInr !== null ? ` · ${formatInr(batch.costInr)}` : ""}
          </p>
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button type="button" aria-label={`More actions for ${batch.name}`} className="rounded-full p-2 text-[#788278] hover:bg-[#F1EFEA]">
              <MoreVertical className="h-4 w-4" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {!batch.leftover && batch.catalogItemId && <DropdownMenuItem onSelect={() => onAction("cook", batch)}>Find recipes</DropdownMenuItem>}
            <DropdownMenuItem onSelect={() => onAction("move", batch)}>Move…</DropdownMenuItem>
            <DropdownMenuItem onSelect={() => onAction("edit", batch)}>Edit details</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onSelect={() => onAction("discard", batch)} className="text-[#C04334]">
              Discard &amp; log waste
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <ExpiryBadge status={batch.expiryStatus} days={batch.daysUntilExpiry} />
        <span className="text-xs text-[#788278]">{formatDate(batch.expiryDate)}</span>
        {batch.expiryEstimated && <Badge variant="secondary">Estimated</Badge>}
      </div>
      <div className="flex items-center justify-between gap-2">
        <span className="text-lg font-bold text-[#26372C]">{formatQty(batch.currentQuantity, batch.unit)}</span>
        <Button size="sm" variant="secondary" onClick={() => onAction("use", batch)}>
          Use
        </Button>
      </div>
    </Card>
  );
}

"use client";

import { Check, ChevronDown } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { useKitchen } from "@/hooks/use-kitchen";
import { cn } from "@/lib/utils";
import Link from "next/link";

export function KitchenSwitcher({ compact = false }: { compact?: boolean }) {
  const { kitchens, kitchen, setKitchenId } = useKitchen();
  const qc = useQueryClient();
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          className={cn(
            "flex items-center gap-2 rounded-xl border border-[#E6E3DA] bg-[#F8F7F2] text-left hover:bg-[#F1EFEA] transition-colors",
            compact ? "px-2.5 py-1.5" : "w-full px-3 py-2",
          )}
          aria-label="Switch kitchen"
        >
          <span aria-hidden>🏡</span>
          <span className={cn("truncate font-semibold text-[#26372C]", compact ? "max-w-[9rem] text-xs" : "flex-1 text-sm")}>
            {kitchen.name}
          </span>
          <ChevronDown className="h-3.5 w-3.5 shrink-0 text-[#788278]" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-60">
        <DropdownMenuLabel>Your kitchens</DropdownMenuLabel>
        {kitchens.map((k) => (
          <DropdownMenuItem
            key={k.id}
            onSelect={() => {
              setKitchenId(k.id);
              void qc.invalidateQueries({ queryKey: ["k"] });
            }}
            className="flex items-center justify-between"
          >
            <span className="truncate">{k.name}</span>
            {k.id === kitchen.id && <Check className="h-4 w-4 text-[#668568]" />}
          </DropdownMenuItem>
        ))}
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link href="/settings">Manage kitchens</Link>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

"use client";

import { useQuery } from "@tanstack/react-query";
import React, { useEffect, useId, useRef, useState } from "react";
import { Input } from "@/components/ui/input";
import { catalogApi } from "@/services/endpoints";
import type { CatalogItem } from "@/services/types";
import { cn } from "@/lib/utils";

function useDebounced<T>(value: T, ms: number) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

/**
 * Indian ingredient autosuggest (FR-INV / UI Screen 6). Free text is allowed; picking a suggestion links the
 * catalog item (category, unit and location defaults).
 */
export function CatalogCombobox({
  value,
  onTextChange,
  onSelect,
  placeholder = "Search paneer, dal, milk…",
  autoFocus,
  id,
}: {
  value: string;
  onTextChange: (text: string) => void;
  onSelect: (item: CatalogItem) => void;
  placeholder?: string;
  autoFocus?: boolean;
  id?: string;
}) {
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(0);
  const listId = useId();
  const debounced = useDebounced(value.trim(), 150);
  const boxRef = useRef<HTMLDivElement>(null);
  const { data = [] } = useQuery({
    queryKey: ["catalog", debounced],
    queryFn: ({ signal }) => catalogApi.search(debounced, 8, signal),
    enabled: debounced.length >= 1,
    staleTime: 5 * 60_000,
  });

  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, []);

  const pick = (item: CatalogItem) => {
    onSelect(item);
    setOpen(false);
  };

  return (
    <div className="relative" ref={boxRef}>
      <Input
        id={id}
        value={value}
        autoFocus={autoFocus}
        autoComplete="off"
        placeholder={placeholder}
        role="combobox"
        aria-expanded={open && data.length > 0}
        aria-controls={listId}
        onChange={(e) => {
          onTextChange(e.target.value);
          setOpen(true);
          setHighlight(0);
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={(e) => {
          if (!open || !data.length) return;
          if (e.key === "ArrowDown") {
            e.preventDefault();
            setHighlight((h) => Math.min(h + 1, data.length - 1));
          } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setHighlight((h) => Math.max(h - 1, 0));
          } else if (e.key === "Enter") {
            e.preventDefault();
            pick(data[highlight]);
          } else if (e.key === "Escape") {
            setOpen(false);
          }
        }}
      />
      {open && value.trim() && data.length > 0 && (
        <ul id={listId} role="listbox" className="absolute z-50 mt-1 max-h-64 w-full overflow-y-auto rounded-xl border border-[#E6E3DA] bg-white py-1 shadow-lg">
          {data.map((item, i) => (
            <li
              key={item.id}
              role="option"
              aria-selected={i === highlight}
              onMouseDown={(e) => {
                e.preventDefault();
                pick(item);
              }}
              onMouseEnter={() => setHighlight(i)}
              className={cn("flex cursor-pointer items-center justify-between px-3 py-2 text-sm", i === highlight && "bg-[#EEF3EE]")}
            >
              <span className="text-[#26372C]">
                {item.nameEn}
                {item.nameHi && item.nameHi !== item.nameEn && <span className="text-[#788278]"> · {item.nameHi}</span>}
              </span>
              <span className="text-[11px] text-[#788278]">{item.categoryName}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

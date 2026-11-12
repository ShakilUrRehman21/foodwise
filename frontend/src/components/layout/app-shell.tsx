"use client";

import { Leaf, MoreHorizontal, Plus, X } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import React, { useState } from "react";
import { Button } from "@/components/ui/button";
import { useKitchen } from "@/hooks/use-kitchen";
import { cn } from "@/lib/utils";
import { KitchenSwitcher } from "./kitchen-switcher";
import { MORE_ITEMS, NAV_ITEMS } from "./nav-items";
import { NotificationBell } from "./notification-bell";

function isActive(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(href + "/");
}

function Sidebar() {
  const pathname = usePathname();
  const { profile } = useKitchen();
  const name = [profile.firstName, profile.lastName].filter(Boolean).join(" ") || profile.email;
  return (
    <aside className="sticky top-0 hidden h-screen w-[260px] shrink-0 flex-col border-r border-[#E6E3DA] bg-white lg:flex">
      <Link href="/dashboard" className="flex items-center gap-2.5 border-b border-[#F1EFEA] px-5 py-4">
        <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#668568] text-white">
          <Leaf className="h-5 w-5" />
        </span>
        <span className="text-lg font-bold tracking-tight text-[#26372C]">FoodWise</span>
      </Link>
      <div className="border-b border-[#F1EFEA] px-4 py-3">
        <KitchenSwitcher />
      </div>
      <nav className="flex-1 space-y-0.5 overflow-y-auto px-3 py-3" aria-label="Main">
        {NAV_ITEMS.map((item) => {
          const Icon = item.icon;
          const active = isActive(pathname, item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors",
                active ? "bg-[#EEF3EE] font-semibold text-[#26372C]" : "text-[#5E6A5F] hover:bg-[#F8F7F2] hover:text-[#26372C]",
              )}
            >
              <Icon className={cn("h-4 w-4", active ? "text-[#668568]" : "")} />
              {item.label}
            </Link>
          );
        })}
      </nav>
      <div className="border-t border-[#F1EFEA] px-4 py-3">
        <Link href="/settings" className="flex items-center gap-2.5 rounded-xl px-1 py-1 hover:bg-[#F8F7F2]">
          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-[#668568] text-xs font-semibold text-white">
            {(profile.firstName?.[0] ?? profile.email[0] ?? "?").toUpperCase()}
          </span>
          <span className="min-w-0">
            <span className="block truncate text-xs font-semibold text-[#26372C]">{name}</span>
            <span className="block truncate text-[11px] text-[#788278]">{profile.emailPending ? "Email syncing…" : profile.email}</span>
          </span>
        </Link>
      </div>
    </aside>
  );
}

function BottomNav() {
  const pathname = usePathname();
  const [moreOpen, setMoreOpen] = useState(false);
  const primary = [NAV_ITEMS[0], NAV_ITEMS[1]];
  const recipes = NAV_ITEMS[4];
  const ScanIcon = NAV_ITEMS[2].icon;
  const moreActive = MORE_ITEMS.some((i) => isActive(pathname, i.href));
  const tab = (item: (typeof NAV_ITEMS)[number]) => {
    const Icon = item.icon;
    const active = isActive(pathname, item.href);
    return (
      <Link
        key={item.href}
        href={item.href}
        className={cn("flex min-h-11 flex-1 flex-col items-center justify-center gap-0.5 text-[11px] font-semibold", active ? "text-[#668568]" : "text-[#788278]")}
      >
        <Icon className="h-5 w-5" />
        {item.label}
      </Link>
    );
  };
  return (
    <>
      {moreOpen && (
        <div className="fixed inset-0 z-40 bg-[#26372C]/40 lg:hidden" onClick={() => setMoreOpen(false)}>
          <div
            className="fixed inset-x-0 bottom-16 rounded-t-[20px] border-t border-[#E6E3DA] bg-white p-4 pb-5 shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-3 flex items-center justify-between">
              <p className="text-sm font-semibold text-[#26372C]">More</p>
              <button type="button" aria-label="Close" onClick={() => setMoreOpen(false)} className="rounded-full p-1.5 hover:bg-[#F1EFEA]">
                <X className="h-5 w-5 text-[#788278]" />
              </button>
            </div>
            <div className="grid grid-cols-2 gap-2">
              {MORE_ITEMS.map((item) => {
                const Icon = item.icon;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    onClick={() => setMoreOpen(false)}
                    className={cn(
                      "flex items-center gap-2.5 rounded-xl border px-3 py-3 text-sm font-semibold",
                      isActive(pathname, item.href) ? "border-[#668568] bg-[#EEF3EE]" : "border-[#E6E3DA] bg-[#F8F7F2]",
                    )}
                  >
                    <Icon className="h-4 w-4 text-[#668568]" />
                    {item.label}
                  </Link>
                );
              })}
            </div>
          </div>
        </div>
      )}
      <nav aria-label="Mobile" className="fixed inset-x-0 bottom-0 z-30 flex h-16 items-center border-t border-[#E6E3DA] bg-white px-1 lg:hidden">
        {primary.map(tab)}
        <div className="flex flex-1 justify-center">
          <Link
            href="/scan"
            aria-label="Scan groceries"
            className="-mt-6 flex h-14 w-14 items-center justify-center rounded-full border-4 border-white bg-[#668568] text-white shadow-lg active:scale-95"
          >
            <ScanIcon className="h-6 w-6" />
          </Link>
        </div>
        {tab(recipes)}
        <button
          type="button"
          onClick={() => setMoreOpen((v) => !v)}
          className={cn("flex min-h-11 flex-1 flex-col items-center justify-center gap-0.5 text-[11px] font-semibold", moreOpen || moreActive ? "text-[#668568]" : "text-[#788278]")}
        >
          <MoreHorizontal className="h-5 w-5" />
          More
        </button>
      </nav>
    </>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen bg-[#F8F7F2]">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col pb-20 lg:pb-0">
        <header className="sticky top-0 z-20 flex items-center justify-between gap-3 border-b border-[#E6E3DA] bg-white/95 px-4 py-2.5 backdrop-blur sm:px-6">
          <div className="flex items-center gap-2 lg:hidden">
            <Link href="/dashboard" aria-label="FoodWise home" className="flex h-8 w-8 items-center justify-center rounded-lg bg-[#668568] text-white">
              <Leaf className="h-4 w-4" />
            </Link>
            <KitchenSwitcher compact />
          </div>
          <div className="hidden lg:block" />
          <div className="flex items-center gap-2">
            <Button asChild size="sm" className="hidden sm:inline-flex">
              <Link href="/inventory?add=1">
                <Plus className="h-4 w-4" /> Add item
              </Link>
            </Button>
            <NotificationBell />
          </div>
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 p-4 sm:p-6 lg:p-8">{children}</main>
      </div>
      <BottomNav />
    </div>
  );
}

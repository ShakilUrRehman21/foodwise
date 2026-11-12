"use client";

import { useQuery } from "@tanstack/react-query";
import { Bell } from "lucide-react";
import { useRouter } from "next/navigation";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { kq, useKitchenMutation } from "@/hooks/use-kitchen-mutation";
import { useKitchen } from "@/hooks/use-kitchen";
import { notificationsApi } from "@/services/endpoints";
import { relativeTime } from "@/lib/format";
import { cn } from "@/lib/utils";

export function NotificationBell() {
  const { kitchenId } = useKitchen();
  const router = useRouter();
  const { data } = useQuery({
    queryKey: kq(kitchenId, "notifications"),
    queryFn: () => notificationsApi.list(kitchenId),
    refetchInterval: 5 * 60_000,
  });
  const markRead = useKitchenMutation((kid, id: string) => notificationsApi.read(kid, id), { toastErrors: false });
  const readAll = useKitchenMutation((kid, _: void) => notificationsApi.readAll(kid));
  const unread = data?.unreadCount ?? 0;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          aria-label={unread ? `${unread} unread notifications` : "Notifications"}
          className="relative flex h-10 w-10 items-center justify-center rounded-full border border-[#E6E3DA] bg-white hover:bg-[#EEF3EE]"
        >
          <Bell className="h-4 w-4 text-[#26372C]" />
          {unread > 0 && (
            <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-[#D76F65] px-1 text-[10px] font-bold text-white ring-2 ring-white">
              {unread > 9 ? "9+" : unread}
            </span>
          )}
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80 max-h-[70vh] overflow-y-auto">
        <div className="flex items-center justify-between pr-2">
          <DropdownMenuLabel>Notifications</DropdownMenuLabel>
          {unread > 0 && (
            <button type="button" className="text-xs font-semibold text-[#668568] hover:underline" onClick={() => readAll.mutate()}>
              Mark all read
            </button>
          )}
        </div>
        <DropdownMenuSeparator />
        {!data?.items.length && <p className="px-3 py-6 text-center text-sm text-[#788278]">You&apos;re all caught up.</p>}
        {data?.items.map((n) => (
          <DropdownMenuItem
            key={n.id}
            className="flex flex-col items-start gap-0.5 py-2.5"
            onSelect={() => {
              if (n.status === "unread") markRead.mutate(n.id);
              if (n.actionUrl) router.push(n.actionUrl);
            }}
          >
            <span className={cn("text-sm", n.status === "unread" ? "font-semibold text-[#26372C]" : "text-[#26372C]")}>
              {n.status === "unread" && <span className="mr-1.5 inline-block h-2 w-2 rounded-full bg-[#D76F65]" />}
              {n.title}
            </span>
            <span className="text-xs text-[#788278] line-clamp-2">{n.message}</span>
            <span className="text-[11px] text-[#A0A79F]">{relativeTime(n.createdAt)}</span>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

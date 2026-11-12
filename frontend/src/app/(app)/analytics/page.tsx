"use client";

import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, Lightbulb } from "lucide-react";
import { useState } from "react";
import { ErrorState, ListSkeleton, PageHeader } from "@/components/shared/states";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useKitchen } from "@/hooks/use-kitchen";
import { kq } from "@/hooks/use-kitchen-mutation";
import { WASTE_REASONS } from "@/lib/constants";
import { currentMonth, formatInr, monthLabel, shiftMonth } from "@/lib/format";
import { analyticsApi } from "@/services/endpoints";

function Kpi({ label, value, sub, tone }: { label: string; value: string; sub?: string; tone?: string }) {
  return (
    <Card className="p-5">
      <p className="text-xs font-medium text-[#788278]">{label}</p>
      <p className={`mt-1 text-2xl font-bold ${tone ?? "text-[#26372C]"}`}>{value}</p>
      {sub && <p className="mt-1 text-xs text-[#788278]">{sub}</p>}
    </Card>
  );
}

export default function AnalyticsPage() {
  const { kitchenId } = useKitchen();
  const [month, setMonth] = useState(currentMonth());
  const summary = useQuery({ queryKey: kq(kitchenId, "analytics", "summary", month), queryFn: () => analyticsApi.summary(kitchenId, month) });
  const breakdown = useQuery({ queryKey: kq(kitchenId, "analytics", "breakdown", month), queryFn: () => analyticsApi.breakdown(kitchenId, month) });
  const top = useQuery({ queryKey: kq(kitchenId, "analytics", "top", month), queryFn: () => analyticsApi.topWasted(kitchenId, month) });
  const s = summary.data;
  const trendMax = Math.max(1, ...(s?.trend.flatMap((t) => [t.spendInr, t.wasteInr]) ?? [1]));

  return (
    <>
      <PageHeader
        title="Analytics"
        subtitle="Where your grocery money goes — and what ends up in the bin."
        actions={
          <div className="flex items-center gap-1 rounded-full border border-[#E6E3DA] bg-white p-1">
            <Button size="sm" variant="ghost" aria-label="Previous month" onClick={() => setMonth(shiftMonth(month, -1))}>
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <span className="min-w-32 text-center text-sm font-semibold">{monthLabel(month)}</span>
            <Button size="sm" variant="ghost" aria-label="Next month" disabled={month >= currentMonth()} onClick={() => setMonth(shiftMonth(month, 1))}>
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        }
      />
      {summary.isPending ? (
        <ListSkeleton rows={3} />
      ) : summary.error ? (
        <ErrorState error={summary.error} onRetry={() => summary.refetch()} />
      ) : (
        s && (
          <div className="space-y-5">
            <div className="grid gap-3 sm:grid-cols-3">
              <Kpi label="Spent on groceries" value={formatInr(s.totalSpendInr)} sub={`${s.purchases} purchase${s.purchases === 1 ? "" : "s"} logged`} />
              <Kpi
                label="Food wasted"
                value={formatInr(s.totalWasteInr)}
                tone="text-[#C04334]"
                sub={
                  s.wasteChangePercent === null
                    ? `${s.itemsWasted} item${s.itemsWasted === 1 ? "" : "s"} thrown away`
                    : `${s.wasteChangePercent > 0 ? "▲" : "▼"} ${Math.abs(s.wasteChangePercent)}% vs last month`
                }
              />
              <Kpi label="Rescued just in time" value={formatInr(s.rescuedInr)} tone="text-[#2D6B3C]" sub="Food used within 3 days of expiry" />
            </div>

            <div className="grid gap-5 lg:grid-cols-2">
              <Card className="p-5">
                <h2 className="mb-3 font-semibold text-[#26372C]">Waste by category</h2>
                {breakdown.data?.length ? (
                  <ul className="space-y-3">
                    {breakdown.data.map((c) => (
                      <li key={c.slug}>
                        <div className="mb-1 flex justify-between text-sm">
                          <span className="text-[#26372C]">{c.category}</span>
                          <span className="font-semibold text-[#26372C]">
                            {formatInr(c.amountInr)} <span className="font-normal text-[#788278]">· {c.percent}%</span>
                          </span>
                        </div>
                        <div className="h-2.5 overflow-hidden rounded-full bg-[#F1EFEA]">
                          <div className="h-full rounded-full" style={{ width: `${Math.max(c.percent, 2)}%`, background: c.colorHex }} />
                        </div>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm text-[#788278]">No waste logged this month. 🎉</p>
                )}
                {s.wasteByReason.length > 0 && (
                  <p className="mt-4 text-xs text-[#788278]">
                    Reasons:{" "}
                    {s.wasteByReason.map((r) => `${WASTE_REASONS.find((w) => w.value === r.reason)?.label ?? r.reason} (${r.count})`).join(" · ")}
                  </p>
                )}
              </Card>

              <Card className="p-5">
                <h2 className="mb-3 font-semibold text-[#26372C]">Most wasted</h2>
                {top.data?.length ? (
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-left text-xs text-[#788278]">
                        <th className="pb-2 font-medium">Item</th>
                        <th className="pb-2 text-right font-medium">Times</th>
                        <th className="pb-2 text-right font-medium">Lost</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#F1EFEA]">
                      {top.data.map((t) => (
                        <tr key={t.itemName}>
                          <td className="py-2 text-[#26372C]">{t.itemName}</td>
                          <td className="py-2 text-right">{t.times}</td>
                          <td className="py-2 text-right font-semibold">{formatInr(t.totalLossInr)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                ) : (
                  <p className="text-sm text-[#788278]">Nothing yet.</p>
                )}
              </Card>
            </div>

            <Card className="p-5">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="font-semibold text-[#26372C]">Last 6 months</h2>
                <div className="flex gap-3 text-xs text-[#788278]">
                  <span><span className="mr-1 inline-block h-2.5 w-2.5 rounded-sm bg-[#668568]" />Spent</span>
                  <span><span className="mr-1 inline-block h-2.5 w-2.5 rounded-sm bg-[#D76F65]" />Wasted</span>
                </div>
              </div>
              <div className="flex h-40 items-end gap-3">
                {s.trend.map((t) => (
                  <div key={t.month} className="flex flex-1 flex-col items-center gap-1">
                    <div className="flex h-32 w-full items-end justify-center gap-1">
                      <div className="w-1/3 rounded-t bg-[#668568]" style={{ height: `${(t.spendInr / trendMax) * 100}%` }} title={`Spent ${formatInr(t.spendInr)}`} />
                      <div className="w-1/3 rounded-t bg-[#D76F65]" style={{ height: `${(t.wasteInr / trendMax) * 100}%` }} title={`Wasted ${formatInr(t.wasteInr)}`} />
                    </div>
                    <span className="text-[11px] text-[#788278]">{monthLabel(t.month, true)}</span>
                  </div>
                ))}
              </div>
            </Card>

            <Card className="flex gap-3 border-[#F9E5C9] bg-[#FDF4E7] p-4">
              <Lightbulb className="h-5 w-5 shrink-0 text-[#B47823]" />
              <p className="text-sm text-[#26372C]">{s.tip}</p>
            </Card>
          </div>
        )
      )}
    </>
  );
}

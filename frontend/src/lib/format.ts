import type { ExpiryStatus, Unit } from "@/services/types";

const inr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });
const inrPrecise = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", minimumFractionDigits: 2, maximumFractionDigits: 2 });

export function formatInr(value: number | null | undefined, precise = false): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  return (precise ? inrPrecise : inr).format(value);
}

export function formatQty(q: number | null | undefined, unit?: Unit | string): string {
  if (q === null || q === undefined) return "—";
  const n = Number(q);
  const text = Number.isInteger(n) ? String(n) : String(Math.round(n * 100) / 100);
  return unit ? `${text} ${unit}` : text;
}

/** Local (kitchen) date as YYYY-MM-DD. */
export function isoDate(d: Date = new Date()): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function addDays(base: string | Date, days: number): string {
  const d = typeof base === "string" ? new Date(base + "T00:00:00") : new Date(base);
  d.setDate(d.getDate() + days);
  return isoDate(d);
}

export function formatDate(iso: string | null | undefined, opts: Intl.DateTimeFormatOptions = { day: "numeric", month: "short" }): string {
  if (!iso) return "—";
  const d = iso.length === 10 ? new Date(iso + "T00:00:00") : new Date(iso);
  return d.toLocaleDateString("en-IN", opts);
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });
}

export function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const min = Math.round(diff / 60000);
  if (min < 1) return "just now";
  if (min < 60) return `${min} min ago`;
  const h = Math.round(min / 60);
  if (h < 24) return `${h} h ago`;
  const d = Math.round(h / 24);
  return d === 1 ? "yesterday" : `${d} days ago`;
}

/** Date-level expiry wording (never hour countdowns; expiry is SQL DATE precision). */
export function expiryLabel(status: ExpiryStatus, days: number): string {
  switch (status) {
    case "expired":
      return days === -1 ? "Expired yesterday" : `Expired ${Math.abs(days)}d ago`;
    case "expiring_today":
      return "Expires today";
    case "use_soon":
      return days === 1 ? "Expires tomorrow" : `${days} days left`;
    default:
      return `${days} days left`;
  }
}

export function greeting(): string {
  const h = new Date().getHours();
  if (h < 12) return "Good morning";
  if (h < 17) return "Good afternoon";
  return "Good evening";
}

export function currentMonth(): string {
  return isoDate().slice(0, 7);
}

export function shiftMonth(month: string, delta: number): string {
  const [y, m] = month.split("-").map(Number);
  const d = new Date(y, m - 1 + delta, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

export function monthLabel(month: string, short = false): string {
  const [y, m] = month.split("-").map(Number);
  return new Date(y, m - 1, 1).toLocaleDateString("en-IN", short ? { month: "short" } : { month: "long", year: "numeric" });
}

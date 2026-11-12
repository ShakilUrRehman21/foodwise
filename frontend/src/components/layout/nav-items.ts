import { BarChart3, Boxes, Camera, Home, Settings, ShoppingCart, Soup, TimerReset, UtensilsCrossed } from "lucide-react";

export const NAV_ITEMS = [
  { label: "Home", href: "/dashboard", icon: Home },
  { label: "Inventory", href: "/inventory", icon: Boxes },
  { label: "Scan", href: "/scan", icon: Camera },
  { label: "Expiry", href: "/expiry", icon: TimerReset },
  { label: "Recipes", href: "/recipes", icon: UtensilsCrossed },
  { label: "Leftovers", href: "/leftovers", icon: Soup },
  { label: "Shopping", href: "/shopping", icon: ShoppingCart },
  { label: "Analytics", href: "/analytics", icon: BarChart3 },
  { label: "Settings", href: "/settings", icon: Settings },
] as const;

export const MORE_ITEMS = NAV_ITEMS.filter((i) => ["/expiry", "/leftovers", "/shopping", "/analytics", "/settings"].includes(i.href));


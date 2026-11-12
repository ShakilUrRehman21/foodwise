import { Clock, Heart } from "lucide-react";
import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import type { RecipeSummary } from "@/services/types";

const READINESS = {
  ready_to_cook: { label: "Ready to cook", variant: "fresh" as const },
  almost_cookable: { label: "Almost there", variant: "warning" as const },
  not_ready: { label: "Needs shopping", variant: "secondary" as const },
};

export function RecipeCard({ recipe }: { recipe: RecipeSummary }) {
  const r = READINESS[recipe.readiness];
  return (
    <Link href={`/recipes/${recipe.slug}`} className="block">
      <Card className="flex h-full flex-col gap-2 p-4 hover:shadow-md">
        <div className="flex items-start justify-between gap-2">
          <h3 className="font-semibold text-[#26372C]">{recipe.title}</h3>
          {recipe.favorite && <Heart className="h-4 w-4 shrink-0 fill-[#D76F65] text-[#D76F65]" aria-label="Favourite" />}
        </div>
        <p className="text-xs text-[#788278]">
          <Clock className="mr-1 inline h-3 w-3" />
          {recipe.totalTimeMinutes} min · {recipe.cuisine} · {recipe.dietaryType}
        </p>
        <div className="flex flex-wrap items-center gap-1.5">
          <Badge variant={r.variant}>{r.label}</Badge>
          <span className="text-xs font-semibold text-[#26372C]">{recipe.matchPercent}% in stock</span>
        </div>
        <div className="mt-auto space-y-1 pt-1 text-xs">
          {recipe.usesExpiring.length > 0 && <p className="text-[#B47823]">Uses expiring: {recipe.usesExpiring.join(", ")}</p>}
          {recipe.missing.length > 0 && <p className="text-[#788278]">Missing: {recipe.missing.slice(0, 3).join(", ")}{recipe.missing.length > 3 ? "…" : ""}</p>}
        </div>
        <div className="h-1.5 w-full overflow-hidden rounded-full bg-[#F1EFEA]">
          <div className="h-full rounded-full bg-[#668568]" style={{ width: `${recipe.matchPercent}%` }} />
        </div>
      </Card>
    </Link>
  );
}

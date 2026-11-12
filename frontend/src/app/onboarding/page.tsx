"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Check, Leaf } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { SessionGate } from "@/components/layout/session-gate";
import { ExpiryDateField } from "@/components/features/expiry-date-field";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Chip, Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { kitchensKey, profileKey, useKitchen } from "@/hooks/use-kitchen";
import { DIETS } from "@/lib/constants";
import { errorMessage } from "@/lib/query";
import { cn } from "@/lib/utils";
import { catalogApi, inventoryApi, kitchensApi, usersApi } from "@/services/endpoints";
import type { DietaryPreference } from "@/services/types";

const STAPLES = ["Rice", "Whole Wheat Flour", "Toor Dal", "Moong Dal", "Cooking Oil", "Ghee", "Salt", "Sugar", "Tea Leaves", "Onion", "Potato", "Turmeric Powder"];

function Wizard() {
  const { kitchen, profile, kitchenId } = useKitchen();
  const router = useRouter();
  const qc = useQueryClient();
  const [step, setStep] = useState(0);
  const [name, setName] = useState(kitchen.name);
  const [diet, setDiet] = useState<DietaryPreference>(profile.preferences.dietaryType);
  const [staples, setStaples] = useState<string[]>([]);
  const [expiry, setExpiry] = useState("");
  const [busy, setBusy] = useState(false);

  const finish = async (withStaples: boolean) => {
    setBusy(true);
    try {
      if (name.trim() && name.trim() !== kitchen.name) await kitchensApi.rename(kitchenId, name.trim());
      await usersApi.updatePreferences({ dietaryType: diet });
      if (withStaples && staples.length) {
        let added = 0;
        for (const s of staples) {
          const [item] = await catalogApi.search(s, 1);
          if (!item) continue;
          await inventoryApi.create(kitchenId, {
            catalogItemId: item.id,
            name: item.nameEn,
            storageLocation: item.defaultLocation,
            quantity: 1,
            unit: item.defaultUnit,
            expiryDate: expiry,
          });
          added++;
        }
        toast.success(`Added ${added} pantry staples.`);
      }
      await usersApi.completeOnboarding();
      await Promise.all([qc.invalidateQueries({ queryKey: profileKey }), qc.invalidateQueries({ queryKey: kitchensKey })]);
      router.replace("/dashboard");
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#F8F7F2] p-4">
      <Card className="w-full max-w-lg p-6 sm:p-8">
        <div className="mb-6 flex items-center justify-between">
          <span className="flex items-center gap-2 font-bold text-[#26372C]">
            <Leaf className="h-5 w-5 text-[#668568]" /> FoodWise
          </span>
          <span className="text-xs text-[#788278]">Step {step + 1} of 3</span>
        </div>
        <div className="mb-6 flex gap-1.5">
          {[0, 1, 2].map((i) => (
            <span key={i} className={cn("h-1.5 flex-1 rounded-full", i <= step ? "bg-[#668568]" : "bg-[#E6E3DA]")} />
          ))}
        </div>

        {step === 0 && (
          <div className="space-y-4">
            <h1 className="text-2xl font-bold text-[#26372C]">Name your kitchen</h1>
            <p className="text-sm text-[#788278]">You can share it with flatmates or family later.</p>
            <Field label="Kitchen name">
              <Input autoFocus value={name} onChange={(e) => setName(e.target.value)} />
            </Field>
            <Button className="w-full" disabled={!name.trim()} onClick={() => setStep(1)}>
              Continue
            </Button>
          </div>
        )}

        {step === 1 && (
          <div className="space-y-4">
            <h1 className="text-2xl font-bold text-[#26372C]">How do you eat?</h1>
            <p className="text-sm text-[#788278]">We&apos;ll only suggest recipes that fit.</p>
            <div className="space-y-2">
              {DIETS.map((d) => (
                <button
                  key={d.value}
                  type="button"
                  onClick={() => setDiet(d.value)}
                  className={cn(
                    "flex w-full items-center justify-between rounded-xl border px-4 py-3 text-left",
                    diet === d.value ? "border-[#668568] bg-[#EEF3EE]" : "border-[#E6E3DA] bg-white hover:bg-[#F8F7F2]",
                  )}
                >
                  <span>
                    <span className="block text-sm font-semibold text-[#26372C]">{d.label}</span>
                    <span className="block text-xs text-[#788278]">{d.hint}</span>
                  </span>
                  {diet === d.value && <Check className="h-5 w-5 text-[#668568]" />}
                </button>
              ))}
            </div>
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setStep(0)}>
                Back
              </Button>
              <Button className="flex-1" onClick={() => setStep(2)}>
                Continue
              </Button>
            </div>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-4">
            <h1 className="text-2xl font-bold text-[#26372C]">Quick-add your staples</h1>
            <p className="text-sm text-[#788278]">Tap what you have right now. You can adjust quantities later.</p>
            <div className="flex flex-wrap gap-2">
              {STAPLES.map((s) => (
                <Chip key={s} active={staples.includes(s)} onClick={() => setStaples(staples.includes(s) ? staples.filter((x) => x !== s) : [...staples, s])}>
                  {staples.includes(s) && <Check className="h-3 w-3" />} {s}
                </Chip>
              ))}
            </div>
            {staples.length > 0 && (
              <div className="rounded-xl bg-[#F8F7F2] p-3">
                <ExpiryDateField
                  label="Roughly when do these expire?"
                  value={expiry}
                  onChange={setExpiry}
                  quick={[
                    { label: "+1 month", days: 30 },
                    { label: "+3 months", days: 91 },
                    { label: "+6 months", days: 182 },
                  ]}
                />
              </div>
            )}
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setStep(1)}>
                Back
              </Button>
              <Button className="flex-1" disabled={busy || (staples.length > 0 && !expiry)} onClick={() => finish(true)}>
                {busy ? "Setting up…" : staples.length ? `Add ${staples.length} & finish` : "Finish"}
              </Button>
            </div>
            <button type="button" className="w-full text-center text-sm text-[#788278] hover:underline" disabled={busy} onClick={() => finish(false)}>
              Skip for now
            </button>
          </div>
        )}
      </Card>
    </div>
  );
}

export default function OnboardingPage() {
  return (
    <SessionGate requireOnboarded={false}>
      <Wizard />
    </SessionGate>
  );
}

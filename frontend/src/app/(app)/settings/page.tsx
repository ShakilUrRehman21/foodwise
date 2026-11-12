"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, LogOut, Trash2 } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Field, Select } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { kitchensKey, profileKey, useKitchen } from "@/hooks/use-kitchen";
import { kq } from "@/hooks/use-kitchen-mutation";
import { useAppAuth } from "@/lib/auth";
import { DIETS, DIGEST_TIMES } from "@/lib/constants";
import { errorMessage } from "@/lib/query";
import { inventoryApi, kitchensApi, usersApi } from "@/services/endpoints";
import type { DietaryPreference } from "@/services/types";

function Section({ title, description, children }: { title: string; description?: string; children: React.ReactNode }) {
  return (
    <Card className="p-5">
      <h2 className="font-semibold text-[#26372C]">{title}</h2>
      {description && <p className="mb-4 mt-0.5 text-sm text-[#788278]">{description}</p>}
      <div className={description ? "" : "mt-4"}>{children}</div>
    </Card>
  );
}

export default function SettingsPage() {
  const { profile, kitchen, kitchenId } = useKitchen();
  const { signOut, mode } = useAppAuth();
  const qc = useQueryClient();
  const [firstName, setFirstName] = useState(profile.firstName ?? "");
  const [lastName, setLastName] = useState(profile.lastName ?? "");
  const [kitchenName, setKitchenName] = useState(kitchen.name);
  const [newKitchen, setNewKitchen] = useState("");
  const [memberEmail, setMemberEmail] = useState("");
  const [resetOpen, setResetOpen] = useState(false);
  const [resetText, setResetText] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const members = useQuery({ queryKey: kq(kitchenId, "members"), queryFn: () => kitchensApi.members(kitchenId) });
  const isOwner = kitchen.role === "owner";
  const prefs = profile.preferences;

  const run = async (key: string, fn: () => Promise<unknown>, success?: string) => {
    setBusy(key);
    try {
      await fn();
      await Promise.all([qc.invalidateQueries({ queryKey: profileKey }), qc.invalidateQueries({ queryKey: kitchensKey }), qc.invalidateQueries({ queryKey: ["k", kitchenId] })]);
      if (success) toast.success(success);
      return true;
    } catch (e) {
      toast.error(errorMessage(e));
      return false;
    } finally {
      setBusy(null);
    }
  };

  const savePref = (body: Parameters<typeof usersApi.updatePreferences>[0]) => run("prefs", () => usersApi.updatePreferences(body), "Preferences saved.");

  const exportCsv = () =>
    run("export", async () => {
      const res = await inventoryApi.exportCsv(kitchenId);
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "foodwise-inventory.csv";
      a.click();
      URL.revokeObjectURL(url);
    });

  return (
    <div className="max-w-3xl">
      <PageHeader title="Settings" />
      <div className="space-y-4">
        <Section title="Profile">
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="First name">
              <Input value={firstName} onChange={(e) => setFirstName(e.target.value)} />
            </Field>
            <Field label="Last name">
              <Input value={lastName} onChange={(e) => setLastName(e.target.value)} />
            </Field>
          </div>
          <p className="mt-2 text-xs text-[#788278]">
            {profile.emailPending ? "Your email will appear once your account finishes syncing." : profile.email}
            {mode === "dev" && " · local development sign-in"}
          </p>
          <Button className="mt-3" size="sm" disabled={busy === "profile"} onClick={() => run("profile", () => usersApi.updateMe({ firstName, lastName }), "Profile updated.")}>
            Save profile
          </Button>
        </Section>

        <Section title="Kitchen" description="Kitchens are shared with everyone you add.">
          <div className="flex gap-2">
            <Input value={kitchenName} onChange={(e) => setKitchenName(e.target.value)} aria-label="Kitchen name" />
            <Button variant="secondary" disabled={busy === "rename" || !kitchenName.trim()} onClick={() => run("rename", () => kitchensApi.rename(kitchenId, kitchenName.trim()), "Kitchen renamed.")}>
              Rename
            </Button>
          </div>
          <h3 className="mb-2 mt-5 text-sm font-semibold text-[#26372C]">Members</h3>
          <ul className="divide-y divide-[#F1EFEA] rounded-xl border border-[#E6E3DA]">
            {members.data?.map((m) => (
              <li key={m.userId} className="flex items-center justify-between gap-2 px-3 py-2.5 text-sm">
                <span className="min-w-0">
                  <span className="block truncate font-medium text-[#26372C]">{[m.firstName, m.lastName].filter(Boolean).join(" ") || m.email}</span>
                  <span className="block truncate text-xs text-[#788278]">{m.email}</span>
                </span>
                <span className="flex items-center gap-2">
                  <Badge variant={m.role === "owner" ? "primary" : "secondary"}>{m.role}</Badge>
                  {(isOwner || m.userId === profile.id) && m.role !== "owner" && (
                    <Button size="sm" variant="ghost" onClick={() => run("remove", () => kitchensApi.removeMember(kitchenId, m.userId), "Member removed.")}>
                      {m.userId === profile.id ? "Leave" : "Remove"}
                    </Button>
                  )}
                </span>
              </li>
            ))}
          </ul>
          {isOwner && (
            <form
              className="mt-3 flex gap-2"
              onSubmit={async (e) => {
                e.preventDefault();
                if (await run("add", () => kitchensApi.addMember(kitchenId, memberEmail.trim()), "Member added.")) setMemberEmail("");
              }}
            >
              <Input type="email" required placeholder="flatmate@email.com" value={memberEmail} onChange={(e) => setMemberEmail(e.target.value)} aria-label="Member email" />
              <Button type="submit" variant="secondary" disabled={busy === "add"}>
                Add
              </Button>
            </form>
          )}
          <p className="mt-2 text-xs text-[#788278]">Flatmates need to sign up first; then add them by email.</p>
          <h3 className="mb-2 mt-5 text-sm font-semibold text-[#26372C]">Create another kitchen</h3>
          <form
            className="flex gap-2"
            onSubmit={async (e) => {
              e.preventDefault();
              if (await run("create", () => kitchensApi.create(newKitchen.trim()), "Kitchen created. Switch to it from the kitchen menu.")) setNewKitchen("");
            }}
          >
            <Input placeholder="e.g. Parents' home" value={newKitchen} onChange={(e) => setNewKitchen(e.target.value)} aria-label="New kitchen name" />
            <Button type="submit" variant="secondary" disabled={!newKitchen.trim() || busy === "create"}>
              Create
            </Button>
          </form>
        </Section>

        <Section title="Food preferences" description="Used to filter recipe suggestions.">
          <Select value={prefs.dietaryType} onChange={(e) => savePref({ dietaryType: e.target.value as DietaryPreference })} aria-label="Diet">
            {DIETS.map((d) => (
              <option key={d.value} value={d.value}>
                {d.label} — {d.hint}
              </option>
            ))}
          </Select>
        </Section>

        <Section title="Notifications" description="In-app alerts in the bell. One morning digest per day, never spam.">
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Warn me this many days before expiry">
              <Select value={prefs.notifyExpiryDaysBefore} onChange={(e) => savePref({ notifyExpiryDaysBefore: Number(e.target.value) })}>
                {[1, 2, 3, 4, 5, 7].map((d) => (
                  <option key={d} value={d}>
                    {d} day{d > 1 ? "s" : ""}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Daily digest time">
              <Select value={prefs.notifyDailyDigestTime.slice(0, 5)} onChange={(e) => savePref({ notifyDailyDigestTime: e.target.value })}>
                {DIGEST_TIMES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
        </Section>

        <Section title="Your data">
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" disabled={busy === "export"} onClick={exportCsv}>
              <Download className="h-4 w-4" /> Export inventory (CSV)
            </Button>
            {isOwner && (
              <Button variant="destructive" onClick={() => setResetOpen(true)}>
                <Trash2 className="h-4 w-4" /> Reset kitchen data
              </Button>
            )}
            <Button variant="ghost" onClick={() => signOut().then(() => (window.location.href = "/"))}>
              <LogOut className="h-4 w-4" /> Sign out
            </Button>
          </div>
        </Section>
      </div>

      <Dialog open={resetOpen} onOpenChange={setResetOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reset {kitchen.name}?</DialogTitle>
            <DialogDescription>
              This permanently deletes all inventory, leftovers, shopping lists, meal plans, scans and analytics for this kitchen. Members stay.
            </DialogDescription>
          </DialogHeader>
          <Field label='Type "RESET" to confirm'>
            <Input value={resetText} onChange={(e) => setResetText(e.target.value)} />
          </Field>
          <DialogFooter className="gap-2">
            <Button variant="secondary" onClick={() => setResetOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={resetText !== "RESET" || busy === "reset"}
              onClick={async () => {
                if (await run("reset", () => kitchensApi.reset(kitchenId), "Kitchen data reset.")) {
                  setResetOpen(false);
                  setResetText("");
                }
              }}
            >
              Delete everything
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

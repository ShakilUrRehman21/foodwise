import { expect, type APIRequestContext, type Page } from "@playwright/test";

export const TOKEN_KEY = "foodwise.devToken";

export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@e2e.foodwise.test`;
}

export function isoDate(offsetDays = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${m}-${day}`;
}

export interface Session {
  token: string;
  expiresAt: string;
  kitchenId: string;
  headers: Record<string, string>;
}

/** Signs a fresh user in through the dev token endpoint and finishes onboarding through the API. */
export async function apiSession(request: APIRequestContext, email: string, firstName = "Tester"): Promise<Session> {
  const tokenRes = await request.post("/api/v1/dev/token", { data: { email, firstName } });
  expect(tokenRes.ok(), await tokenRes.text()).toBeTruthy();
  const { data: tok } = await tokenRes.json();
  const headers = { Authorization: `Bearer ${tok.token}` };
  expect((await request.get("/api/v1/users/me", { headers })).ok()).toBeTruthy();
  expect((await request.post("/api/v1/users/me/onboarding-complete", { headers })).ok()).toBeTruthy();
  const kitchens = await (await request.get("/api/v1/kitchens", { headers })).json();
  return { token: tok.token, expiresAt: tok.expiresAt, kitchenId: kitchens.data[0].id, headers };
}

/** Makes the browser start already signed in as the given session. */
export async function useSession(page: Page, s: Session): Promise<void> {
  await page.addInitScript(
    ([key, value]) => window.localStorage.setItem(key, value),
    [TOKEN_KEY, JSON.stringify({ token: s.token, expiresAt: s.expiresAt })] as const,
  );
}

export async function catalogId(request: APIRequestContext, s: Session, name: string): Promise<string> {
  const res = await request.get("/api/v1/catalog/search", { headers: s.headers, params: { q: name, limit: 5 } });
  const items: { id: string; nameEn: string }[] = (await res.json()).data;
  const hit = items.find((i) => i.nameEn.toLowerCase() === name.toLowerCase()) ?? items[0];
  expect(hit, `catalog item ${name}`).toBeTruthy();
  return hit.id;
}

export async function addBatch(
  request: APIRequestContext,
  s: Session,
  b: { name: string; quantity: number; unit: string; location: string; expiresInDays: number; costInr?: number },
): Promise<string> {
  const res = await request.post(`/api/v1/kitchens/${s.kitchenId}/inventory/batches`, {
    headers: s.headers,
    data: {
      catalogItemId: await catalogId(request, s, b.name),
      name: b.name,
      storageLocation: b.location,
      quantity: b.quantity,
      unit: b.unit,
      expiryDate: isoDate(b.expiresInDays),
      costInr: b.costInr ?? null,
    },
  });
  expect(res.status(), await res.text()).toBe(201);
  return (await res.json()).data.id;
}

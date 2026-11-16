import { expect, test } from "@playwright/test";
import { addBatch, apiSession, uniqueEmail, useSession } from "./helpers";

test("cooking a ready recipe deducts its ingredients from inventory", async ({ page, request }) => {
  const s = await apiSession(request, uniqueEmail("journey2"));
  const paneerId = await addBatch(request, s, { name: "Paneer", quantity: 500, unit: "g", location: "fridge", expiresInDays: 1, costInr: 200 });
  await addBatch(request, s, { name: "Spinach", quantity: 2, unit: "bunch", location: "fridge", expiresInDays: 2 });
  await addBatch(request, s, { name: "Onion", quantity: 1000, unit: "g", location: "pantry", expiresInDays: 20 });
  await addBatch(request, s, { name: "Tomato", quantity: 500, unit: "g", location: "fridge", expiresInDays: 5 });
  await useSession(page, s);

  // Palak Paneer uses the paneer that expires tomorrow, so it is suggested.
  await page.goto("/recipes");
  await expect(page.getByText("Palak Paneer").first()).toBeVisible();

  await page.goto("/recipes/palak-paneer");
  await expect(page.getByRole("heading", { name: "Palak Paneer" })).toBeVisible();
  await page.getByRole("button", { name: "I cooked this" }).click();
  const confirm = page.getByRole("dialog");
  await expect(confirm.getByText("−200 g").first()).toBeVisible();
  await confirm.getByRole("button", { name: "Update inventory" }).click();

  const done = page.getByRole("dialog", { name: "Inventory updated" });
  await expect(done).toBeVisible();
  await done.getByRole("button", { name: /Done|Not now/ }).click();

  // 2 servings of Palak Paneer use 200 g of the 500 g paneer.
  const res = await request.get(`/api/v1/kitchens/${s.kitchenId}/inventory/batches`, { headers: s.headers });
  const paneer = (await res.json()).data.find((b: { id: string }) => b.id === paneerId);
  expect(Number(paneer.currentQuantity)).toBe(300);

  await page.goto("/inventory");
  await expect(page.getByText("300 g")).toBeVisible();
});

test("cooking more than you have is refused and nothing is deducted", async ({ request }) => {
  const s = await apiSession(request, uniqueEmail("journey2b"));
  const paneerId = await addBatch(request, s, { name: "Paneer", quantity: 100, unit: "g", location: "fridge", expiresInDays: 1 });
  await addBatch(request, s, { name: "Spinach", quantity: 1, unit: "bunch", location: "fridge", expiresInDays: 2 });

  const detail = await request.get(`/api/v1/kitchens/${s.kitchenId}/recipes/palak-paneer`, { headers: s.headers });
  const recipeId = (await detail.json()).data.summary.id;
  const cook = await request.post(`/api/v1/kitchens/${s.kitchenId}/recipes/${recipeId}/cook`, {
    headers: s.headers,
    data: { servings: 2, mode: "STRICT" },
  });
  expect(cook.status()).toBe(409);

  const res = await request.get(`/api/v1/kitchens/${s.kitchenId}/inventory/batches`, { headers: s.headers });
  const paneer = (await res.json()).data.find((b: { id: string }) => b.id === paneerId);
  expect(Number(paneer.currentQuantity)).toBe(100);
});

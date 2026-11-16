import { expect, test } from "@playwright/test";
import { apiSession, uniqueEmail, useSession } from "./helpers";

test("shopping list items can be bought and moved into the kitchen", async ({ page, request }) => {
  const s = await apiSession(request, uniqueEmail("journey3"));
  await useSession(page, s);

  await page.goto("/shopping");
  await expect(page.getByText("Your list is empty")).toBeVisible();

  // Add tomatoes from the catalog.
  await page.getByPlaceholder("Add an item, e.g. tomatoes").fill("Tomato");
  await page.getByRole("option", { name: /^Tomato/ }).first().click();
  await page.getByLabel("Quantity").fill("500");
  await page.getByRole("button", { name: "Add", exact: true }).click();
  await expect(page.getByText("500 g")).toBeVisible();

  // Tick it off in the shop, then add it to the kitchen with an expiry date.
  // The checkbox reflects server state, so it only flips once the update is saved.
  const bought = page.getByLabel("Bought Tomato");
  await bought.click();
  await expect(bought).toBeChecked();
  await expect(page.getByText("in your basket")).toBeVisible();
  await page.getByRole("button", { name: "Add to kitchen" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("button", { name: "Set all expiry dates" })).toBeDisabled();
  const d = new Date();
  d.setDate(d.getDate() + 5);
  const expiry = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  await dialog.getByLabel("Expiry date").fill(expiry);
  await dialog.getByLabel("Price").fill("40");
  await dialog.getByRole("button", { name: "Add 1 items" }).click();
  await expect(dialog).toBeHidden();

  // The tomatoes are now in inventory.
  const res = await request.get(`/api/v1/kitchens/${s.kitchenId}/inventory/batches`, { headers: s.headers });
  const batches: { name: string; currentQuantity: number; expiryDate: string }[] = (await res.json()).data;
  const tomato = batches.find((b) => b.name === "Tomato");
  expect(tomato).toBeTruthy();
  expect(Number(tomato!.currentQuantity)).toBe(500);
  expect(tomato!.expiryDate).toBe(expiry);
});

test("a user cannot read another kitchen's data", async ({ request }) => {
  const alice = await apiSession(request, uniqueEmail("alice"));
  const bob = await apiSession(request, uniqueEmail("bob"));
  for (const path of ["inventory/batches", "shopping", "leftovers", "analytics/monthly-summary", "dashboard/pulse"]) {
    const res = await request.get(`/api/v1/kitchens/${alice.kitchenId}/${path}`, { headers: bob.headers });
    expect(res.status(), path).toBe(403);
  }
});

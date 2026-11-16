import { expect, test } from "@playwright/test";
import { uniqueEmail } from "./helpers";

test("new user signs up, finishes onboarding, adds food and logs waste", async ({ page }) => {
  // Sign up with the local dev sign-in.
  await page.goto("/sign-up");
  await page.getByPlaceholder("you@example.com").fill(uniqueEmail("journey1"));
  await page.getByPlaceholder("Rohan").fill("Asha");
  await page.getByRole("button", { name: "Continue" }).click();

  // First-time users land on the onboarding wizard.
  await expect(page).toHaveURL(/\/onboarding/);
  await expect(page.getByRole("heading", { name: "Name your kitchen" })).toBeVisible();
  await page.getByRole("textbox").fill("Asha's Test Kitchen");
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page.getByRole("heading", { name: "How do you eat?" })).toBeVisible();
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page.getByRole("heading", { name: "Quick-add your staples" })).toBeVisible();
  await page.getByRole("button", { name: "Skip for now" }).click();

  // Dashboard greets the user by name and shows the renamed kitchen.
  await expect(page).toHaveURL(/\/dashboard/);
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Asha");
  await expect(page.getByText("Asha's Test Kitchen").first()).toBeVisible();

  // Add paneer from the catalog autosuggest.
  await page.goto("/inventory");
  await page.getByRole("button", { name: "Add food" }).first().click();
  const dialog = page.getByRole("dialog");
  await dialog.getByPlaceholder("Search paneer, dal, milk…").fill("Paneer");
  await dialog.getByRole("option", { name: /^Paneer/ }).first().click();
  await dialog.locator('input[type="number"]').first().fill("400");

  // Expiry is mandatory: saving without it shows a validation error.
  await dialog.getByRole("button", { name: "Save item" }).click();
  await expect(dialog.getByText("Please choose an expiry date.")).toBeVisible();

  await dialog.getByRole("button", { name: "+3 days" }).click();
  await dialog.getByPlaceholder("0").fill("180");
  await dialog.getByRole("button", { name: "Save item" }).click();
  await expect(page.getByText("Paneer added to fridge.")).toBeVisible();
  await expect(page.getByText("400 g")).toBeVisible();

  // Throw it away and check the rupee loss is logged.
  await page.getByRole("button", { name: "More actions for Paneer" }).click();
  await page.getByRole("menuitem", { name: "Discard & log waste" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Discard & log waste" }).click();
  await expect(page.getByText(/Logged .*180.* of waste\./)).toBeVisible();
  await expect(page.getByText("400 g")).toHaveCount(0);
});

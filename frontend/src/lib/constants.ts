import type { DietaryPreference, InventoryUnit, LeftoverType, MealSlot, StorageLocation, WasteReason } from "@/services/types";

export const INVENTORY_UNITS: InventoryUnit[] = ["g", "kg", "ml", "L", "pcs", "pkt", "bunch"];

export const LOCATIONS: { value: StorageLocation; label: string; emoji: string }[] = [
  { value: "fridge", label: "Fridge", emoji: "❄️" },
  { value: "freezer", label: "Freezer", emoji: "🧊" },
  { value: "pantry", label: "Pantry", emoji: "🥫" },
];

export const WASTE_REASONS: { value: WasteReason; label: string }[] = [
  { value: "expired", label: "Expired" },
  { value: "spoiled", label: "Spoiled / went bad" },
  { value: "forgotten_leftover", label: "Forgotten leftover" },
  { value: "cooked_too_much", label: "Cooked too much" },
  { value: "bad_taste", label: "Didn't taste right" },
];

export const LEFTOVER_TYPES: { value: LeftoverType; label: string }[] = [
  { value: "dal", label: "Dal / lentils" },
  { value: "rice", label: "Rice" },
  { value: "sabzi", label: "Sabzi" },
  { value: "roti", label: "Roti / paratha" },
  { value: "curry", label: "Curry / gravy" },
  { value: "other", label: "Other" },
];

export const DIETS: { value: DietaryPreference; label: string; hint: string }[] = [
  { value: "vegetarian", label: "Vegetarian", hint: "Veg, paneer and plant-based dishes" },
  { value: "eggetarian", label: "Eggetarian", hint: "Vegetarian plus eggs" },
  { value: "omnivore", label: "Non-vegetarian", hint: "Chicken, mutton, fish and eggs too" },
  { value: "jain", label: "Jain", hint: "No onion, garlic or root vegetables" },
  { value: "vegan", label: "Vegan", hint: "No dairy, eggs or meat" },
];

export const MEAL_SLOTS: { value: MealSlot; label: string }[] = [
  { value: "breakfast", label: "Breakfast" },
  { value: "lunch", label: "Lunch" },
  { value: "snack", label: "Snack" },
  { value: "dinner", label: "Dinner" },
];

/** 15-minute digest times (matches the backend scheduler precision). */
export const DIGEST_TIMES: string[] = Array.from({ length: 24 * 4 }, (_, i) =>
  `${String(Math.floor(i / 4)).padStart(2, "0")}:${String((i % 4) * 15).padStart(2, "0")}`,
);

export const ACTIVE_KITCHEN_KEY = "foodwise.activeKitchen";
export const DEV_TOKEN_KEY = "foodwise.devToken";

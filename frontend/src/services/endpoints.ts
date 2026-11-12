import { api } from "./api-client";
import type {
  Batch, CatalogItem, Category, CookResult, InventoryUnit, Kitchen, Leftover, LeftoverType, LowStockItem, MealPlan,
  MealSlot, Member, MonthlySummary, NotificationItem, NotificationPage, Preferences, Profile, Pulse, RecipeDetail,
  RecipeSummary, RepurposeIdea, ScanSession, ShoppingList, StorageLocation, Timeline, TopWasted, CategorySlice,
  WasteReason, DietaryPreference,
} from "./types";

const k = (kitchenId: string) => `/api/v1/kitchens/${kitchenId}`;

// ---------------------------------------------------------------- users & kitchens
export const usersApi = {
  me: () => api.get<Profile>("/api/v1/users/me"),
  updateMe: (body: { firstName?: string; lastName?: string }) => api.patch<Profile>("/api/v1/users/me", body),
  completeOnboarding: () => api.post<Profile>("/api/v1/users/me/onboarding-complete"),
  updatePreferences: (body: Partial<{
    dietaryType: DietaryPreference; notifyExpiryDaysBefore: number; notifyDailyDigestTime: string;
    emailNotificationsEnabled: boolean; pushNotificationsEnabled: boolean;
  }>) => api.patch<Preferences>("/api/v1/users/preferences", body),
  devToken: (body: { email: string; firstName?: string }) =>
    api.post<{ token: string; expiresAt: string }>("/api/v1/dev/token", body),
};

export const kitchensApi = {
  list: () => api.get<Kitchen[]>("/api/v1/kitchens"),
  create: (name: string) => api.post<Kitchen>("/api/v1/kitchens", { name }),
  rename: (id: string, name: string) => api.patch<Kitchen>(`/api/v1/kitchens/${id}`, { name }),
  members: (id: string) => api.get<Member[]>(`${k(id)}/members`),
  addMember: (id: string, email: string) => api.post<Member[]>(`${k(id)}/members`, { email }),
  removeMember: (id: string, userId: string) => api.del<void>(`${k(id)}/members/${userId}`),
  reset: (id: string) => api.post<void>(`${k(id)}/reset`),
};

// ---------------------------------------------------------------- catalog
export const catalogApi = {
  search: (q: string, limit = 8, signal?: AbortSignal) => api.get<CatalogItem[]>("/api/v1/catalog/search", { q, limit }, signal),
  categories: () => api.get<Category[]>("/api/v1/catalog/categories"),
};

// ---------------------------------------------------------------- inventory
export interface CreateBatchInput {
  catalogItemId?: string | null;
  name?: string;
  categoryId?: string | null;
  storageLocation: StorageLocation;
  quantity: number;
  unit: InventoryUnit;
  purchaseDate?: string;
  expiryDate?: string | null;
  costInr?: number | null;
  storeName?: string;
}

export interface InventoryFilter {
  location?: StorageLocation;
  categoryId?: string;
  q?: string;
  sort?: "expiry_asc" | "expiry_desc" | "added_desc" | "name_asc";
  leftovers?: boolean;
}

export const inventoryApi = {
  list: (kid: string, f: InventoryFilter = {}) => api.get<Batch[]>(`${k(kid)}/inventory/batches`, { ...f }),
  create: (kid: string, body: CreateBatchInput) => api.post<Batch>(`${k(kid)}/inventory/batches`, body),
  update: (kid: string, id: string, body: { name?: string; categoryId?: string; expiryDate?: string; costInr?: number; notes?: string }) =>
    api.patch<Batch>(`${k(kid)}/inventory/batches/${id}`, body),
  use: (kid: string, id: string, delta: number) => api.patch<Batch>(`${k(kid)}/inventory/batches/${id}/quantity`, { delta }),
  setQuantity: (kid: string, id: string, quantity: number) =>
    api.patch<Batch>(`${k(kid)}/inventory/batches/${id}/quantity`, { quantity }),
  consume: (kid: string, id: string) => api.post<Batch>(`${k(kid)}/inventory/batches/${id}/consume`),
  move: (kid: string, id: string, location: StorageLocation, expiryDate?: string) =>
    api.patch<Batch>(`${k(kid)}/inventory/batches/${id}/location`, { location, expiryDate: expiryDate || undefined }),
  discard: (kid: string, id: string, reason?: WasteReason, quantity?: number) =>
    api.del<{ batch: Batch; estimatedLossInr: number; reason: WasteReason }>(`${k(kid)}/inventory/batches/${id}`, { reason, quantity }),
  lowStock: (kid: string) => api.get<LowStockItem[]>(`${k(kid)}/inventory/low-stock`),
  exportCsv: (kid: string) => api.download(`${k(kid)}/inventory/export.csv`),
};

export const expiryApi = {
  timeline: (kid: string) => api.get<Timeline>(`${k(kid)}/expiry/timeline`),
};

export const dashboardApi = {
  pulse: (kid: string) => api.get<Pulse>(`${k(kid)}/dashboard/pulse`),
};

export const notificationsApi = {
  list: (kid: string) => api.get<NotificationPage>(`${k(kid)}/notifications`, { size: 20 }),
  read: (kid: string, id: string) => api.patch<NotificationItem>(`${k(kid)}/notifications/${id}/read`),
  readAll: (kid: string) => api.post<{ updated: number }>(`${k(kid)}/notifications/read-all`),
};

// ---------------------------------------------------------------- scan
export interface ConfirmScanItem {
  itemName: string;
  catalogItemId: string | null;
  categoryId: string | null;
  quantity: number;
  unit: InventoryUnit;
  storageLocation: StorageLocation;
  expiryDate: string;
  priceInr: number | null;
}

export const scanApi = {
  upload: (kid: string, file: File, scanType: "receipt" | "grocery_haul") => {
    const form = new FormData();
    form.append("file", file);
    return api.upload<ScanSession>(`${k(kid)}/scans`, form, { scanType });
  },
  history: (kid: string) => api.get<ScanSession[]>(`${k(kid)}/scans`, { limit: 10 }),
  confirm: (kid: string, id: string, body: { items: ConfirmScanItem[]; storeName?: string | null; purchaseDate?: string | null }) =>
    api.post<{ session: ScanSession; created: Batch[] }>(`${k(kid)}/scans/${id}/confirm`, body),
  reject: (kid: string, id: string) => api.post<ScanSession>(`${k(kid)}/scans/${id}/reject`),
};

// ---------------------------------------------------------------- recipes & meal plans
export interface RecipeQuery {
  diet?: string;
  maxCookTime?: number;
  filter?: "READY_ONLY" | "EXPIRING" | "FAVORITES";
  ingredient?: string;
  q?: string;
  limit?: number;
}

export const recipesApi = {
  recommendations: (kid: string, query: RecipeQuery) => api.get<RecipeSummary[]>(`${k(kid)}/recipes/recommendations`, { ...query }),
  detail: (kid: string, slug: string, servings?: number) => api.get<RecipeDetail>(`${k(kid)}/recipes/${slug}`, { servings }),
  cook: (kid: string, id: string, body: { servings: number; mode: "STRICT" | "AVAILABLE_ONLY"; mealSlot?: MealSlot }) =>
    api.post<CookResult>(`${k(kid)}/recipes/${id}/cook`, body),
  favorite: (kid: string, id: string, on: boolean) =>
    on ? api.post<{ favorite: boolean }>(`${k(kid)}/recipes/${id}/favorite`) : api.del<{ favorite: boolean }>(`${k(kid)}/recipes/${id}/favorite`),
};

export const mealPlanApi = {
  week: (kid: string, start: string) => api.get<MealPlan[]>(`${k(kid)}/meal-plans`, { start, days: 7 }),
  upsert: (kid: string, body: { planDate: string; mealSlot: MealSlot; recipeId?: string; customMealTitle?: string }) =>
    api.post<MealPlan>(`${k(kid)}/meal-plans`, body),
  setStatus: (kid: string, id: string, status: MealPlan["status"]) => api.patch<MealPlan>(`${k(kid)}/meal-plans/${id}/status`, { status }),
  remove: (kid: string, id: string) => api.del<void>(`${k(kid)}/meal-plans/${id}`),
};

// ---------------------------------------------------------------- leftovers
export const leftoversApi = {
  list: (kid: string) => api.get<Leftover[]>(`${k(kid)}/leftovers`),
  log: (kid: string, body: {
    dishName: string; leftoverType: LeftoverType; quantity: number; unit: InventoryUnit; cookedAt?: string;
    expiryDate: string; storageLocation?: StorageLocation; containerNote?: string;
  }) => api.post<Leftover>(`${k(kid)}/leftovers`, body),
  ideas: (kid: string, id: string) => api.get<RepurposeIdea[]>(`${k(kid)}/leftovers/${id}/repurpose`),
  consume: (kid: string, id: string) => api.patch<Batch>(`${k(kid)}/leftovers/${id}/consume`, {}),
  discard: (kid: string, id: string) => api.post<Batch>(`${k(kid)}/leftovers/${id}/discard`),
};

// ---------------------------------------------------------------- shopping
export interface AddShoppingInput {
  catalogItemId?: string | null;
  name?: string;
  quantity?: number;
  unit?: InventoryUnit;
  estimatedCostInr?: number | null;
}

interface AddResult {
  list: ShoppingList;
  added: number;
  alreadyOnList: number;
}

export const shoppingApi = {
  get: (kid: string) => api.get<ShoppingList>(`${k(kid)}/shopping`),
  add: (kid: string, body: AddShoppingInput) => api.post<AddResult>(`${k(kid)}/shopping/items`, body),
  fromRecipe: (kid: string, recipeId: string, catalogItemIds: string[]) =>
    api.post<AddResult>(`${k(kid)}/shopping/from-recipe/${recipeId}`, { catalogItemIds }),
  addLowStock: (kid: string) => api.post<AddResult>(`${k(kid)}/shopping/low-stock`),
  check: (kid: string, itemId: string, checked: boolean) => api.patch<ShoppingList>(`${k(kid)}/shopping/items/${itemId}/check`, { checked }),
  remove: (kid: string, itemId: string) => api.del<ShoppingList>(`${k(kid)}/shopping/items/${itemId}`),
  clearChecked: (kid: string) => api.post<ShoppingList>(`${k(kid)}/shopping/clear-checked`),
  importToInventory: (kid: string, listId: string, body: {
    items: { itemId: string; storageLocation: StorageLocation; expiryDate: string; quantity?: number; unit?: InventoryUnit; costInr?: number | null }[];
    storeName?: string;
  }) => api.post<{ created: Batch[]; list: ShoppingList }>(`${k(kid)}/shopping/${listId}/import-to-inventory`, body),
};

// ---------------------------------------------------------------- analytics
export const analyticsApi = {
  summary: (kid: string, month: string) => api.get<MonthlySummary>(`${k(kid)}/analytics/monthly-summary`, { month }),
  breakdown: (kid: string, month: string) => api.get<CategorySlice[]>(`${k(kid)}/analytics/waste-breakdown`, { month }),
  topWasted: (kid: string, month: string) => api.get<TopWasted[]>(`${k(kid)}/analytics/top-wasted`, { month, limit: 5 }),
};

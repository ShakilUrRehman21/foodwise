// Types mirroring the Spring Boot API DTOs. Enum values are the lowercase strings used by the API.

export type StorageLocation = "fridge" | "freezer" | "pantry";
export type Unit = "g" | "kg" | "ml" | "L" | "pcs" | "pkt" | "bunch" | "tbsp" | "tsp" | "pinch";
export type InventoryUnit = Exclude<Unit, "tbsp" | "tsp" | "pinch">;
export type ExpiryStatus = "fresh" | "use_soon" | "expiring_today" | "expired";
export type BatchStatus = "active" | "consumed" | "discarded" | "spoiled";
export type WasteReason = "expired" | "spoiled" | "cooked_too_much" | "forgotten_leftover" | "bad_taste";
export type LeftoverType = "dal" | "rice" | "sabzi" | "roti" | "curry" | "other";
export type DietaryPreference = "vegetarian" | "vegan" | "eggetarian" | "jain" | "omnivore";
export type RecipeDiet = "vegetarian" | "vegan" | "eggetarian" | "non-vegetarian";
export type MealSlot = "breakfast" | "lunch" | "dinner" | "snack";
export type Readiness = "ready_to_cook" | "almost_cookable" | "not_ready";

export interface ApiEnvelope<T> {
  success: boolean;
  data?: T;
  message?: string;
  error?: { code: string; message: string; details?: Record<string, string[]> };
  timestamp: string;
}

export interface Preferences {
  dietaryType: DietaryPreference;
  currencyCode: string;
  currencySymbol: string;
  notifyExpiryDaysBefore: number;
  notifyDailyDigestTime: string;
  emailNotificationsEnabled: boolean;
  pushNotificationsEnabled: boolean;
}

export interface Profile {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  imageUrl: string | null;
  emailPending: boolean;
  onboardingCompleted: boolean;
  preferences: Preferences;
}

export interface Kitchen {
  id: string;
  name: string;
  role: "owner" | "member";
  memberCount: number;
  createdAt: string;
}

export interface Member {
  userId: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  role: "owner" | "member";
  joinedAt: string;
}

export interface Category {
  id: string;
  slug: string;
  name: string;
  iconName: string;
  colorHex: string;
  displayOrder: number;
}

export interface CatalogItem {
  id: string;
  nameEn: string;
  nameHi: string | null;
  categoryId: string;
  categorySlug: string;
  categoryName: string;
  defaultUnit: InventoryUnit;
  defaultLocation: StorageLocation;
  shelfLifeRoomTempDays: number | null;
  shelfLifeFridgeDays: number | null;
  shelfLifeFreezerDays: number | null;
  perishable: boolean;
  storageTip: string | null;
}

export interface Batch {
  id: string;
  catalogItemId: string | null;
  name: string;
  nameHi: string | null;
  categoryId: string | null;
  categorySlug: string | null;
  categoryName: string | null;
  storageLocation: StorageLocation;
  initialQuantity: number;
  currentQuantity: number;
  unit: InventoryUnit;
  purchaseDate: string;
  expiryDate: string;
  expiryEstimated: boolean;
  expiryStatus: ExpiryStatus;
  daysUntilExpiry: number;
  costInr: number | null;
  leftover: boolean;
  leftoverType: LeftoverType | null;
  cookedAt: string | null;
  leftoverNotes: string | null;
  status: BatchStatus;
  createdAt: string;
  updatedAt: string;
}

export interface Timeline {
  expiringToday: Batch[];
  nextThreeDays: Batch[];
  thisWeek: Batch[];
  later: Batch[];
  expired: Batch[];
  valueAtRiskInr: number;
}

export interface RecipeSummary {
  id: string;
  slug: string;
  title: string;
  description: string | null;
  cuisine: string;
  dietaryType: RecipeDiet;
  prepTimeMinutes: number;
  cookTimeMinutes: number;
  totalTimeMinutes: number;
  servings: number;
  difficulty: "easy" | "medium" | "hard";
  imageUrl: string | null;
  favorite: boolean;
  readiness: Readiness;
  matchPercent: number;
  rankScore: number;
  missingCount: number;
  missing: string[];
  usesExpiring: string[];
}

export interface IngredientMatch {
  catalogItemId: string | null;
  name: string;
  requiredQuantity: number;
  unit: Unit;
  optional: boolean;
  pantryStaple: boolean;
  status: "available" | "insufficient" | "missing" | "staple" | "untracked";
  availableQuantity: number | null;
  expiryStatus: ExpiryStatus | null;
  unitMismatch: boolean;
}

export interface RecipeDetail {
  summary: RecipeSummary;
  instructions: string[];
  servingsRequested: number;
  ingredients: IngredientMatch[];
}

export interface CookResult {
  recipeTitle: string;
  servings: number;
  deductions: { batchId: string; name: string; deducted: number; unit: Unit; remaining: number }[];
  skipped: { ingredient: string; reason: string }[];
  lowStock: { catalogItemId: string; name: string; remaining: number; unit: Unit }[];
  mealPlanId: string;
}

export interface MealPlan {
  id: string;
  planDate: string;
  mealSlot: MealSlot;
  recipeId: string | null;
  recipeSlug: string | null;
  title: string;
  notes: string | null;
  status: "planned" | "cooked" | "skipped";
}

export interface RepurposeIdea {
  id: string;
  title: string;
  description: string;
  steps: string[];
  recipeSlug: string | null;
}

export interface Leftover {
  batch: Batch;
  topIdea: RepurposeIdea | null;
}

export interface ShoppingItem {
  id: string;
  catalogItemId: string | null;
  name: string;
  categoryId: string | null;
  categorySlug: string | null;
  categoryName: string | null;
  quantity: number;
  unit: InventoryUnit;
  estimatedCostInr: number | null;
  checked: boolean;
  checkedAt: string | null;
  addedReason: "manual" | "auto_low_stock" | "recipe_missing";
  linkedRecipeId: string | null;
  linkedRecipeTitle: string | null;
  suggestedLocation: StorageLocation;
  shelfLifeDays: number | null;
}

export interface ShoppingList {
  id: string;
  name: string;
  items: ShoppingItem[];
  checkedCount: number;
  estimatedTotalInr: number;
}

export interface ScanCandidate {
  itemName: string;
  rawText: string;
  catalogItemId: string | null;
  categoryId: string | null;
  quantity: number;
  unit: InventoryUnit;
  suggestedLocation: StorageLocation;
  expiryDate: string | null;
  expiryEstimated: boolean;
  priceInr: number | null;
  confidenceScore: number;
  lowConfidence: boolean;
  experimental: boolean;
}

export interface ScanSession {
  id: string;
  scanType: "receipt" | "grocery_haul" | "fridge_shelf";
  status: "pending" | "confirmed" | "rejected" | "failed";
  items: ScanCandidate[];
  overallConfidence: number | null;
  lowConfidence: boolean;
  experimental: boolean;
  storeName: string | null;
  purchaseDate: string | null;
  detectedTotalInr: number | null;
  confirmedItemsCount: number;
  errorMessage: string | null;
  createdAt: string;
}

export interface NotificationItem {
  id: string;
  type: "expiry_warning" | "item_expired" | "stock_low" | "leftover_alert" | "waste_summary";
  title: string;
  message: string;
  status: "unread" | "read" | "archived";
  actionUrl: string | null;
  createdAt: string;
}

export interface NotificationPage {
  items: NotificationItem[];
  unreadCount: number;
  page: number;
  size: number;
  total: number;
}

export interface Pulse {
  counts: { total: number; fridge: number; freezer: number; pantry: number; leftovers: number };
  urgent: Batch[];
  expiringToday: number;
  useSoon: number;
  expired: number;
  valueAtRiskInr: number;
  suggestion: RecipeSummary | null;
  todaysMeals: MealPlan[];
  lowStockCount: number;
  unreadNotifications: number;
  monthWasteInr: number;
  monthRescuedInr: number;
}

export interface MonthlySummary {
  month: string;
  totalSpendInr: number;
  totalWasteInr: number;
  rescuedInr: number;
  purchases: number;
  itemsWasted: number;
  itemsUsedUp: number;
  previousMonthWasteInr: number;
  wasteChangePercent: number | null;
  wasteByReason: { reason: WasteReason; amountInr: number; count: number }[];
  trend: { month: string; spendInr: number; wasteInr: number }[];
  tip: string;
}

export interface CategorySlice {
  category: string;
  slug: string;
  colorHex: string;
  amountInr: number;
  count: number;
  percent: number;
}

export interface TopWasted {
  itemName: string;
  times: number;
  totalLossInr: number;
}

export interface LowStockItem {
  catalogItemId: string;
  name: string;
  categoryId: string | null;
  unit: InventoryUnit;
  remaining: number;
  suggestedQuantity: number;
}

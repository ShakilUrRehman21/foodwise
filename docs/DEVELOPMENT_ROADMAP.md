# FoodWise — Engineering & Development Roadmap

**Document Version:** 1.1.4  
**Status:** Pending Technical Lead Approval — Final M0 Documentation Consistency Review  
**Author:** FoodWise Product & Architecture Team  
**Reviewer / Technical Lead:** Technical Lead (ChatGPT)  
**Implementation Methodology:** Incremental Milestone-Driven Architecture (Vertical Slice Delivery)  
**Changelog:** v1.1.4 — Removed premature claims of M0 completion/freezing, marking Milestone M0 as pending final Technical Lead approval; standardized expiry boundary strictly to expiry_date < LocalDate.now() (items expiring today remain EXPIRING_TODAY, not EXPIRED); clarified 15-minute scheduler interval and UI time picker constraint with due-time window deduplication in M3; updated deliverables list.

---

## 1. Development Milestones & Phased Execution

The implementation is broken into 9 sequential, independently verifiable milestones (M0 through M8). Each milestone is structured as two parallel tracks — **Backend (Spring Boot)** and **Frontend (Next.js)** — with clear inter-track dependencies.

```
[M0: Foundation & Specs] ← Pending Technical Lead Approval
           │
           ▼
[M1: Repository Bootstrap, Design System & Auth Foundation]
           │
           ▼
[M2: Kitchen Inventory & Batch Tracking Engine]
           │
           ▼
[M3: Expiry Management & Spring Scheduler Alert Engine]
           │
           ▼
[M4: OCR Receipt Scan & Grocery Image Recognition]
           │
           ▼
[M5: Dataset-Driven Recipe Recommendation Engine]
           │
           ▼
[M6: Leftover Management & Meal Planning]
           │
           ▼
[M7: Smart Shopping List & Rupee Spending Analytics]
           │
           ▼
[M8: PWA Hardening, Integration Testing & Production Launch]
```

---

## 2. Detailed Milestone & Task Breakdown

### Milestone M0: Product Foundation & UI Specification *(Pending Technical Lead Approval)*
- **Status:** Complete and pending final Technical Lead approval.
- **Deliverables:**
  - `docs/PRD.md` (v1.1.4)
  - `docs/SYSTEM_DESIGN.md` (v1.1.4)
  - `docs/DATABASE_SCHEMA.md` (v1.1.3)
  - `docs/UI_SPECIFICATION.md` (v1.1.3)
  - `docs/DEVELOPMENT_ROADMAP.md` (v1.1.4)
- **Verification:** Technical lead sign-off on all architectural decisions (AD-001: Spring Boot backend; AD-002: No generative AI).

---

### Milestone M1: Repository Bootstrap, Design System & Auth Foundation

**Goal:** Establish a working monorepo with authenticated frontend shell communicating with a live Spring Boot backend. User provisioning end-to-end verified.

#### Backend Tasks (Spring Boot)
- **Task 1.B1:** Initialize Spring Boot 3.x project via Spring Initializr with dependencies: Spring Web, Spring Security, Spring Data JPA, Spring Validation, Flyway, PostgreSQL Driver, Springdoc OpenAPI, Lombok, MapStruct, and `spring-boot-starter-actuator`.
- **Task 1.B2:** Configure `application.yml` with profiles (`dev`, `prod`). Externalize `DATABASE_URL`, `CLERK_JWKS_URI`, `CLERK_WEBHOOK_SECRET`, `CLOUDINARY_*` as environment variables.
- **Task 1.B3:** Implement `SecurityConfig.java`: configure Spring Security 6 `SecurityFilterChain`, stateless sessions, `JwtAuthenticationFilter` verifying Clerk JWTs via JWKS endpoint.
- **Task 1.B4:** Implement `KitchenAuthGuard.java`: verify `kitchen_members` membership on every kitchen-scoped service call.
- **Task 1.B5:** Implement `GlobalExceptionHandler.java` (`@RestControllerAdvice`) mapping domain exceptions to the `ApiResponse` error envelope.
- **Task 1.B6:** Implement Authoritative User Provisioning:
  - Primary path: Clerk webhook handler (`POST /api/webhooks/clerk`) with Svix signature verification (`user.created` event provisions `users`, `kitchens`, `kitchen_members`, and `user_preferences`).
  - Fallback path: Idempotent JIT provisioning in `UserService.provisionUserIfAbsent()` triggered on first authenticated API call if webhook delivery was delayed.
- **Task 1.B7:** Write `V1__create_core_tables.sql` Flyway migration (all 19 schema tables).
- **Task 1.B8:** Write `V2__seed_food_categories.sql` and `V3__seed_food_catalog.sql`: seed ≈200 Indian food items with canonical names, food categories, vernacular names (Hindi/Hinglish), measurement units, and default storage locations. In accordance with the food-safety validation policy, shelf-life columns (`shelf_life_room_temp_days`, `shelf_life_fridge_days`, `shelf_life_freezer_days`) must remain `NULL` where credible, India-relevant validation sources have not yet been documented and approved; validated shelf-life values may be seeded only when their primary source references and Technical Lead approval are available. Preserves the existing food catalog schema.
- **Task 1.B9:** Implement `GET /api/v1/health` public endpoint returning safe Spring Boot Actuator health status (configured with `management.endpoint.health.show-details: never` to expose only high-level `{"status":"UP"}` without disclosing environment variables, database credentials, or internal configuration).
- **Task 1.B10:** Configure Springdoc OpenAPI at `/api/docs` (dev profile only).

#### Frontend Tasks (Next.js)
- **Task 1.F1:** Initialize Next.js 15+ project with TypeScript, Tailwind CSS, ESLint, and Lucide React in `foodwise/frontend/`.
- **Task 1.F2:** Configure Tailwind CSS with FoodWise design tokens (Sage `#668568`, Warm Cream `#F8F7F2`, Deep Forest `#26372C`).
- **Task 1.F3:** Install and configure shadcn/ui base components: `Button`, `Card`, `Input`, `Dialog`, `DropdownMenu`, `Tabs`, `Badge`.
- **Task 1.F4:** Integrate Clerk `<ClerkProvider>`, configure sign-in and sign-up pages using Clerk React components.
- **Task 1.F5:** Create `services/api-client.ts`: Axios instance with Clerk JWT interceptor (`Authorization: Bearer`).
- **Task 1.F6:** Build App Shell layout: Desktop left sidebar (260px) + Mobile fixed bottom navigation (5 tabs).
- **Task 1.F7:** Build the onboarding wizard (3-step: kitchen setup, dietary preferences, common staples selection). The Clerk webhook (or idempotent JIT handler) serves as the primary mechanism provisioning the user's initial default kitchen upon signup; onboarding fetches this provisioned kitchen (`GET /api/v1/kitchens`) and updates it via `PATCH /api/v1/kitchens/{id}`, alongside `PATCH /api/v1/users/preferences`. `POST /api/v1/kitchens` is strictly reserved for creating additional secondary kitchens, preventing duplicate initial kitchen creation.

#### Acceptance Criteria — M1
- [ ] A new user can sign up via Clerk (Google OAuth or email magic link).
- [ ] Clerk webhook fires and creates `users`, `kitchens`, `kitchen_members`, and `user_preferences` rows in PostgreSQL (verifiable via Flyway + SQL query).
- [ ] First-time user onboarding operates on the webhook-provisioned initial kitchen via `PATCH /api/v1/kitchens/{id}` without creating a duplicate kitchen (`POST /api/v1/kitchens` creates only secondary kitchens).
- [ ] An unauthenticated request to `GET /api/v1/health` returns `200 OK` exposing safe high-level health status (`{"status":"UP"}`) without requiring authentication.
- [ ] An unauthenticated request to any protected API endpoint (e.g., `GET /api/v1/kitchens`) returns `401 UNAUTHORIZED`.
- [ ] Authenticated frontend requests (carrying a valid Clerk Bearer JWT) to protected endpoints succeed and are authorized correctly.
- [ ] The app shell renders correctly on mobile (375px) and desktop (1280px) with empty-state placeholder screens.
- [ ] `GET /api/docs` renders Swagger UI in the dev profile.

---

### Milestone M2: Kitchen Inventory & Batch Tracking Engine

**Goal:** Enable users to view, add, adjust, and categorize food inventory batches across Fridge, Freezer, and Pantry through the React UI talking to Spring Boot REST APIs.

#### Backend Tasks (Spring Boot)
- **Task 2.B1:** Implement `InventoryBatch` JPA entity with all columns per `DATABASE_SCHEMA.md`. Concurrency for batch mutations is governed by pessimistic locking (`SELECT FOR UPDATE`), establishing a single coherent locking strategy without `@Version` ambiguity. The database status column permits strictly `active`, `consumed`, `discarded`, `spoiled`; expiry is a derived state based on `expiry_date` and status, with no persisted `expired` database status. Java enum constants (`BatchStatus.ACTIVE`, etc.) map to lowercase database check-constraint strings via JPA `@Converter`.
- **Task 2.B2:** Implement `InventoryEvent` JPA entity (immutable audit log).
- **Task 2.B3:** Implement `InventoryBatchRepository` (Spring Data JPA) with custom JPQL queries: findByKitchenAndStatus, findByKitchenAndLocation, findExpiringBefore.
- **Task 2.B4:** Implement `InventoryService`:
  - `createBatch()` — validates unit; inspects catalog shelf-life guidance: if a validated shelf-life value is present, computes suggested expiry date; if catalog shelf-life is `NULL` (unvalidated per food-safety policy), does not automatically calculate or invent an expiry date, requiring an explicit user-provided expiry date to satisfy the non-null `inventory_batches.expiry_date` schema requirement (never silently allowing missing or invented expiry dates); writes `CREATED` event. `@Transactional`.
  - `deductQuantity()` — pessimistic lock via `SELECT FOR UPDATE`, validates non-negative result, writes `CONSUMED_PARTIALLY` or `CONSUMED_FULLY` event. `@Transactional`.
  - `updateLocation()` — moves batch to Fridge/Freezer/Pantry; recalculates expiry only if validated catalog shelf-life guidance exists for the new location, otherwise preserves existing user-defined expiry without inventing dates. `@Transactional`.
  - `discardBatch()` — transitions batch to `DISCARDED`, writes waste log entry. `@Transactional`.
- **Task 2.B5:** Implement `InventoryController` with endpoints:
  - `GET /api/v1/kitchens/{id}/inventory/batches` (filter: status, location, category; sort: expiryDate, addedDate, name)
  - `POST /api/v1/kitchens/{id}/inventory/batches`
  - `PATCH /api/v1/kitchens/{id}/inventory/batches/{batchId}/quantity`
  - `PATCH /api/v1/kitchens/{id}/inventory/batches/{batchId}/location`
  - `DELETE /api/v1/kitchens/{id}/inventory/batches/{batchId}` (soft-discard)
- **Task 2.B6:** Implement `CatalogService`: `GET /api/v1/catalog/search?q=` — returns fuzzy-matched food catalog items for autosuggest.
- **Task 2.B7:** Write JUnit 5 + Mockito unit tests for `InventoryService` covering critical paths: valid quantity deduction, insufficient stock rejection, atomic transaction rollback integrity, concurrent update handling via pessimistic locking (`SELECT FOR UPDATE`), and kitchen authorization enforcement. Code coverage serves as a supporting metric (≥80% line coverage target), not the sole criterion.
- **Task 2.B8:** Write Testcontainers integration test: full deduction roundtrip against a real PostgreSQL container.

#### Frontend Tasks (Next.js)
- **Task 2.F1:** Create `services/inventory.service.ts` with typed wrappers for all inventory REST endpoints.
- **Task 2.F2:** Create `hooks/use-inventory.ts` using TanStack Query (`useQuery`, `useMutation`) for data fetching and optimistic updates.
- **Task 2.F3:** Build the Kitchen Inventory screen (`/inventory`) with tabbed filtering (`All`, `Fridge`, `Freezer`, `Pantry`), search bar, and sort controls.
- **Task 2.F4:** Build the Add Food Form modal: Indian ingredient autosuggest (calls `GET /api/v1/catalog/search`), storage location toggle, quantity stepper, date pickers. Clearly distinguishes user-entered expiry dates from catalog-derived estimates in the UI (e.g. "Estimated" badge vs manual entry); if catalog shelf-life is `NULL`, prompts the user for manual expiry date selection without prefilling unvalidated dates.
- **Task 2.F5:** Build `InventoryBatchCard` component displaying status badge, quantity, unit, expiry date, and overflow action menu.
- **Task 2.F6:** Implement mobile swipe gestures (left swipe = Quick Consume, right swipe = Discard) using `framer-motion` drag.

#### Acceptance Criteria — M2
- [ ] User can add a 500g batch of Paneer (Fridge) with a user-provided expiry date (e.g., 3 days from today) via the Add Food form, without relying on unvalidated catalog defaults.
- [ ] The batch appears in the Fridge tab with a `Fresh` status badge.
- [ ] Calling `PATCH /quantity` with `delta=100` returns the batch updated to 400g, and an `inventory_events` row of type `CONSUMED_PARTIALLY` is present in the database.
- [ ] Attempting to deduct 600g from a 400g batch returns `409 CONFLICT` with error code `CONFLICT` and zero database changes occur.
- [ ] Search: typing "pan" in the catalog search returns "Paneer" within 200ms.
- [ ] Critical-path tests pass for `InventoryService`: explicit automated test cases verified for (1) valid quantity deduction, (2) insufficient stock rejection with `409 Conflict`, (3) atomic transaction rollback on failure, (4) concurrent update protection via pessimistic locking (`SELECT FOR UPDATE`), and (5) kitchen authorization enforcement.
- [ ] Testcontainers integration test passes against PostgreSQL 16.

---

### Milestone M3: Expiry Management & Spring Scheduler Alert Engine

**Goal:** Deliver proactive expiry risk awareness through visual triage, one-tap disposal logging, and an automated Spring-scheduled daily digest.

#### Backend Tasks (Spring Boot)
- **Task 3.B1:** Implement `ExpiryClassificationService`: pure deterministic logic classifying active `InventoryBatch` items into `FRESH` (>3d), `USE_SOON` (2–3d), `EXPIRING_TODAY` (`expiry_date = LocalDate.now()`), and `EXPIRED` (`expiry_date < LocalDate.now()`). Items expiring today strictly remain `EXPIRING_TODAY`, not `EXPIRED`. Expiry is purely a derived query/DTO classification; `inventory_batches.status` permits strictly `'active'`, `'consumed'`, `'discarded'`, `'spoiled'`, with zero persisted `'expired'` database status.
- **Task 3.B2:** Implement `ExpiryController`:
  - `GET /api/v1/kitchens/{id}/expiry/timeline` — returns all active batches grouped by urgency bucket.
  - `GET /api/v1/kitchens/{id}/expiry/urgent` — returns only USE_SOON and EXPIRING_TODAY batches.
- **Task 3.B3:** Implement `FoodWasteLogRepository` and `FoodWasteLog` JPA entity.
- **Task 3.B4:** Extend `InventoryService.discardBatch()` to write a `food_waste_logs` record computing `estimated_loss_inr` from batch `cost_inr` × proportional quantity discarded (transitions batch status to `'discarded'`).
- **Task 3.B5:** Implement `ExpiryScheduler`: periodic Spring `@Scheduled` runner executing every 15 minutes (`cron = "0 0/15 * * * *"`) to match users whose configured `notify_daily_digest_time` is due within the interval window (default 08:00, with UI time picker constrained to 15-minute intervals). Supported by daily deduplication so each eligible user receives at most one morning digest per calendar date. For each eligible user, query their kitchen's active batches expiring within `notify_expiry_days_before`. Create user-specific `notifications` records (`notifications.user_id = user.id`) with lowercase `type = 'expiry_warning'` (mapped from Java `NotificationType.EXPIRY_WARNING`) and a deep-link `action_url`, avoiding duplicate notifications per user and day.
- **Task 3.B6:** Implement `NotificationController`:
  - `GET /api/v1/kitchens/{id}/notifications` (paginated, filterable by status)
  - `PATCH /api/v1/kitchens/{id}/notifications/{notifId}/read`
- **Task 3.B7:** Write JUnit 5 tests for `ExpiryClassificationService` covering all four urgency buckets and edge cases (same-day expiry, past midnight boundary).
- **Task 3.B8:** Write Testcontainers integration test for `ExpiryScheduler`: trigger the scheduled method directly for a scenario with multiple users and kitchens having urgent items, asserting that distinct, user-specific notification rows are created for each eligible kitchen member without duplication.

#### Frontend Tasks (Next.js)
- **Task 3.F1:** Build the Expiry Management triage screen (`/expiry`) with kanban-style grouping: `Expiring Today`, `Next 2-3 Days`, `This Week`, `Expired` (derived presentation buckets).
- **Task 3.F2:** Implement quick action buttons on each expiry card: `Cook Tonight` (navigates to `/recipes?ingredient=`), `Freeze` (calls `PATCH /location`; recalculates expiry only if validated freezer guidance exists, otherwise preserves existing user-entered date), `Discard & Log Waste` (calls `DELETE /batches/{id}` with reason, transitioning status to `'discarded'`).
- **Task 3.F3:** Build the notification bell component: unread badge count, dropdown list of recent notifications with deep-link navigation.
- **Task 3.F4:** Add `Urgent Expiry Radar` banner to the Dashboard fetching from `GET /api/v1/kitchens/{id}/expiry/urgent`.

#### Acceptance Criteria — M3
- [ ] Items expiring today display the `Expiring Today` badge (red pulsating dot) on the Inventory and Expiry screens.
- [ ] Discarding a batch with a recorded `cost_inr` of ₹95 and discarding 100% of the quantity creates a `food_waste_logs` row with `estimated_loss_inr = 95.00` and transitions batch status to `discarded` (never persisting an unsupported `expired` status).
- [ ] Manually triggering `ExpiryScheduler.processDailyExpiryDigests()` in a test with 2 kitchens and 3 eligible member users having urgent items creates exactly 3 user-specific `notifications` rows (one per eligible user, verified via `notifications.user_id`) with lowercase `type = 'expiry_warning'`, confirmed via Testcontainers.
- [ ] `GET /api/v1/kitchens/{id}/notifications` returns correct unread notification count.
- [ ] `ExpiryClassificationService` unit tests: 100% coverage of urgency transitions.

---

### Milestone M4: OCR Receipt Scan & Grocery Image Recognition

**Goal:** Implement the local Tesseract OCR receipt scanner and the ONNX-based grocery haul image recognition pipeline, both with mandatory human-in-the-loop review.

> **Pre-requisite (Developer Offline):** Before this milestone begins, formal verification of the actual downloaded dataset archives, annotation licenses, attribution obligations, and applicable commercial-use terms must be documented with primary evidence and approved by the technical lead. Dataset approval cannot be marked complete without verified evidence. Furthermore, local Indian produce image collection and annotation must be completed before YOLO training and ONNX export per `SYSTEM_DESIGN.md` Section 6.5. The verified `foodwise-haul-v1.0.onnx` file must be available before M4 code integration.

#### Backend Tasks (Spring Boot)
- **Task 4.B1:** Add Maven dependencies: `net.sourceforge.tess4j:tess4j` (Tess4J), `com.microsoft.onnxruntime:onnxruntime`, `org.openpnp:opencv` (or `org.bytedeco:opencv`) for image preprocessing.
- **Task 4.B2:** Implement `ImagePreprocessingService`: grayscale → adaptive threshold → deskew → denoise pipeline using OpenCV (via JavaCV/Bytedeco). Input: raw `MultipartFile`. Output: preprocessed `BufferedImage`.
- **Task 4.B3:** Implement `OcrService` (Tess4J wrapper): initialize `Tesseract` instance with `tessdata` path from config; expose `extractText(BufferedImage)` returning raw OCR string.
- **Task 4.B4:** Implement `ReceiptTextParser`: regex-based line tokenizer extracting `(itemName, quantity, unit, priceInr)` tuples from raw OCR text. Handle common Indian receipt patterns (Blinkit, Zepto, D-Mart).
- **Task 4.B5:** Implement `CatalogMatcherService`: for each parsed token, compute JaroWinkler similarity against all `food_catalog.name_en` and `food_catalog.name_hi` entries. Return top match if score ≥ 0.75.
- **Task 4.B6:** Implement `OnnxInferenceService`: load ONNX model at startup; expose `detectObjects(BufferedImage)` returning `List<Detection>(label, boundingBox, confidence)`. Apply NMS post-processing.
- **Task 4.B7:** Implement `ScanService` orchestrating both pipelines:
  - `processReceiptScan(kitchenId, userId, MultipartFile)` → preprocesses → OCR → parse → catalog match → save `ScanSessionEntity` in `scan_sessions` table (status=PENDING) → return `ScanSessionDto`.
  - `processHaulScan(kitchenId, userId, MultipartFile)` → preprocesses → ONNX inference → catalog match → save `ScanSessionEntity` in `scan_sessions` table (status=PENDING, marked experimental) → return `ScanSessionDto`.
  - `confirmScan(kitchenId, userId, sessionId, List<ConfirmedItem>)` → `@Transactional` → validates confirmed items, enforcing non-null `expiry_date` per schema (requires explicit user-entered date when catalog shelf-life is `NULL`, never inventing unverified dates) → creates `InventoryBatch` records for each confirmed item → updates `ScanSessionEntity` status to `CONFIRMED`.
- **Task 4.B8:** Implement `ScanController`:
  - `POST /api/v1/kitchens/{id}/scans` (multipart upload, `scan_type` param: `RECEIPT` or `GROCERY_HAUL`)
  - `POST /api/v1/kitchens/{id}/scans/{sessionId}/confirm`
  - `POST /api/v1/kitchens/{id}/scans/{sessionId}/reject`
  - `GET /api/v1/kitchens/{id}/scans` (history)
- **Task 4.B9:** Implement rate limiting interceptor: maximum 20 scan requests per kitchen per hour using an in-memory token bucket (Guava `RateLimiter` or Bucket4j).
- **Task 4.B10:** Write unit tests for `ReceiptTextParser` covering 5 representative Indian receipt formats (Blinkit, Zepto, D-Mart, handwritten, thermal-faded).
- **Task 4.B11:** Write unit tests for `CatalogMatcherService`: verify JaroWinkler scores and correct catalog resolution for common aliases (e.g., "Dhaniya" → "Coriander", "Atta" → "Whole Wheat Flour").

#### Frontend Tasks (Next.js)
- **Task 4.F1:** Build the Scan screen (`/scan`) with two entry modes: `📷 Receipt` and `🥬 Grocery Haul`.
- **Task 4.F2:** Implement camera capture widget (HTML5 `getUserMedia`) and file upload fallback. Show local image preview before submission.
- **Task 4.F3:** Call `POST /api/v1/kitchens/{id}/scans` (multipart) on submit. Show processing progress bar (polling or WebSocket — TBD, default: polling every 1.5s).
- **Task 4.F4:** Build the Scan Review Table: editable rows for item name, quantity, unit, storage location, expiry date, price. Prefill expiry date as `today + validated shelf life` (with "Estimated" chip) only when approved guidance exists; leave blank with required `[Set Date *]` prompt when catalog shelf-life is `NULL`, preventing invented dates and enforcing non-null expiry before confirmation. Low-confidence items (< 0.75) highlighted in amber. Haul-recognition rows labelled "Experimental".
- **Task 4.F5:** Call `POST /api/v1/kitchens/{id}/scans/{sessionId}/confirm` on user approval; redirect to `/inventory` with success toast.

#### Acceptance Criteria — M4
- [ ] Uploading a clear photo of a Blinkit text receipt returns parsed items within 6 seconds.
- [ ] At least 70% of line items on a clean printed receipt are correctly identified and matched to a catalog entry (measured on a manually labelled test set of 10 diverse receipts).
- [ ] No item is written to `inventory_batches` without an explicit `POST /confirm` call and a validated or user-provided `expiry_date` (satisfying the non-null database constraint without invented dates).
- [ ] Uploading an image with no food content returns an empty candidate list (zero items), not an error.
- [ ] Rate limiter returns `429 Too Many Requests` on the 21st scan request within a 1-hour window.
- [ ] `ReceiptTextParser` unit tests pass for all 5 receipt format scenarios.
- [ ] `CatalogMatcherService` unit tests achieve ≥ 90% correct resolution for the 20 tested alias pairs.

---

### Milestone M5: Dataset-Driven Recipe Recommendation Engine

**Goal:** Deliver the ingredient-matching recipe recommender, expiry-weighted ranking, and transactional inventory deduction on "cook" confirmation.

> **Pre-requisite (Developer Offline):** Before this milestone begins, the recipe seed data must be curated from the Indian Food 101 dataset and manually enriched with ingredient quantities. See `SYSTEM_DESIGN.md` Section 6.4.3.

#### Backend Tasks (Spring Boot)
- **Task 5.B1:** Write `V4__seed_recipes.sql` Flyway migration: 75+ Indian recipes with complete `recipe_ingredients` rows (ingredient, quantity, unit, `is_pantry_staple`, `is_optional`).
- **Task 5.B2:** Implement `Recipe` and `RecipeIngredient` JPA entities and repositories.
- **Task 5.B3:** Implement `RecipeMatchingService`:
  - For each candidate recipe, compute an **availability score**:
    ```
    score = Σ (matched_ingredient_weight) / Σ (total_ingredient_weight)
    where:
      matched_ingredient_weight = base_weight × expiry_multiplier
      base_weight = 1.0 for required; 0.5 for optional; 0.0 for pantry_staple (excluded)
      expiry_multiplier = 3.0 if EXPIRING_TODAY; 2.0 if USE_SOON; 1.0 otherwise
    ```
  - A recipe is `READY_TO_COOK` if all non-optional, non-staple ingredients are present (score ≥ 1.0 on non-optional set).
  - A recipe is `ALMOST_COOKABLE` if 80–99% of non-optional ingredients are present (missing 1–2 items).
  - Return sorted list: READY_TO_COOK first, then ALMOST_COOKABLE by descending score.
- **Task 5.B4:** Implement `RecipeService`:
  - `getRecommendations(kitchenId, userId, filters)` — calls `RecipeMatchingService`, applies dietary and cook-time filters.
  - `cookRecipe(kitchenId, userId, recipeId, servings)` — `@Transactional`: deducts ingredient quantities from active batches using `InventoryService.deductQuantity()` scaled by serving ratio; creates a `meal_plans` entry with status `COOKED`.
- **Task 5.B5:** Implement `RecipeController`:
  - `GET /api/v1/kitchens/{id}/recipes/recommendations` (query params: dietary, maxCookTime, filter=READY_ONLY)
  - `GET /api/v1/recipes/{recipeId}` (detail with ingredient availability overlay per kitchen)
  - `POST /api/v1/kitchens/{id}/recipes/{recipeId}/cook` (body: servings count)
  - `POST /api/v1/kitchens/{id}/recipes/{recipeId}/favorite`
  - `GET /api/v1/kitchens/{id}/recipes/favorites`
- **Task 5.B6:** Write unit tests for `RecipeMatchingService`:
  - Test: all ingredients present → score = 1.0, status = READY_TO_COOK.
  - Test: 1 required ingredient missing → status = ALMOST_COOKABLE.
  - Test: expiring ingredient scores higher than fresh equivalent.
  - Test: pantry staples excluded from scoring calculation.
- **Task 5.B7:** Write Testcontainers integration test for `cookRecipe()`: seed inventory, call cook endpoint, assert all batch quantities correctly deducted.

#### Frontend Tasks (Next.js)
- **Task 5.F1:** Build Recipe Discovery screen (`/recipes`) calling `GET /recommendations`. Display `RecipeCard` with availability badge, cook-time, cuisine, and highlighted expiring ingredient tags.
- **Task 5.F2:** Implement filter chips: `100% Match`, `Quick (< 20 min)`, `Vegetarian`, `Uses Expiring Items`.
- **Task 5.F3:** Build Recipe Detail screen (`/recipes/[slug]`) showing ingredient checklist (green tick = in kitchen, red = missing) and step-by-step instructions.
- **Task 5.F4:** "I Cooked This!" button: shows confirmation modal with ingredient deduction summary → calls `POST /cook` → redirects to dashboard with positive reinforcement toast.
- **Task 5.F5:** "Add Missing Ingredients" button on ALMOST_COOKABLE recipes: calls `POST /shopping/items` for each missing ingredient.

#### Acceptance Criteria — M5
- [ ] Palak Paneer recipe surfaces as `READY_TO_COOK` when Paneer (200g) and Spinach (1 bunch) are both active in the kitchen with stock ≥ recipe requirements.
- [ ] Palak Paneer ranks above Aloo Gobi when Paneer is in `EXPIRING_TODAY` state vs Potato being `FRESH`, demonstrating expiry weighting.
- [ ] Clicking "I Cooked This" for 2 servings of a recipe requiring 200g Paneer per serving deducts 400g from the Paneer batch in a single transaction.
- [ ] If the Paneer batch has only 300g and the recipe requires 400g, the cook action returns `409 CONFLICT` and zero deductions occur.
- [ ] `RecipeMatchingService` unit tests: all 6 scenarios pass.
- [ ] Testcontainers integration test: cook roundtrip passes.

---

### Milestone M6: Leftover Management & Meal Planning

**Goal:** Eliminate cooked-food waste with conservative validated date-level storage guidance, explicit user date entry for unvalidated items, and a rule-based culinary repurposing engine.

#### Backend Tasks (Spring Boot)
- **Task 6.B1:** Write `V5__seed_leftover_repurposing_rules.sql`: define transformation rules as relational rows, e.g., `(source_food_category='Cooked Lentils', transformation_name='Dal Paratha', instructions='Mix into atta...', min_quantity_g=100)`.
- **Task 6.B2:** Implement `LeftoverService`:
  - `logLeftover(kitchenId, userId, request)` — creates an `inventory_batches` row with `is_leftover=true`, `cooked_at=now()`; computes date-level `expiry_date = LocalDate.now().plusDays(validated_days)` only when approved leftover storage guidance exists; if unvalidated or no approved guidance exists, requires a user-provided expiry date (SQL `DATE` precision) without silently inventing a date (enforcing the non-null `inventory_batches.expiry_date` constraint).
  - `getActiveLeftovers(kitchenId)` — returns all active leftover batches with computed `daysRemaining` (date-level precision).
  - `getRepurposingSuggestions(batchId)` — queries transformation rules matching the leftover's `catalog_item_id` or category.
- **Task 6.B3:** Implement `LeftoverController`:
  - `POST /api/v1/kitchens/{id}/leftovers`
  - `GET /api/v1/kitchens/{id}/leftovers`
  - `GET /api/v1/kitchens/{id}/leftovers/{batchId}/repurpose`
  - `PATCH /api/v1/kitchens/{id}/leftovers/{batchId}/consume`
- **Task 6.B4:** Implement `MealPlanController`:
  - `GET /api/v1/kitchens/{id}/meal-plans?week=2026-W45`
  - `POST /api/v1/kitchens/{id}/meal-plans` (assign recipe to a meal slot)
  - `PATCH /api/v1/kitchens/{id}/meal-plans/{planId}/status`
- **Task 6.B5:** Unit tests for `LeftoverService`: verify expiry calculations using validated, approved leftover rules, fallback to required user-provided expiry date when rules are absent/unvalidated, and rule-based repurposing match logic.

#### Frontend Tasks (Next.js)
- **Task 6.F1:** Build Leftover Management screen (`/leftovers`) with date-level visual indicators per leftover batch (e.g. `Fresh` (>2 days), `Use Soon` (1–2 days), `Expiring Today` (due today)), avoiding unsupported hour-level countdown precision; leftover entry modal prompts user for manual expiry date when approved guidance is unavailable, distinguishing estimates from user inputs.
- **Task 6.F2:** Implement `RepurposeModal`: tap "Repurpose Ideas" on a leftover card → fetch `GET /leftovers/{id}/repurpose` → render 2–3 transformation suggestions.
- **Task 6.F3:** Build weekly Meal Planning calendar widget on the dashboard (compact 7-day strip; full view on `/recipes#meal-plan`).

#### Acceptance Criteria — M6
- [ ] Logging "Dal Tadka" at 14:00 IST creates a leftover batch with date-level `expiry_date` computed from validated, approved guidance (e.g., 3-day advisory window if approved) or an explicit user-provided date, verified in the database.
- [ ] When in the final day before expiry (or 'Expiring Today' state), `GET /repurpose` returns at least one rule-based suggestion for Dal.
- [ ] `LeftoverService` unit tests verify validated leftover shelf-life rules, manual expiry requirement when unvalidated, and repurposing suggestion lookup.

---

### Milestone M7: Smart Shopping List & Rupee Spending Analytics

**Goal:** Close the inventory loop by connecting shopping to the pantry, and provide financial transparency through spending and waste analytics.

#### Backend Tasks (Spring Boot)
- **Task 7.B1:** Implement `ShoppingList`, `ShoppingListItem` JPA entities and repositories.
- **Task 7.B2:** Implement `ShoppingService`:
  - `getOrCreateActiveList(kitchenId)` — returns the single active list per kitchen.
  - `addItem(kitchenId, userId, request)` — adds item with `added_reason` (MANUAL, AUTO_LOW_STOCK, RECIPE_MISSING).
  - `checkItem(kitchenId, userId, itemId)` — marks item as checked with `checked_at` timestamp.
  - `importCheckedToInventory(kitchenId, userId, listId)` — `@Transactional`: creates `InventoryBatch` for each checked item using validated catalog shelf-life where present; must not invent or automatically extrapolate expiry dates when catalog shelf-life values are `NULL`, requiring user-supplied dates or preserving manual input; archives list.
- **Task 7.B3:** Implement `ShoppingController`:
  - `GET /api/v1/kitchens/{id}/shopping`
  - `POST /api/v1/kitchens/{id}/shopping/items`
  - `PATCH /api/v1/kitchens/{id}/shopping/items/{itemId}/check`
  - `DELETE /api/v1/kitchens/{id}/shopping/items/{itemId}`
  - `POST /api/v1/kitchens/{id}/shopping/{listId}/import-to-inventory`
- **Task 7.B4:** Implement `AnalyticsService`:
  - `getMonthlySummary(kitchenId, yearMonth)` — total INR spend (from `grocery_purchases`), total INR wasted (from `food_waste_logs`), rupees rescued (cost of consumed items that were previously Use Soon/Expiring).
  - `getWasteCategoryBreakdown(kitchenId, yearMonth)` — aggregate waste by `food_categories.name`.
  - `getTopWastedItems(kitchenId, yearMonth, limit)` — items most frequently appearing in `food_waste_logs`.
- **Task 7.B5:** Implement `AnalyticsController`:
  - `GET /api/v1/kitchens/{id}/analytics/monthly-summary?month=2026-10`
  - `GET /api/v1/kitchens/{id}/analytics/waste-breakdown?month=2026-10`
  - `GET /api/v1/kitchens/{id}/analytics/top-wasted?month=2026-10`
- **Task 7.B6:** Unit tests for `AnalyticsService`: verify rupee calculations with known seed data.

#### Frontend Tasks (Next.js)
- **Task 7.F1:** Build Smart Shopping List screen (`/shopping`) with category-grouped checklist (Vegetables, Dairy, Staples, Spices).
- **Task 7.F2:** Implement In-Store Shopping Mode: fullscreen high-contrast toggle, enlarged text, maximum tap-target height.
- **Task 7.F3:** Implement "Import to Inventory" confirmation banner/modal after items are checked; prompts user for manual expiry dates for any items where catalog shelf-life is `NULL` so unverified dates are never invented; calls `POST /import-to-inventory`.
- **Task 7.F4:** Build Analytics Dashboard (`/analytics`): KPI cards (Total Spend, Rupee Waste, Rupee Rescued), donut chart for waste category breakdown (using Recharts), and top wasted items table.

#### Acceptance Criteria — M7
- [ ] Checking all items in a shopping list and clicking "Import to Inventory" creates the correct number of `inventory_batches` rows using validated catalog shelf lives where available, or user-provided expiry dates for items where catalog shelf-life is `NULL`, without inventing unverified dates.
- [ ] Analytics: discarding a ₹95 Paneer batch and a ₹38 Tomatoes batch in October shows `total_waste_inr = 133.00` in `GET /analytics/monthly-summary?month=2026-10`.
- [ ] `AnalyticsService` unit tests pass for all 3 aggregate computations.

---

### Milestone M8: PWA Hardening, Integration Testing & Production Launch

**Goal:** Make the application installable, harden security, run comprehensive integration tests, and deploy to production.

#### Backend Tasks (Spring Boot)
- **Task 8.B1:** Review all `@Transactional` boundaries and ensure no silent transaction scope expansion (avoid calling `@Transactional` public methods from within the same Spring bean).
- **Task 8.B2:** Write JUnit 5 + Testcontainers full integration tests for critical cross-module user journeys:
  1. User created via webhook → add pantry items → expiry scheduler fires → notification created.
  2. Upload receipt image → OCR processes → confirm scan → inventory batches created.
  3. Get recipe recommendations → cook recipe → inventory deducted atomically.
- **Task 8.B3:** Security penetration test: verify tenant isolation by asserting that a JWT for User A cannot read or mutate any kitchen data for User B (automated via REST Assured test suite).
- **Task 8.B4:** Disable Springdoc OpenAPI in the `prod` profile (`springdoc.api-docs.enabled=false`).
- **Task 8.B5:** Configure Spring Boot Docker image build (`./mvnw spring-boot:build-image`). Verify JVM settings (G1GC, `-Xmx512m` for constrained hosting tier).
- **Task 8.B6:** Configure Flyway `spring.flyway.validate-on-migrate=true` and `out-of-order=false` for production safety.

#### Frontend Tasks (Next.js)
- **Task 8.F1:** Configure PWA Web App Manifest (`manifest.json`) with FoodWise icons, theme color Sage `#668568`, and `display: standalone`.
- **Task 8.F2:** Implement Service Worker offline shell fallback (cache app shell on install, serve stale shell on network failure).
- **Task 8.F3:** Conduct WCAG 2.1 AA accessibility audit: verify contrast ratios, keyboard navigation tab order, ARIA labels on custom controls, and screen reader compatibility.
- **Task 8.F4:** Write Playwright E2E tests covering:
  1. Sign up → Onboarding wizard → Add food item → Verify dashboard.
  2. Scan receipt image (mocked backend) → Review & confirm → Inventory updated.
  3. View 100% matched recipe → Cook recipe → Inventory deducted → Success state shown.
- **Task 8.F5:** Lighthouse audit: target Performance ≥ 90, Accessibility ≥ 95, Best Practices ≥ 95.
- **Task 8.F6:** Deploy frontend to Vercel; configure `NEXT_PUBLIC_API_URL` pointing to Spring Boot production URL.

#### Acceptance Criteria — M8
- [ ] All Testcontainers integration tests pass against PostgreSQL 16.
- [ ] Tenant isolation security test: User A's JWT receives `403 FORBIDDEN` on all of User B's kitchen endpoints (100% of tested routes).
- [ ] Playwright E2E suite: all 3 critical user journeys pass.
- [ ] Lighthouse performance score ≥ 90 on mobile simulation (Fast 3G).
- [ ] Application installable as a PWA on Android Chrome and iOS Safari.
- [ ] Production Spring Boot instance starts, Flyway migrations run, and `/api/v1/health` returns `200 UP`.

---

## 3. Module Dependencies & Execution Flow

```
[M1: Auth & Repo Bootstrap]
          │
          ▼
[M2: Inventory Engine] ◀──────────────────────────────┐
          │                                            │
          ├────────────────────┐                       │ (importCheckedToInventory)
          ▼                    ▼                       │
[M3: Expiry Engine]    [M4: OCR/CV Scanner]            │
          │                    │                       │
          ├────────────────────┘                       │
          ▼                                            │
[M5: Recipe Engine]                                    │
     (depends on M2 inventory batches)                 │
          │                                            │
          ▼                                            │
[M6: Leftovers & Meal Planning]                        │
     (extends M2 inventory batch model)                │
          │                                            │
          ▼                                            │
[M7: Shopping List & Analytics] ───────────────────────┘
     (depends on M2, M3 waste logs, M5 recipe engine)
          │
          ▼
[M8: Hardening, Testing & Launch]
```

---

## 4. Testing & Quality Assurance Plan

| Test Type | Scope | Tooling | Where Written |
| :--- | :--- | :--- | :--- |
| **Unit Tests (Backend)** | Service business logic: expiry classification, recipe scoring, deduction math, OCR text parsing, catalog matching | JUnit 5 + Mockito | `backend/src/test/java/...` |
| **Integration Tests (Backend)** | Full DB roundtrips via real PostgreSQL: inventory deductions, scan confirmation, cook recipe, notification creation | JUnit 5 + Testcontainers | `backend/src/test/java/...` |
| **Security Tests (Backend)** | Tenant isolation: User A cannot access User B's kitchen at any endpoint | REST Assured + JUnit 5 + Testcontainers | `backend/src/test/java/.../security/` |
| **Component Tests (Frontend)** | StatusBadge states, InventoryBatchCard rendering, ScanReviewTable edit interactions | React Testing Library + Vitest | `frontend/src/.../__tests__/` |
| **E2E Tests (Full Stack)** | Critical user journeys against running dev stack | Playwright | `frontend/e2e/` |
| **OCR Accuracy Tests** | Measure item extraction accuracy on a labeled receipt test set (10+ receipts) | JUnit 5 + labeled ground-truth CSV | `backend/src/test/resources/receipts/` |

### Quality & Coverage Guidelines
- Correctness is established primarily through **meaningful test scenarios, edge-case assertions, and integration tests** against a real PostgreSQL container (covering quantity deduction, insufficient stock rejection, atomic transaction rollback, concurrent pessimistic locking, and tenant isolation), rather than code coverage percentages alone.
- Backend service layer line coverage: **≥ 80% line coverage** (JaCoCo Maven plugin enforced in CI) serves as a supporting quality metric.
- Mandatory critical-path test suites must pass:
  1. `InventoryService`: valid deduction, insufficient stock rejection with `409 Conflict`, atomic rollback on exception, concurrent pessimistic locking (`SELECT FOR UPDATE`), and kitchen tenancy authorization.
  2. `ScanService`: OCR token parsing, low-confidence flagging, and transactional confirmation into `inventory_batches`.
  3. `RecipeService`: ingredient matching math, expiry weighting, and atomic inventory deduction on recipe cook.
- E2E: **3 critical journeys passing** before production deployment.

---

## 5. Deployment Architecture

### Development Environment
- **Backend:** `./mvnw spring-boot:run` with `application-dev.yml` pointing to local PostgreSQL (Docker Compose).
- **Frontend:** `npm run dev` with `NEXT_PUBLIC_API_URL=http://localhost:8080`.
- **Database:** Docker Compose PostgreSQL 16 container with a persistent named volume.

### Staging / Preview Environment
- **Backend:** Docker image pushed to container registry on every merge to `main`; deployed to staging slot (Railway / Render).
- **Frontend:** Vercel preview deployment triggered automatically on pull requests.
- **Database:** Staging PostgreSQL instance; Flyway migrations run automatically on backend startup.

### Production Environment
- **Backend:** Spring Boot Docker container deployed to Fly.io or Railway (Mumbai region preferred for India latency).
- **Frontend:** Vercel production deployment with custom domain; CDN edge caching for static assets.
- **Database:** Supabase or Railway PostgreSQL 16 with daily automated backups and point-in-time recovery.
- **Secrets:** All environment variables injected via hosting platform secret management (never in source control).

---

## 6. Unresolved Decisions & Dependencies Requiring Technical Lead Input

| ID | Decision | Impact | Options | Status |
| :--- | :--- | :--- | :--- | :--- |
| **UD-001** | Backend hosting provider | Deployment cost and India region latency | Fly.io (preferred), Railway, Render | **Awaiting confirmation** |
| **UD-002** | Polling vs WebSocket for scan progress | UX during OCR processing (6–8 sec) | HTTP polling every 1.5s (simpler); WebSocket (better UX) | **Awaiting confirmation** |
| **UD-003** | Image storage for uploaded receipts | Privacy, cost, retention policy | Cloudinary (CDN, managed); Local server filesystem (simpler for MVP) | **Awaiting confirmation** |
| **UD-004** | Dataset licensing verification & Haul scan model training | M4 cannot begin until model and datasets are approved | Must inspect downloaded dataset archives, verify annotation licenses, document attribution obligations and commercial-use terms with primary evidence, and complete local image collection before training authorization | **Awaiting evidence & technical lead approval** |
| **UD-005** | Email provider for daily digest | M3 notification completeness | JavaMailSender + SMTP (free); Resend HTTP API (simpler template management) | **Awaiting confirmation** |
| **UD-006** | Tesseract `tessdata` bundling strategy | Affects Docker image size (+50MB for `eng.traineddata`) | Bundle in Docker image; mount as a Docker volume; download on first startup | **Awaiting confirmation** |

---
*End of Development Roadmap v1.1.4. All milestones updated for AD-001 (Spring Boot backend) and AD-002 (no generative AI). Awaiting final Technical Lead M0 approval.*
*Awaiting technical lead authorization before Milestone M1 implementation begins.*

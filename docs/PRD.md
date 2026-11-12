# FoodWise — Product Requirements Document (PRD)

**Document Version:** 1.1.4  
**Status:** Pending Technical Lead Approval — Final M0 Documentation Consistency Review  
**Author:** FoodWise Product & Architecture Team  
**Reviewer / Technical Lead:** Technical Lead (ChatGPT)  
**Target Platform:** Web (Desktop & Mobile-first Responsive)  
**Changelog:** v1.1.4 — Standardized expiry boundary strictly to expiry_date < CURRENT_DATE (items expiring today remain Expiring Today, not Expired); aligned digest notification to 15-minute user preferences and periodic scheduler; replaced '< 24 hours' in US-02 with date-based phrasing ('paneer expiring today and spinach expiring in 2 days'); marked Milestone M0 as pending final Technical Lead approval.

---

## 1. Product Vision

**Product Name:** FoodWise  
**Tagline:** *Know what you have. Know what to eat. Know what to buy. Waste less.*

### Vision Statement
FoodWise is an India-first personal food management platform engineered to eliminate domestic food waste and financial leakage in modern, fast-paced Indian households. By turning chaotic refrigerator drawers and pantry shelves into a live, transparent, and intelligent inventory, FoodWise empowers young professionals, students, and budget-conscious homemakers to make timely cooking choices, shop intentionally, and cook delicious meals with the ingredients they already possess.

All intelligent features are powered by **open datasets, local OCR, trained computer vision models, and deterministic algorithmic logic**. FoodWise does not use any generative AI API or cloud LLM service.

---

## 2. Problem Statement

Food waste in modern urban Indian households has surged due to lifestyle shifts, the rise of hyper-convenient quick-commerce (Blinkit, Zepto, Swiggy Instamart), and busy professional schedules:

1. **The "Crisper Drawer Graveyard" Phenomenon:**
   Quick-commerce enables instant impulse buying. Urbanites order coriander (*dhaniya*), chillies, tomatoes, paneer, and leafy greens (*palak*, *methi*) with minimal friction. Without visibility, these perishables sit in refrigerator crispers until they decompose.
2. **Invisible Expiry Dates:**
   Packaged staples (bread, dahi, milk packets, tofu, batter) and cooked leftovers (*dal*, *sabzi*, boiled rice) lack active reminders. In warm, humid Indian weather, ambient and poorly stored foods spoil rapidly within 24–48 hours.
3. **The Daily Friction of "What Do I Cook Tonight?":**
   After a long workday, individuals stare at a fridge full of disparate ingredients, cannot synthesize a recipe, and surrender to expensive, unhealthy food delivery apps (Zomato/Swiggy).
4. **Duplicate & Chaotic Shopping:**
   Without a synchronized real-time inventory, shoppers at local *kirana* stores or supermarkets buy duplicate packets of spices, pulses, or sauces while forgetting the single critical missing ingredient needed for dinner.
5. **Unmeasured Financial Drain:**
   Few individuals realize that discarding ₹150 worth of spoiled vegetables, sour milk, and rotting leftovers every week results in an annual financial loss of ₹8,000–₹12,000 per person.

---

## 3. Target Audience

FoodWise is focused on urban and semi-urban Indian kitchen managers:

1. **College & University Students Living in PGs/Flats:**
   - Age: 18–23.
   - Profile: Cooking for themselves for the first time on a shoe-string pocket allowance. Frequent grocery spillage and lack of basic preservation knowledge.
2. **Young Working Professionals Living Alone:**
   - Age: 22–30.
   - Profile: IT/corporate employees in tech hubs (Bengaluru, Hyderabad, Pune, Gurgaon, Mumbai). High quick-commerce usage, irregular cooking habits, high food delivery spend.
3. **Couples & Flatmates Managing Shared Pantries:**
   - Age: 24–35.
   - Profile: Shared grocery budgets, split cooking duties, frequent confusion over who bought what and when perishables expire.
4. **Budget-Conscious Independent Homemakers:**
   - Age: 25–45.
   - Profile: Seeking to minimize grocery bills, organize regional pantries (*masala dabbas*, lentils, flours), and preserve homemade food.

---

## 4. User Personas

### Persona 1: Rohan Sharma (The Quick-Commerce Tech Worker)
- **Age:** 25 | **Location:** HSR Layout, Bengaluru | **Role:** SDE-1
- **Living Setup:** Rented 1BHK, lives alone.
- **Cooking Habits:** Cooks 3–4 days a week, orders food on others. Orders daily groceries on Zepto/Blinkit.
- **Frustration:** Discovers slimy coriander, moldy bread, and expired Greek yogurt every Sunday clean-up. Loses ₹1,500+ every month to spoiled food.
- **Goal:** Get quick alerts before food goes bad, scan a photo of groceries after delivery to log them effortlessly, and get 20-minute recipe ideas based on what's inside his fridge.

### Persona 2: Priya Deshmukh (The Budget-Conscious PG Student)
- **Age:** 21 | **Location:** Kothrud, Pune | **Role:** Final-year Master's Student
- **Living Setup:** Shared 2BHK flat with 2 roommates.
- **Cooking Habits:** Prepares simple one-pot meals (khichdi, poha, egg curry, dal-chawal).
- **Frustration:** Strict monthly allowance of ₹6,000 for groceries. Roommates often buy duplicate items while milk packets curdle because nobody tracked the expiry.
- **Goal:** Keep an accurate count of shared kitchen items, maintain a joint shopping list, and reduce wasteful spending.

### Persona 3: Vikram & Ananya Sengupta (The Working Urban Couple)
- **Age:** 29 & 31 | **Location:** Cyber City, Gurgaon | **Role:** Marketing Lead & Product Manager
- **Living Setup:** 2BHK high-rise apartment.
- **Cooking Habits:** Alternate cooking on weekdays; heavy meal prepping on Sundays.
- **Frustration:** Cooked dishes (*dal tadka*, *paneer bhurji*) get stored in identical steel or glass *dabbas* and pushed to the back of the fridge, then forgotten until mold appears.
- **Goal:** Track cooked leftovers with a reliable countdown timer and receive recommendations for repurposing leftover dal/sabzi into parathas or cutlets.

---

## 5. User Pain Points Matrix

| Pain Point | Real-World Impact | FoodWise Solution |
| :--- | :--- | :--- |
| **Silent Spoilage** | Vegetables & dairy rot out of sight. | Visual expiry timeline, color-coded health badges, and automated proactive morning alerts via Spring Scheduler. |
| **Manual Data-Entry Fatigue** | Logging 20 items one-by-one feels like homework. | OCR-based receipt scanning (Tesseract) and a food catalog matcher that auto-classifies items, followed by mandatory human-in-the-loop review. |
| **Decision Paralysis** | Staring at ingredients without knowing what to cook. | Dataset-driven Recipe Matcher ranking dishes by weighted ingredient availability score, prioritizing items near expiry. |
| **Forgotten Cooked Leftovers** | Unlabeled containers (*dabbas*) spoil after 48 hours. | Dedicated Leftover Tracker with conservative, user-configurable storage guidance countdowns and rule-based culinary transformation suggestions. |
| **Disorganized Shopping** | Forgetting essentials; buying duplicate staples. | Consolidated Shopping List synced with pantry thresholds and missing recipe ingredients. |
| **Unconscious Financial Waste** | Discarding food feels free in the moment. | Monthly Spending & Waste Analytics showing real rupee (₹) losses. |

---

## 6. Product Objectives & KPIs

| Objective | Metric / KPI | MVP Target (3 Months Post-Launch) |
| :--- | :--- | :--- |
| **Reduce Household Waste** | Self-reported grocery waste reduction | ≥ 30% reduction per active user |
| **Lower Logging Friction** | Time to log a 10-item grocery haul | < 60 seconds via OCR receipt scan + review |
| **Drive Daily Cooking Utility** | Recipe recommendations converted to cooked meals | ≥ 2.5 meals cooked/logged per week |
| **Prevent Expiry Spoilage** | Items consumed before expiry date | ≥ 85% of tracked perishable items |
| **Shopping Accuracy** | Duplicate purchases reported | < 5% of shopping trips |
| **User Retention** | Weekly Active Users (WAU / MAU) | ≥ 40% (high utility baseline) |

---

## 7. Functional Requirements

### 7.1 Kitchen Inventory Management
- **FR-INV-001:** Users can categorize items by Storage Location: *Fridge*, *Freezer*, *Pantry*.
- **FR-INV-002:** Support standard Indian culinary measurement units: *grams (g)*, *kilograms (kg)*, *milliliters (ml)*, *liters (L)*, *pieces (pcs)*, *packets (pkt)*, *bunch/gaddi*.
- **FR-INV-003:** Inventory Batch Tracking: Track separate batches of the same food item bought on different dates with distinct expiry dates (SQL `DATE` precision) and prices.
- **FR-INV-004:** Quantity Adjustment: Support partial consumption (e.g., deducting 250g from a 1kg flour bag or 2 eggs from a 6-pack) with one-tap or slider controls.
- **FR-INV-005:** Search, Filter & Sort: Instant real-time filtering by category (Dairy, Veggies, Pulses, Masalas, etc.), storage location, and sorting by *Expiry Date (Ascending/Descending)*, *Date Added*, and *Alphabetical*.

### 7.2 Food Expiry Tracking & Alerts
- **FR-EXP-001:** Conservative, configurable shelf-life storage guidance defaults drawn from the integrated Indian Food Master Catalog. USDA FoodKeeper is a candidate reference for research and validation only, not an automatically approved source of shelf-life values for the Indian food catalog. Any proposed shelf-life values must be cross-checked against credible, India-relevant food-safety sources and explicitly approved by the Technical Lead before being incorporated. Until formal approval, catalog shelf-life values remain `NULL`. The application must never invent or silently assume shelf-life defaults. USDA FoodData Central is utilized solely for canonical food name normalization, nutrient attributes, and category classification (not shelf-life estimation). Storage guidance represents advisory estimates rather than absolute food safety guarantees; actual safety depends on preparation hygiene, promptness of cooling, handling, and continuous storage temperature. If no validated value exists, the user must provide an expiry date. Catalog-derived estimates and user-entered dates must be clearly distinguishable in the UI. Users may customize any default duration.
- **FR-EXP-002:** Dynamic Visual Status Badges (Derived UI States):
  - `Fresh`: > 3 days remaining (Green)
  - `Use Soon`: 2–3 days remaining (Amber)
  - `Expiring Today`: Due today (`expiry_date = CURRENT_DATE`, Orange)
  - `Expired`: Overdue based on `expiry_date < CURRENT_DATE` (Muted Red). Items expiring today remain `Expiring Today`, not `Expired`. Expiry is a derived UI and triage state; `inventory_batches.status` permits only `'active'`, `'consumed'`, `'discarded'`, `'spoiled'` in the database schema, with no persisted `'expired'` lifecycle state.
- **FR-EXP-003:** Daily Expiry Summary Notification: A Spring `@Scheduled` job dispatches user-specific morning digest notifications for eligible kitchen members according to each user's configured `notify_daily_digest_time` preference (constrained to 15-minute intervals, default 08:00), evaluated periodically with daily deduplication, creating distinct `notifications` rows per recipient user without duplicate alerts.
- **FR-EXP-004:** One-Tap Disposal / Consumption: Quick actions from alerts to mark an item as `Cooked / Consumed` or `Discarded (Logged as Waste)`.

### 7.3 OCR-Based Receipt Scanning & Food Item Recognition

> **Architectural Note (AD-002):** This feature uses **local, open-source OCR and trained computer vision** exclusively. No generative AI API (Gemini, OpenAI, etc.) is used at any layer.

#### 7.3.1 Receipt OCR (Grocery Bills)
- **FR-SCAN-001:** Image Upload / Camera Capture: Users can capture or upload photos of paper grocery bills (Blinkit, Zepto, D-Mart, Spencer's, Reliance Fresh) or thermal receipt printouts.
- **FR-SCAN-002:** OCR Processing via Tesseract: The uploaded receipt image is preprocessed (deskew, binarize, denoise) on the Spring Boot backend and submitted to Tesseract 5 (`tesseract-core` via Tess4J) for raw text extraction.
- **FR-SCAN-003:** Text Parsing & Catalog Matching: A rule-based text parser tokenizes OCR output and matches tokens against the `food_catalog` using fuzzy string matching (Apache Commons Text `JaroWinklerDistance` or similar) to identify food item names, quantities, and prices.
- **FR-SCAN-004:** Human-in-the-Loop Review: Parsed candidates are **always** presented in an editable confirmation table. No item is written to inventory without user review and explicit confirmation.

#### 7.3.2 Grocery Image Recognition (Countertop Hauls)
- **FR-SCAN-005:** For countertop haul photographs (photographing multiple loose vegetables and packed goods), FoodWise uses a **pre-trained or fine-tuned object detection model** (e.g., YOLOv8 fine-tuned on a food image dataset). Detected object labels are matched against `food_catalog`.
- **FR-SCAN-006:** This feature is clearly labelled as "experimental" in the UI for the MVP, with confidence scores surfaced to the user.
- **FR-SCAN-007:** All recognized items pass through the same human-in-the-loop review stage as receipt OCR output before any inventory write occurs.

### 7.4 Ingredient-Based Recipe Recommendations
- **FR-REC-001:** Deterministic Ingredient Matching: The Spring Boot `RecipeRecommendationService` computes a weighted availability score for each recipe against active inventory batches.
- **FR-REC-002:** Readiness Indicators:
  - `Ready to Cook (100% Match)`: All non-staple ingredients available.
  - `Missing 1–2 Items (80–90% Match)`: Almost cookable; missing items can be added to the shopping list with one click.
- **FR-REC-003:** Expiry-Priority Weighting: Ingredients in `Use Soon` or `Expiring Today` status receive elevated weight in the scoring formula, surfacing recipes that rescue perishables.
- **FR-REC-004:** Cooking Mode & Inventory Deduction: When the user marks a recipe as "Cooked", the Spring Boot backend automatically deducts recipe ingredient quantities from the corresponding active inventory batches inside a single database transaction.

### 7.5 Leftover Management
- **FR-LFT-001:** Dedicated Leftover Entry: Fast logging for cooked foods (*Dal*, *Sabzi*, *Rice*, *Curry*, *Roti*) with date/time prepared and storage container note.
- **FR-LFT-002:** Storage Guidance Date Indicators: Provide conservative, user-configurable date-level storage guidance for cooked Indian food. Existing guidance examples for cooked Indian foods (e.g., refrigerated cooked rice: ~2 days; cooked dal: ~3 days; dairy-based gravies: ~2 days under proper continuous refrigeration) are non-normative advisory examples until formally validated against India-relevant food-safety references and approved by the Technical Lead; they must not be hardcoded as production safety defaults. M6 and the production application must use only validated, approved leftover guidance. Where no approved guidance exists, the application must require an explicit user-provided expiry date without silently inventing safety defaults. Expiry tracking operates strictly on date-level precision aligned with SQL `DATE` (`inventory_batches.expiry_date`), preserving `cooked_at` timestamp semantics while avoiding unsupported hour-level countdown precision. The application explicitly communicates that these indicators provide general storage guidance rather than guarantees of food safety, as actual safety depends on cooking temperature, cooling rate, handling, and refrigerator performance.
- **FR-LFT-003:** Leftover Repurposing Engine: Rule-based culinary transformation engine (maintained as structured data in the database) suggests practical culinary transformations (e.g., leftover dal → *Missi Roti* dough, leftover rice → *Lemon Rice* or *Fried Rice*, leftover sabzi → toasted sandwich).

### 7.6 Grocery Shopping Lists
- **FR-SHP-001:** Smart Auto-Population: One-click addition of missing ingredients from recipe discovery and low-stock inventory alerts.
- **FR-SHP-002:** Manual Add & Categorization: Group items automatically by store aisle / category (Vegetables, Dairy, Staples, Spices).
- **FR-SHP-003:** In-Store Shopping Mode: High-contrast checklist with tap-to-cross.
- **FR-SHP-004:** Direct Transfer to Inventory: When an item is checked off as bought, provide a prompt: *"Add checked items to Kitchen Inventory?"*. Automatic shelf-life assignment applies only when validated catalog guidance exists; otherwise, the application must request a user-provided expiry date. The application must never invent or silently assume an expiry date.

### 7.7 Food Spending & Waste Analytics
- **FR-ANA-001:** Monthly Kitchen Spend Tracker: Log purchase prices in Indian Rupees (₹) to display total monthly grocery expenditure.
- **FR-ANA-002:** Rupee Waste Quantification: Whenever items are discarded as spoiled or expired, compute the financial loss based on item purchase cost.
- **FR-ANA-003:** Waste Category Breakdown: Visual breakdown showing which categories cause the most waste (e.g., 60% Vegetables, 25% Dairy, 15% Bakery).
- **FR-ANA-004:** Savings Impact Indicator: Show positive reinforcement metrics (e.g., *"You rescued ₹840 worth of groceries this month by cooking them on time!"*).

---

## 8. Non-Functional Requirements

### 8.1 Performance & Responsiveness
- **NFR-PERF-001:** First Contentful Paint (FCP) under 1.2s on 4G mobile networks.
- **NFR-PERF-002:** Time to Interactive (TTI) under 2.5s.
- **NFR-PERF-003:** Inventory search and filter REST API response time under 200ms for up to 500 items (Spring Boot + JPA indexed queries).
- **NFR-PERF-004:** OCR receipt processing completed and results returned to client within 6 seconds under normal server load. This budget accounts for image preprocessing + Tesseract + catalog matching on the Spring Boot backend.
- **NFR-PERF-005:** Grocery haul image recognition returned within 8 seconds. Results marked as experimental.

### 8.2 Usability & Mobile Experience
- **NFR-UX-001:** Mobile-First Layout: Optimized for single-thumb mobile usage (bottom navigation, minimum 44px tap targets).
- **NFR-UX-002:** Accessible Color Contrast: Minimum 4.5:1 contrast ratio across all text and UI badges adhering to WCAG 2.1 AA standards.
- **NFR-UX-003:** Low Cognitive Load: Minimal forms, intelligent defaults, auto-complete for Indian ingredient names, and no nested submenus exceeding 2 levels.

### 8.3 Reliability & Data Integrity
- **NFR-REL-001:** Inventory Batch Consistency: Strict Spring `@Transactional` with pessimistic locking for every inventory quantity change to prevent drift or negative stock.
- **NFR-REL-002:** Offline Resilience: Graceful fallback when network connection drops, preserving uncommitted form inputs in browser local storage.

### 8.4 Security & Privacy
- **NFR-SEC-001:** Secure Authentication: Clerk-managed authentication on the frontend; Spring Security verifies Clerk-issued JWTs on every protected API call.
- **NFR-SEC-002:** Data Isolation: Kitchen-level multi-tenant isolation enforced in Spring Boot service layer ensuring users can only read or mutate records tied to their authorized kitchen ID.
- **NFR-SEC-003:** Receipt & Image Privacy: Uploaded grocery images stored server-side (or Cloudinary), with temporary OCR processing files deleted immediately after text extraction completes.

---

## 9. User Stories & Acceptance Criteria

### US-01: Quick Grocery Logging via Receipt OCR
> **As a** busy software developer  
> **I want to** take a photo of my grocery bill  
> **So that** my inventory is populated without tedious manual typing.

**Acceptance Criteria (Given-When-Then):**
- **Given** I am on the Scan screen and select "Scan Receipt",
- **When** I upload a photo of my Blinkit bill and tap "Process",
- **Then** the Spring Boot backend processes OCR within 6 seconds and presents an editable review table listing extracted items with names, estimated quantities, units, and shelf-life guidance where validated catalog estimates exist (or requiring user-entered expiry dates when unvalidated).
- **Given** an item has been incorrectly recognized by OCR,
- **When** I tap the item row in the review table,
- **Then** I can correct the name or quantity before clicking "Confirm & Add to Inventory".

### US-02: Expiry Risk Morning Briefing
> **As an** independent renter managing my kitchen  
> **I want to** see which ingredients are expiring in the next 48 hours  
> **So that** I prioritize them for today's dinner before they spoil.

**Acceptance Criteria:**
- **Given** I have paneer expiring today and spinach expiring in 2 days,
- **When** I open the FoodWise dashboard,
- **Then** both items appear at the top of the "Urgent Expiry" carousel with Amber and Orange status badges.
- **When** I tap "Find Recipes with These",
- **Then** the recipe discovery engine immediately returns recipes utilizing *paneer* and *palak* (e.g., Palak Paneer) sorted by match score.

### US-03: Cook & Auto-Deduct Inventory
> **As a** home cook  
> **I want to** mark a recipe as "Cooked"  
> **So that** my inventory balances automatically update without manual recalculation.

**Acceptance Criteria:**
- **Given** I have 500g Toor Dal in my pantry and cook a recipe requiring 200g Toor Dal,
- **When** I click the "I Cooked This" button on the Recipe Detail page,
- **Then** the Spring Boot backend executes a `@Transactional` deduction, logs an inventory event of type `consumed_partially`, and updates the active batch quantity to 300g.
- **Then** if the remaining balance falls below the minimum safety threshold (e.g., 250g), an optional prompt offers: *"Add Toor Dal to Shopping List?"*.

### US-04: Leftover Tracking
> **As a** working professional  
> **I want to** log a bowl of leftover Dal Tadka  
> **So that** I have clear storage guidance to consume it before quality degrades and receive ideas to repurpose it.

**Acceptance Criteria:**
- **Given** I store cooked dal in the fridge,
- **When** I log it in "Leftovers" with time `Today, 2:00 PM`,
- **Then** the backend computes an advisory storage window based on validated and approved guidance for cooked lentil dishes (e.g., ~3-day advisory date if approved), or requires a user-provided expiry date if unvalidated, returning a date-level expiry date (SQL `DATE` precision, reminding the user that actual safety depends on prompt refrigeration and proper reheating, never silently inventing safety guarantees).
- **When** the item reaches its final day before expiry (or 'Expiring Today' state),
- **Then** FoodWise surfaces the rule-based suggestion: *"Make Dal Parathas for dinner tonight with your remaining Dal Tadka"*.

---

## 10. MVP Feature Scope

### Included in MVP (Milestone M0 through M8)
1. **Kitchen Profile & Preferences:** Single kitchen management with support for direct kitchen member records (shareable flatmate invitation links and tokenized invitation workflows are future functionality outside the MVP).
2. **Indian Food Master Catalog:** Built-in seed data via Flyway migrations with ~200 common Indian vegetables, staples, dairy products, spices, and fruits with deterministic default storage recommendations.
3. **Multi-Location Inventory:** Fridge, Freezer, Pantry tabs with batch tracking (quantity, unit, purchase date, expiry date, cost in ₹).
4. **Expiry Management Engine:** Visual indicators (Fresh, Use Soon, Expiring Today, Expired derived from expiry date; items expiring today are Expiring Today, not Expired) and periodic `@Scheduled` digest notifications dispatched per-user based on `notify_daily_digest_time` preference in 15-minute intervals (default: 08:00).
5. **OCR Receipt Scan:** Tesseract-based OCR for grocery receipt image scanning, with catalog fuzzy-matching and mandatory human-in-the-loop review.
6. **Experimental Grocery Haul Recognition:** Computer vision model (YOLO-based) detection on countertop photos, with results clearly labelled as experimental.
7. **Dataset-Driven Recipe Recommendation:** 75+ tested simple Indian recipes in the seed database, matched by weighted ingredient availability score.
8. **Leftover Tracker:** Conservative date-level storage guidance and rule-based culinary repurposing for cooked Indian dishes.
9. **Smart Shopping List:** Category-grouped checklist with low-stock sync and one-tap inventory import.
10. **Basic Rupee Spending & Waste Analytics:** Total spent, total wasted in ₹, category distribution chart.

---

## 11. Explicitly Excluded Features (Out of Scope for MVP)

- ❌ **Generative AI APIs:** No Gemini, OpenAI, Anthropic, or any cloud LLM is used at any layer of FoodWise.
- ❌ **Direct Quick-Commerce API Ordering:** No automated reordering via Blinkit, Zepto, or Swiggy APIs (these APIs are proprietary and lack public partner access).
- ❌ **Barcode Scanning Hardware & Global UPC Databases:** Indian loose produce and local *kirana* repackaged pulses rarely carry standard barcodes. OCR-based receipt scanning and catalog matching are prioritized instead.
- ❌ **Flatmate Invitation-Link Generation / Tokenized Sharing:** Direct kitchen membership is supported via relational member records, but shareable invitation links, tokenized URLs, and external invite workflows are deferred outside the MVP.
- ❌ **Multi-Flatmate Shared Expense Splitting / Splitwise Integration:** Cost-sharing calculations between roommates are deferred to future milestones.
- ❌ **Complex Macro & Calorie Tracking / Diet Coach:** FoodWise is an inventory and anti-waste platform, not a fitness/macro-counting app like MyFitnessPal.
- ❌ **IoT Smart Refrigerator Hardware Integrations:** No hardware sensor or proprietary appliance telemetry hooks.
- ❌ **Multi-Language Localization (Vernacular UI):** English interface with Hinglish/vernacular food item terms (e.g., *Jeera*, *Bhindi*, *Atta*, *Paneer*) in the catalog for MVP.
- ❌ **Custom ML Model Training Infrastructure:** Model training and fine-tuning occur offline before deployment. The MVP does not include a training pipeline or retraining loop.

---

## 12. Success Metrics & Target Thresholds

1. **Activation Rate:** ≥ 70% of registered users add at least 5 inventory items within their first 24 hours.
2. **Scan Adoption:** ≥ 50% of multi-item inventory additions initiated via OCR receipt scan rather than manual single-item forms.
3. **Recipe Utility:** ≥ 30% of active users trigger "Cook with what I have" at least twice weekly.
4. **Waste Reduction:** Average active user self-reports a reduction in discarded perishables from 4 items/week down to ≤ 1 item/week.
5. **Financial Consciousness:** 80% of active users view the Spending & Waste Analytics screen at least once a month.

---

## 13. Product Risks & Mitigation Strategies

| Risk | Impact | Likelihood | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| **OCR Accuracy on Indian Receipts** | High | High | Tesseract performs adequately on clean printed receipts but degrades on low-quality thermal prints. Image preprocessing (deskew, binarize, denoise via OpenCV/Imgscalr) is mandatory before OCR. Mandatory human review prevents bad data entering inventory. |
| **Computer Vision Accuracy on Countertop Hauls** | Medium | High | Haul recognition is explicitly marked "experimental" in the UI. The YOLO model handles common objects but will struggle with uncommon Indian regional produce. Human review is mandatory. |
| **Food Catalog Coverage Gaps** | Medium | Medium | Fuzzy matching covers 80–90% of common items. Users can manually add unlisted items; the catalog is extensible via Flyway migrations. |
| **Notification Fatigue** | High | Medium | Single consolidated morning digest scheduled per user's digest time preference (default 08:00) via Spring Scheduler rather than sporadic random alerts throughout the day. |
| **Discrepancy in Shelf-Life Rules** | Medium | High | Conservative default storage guidance tailored to Indian conditions; defaults validated against credible food-safety sources; users can manually customize durations; UI clarifies guidance is advisory and that actual safety depends on storage temperature. |
| **Inventory Desynchronization** | High | Medium | Easy quick-audit screen ("Kitchen Pulse") allowing users to mark items as gone with a single swipe; inventory events ledger provides auditability. |

---
*End of PRD v1.1.4. Updated to reflect AD-001 (Spring Boot backend) and AD-002 (no generative AI). Awaiting final Technical Lead M0 approval.*

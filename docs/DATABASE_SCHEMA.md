# FoodWise — Relational Database Schema Specification

**Document Version:** 1.1.3  
**Status:** Pending Technical Lead Approval — Final M0 Documentation Consistency Review  
**Target Engine:** PostgreSQL 16+  
**ORM Target:** Spring Data JPA / Hibernate  
**Migration Tool:** Flyway  
**Author:** FoodWise Product & Architecture Team  
**Reviewer / Technical Lead:** Technical Lead (ChatGPT)  
**Changelog:** v1.1.3 — Standardized derived expiry logic to expiry_date < CURRENT_DATE (items expiring today remain 'Expiring Today', not 'Expired'); marked Milestone M0 baseline as pending final Technical Lead approval; preserved all schema columns, types, nullability, constraints, relationships, and indexes.

---

## 1. Schema Architecture & Design Philosophy

The FoodWise database schema is designed with four foundational guarantees:

1. **Strict Multi-Tenant Isolation:** Every kitchen resource is explicitly partitioned by `kitchen_id`. Cross-kitchen data leakage is structurally impossible.
2. **Immutable Transactional History:** Physical food stock changes (purchasing, consuming, spoiling, adjusting) are backed by an append-only event ledger (`inventory_events`) alongside mutable state (`inventory_batches`).
3. **Batch-Level Granularity:** Perishables purchased at different times or prices maintain distinct expiration countdowns and unit costs.
4. **Referential Integrity & Safe Deletions:** Appropriate cascading rules (`ON DELETE CASCADE` for parent-child dependencies; `ON DELETE SET NULL` for historical references) guarantee zero orphaned records or historical corruption.

> **ORM & Migration Notes:**
> - All schema declarations are implemented as Spring Data JPA / Hibernate `@Entity` classes. The Hibernate schema DDL auto-generation setting is set to `validate` in production; it is never used to create or alter tables.
> - All schema creation, alteration, and seed data operations are managed exclusively by **Flyway** versioned SQL migration scripts located at `backend/src/main/resources/db/migration/`. No migration SQL is included in this document — this document specifies the target schema state only.
> - **Java Enum & Database Check-Constraint Mapping:** Database check constraints and string column values remain strictly **lowercase** across all tables (e.g. `notification_type` values `'expiry_warning'`, `'item_expired'`, `'stock_low'`, `'leftover_alert'`, `'waste_summary'`; `scan_type` values `'receipt'`, `'grocery_haul'`, `'fridge_shelf'`; `scan_status` values `'pending'`, `'confirmed'`, `'rejected'`, `'failed'`; `batch_status` values `'active'`, `'consumed'`, `'discarded'`, `'spoiled'`; `storage_location` values `'fridge'`, `'freezer'`, `'pantry'`). Java domain models use conventional UPPERCASE enum constants (e.g. `NotificationType.EXPIRY_WARNING`, `BatchStatus.ACTIVE`) mapped to lowercase database strings via JPA `@Converter(autoApply = true)` or custom `AttributeConverter` implementations.
> - **Lifecycle Status & Expiry Semantics:** In `inventory_batches`, the `status` column permits strictly `'active'`, `'consumed'`, `'discarded'`, `'spoiled'`. There is **NO `expired` database status**. Expiry is a derived query/UI state based on `expiry_date < CURRENT_DATE` and `status = 'active'` (items where `expiry_date = CURRENT_DATE` remain `Expiring Today`, not `Expired`). Expiry tracking operates strictly on SQL `DATE` precision (`inventory_batches.expiry_date`), while `cooked_at` preserves `TIMESTAMPTZ` audit semantics.
> - Do not generate Java entities or Flyway migration scripts from this document during Milestone M0.

---

## 2. Entity-Relationship Overview

```
                                  +-------------------+
                                  |       users       |
                                  +-------------------+
                                    |        |       |
                 +------------------+        |       +-------------------+
                 |                           |                           |
                 v                           v                           v
       +-------------------+       +-------------------+       +-------------------+
       | kitchen_members   |       | user_preferences  |       |   notifications   |
       +-------------------+       +-------------------+       +-------------------+
                 |
                 v
       +-------------------+
       |     kitchens      |
       +-------------------+
         |       |       |
         |       |       +-------------------------------------------------------+
         |       v                                                               |
         |  +-------------------+       +-------------------+       +-------------------+
         |  |   shopping_lists  |       | grocery_purchases |       | food_waste_logs   |
         |  +-------------------+       +-------------------+       +-------------------+
         |           |                           |                           |
         |           v                           v                           |
         |  +-------------------+       +-------------------+                |
         |  |shopping_list_items|       |  purchase_items   |                |
         |  +-------------------+       +-------------------+                |
         |                                                                   |
         v                                                                   |
+-------------------+                                                        |
| inventory_batches |<-------------------------------------------------------+
+-------------------+
   |             ^
   |             | (audit link)
   v             |
+-------------------+
| inventory_events  |
+-------------------+
```

---

## 3. Detailed Table Specifications

### 3.1 `users`
Stores synchronized user profiles authenticated via Clerk.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Internal system UUID |
| `clerk_id` | `VARCHAR(128)` | No | None | Unique | Clerk external subject ID |
| `email` | `VARCHAR(255)` | No | None | Unique | User primary email address |
| `first_name` | `VARCHAR(100)` | Yes | `NULL` | | First name |
| `last_name` | `VARCHAR(100)` | Yes | `NULL` | | Last name |
| `image_url` | `TEXT` | Yes | `NULL` | | Avatar image URL |
| `created_at` | `TIMESTAMPTZ` | No | `now()` | | Timestamp created |
| `updated_at` | `TIMESTAMPTZ` | No | `now()` | | Timestamp updated |

- **Indexes:**
  - `idx_users_clerk_id` ON (`clerk_id`)
  - `idx_users_email` ON (`email`)

---

### 3.2 `kitchens`
Logical household or shared living unit.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Internal kitchen UUID |
| `name` | `VARCHAR(100)` | No | None | | e.g., "Rohan's Kitchen" |
| `created_by_user_id` | `UUID` | No | None | FK -> `users(id)` ON DELETE CASCADE | Founding user |
| `created_at` | `TIMESTAMPTZ` | No | `now()` | | Timestamp created |
| `updated_at` | `TIMESTAMPTZ` | No | `now()` | | Timestamp updated |

- **Indexes:**
  - `idx_kitchens_created_by` ON (`created_by_user_id`)

---

### 3.3 `kitchen_members`
Association table linking users to kitchens with role permissions.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Record UUID |
| `kitchen_id` | `UUID` | No | None | FK -> `kitchens(id)` ON DELETE CASCADE | Associated kitchen |
| `user_id` | `UUID` | No | None | FK -> `users(id)` ON DELETE CASCADE | Associated user |
| `role` | `VARCHAR(20)` | No | `'owner'` | CHECK (`role` IN ('owner', 'member')) | Permission level |
| `joined_at` | `TIMESTAMPTZ` | No | `now()` | | Date user joined kitchen |

- **Constraints & Indexes:**
  - `uq_kitchen_members_kitchen_user` UNIQUE (`kitchen_id`, `user_id`)
  - `idx_kitchen_members_user` ON (`user_id`)

---

### 3.4 `user_preferences`
Personalization settings per user.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Record UUID |
| `user_id` | `UUID` | No | None | FK -> `users(id)` ON DELETE CASCADE, UNIQUE | 1:1 with User |
| `dietary_type` | `VARCHAR(30)` | No | `'omnivore'` | CHECK (`dietary_type` IN ('vegetarian', 'vegan', 'eggetarian', 'jain', 'omnivore')) | Diet preference |
| `currency_code` | `VARCHAR(10)` | No | `'INR'` | | Default ISO currency |
| `currency_symbol` | `VARCHAR(5)` | No | `'₹'` | | Visual symbol |
| `notify_expiry_days_before`| `INTEGER` | No | `3` | CHECK (`notify_expiry_days_before` > 0) | Days before expiry to alert |
| `notify_daily_digest_time` | `TIME` | No | `'08:00:00'` | | Daily morning alert time |
| `email_notifications_enabled` | `BOOLEAN` | No | `true` | | Email digest toggle |
| `push_notifications_enabled` | `BOOLEAN` | No | `true` | | Push notifications toggle |
| `created_at` | `TIMESTAMPTZ` | No | `now()` | | Creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | No | `now()` | | Update timestamp |

---

### 3.5 `food_categories`
Taxonomy of grocery items.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Category UUID |
| `name` | `VARCHAR(50)` | No | None | Unique | e.g. "Vegetables", "Dairy" |
| `slug` | `VARCHAR(50)` | No | None | Unique | URL slug (e.g. `vegetables`) |
| `description` | `TEXT` | Yes | `NULL` | | Short description |
| `icon_name` | `VARCHAR(50)` | Yes | `'Package'` | | Lucide icon name |
| `color_hex` | `VARCHAR(10)` | Yes | `'#668568'` | | Hex color code |
| `display_order` | `INTEGER` | No | `0` | | Sort order |
| `created_at` | `TIMESTAMPTZ` | No | `now()` | | Timestamp |

---

### 3.6 `food_catalog`
Pre-seeded master directory of common Indian ingredients with optional validated shelf-life guidance. In accordance with the food-safety validation policy, shelf-life columns (`shelf_life_room_temp_days`, `shelf_life_fridge_days`, `shelf_life_freezer_days`) are nullable and populated ONLY when credible, India-relevant sources are approved by the Technical Lead; this catalog does not guarantee that every item has populated shelf-life values. Unvalidated values remain `NULL`, and the application never assumes or invents default shelf lives.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Catalog Item UUID |
| `category_id` | `UUID` | No | None | FK -> `food_categories(id)` ON DELETE RESTRICT | Category mapping |
| `name_en` | `VARCHAR(100)` | No | None | | English name (e.g., Coriander) |
| `name_hi` | `VARCHAR(100)` | Yes | `NULL` | | Hindi/Vernacular name (*Dhaniya*) |
| `default_unit` | `VARCHAR(20)` | No | `'pcs'` | CHECK (`default_unit` IN ('g', 'kg', 'ml', 'L', 'pcs', 'pkt', 'bunch')) | Preferred unit |
| `default_location` | `VARCHAR(20)` | No | `'fridge'` | CHECK (`default_location` IN ('fridge', 'freezer', 'pantry')) | Suggested placement |
| `shelf_life_room_temp_days` | `INTEGER` | Yes | `NULL` | | Ambient shelf life |
| `shelf_life_fridge_days` | `INTEGER` | Yes | `NULL` | | Refrigerated shelf life |
| `shelf_life_freezer_days`| `INTEGER` | Yes | `NULL` | | Frozen shelf life |
| `is_perishable` | `BOOLEAN` | No | `true` | | Spoilage indicator |
| `common_storage_tip` | `TEXT` | Yes | `NULL` | | Preservation guideline |
| `created_at` | `TIMESTAMPTZ` | No | `now()` | | Seeded timestamp |

- **Indexes:**
  - `idx_food_catalog_name_en` ON (`name_en`)
  - `idx_food_catalog_name_hi` ON (`name_hi`)
  - `idx_food_catalog_category` ON (`category_id`)

---

### 3.7 `inventory_batches`
Core state table tracking active and historical grocery batches.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Unique Batch UUID |
| `kitchen_id` | `UUID` | No | None | FK -> `kitchens(id)` ON DELETE CASCADE | Owning kitchen |
| `catalog_item_id` | `UUID` | Yes | `NULL` | FK -> `food_catalog(id)` ON DELETE SET NULL | Linked master item |
| `custom_item_name`| `VARCHAR(100)` | No | None | | Human display name |
| `category_id` | `UUID` | Yes | `NULL` | FK -> `food_categories(id)` ON DELETE SET NULL | Category classification |
| `storage_location`| `VARCHAR(20)` | No | `'fridge'` | CHECK (`storage_location` IN ('fridge', 'freezer', 'pantry')) | Physical placement |
| `initial_quantity`| `NUMERIC(10,2)`| No | None | CHECK (`initial_quantity` > 0) | Quantity at acquisition |
| `current_quantity`| `NUMERIC(10,2)`| No | None | CHECK (`current_quantity` >= 0) | Remaining quantity |
| `unit` | `VARCHAR(20)` | No | None | CHECK (`unit` IN ('g', 'kg', 'ml', 'L', 'pcs', 'pkt', 'bunch')) | Metric / Count unit |
| `purchase_date` | `DATE` | No | `CURRENT_DATE`| | Date acquired |
| `expiry_date` | `DATE` | No | None | | User-entered or validated-catalog-computed expiry (never invented when catalog guidance is NULL) |
| `cost_inr` | `NUMERIC(10,2)`| Yes | `NULL` | CHECK (`cost_inr` >= 0) | Purchase price in INR |
| `is_leftover` | `BOOLEAN` | No | `false` | | Cooked food flag |
| `cooked_at` | `TIMESTAMPTZ` | Yes | `NULL` | | Timestamp dish was cooked |
| `leftover_notes` | `TEXT` | Yes | `NULL` | | Container/dabba notes |
| `status` | `VARCHAR(20)` | No | `'active'` | CHECK (`status` IN ('active', 'consumed', 'discarded', 'spoiled')) | Lifecycle state |
| `created_at` | `TIMESTAMPTZ` | No | `now()` | | Record creation |
| `updated_at` | `TIMESTAMPTZ` | No | `now()` | | Last modified |

- **Table Constraints:**
  - `chk_current_lte_initial`: CHECK (`current_quantity` <= `initial_quantity`)
- **Indexes:**
  - `idx_inventory_kitchen_status_expiry` ON (`kitchen_id`, `status`, `expiry_date`)
  - `idx_inventory_kitchen_location` ON (`kitchen_id`, `storage_location`)
  - `idx_inventory_leftovers` ON (`kitchen_id`, `is_leftover`, `status`)

---

### 3.8 `inventory_events`
Immutable transaction ledger recording every quantity change or lifecycle transition.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Event record UUID |
| `batch_id` | `UUID` | No | None | FK -> `inventory_batches(id)` ON DELETE CASCADE | Target batch |
| `kitchen_id` | `UUID` | No | None | FK -> `kitchens(id)` ON DELETE CASCADE | Associated kitchen |
| `event_type` | `VARCHAR(30)` | No | None | CHECK (`event_type` IN ('created', 'consumed_partially', 'consumed_fully', 'discarded_spoiled', 'discarded_expired', 'quantity_adjusted', 'location_moved')) | Event type |
| `quantity_delta` | `NUMERIC(10,2)`| No | None | | Change amount (+/-) |
| `quantity_after` | `NUMERIC(10,2)`| No | None | CHECK (`quantity_after` >= 0) | Remaining quantity after |
| `reason` | `TEXT` | Yes | `NULL` | | Narrative explanation |
| `recipe_id` | `UUID` | Yes | `NULL` | FK -> `recipes(id)` ON DELETE SET NULL | Triggering recipe (if cooked)|
| `waste_log_id` | `UUID` | Yes | `NULL` | | Triggering waste log |
| `performed_by_user_id` | `UUID` | No | None | FK -> `users(id)` ON DELETE RESTRICT | Actor UUID |
| `created_at` | `TIMESTAMPTZ` | No | `now()` | | Timestamp of occurrence |

- **Indexes:**
  - `idx_inventory_events_batch_time` ON (`batch_id`, `created_at` DESC)
  - `idx_inventory_events_kitchen_type` ON (`kitchen_id`, `event_type`)

---

### 3.9 `scan_sessions`
Tracks the lifecycle of a grocery scan request from image upload through OCR/CV processing to human confirmation. Previously referenced as `ai_scan_sessions` — renamed to `scan_sessions` to remove generative-AI connotation; the underlying table stores results from **local OCR (Tesseract) and local computer vision (ONNX/YOLO)** processing, not from any external AI API.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Session UUID |
| `kitchen_id` | `UUID` | No | None | FK -> `kitchens(id)` ON DELETE CASCADE | Associated kitchen |
| `user_id` | `UUID` | No | None | FK -> `users(id)` ON DELETE CASCADE | Submitting user |
| `image_url` | `TEXT` | No | None | | Stored image URL (Cloudinary CDN or local path) |
| `image_public_id`| `VARCHAR(255)` | Yes | `NULL` | | Cloudinary asset ID (null if locally stored) |
| `scan_type` | `VARCHAR(30)` | No | None | CHECK (`scan_type` IN ('receipt', 'grocery_haul', 'fridge_shelf')) | Scan context |
| `status` | `VARCHAR(20)` | No | `'pending'` | CHECK (`status` IN ('pending', 'confirmed', 'rejected', 'failed')) | Session lifecycle state |
| `raw_processing_output` | `JSONB` | Yes | `NULL` | | Raw output from the OCR or CV processing step. For `receipt` scans: the structured token array produced by Tesseract text parsing. For `grocery_haul` scans: the raw detection array (label, bounding box, confidence) from ONNX model inference. Stored for debugging and audit only; not displayed to users. |
| `parsed_items` | `JSONB` | No | `'[]'::jsonb`| | Normalized candidate items after catalog matching. This is the payload shown to the user in the human-in-the-loop review table. Each element contains: `item_name`, `quantity`, `unit`, `suggested_location`, `expiry_date`, `price_inr`, `confidence_score`, `catalog_item_id`. |
| `confirmed_items_count`| `INTEGER` | No | `0` | | Number of items the user explicitly confirmed and imported into `inventory_batches`. |
| `overall_confidence`| `NUMERIC(4,3)`| Yes | `NULL` | CHECK (`overall_confidence` BETWEEN 0.000 AND 1.000) | Aggregate recognition confidence for the session. For `receipt` scans: mean JaroWinkler catalog-match score across all parsed tokens. For `grocery_haul` scans: mean YOLO detection confidence across all retained detections (after NMS and threshold filtering). Surfaced to the user as a scan quality indicator; low values trigger a prominent "Low confidence — please review carefully" banner. |
| `error_message` | `TEXT` | Yes | `NULL` | | Human-readable error description if `status = 'failed'` (e.g., "OCR returned empty text — image may be too dark or blurry"). |
| `created_at` | `TIMESTAMPTZ` | No | `now()` | | Timestamp of image upload |
| `updated_at` | `TIMESTAMPTZ` | No | `now()` | | Timestamp of last status change |

- **Indexes:**
  - `idx_scan_sessions_kitchen_status` ON (`kitchen_id`, `status`)

> **Column rename note:** The column previously called `raw_model_response` (a Gemini-specific term) is now `raw_processing_output` to accurately reflect that its source is local OCR/CV processing. The foreign key reference in `grocery_purchases.receipt_scan_session_id` must reference `scan_sessions(id)` (table rename applied). No other schema relationships change.

---

### 3.10 `recipes`
Master catalog of recipes and user-created custom dishes.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Recipe UUID |
| `title` | `VARCHAR(150)` | No | None | | e.g. "Homestyle Palak Paneer" |
| `slug` | `VARCHAR(150)` | No | None | Unique | URL slug |
| `description` | `TEXT` | Yes | `NULL` | | Dish summary |
| `cuisine` | `VARCHAR(50)` | No | `'Indian'` | | North Indian, South Indian, etc. |
| `dietary_type` | `VARCHAR(30)` | No | `'vegetarian'` | CHECK (`dietary_type` IN ('vegetarian', 'vegan', 'eggetarian', 'non-vegetarian')) | Classification |
| `prep_time_minutes`| `INTEGER` | No | `10` | CHECK (`prep_time_minutes` >= 0) | Prep time |
| `cook_time_minutes`| `INTEGER` | No | `20` | CHECK (`cook_time_minutes` >= 0) | Cook time |
| `servings` | `INTEGER` | No | `2` | CHECK (`servings` > 0) | Portion count |
| `difficulty` | `VARCHAR(20)` | No | `'easy'` | CHECK (`difficulty` IN ('easy', 'medium', 'hard')) | Skill level |
| `instructions` | `JSONB` | No | `'[]'::jsonb`| | Ordered array of step texts |
| `image_url` | `TEXT` | Yes | `NULL` | | Food photography asset |
| `is_system_recipe` | `BOOLEAN` | No | `true` | | Verified default recipe |
| `created_by_user_id`| `UUID` | Yes | `NULL` | FK -> `users(id)` ON DELETE SET NULL | Author (null for system) |
| `created_at` | `TIMESTAMPTZ` | No | `now()` | | Record creation |
| `updated_at` | `TIMESTAMPTZ` | No | `now()` | | Record update |

- **Indexes:**
  - `idx_recipes_dietary` ON (`dietary_type`)
  - `idx_recipes_slug` ON (`slug`)

---

### 3.11 `recipe_ingredients`
Normalized ingredient bill of materials for each recipe.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Item UUID |
| `recipe_id` | `UUID` | No | None | FK -> `recipes(id)` ON DELETE CASCADE | Associated recipe |
| `catalog_item_id` | `UUID` | Yes | `NULL` | FK -> `food_catalog(id)` ON DELETE SET NULL | Link to catalog |
| `ingredient_name` | `VARCHAR(100)`| No | None | | Text label (e.g. "Paneer") |
| `quantity` | `NUMERIC(10,2)`| No | None | CHECK (`quantity` > 0) | Required amount |
| `unit` | `VARCHAR(20)` | No | None | CHECK (`unit` IN ('g', 'kg', 'ml', 'L', 'pcs', 'pkt', 'bunch', 'tbsp', 'tsp', 'pinch')) | Measurement |
| `is_optional` | `BOOLEAN` | No | `false` | | Optional ingredient flag |
| `is_pantry_staple`| `BOOLEAN` | No | `false` | | Excluded from pantry match check (e.g. salt, water) |
| `substitute_notes`| `TEXT` | Yes | `NULL` | | e.g. "Tofu can replace paneer" |
| `display_order` | `INTEGER` | No | `0` | | Step order |

- **Indexes:**
  - `idx_recipe_ingredients_recipe` ON (`recipe_id`)
  - `idx_recipe_ingredients_catalog` ON (`catalog_item_id`)

---

### 3.12 `recipe_favorites`
User bookmarked recipes.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Record UUID |
| `user_id` | `UUID` | No | None | FK -> `users(id)` ON DELETE CASCADE | User UUID |
| `recipe_id` | `UUID` | No | None | FK -> `recipes(id)` ON DELETE CASCADE | Recipe UUID |
| `created_at` | `TIMESTAMPTZ` | No | `now()` | | Timestamp saved |

- **Constraints:**
  - `uq_recipe_favorites_user_recipe` UNIQUE (`user_id`, `recipe_id`)

---

### 3.13 `meal_plans`
Planned kitchen schedule for meals.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Record UUID |
| `kitchen_id` | `UUID` | No | None | FK -> `kitchens(id)` ON DELETE CASCADE | Associated kitchen |
| `user_id` | `UUID` | No | None | FK -> `users(id)` ON DELETE CASCADE | Scheduling user |
| `plan_date` | `DATE` | No | None | | Target date |
| `meal_slot` | `VARCHAR(20)` | No | None | CHECK (`meal_slot` IN ('breakfast', 'lunch', 'dinner', 'snack')) | Meal time |
| `recipe_id` | `UUID` | Yes | `NULL` | FK -> `recipes(id)` ON DELETE SET NULL | Planned recipe |
| `custom_meal_title`| `VARCHAR(150)`| Yes | `NULL` | | Freeform meal description |
| `notes` | `TEXT` | Yes | `NULL` | | Cooking notes |
| `status` | `VARCHAR(20)` | No | `'planned'` | CHECK (`status` IN ('planned', 'cooked', 'skipped')) | Meal status |
| `created_at` | `TIMESTAMPTZ` | No | `now()` | | Timestamp created |

- **Constraints & Indexes:**
  - `uq_meal_plan_slot` UNIQUE (`kitchen_id`, `plan_date`, `meal_slot`)
  - `idx_meal_plans_kitchen_date` ON (`kitchen_id`, `plan_date`)

---

### 3.14 `shopping_lists`
Parent shopping checklist.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | List UUID |
| `kitchen_id` | `UUID` | No | None | FK -> `kitchens(id)` ON DELETE CASCADE | Associated kitchen |
| `name` | `VARCHAR(100)` | No | `'Current Shopping List'` | | List title |
| `status` | `VARCHAR(20)` | No | `'active'` | CHECK (`status` IN ('active', 'archived')) | Status |
| `created_at` | `TIMESTAMPTZ` | No | `now()` | | Created timestamp |
| `updated_at` | `TIMESTAMPTZ` | No | `now()` | | Updated timestamp |

- **Indexes:**
  - `idx_shopping_lists_kitchen_status` ON (`kitchen_id`, `status`)

---

### 3.15 `shopping_list_items`
Individual items inside a shopping list.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Item UUID |
| `shopping_list_id`| `UUID` | No | None | FK -> `shopping_lists(id)` ON DELETE CASCADE | Parent list |
| `catalog_item_id` | `UUID` | Yes | `NULL` | FK -> `food_catalog(id)` ON DELETE SET NULL | Linked catalog item |
| `item_name` | `VARCHAR(100)`| No | None | | Display item text |
| `category_id` | `UUID` | Yes | `NULL` | FK -> `food_categories(id)` ON DELETE SET NULL | Supermarket aisle category |
| `quantity` | `NUMERIC(10,2)`| No | `1.0` | CHECK (`quantity` > 0) | Quantity needed |
| `unit` | `VARCHAR(20)` | No | `'pcs'` | CHECK (`unit` IN ('g', 'kg', 'ml', 'L', 'pcs', 'pkt', 'bunch')) | Unit |
| `estimated_cost_inr` | `NUMERIC(10,2)` | Yes | `NULL` | CHECK (`estimated_cost_inr` >= 0) | Approximate cost |
| `is_checked` | `BOOLEAN` | No | `false` | | Checkbox state |
| `checked_at` | `TIMESTAMPTZ` | Yes | `NULL` | | When checked in store |
| `checked_by_user_id`| `UUID` | Yes | `NULL` | FK -> `users(id)` ON DELETE SET NULL | Ticking user |
| `added_reason` | `VARCHAR(30)` | No | `'manual'` | CHECK (`added_reason` IN ('manual', 'auto_low_stock', 'recipe_missing')) | Origin |
| `linked_recipe_id`| `UUID` | Yes | `NULL` | FK -> `recipes(id)` ON DELETE SET NULL | Sourced from recipe |
| `created_at` | `TIMESTAMPTZ` | No | `now()` | | Creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | No | `now()` | | Modification timestamp |

- **Indexes:**
  - `idx_shopping_items_list` ON (`shopping_list_id`, `is_checked`)

---

### 3.16 `grocery_purchases`
Historical financial record of shopping trips.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Purchase record UUID |
| `kitchen_id` | `UUID` | No | None | FK -> `kitchens(id)` ON DELETE CASCADE | Associated kitchen |
| `user_id` | `UUID` | No | None | FK -> `users(id)` ON DELETE RESTRICT | Paying user |
| `purchase_date` | `DATE` | No | `CURRENT_DATE`| | Date of purchase |
| `store_name` | `VARCHAR(100)`| Yes | `NULL` | | Store name (e.g. Zepto, Kirana)|
| `total_amount_inr`| `NUMERIC(10,2)`| No | None | CHECK (`total_amount_inr` >= 0) | Total bill in INR |
| `receipt_scan_session_id`| `UUID` | Yes | `NULL` | FK -> `scan_sessions(id)` ON DELETE SET NULL | Linked scan session (references renamed table) |
| `receipt_image_url`| `TEXT` | Yes | `NULL` | | Uploaded receipt copy |
| `notes` | `TEXT` | Yes | `NULL` | | Miscellaneous notes |
| `created_at` | `TIMESTAMPTZ` | No | `now()` | | Log timestamp |

- **Indexes:**
  - `idx_grocery_purchases_kitchen_date` ON (`kitchen_id`, `purchase_date` DESC)

---

### 3.17 `purchase_items`
Individual line items itemized on a grocery purchase.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Item UUID |
| `purchase_id` | `UUID` | No | None | FK -> `grocery_purchases(id)` ON DELETE CASCADE | Parent purchase |
| `catalog_item_id` | `UUID` | Yes | `NULL` | FK -> `food_catalog(id)` ON DELETE SET NULL | Linked catalog item |
| `item_name` | `VARCHAR(100)`| No | None | | Line item name |
| `category_id` | `UUID` | Yes | `NULL` | FK -> `food_categories(id)` ON DELETE SET NULL | Category |
| `quantity` | `NUMERIC(10,2)`| No | None | CHECK (`quantity` > 0) | Quantity purchased |
| `unit` | `VARCHAR(20)` | No | None | CHECK (`unit` IN ('g', 'kg', 'ml', 'L', 'pcs', 'pkt', 'bunch')) | Unit |
| `price_inr` | `NUMERIC(10,2)`| No | None | CHECK (`price_inr` >= 0) | Line item total in INR |
| `associated_batch_id`| `UUID` | Yes | `NULL` | FK -> `inventory_batches(id)` ON DELETE SET NULL | Linked live batch |
| `created_at` | `TIMESTAMPTZ` | No | `now()` | | Record timestamp |

- **Indexes:**
  - `idx_purchase_items_purchase` ON (`purchase_id`)

---

### 3.18 `food_waste_logs`
Financial and ecological accounting of discarded food.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Waste entry UUID |
| `kitchen_id` | `UUID` | No | None | FK -> `kitchens(id)` ON DELETE CASCADE | Associated kitchen |
| `user_id` | `UUID` | No | None | FK -> `users(id)` ON DELETE RESTRICT | Discarding user |
| `batch_id` | `UUID` | Yes | `NULL` | FK -> `inventory_batches(id)` ON DELETE SET NULL | Origin batch |
| `item_name` | `VARCHAR(100)`| No | None | | Name of wasted item |
| `category_id` | `UUID` | Yes | `NULL` | FK -> `food_categories(id)` ON DELETE SET NULL | Category classification |
| `quantity_wasted`| `NUMERIC(10,2)`| No | None | CHECK (`quantity_wasted` > 0) | Quantity thrown away |
| `unit` | `VARCHAR(20)` | No | None | CHECK (`unit` IN ('g', 'kg', 'ml', 'L', 'pcs', 'pkt', 'bunch')) | Unit |
| `estimated_loss_inr`| `NUMERIC(10,2)`| No | None | CHECK (`estimated_loss_inr` >= 0) | Money wasted in INR |
| `waste_reason` | `VARCHAR(30)` | No | `'expired'` | CHECK (`waste_reason` IN ('expired', 'spoiled', 'cooked_too_much', 'forgotten_leftover', 'bad_taste')) | Reason |
| `logged_at` | `TIMESTAMPTZ` | No | `now()` | | Timestamp logged |

- **Indexes:**
  - `idx_food_waste_kitchen_time` ON (`kitchen_id`, `logged_at` DESC)
  - `idx_food_waste_category` ON (`category_id`)

---

### 3.19 `notifications`
In-app and push notification log for users.

| Column Name | Data Type | Nullable | Default | Constraints / References | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | `gen_random_uuid()` | Primary Key | Notification UUID |
| `user_id` | `UUID` | No | None | FK -> `users(id)` ON DELETE CASCADE | Target user |
| `kitchen_id` | `UUID` | No | None | FK -> `kitchens(id)` ON DELETE CASCADE | Kitchen context |
| `type` | `VARCHAR(30)` | No | None | CHECK (`type` IN ('expiry_warning', 'item_expired', 'stock_low', 'leftover_alert', 'waste_summary')) | Notification category |
| `title` | `VARCHAR(150)` | No | None | | Headline text |
| `message` | `TEXT` | No | None | | Detailed message text |
| `status` | `VARCHAR(20)` | No | `'unread'` | CHECK (`status` IN ('unread', 'read', 'archived')) | Status |
| `action_url` | `VARCHAR(255)` | Yes | `NULL` | | In-app navigation route |
| `metadata` | `JSONB` | Yes | `NULL` | | Context payload (e.g. batch ID)|
| `created_at` | `TIMESTAMPTZ` | No | `now()` | | Creation timestamp |
| `read_at` | `TIMESTAMPTZ` | Yes | `NULL` | | When viewed by user |

- **Indexes:**
  - `idx_notifications_user_status` ON (`user_id`, `status`, `created_at` DESC)

---

## 4. Quantity Consistency & Transaction History Details

To guarantee zero stock drift, every batch mutation follows a strict protocol:

1. **State Update (`inventory_batches`):**
   - When 200g of a 500g batch is consumed, `current_quantity` updates from `500.00` to `300.00`.
   - If `current_quantity` reaches `0.00`, `status` is automatically transitioned from `'active'` to `'consumed'`.
2. **Audit Event (`inventory_events`):**
   - An immutable record is created with `event_type = 'consumed_partially'`, `quantity_delta = -200.00`, and `quantity_after = 300.00`.
3. **Database Transaction Guard:**
   - Both the batch modification and the event creation execute inside an atomic Spring `@Transactional` method. The equivalent SQL contract is:
   ```sql
   BEGIN;
   UPDATE inventory_batches 
   SET current_quantity = current_quantity - 200.00, updated_at = now()
   WHERE id = '...' AND current_quantity >= 200.00;
   
   INSERT INTO inventory_events (batch_id, kitchen_id, event_type, quantity_delta, quantity_after, performed_by_user_id)
   VALUES ('...', '...', 'consumed_partially', -200.00, 300.00, '...');
   COMMIT;
   ```
   If `current_quantity < 200.00`, the transaction fails, preventing negative stock.

---
*End of Database Schema Specification v1.1.3. Schema implementation uses Spring Data JPA entities (Milestone M2) and Flyway migration scripts (Milestone M1). Awaiting final Technical Lead M0 approval.*


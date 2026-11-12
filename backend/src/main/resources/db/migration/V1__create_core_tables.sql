-- FoodWise core schema (DATABASE_SCHEMA.md v1.1.3) — all 19 tables.
-- Lowercase enum strings are enforced by CHECK constraints; Java maps UPPERCASE enums via AttributeConverters.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 3.1 users -------------------------------------------------------------------
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_id    VARCHAR(128) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    first_name  VARCHAR(100),
    last_name   VARCHAR(100),
    image_url   TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_clerk_id UNIQUE (clerk_id),
    CONSTRAINT uq_users_email UNIQUE (email)
);
CREATE INDEX idx_users_clerk_id ON users (clerk_id);
CREATE INDEX idx_users_email ON users (email);

-- 3.2 kitchens ----------------------------------------------------------------
CREATE TABLE kitchens (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(100) NOT NULL,
    created_by_user_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_kitchens_created_by ON kitchens (created_by_user_id);

-- 3.3 kitchen_members ---------------------------------------------------------
CREATE TABLE kitchen_members (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kitchen_id  UUID NOT NULL REFERENCES kitchens (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL DEFAULT 'owner' CHECK (role IN ('owner', 'member')),
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_kitchen_members_kitchen_user UNIQUE (kitchen_id, user_id)
);
CREATE INDEX idx_kitchen_members_user ON kitchen_members (user_id);

-- 3.4 user_preferences --------------------------------------------------------
CREATE TABLE user_preferences (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    dietary_type                VARCHAR(30) NOT NULL DEFAULT 'omnivore'
                                CHECK (dietary_type IN ('vegetarian', 'vegan', 'eggetarian', 'jain', 'omnivore')),
    currency_code               VARCHAR(10) NOT NULL DEFAULT 'INR',
    currency_symbol             VARCHAR(5) NOT NULL DEFAULT '₹',
    notify_expiry_days_before   INTEGER NOT NULL DEFAULT 3 CHECK (notify_expiry_days_before > 0),
    notify_daily_digest_time    TIME NOT NULL DEFAULT '08:00:00',
    email_notifications_enabled BOOLEAN NOT NULL DEFAULT true,
    push_notifications_enabled  BOOLEAN NOT NULL DEFAULT true,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3.5 food_categories ---------------------------------------------------------
CREATE TABLE food_categories (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(50) NOT NULL UNIQUE,
    slug          VARCHAR(50) NOT NULL UNIQUE,
    description   TEXT,
    icon_name     VARCHAR(50) DEFAULT 'Package',
    color_hex     VARCHAR(10) DEFAULT '#668568',
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3.6 food_catalog ------------------------------------------------------------
CREATE TABLE food_catalog (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id                 UUID NOT NULL REFERENCES food_categories (id) ON DELETE RESTRICT,
    name_en                     VARCHAR(100) NOT NULL,
    name_hi                     VARCHAR(100),
    default_unit                VARCHAR(20) NOT NULL DEFAULT 'pcs'
                                CHECK (default_unit IN ('g', 'kg', 'ml', 'L', 'pcs', 'pkt', 'bunch')),
    default_location            VARCHAR(20) NOT NULL DEFAULT 'fridge'
                                CHECK (default_location IN ('fridge', 'freezer', 'pantry')),
    shelf_life_room_temp_days   INTEGER,
    shelf_life_fridge_days      INTEGER,
    shelf_life_freezer_days     INTEGER,
    is_perishable               BOOLEAN NOT NULL DEFAULT true,
    common_storage_tip          TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_food_catalog_name_en ON food_catalog (name_en);
CREATE INDEX idx_food_catalog_name_hi ON food_catalog (name_hi);
CREATE INDEX idx_food_catalog_category ON food_catalog (category_id);

-- 3.10 recipes (created before inventory_events, which references it) ---------
CREATE TABLE recipes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title               VARCHAR(150) NOT NULL,
    slug                VARCHAR(150) NOT NULL UNIQUE,
    description         TEXT,
    cuisine             VARCHAR(50) NOT NULL DEFAULT 'Indian',
    dietary_type        VARCHAR(30) NOT NULL DEFAULT 'vegetarian'
                        CHECK (dietary_type IN ('vegetarian', 'vegan', 'eggetarian', 'non-vegetarian')),
    prep_time_minutes   INTEGER NOT NULL DEFAULT 10 CHECK (prep_time_minutes >= 0),
    cook_time_minutes   INTEGER NOT NULL DEFAULT 20 CHECK (cook_time_minutes >= 0),
    servings            INTEGER NOT NULL DEFAULT 2 CHECK (servings > 0),
    difficulty          VARCHAR(20) NOT NULL DEFAULT 'easy' CHECK (difficulty IN ('easy', 'medium', 'hard')),
    instructions        JSONB NOT NULL DEFAULT '[]'::jsonb,
    image_url           TEXT,
    is_system_recipe    BOOLEAN NOT NULL DEFAULT true,
    created_by_user_id  UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_recipes_dietary ON recipes (dietary_type);
CREATE INDEX idx_recipes_slug ON recipes (slug);

-- 3.7 inventory_batches -------------------------------------------------------
CREATE TABLE inventory_batches (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kitchen_id        UUID NOT NULL REFERENCES kitchens (id) ON DELETE CASCADE,
    catalog_item_id   UUID REFERENCES food_catalog (id) ON DELETE SET NULL,
    custom_item_name  VARCHAR(100) NOT NULL,
    category_id       UUID REFERENCES food_categories (id) ON DELETE SET NULL,
    storage_location  VARCHAR(20) NOT NULL DEFAULT 'fridge' CHECK (storage_location IN ('fridge', 'freezer', 'pantry')),
    initial_quantity  NUMERIC(10, 2) NOT NULL CHECK (initial_quantity > 0),
    current_quantity  NUMERIC(10, 2) NOT NULL CHECK (current_quantity >= 0),
    unit              VARCHAR(20) NOT NULL CHECK (unit IN ('g', 'kg', 'ml', 'L', 'pcs', 'pkt', 'bunch')),
    purchase_date     DATE NOT NULL DEFAULT CURRENT_DATE,
    expiry_date       DATE NOT NULL,
    cost_inr          NUMERIC(10, 2) CHECK (cost_inr >= 0),
    is_leftover       BOOLEAN NOT NULL DEFAULT false,
    cooked_at         TIMESTAMPTZ,
    leftover_notes    TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'consumed', 'discarded', 'spoiled')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_current_lte_initial CHECK (current_quantity <= initial_quantity)
);
CREATE INDEX idx_inventory_kitchen_status_expiry ON inventory_batches (kitchen_id, status, expiry_date);
CREATE INDEX idx_inventory_kitchen_location ON inventory_batches (kitchen_id, storage_location);
CREATE INDEX idx_inventory_leftovers ON inventory_batches (kitchen_id, is_leftover, status);

-- 3.8 inventory_events --------------------------------------------------------
CREATE TABLE inventory_events (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id              UUID NOT NULL REFERENCES inventory_batches (id) ON DELETE CASCADE,
    kitchen_id            UUID NOT NULL REFERENCES kitchens (id) ON DELETE CASCADE,
    event_type            VARCHAR(30) NOT NULL CHECK (event_type IN ('created', 'consumed_partially', 'consumed_fully',
                              'discarded_spoiled', 'discarded_expired', 'quantity_adjusted', 'location_moved')),
    quantity_delta        NUMERIC(10, 2) NOT NULL,
    quantity_after        NUMERIC(10, 2) NOT NULL CHECK (quantity_after >= 0),
    reason                TEXT,
    recipe_id             UUID REFERENCES recipes (id) ON DELETE SET NULL,
    waste_log_id          UUID,
    performed_by_user_id  UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_inventory_events_batch_time ON inventory_events (batch_id, created_at DESC);
CREATE INDEX idx_inventory_events_kitchen_type ON inventory_events (kitchen_id, event_type);

-- 3.9 scan_sessions -----------------------------------------------------------
CREATE TABLE scan_sessions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kitchen_id             UUID NOT NULL REFERENCES kitchens (id) ON DELETE CASCADE,
    user_id                UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    image_url              TEXT NOT NULL,
    image_public_id        VARCHAR(255),
    scan_type              VARCHAR(30) NOT NULL CHECK (scan_type IN ('receipt', 'grocery_haul', 'fridge_shelf')),
    status                 VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'confirmed', 'rejected', 'failed')),
    raw_processing_output  JSONB,
    parsed_items           JSONB NOT NULL DEFAULT '[]'::jsonb,
    confirmed_items_count  INTEGER NOT NULL DEFAULT 0,
    overall_confidence     NUMERIC(4, 3) CHECK (overall_confidence BETWEEN 0.000 AND 1.000),
    error_message          TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_scan_sessions_kitchen_status ON scan_sessions (kitchen_id, status);

-- 3.11 recipe_ingredients -----------------------------------------------------
CREATE TABLE recipe_ingredients (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id         UUID NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    catalog_item_id   UUID REFERENCES food_catalog (id) ON DELETE SET NULL,
    ingredient_name   VARCHAR(100) NOT NULL,
    quantity          NUMERIC(10, 2) NOT NULL CHECK (quantity > 0),
    unit              VARCHAR(20) NOT NULL CHECK (unit IN ('g', 'kg', 'ml', 'L', 'pcs', 'pkt', 'bunch', 'tbsp', 'tsp', 'pinch')),
    is_optional       BOOLEAN NOT NULL DEFAULT false,
    is_pantry_staple  BOOLEAN NOT NULL DEFAULT false,
    substitute_notes  TEXT,
    display_order     INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_recipe_ingredients_recipe ON recipe_ingredients (recipe_id);
CREATE INDEX idx_recipe_ingredients_catalog ON recipe_ingredients (catalog_item_id);

-- 3.12 recipe_favorites -------------------------------------------------------
CREATE TABLE recipe_favorites (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recipe_id   UUID NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_recipe_favorites_user_recipe UNIQUE (user_id, recipe_id)
);

-- 3.13 meal_plans -------------------------------------------------------------
CREATE TABLE meal_plans (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kitchen_id         UUID NOT NULL REFERENCES kitchens (id) ON DELETE CASCADE,
    user_id            UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan_date          DATE NOT NULL,
    meal_slot          VARCHAR(20) NOT NULL CHECK (meal_slot IN ('breakfast', 'lunch', 'dinner', 'snack')),
    recipe_id          UUID REFERENCES recipes (id) ON DELETE SET NULL,
    custom_meal_title  VARCHAR(150),
    notes              TEXT,
    status             VARCHAR(20) NOT NULL DEFAULT 'planned' CHECK (status IN ('planned', 'cooked', 'skipped')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_meal_plan_slot UNIQUE (kitchen_id, plan_date, meal_slot)
);
CREATE INDEX idx_meal_plans_kitchen_date ON meal_plans (kitchen_id, plan_date);

-- 3.14 shopping_lists ---------------------------------------------------------
CREATE TABLE shopping_lists (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kitchen_id  UUID NOT NULL REFERENCES kitchens (id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL DEFAULT 'Current Shopping List',
    status      VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'archived')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_shopping_lists_kitchen_status ON shopping_lists (kitchen_id, status);

-- 3.15 shopping_list_items ----------------------------------------------------
CREATE TABLE shopping_list_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shopping_list_id    UUID NOT NULL REFERENCES shopping_lists (id) ON DELETE CASCADE,
    catalog_item_id     UUID REFERENCES food_catalog (id) ON DELETE SET NULL,
    item_name           VARCHAR(100) NOT NULL,
    category_id         UUID REFERENCES food_categories (id) ON DELETE SET NULL,
    quantity            NUMERIC(10, 2) NOT NULL DEFAULT 1.0 CHECK (quantity > 0),
    unit                VARCHAR(20) NOT NULL DEFAULT 'pcs' CHECK (unit IN ('g', 'kg', 'ml', 'L', 'pcs', 'pkt', 'bunch')),
    estimated_cost_inr  NUMERIC(10, 2) CHECK (estimated_cost_inr >= 0),
    is_checked          BOOLEAN NOT NULL DEFAULT false,
    checked_at          TIMESTAMPTZ,
    checked_by_user_id  UUID REFERENCES users (id) ON DELETE SET NULL,
    added_reason        VARCHAR(30) NOT NULL DEFAULT 'manual' CHECK (added_reason IN ('manual', 'auto_low_stock', 'recipe_missing')),
    linked_recipe_id    UUID REFERENCES recipes (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_shopping_items_list ON shopping_list_items (shopping_list_id, is_checked);

-- 3.16 grocery_purchases ------------------------------------------------------
CREATE TABLE grocery_purchases (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kitchen_id               UUID NOT NULL REFERENCES kitchens (id) ON DELETE CASCADE,
    user_id                  UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    purchase_date            DATE NOT NULL DEFAULT CURRENT_DATE,
    store_name               VARCHAR(100),
    total_amount_inr         NUMERIC(10, 2) NOT NULL CHECK (total_amount_inr >= 0),
    receipt_scan_session_id  UUID REFERENCES scan_sessions (id) ON DELETE SET NULL,
    receipt_image_url        TEXT,
    notes                    TEXT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_grocery_purchases_kitchen_date ON grocery_purchases (kitchen_id, purchase_date DESC);

-- 3.17 purchase_items ---------------------------------------------------------
CREATE TABLE purchase_items (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_id          UUID NOT NULL REFERENCES grocery_purchases (id) ON DELETE CASCADE,
    catalog_item_id      UUID REFERENCES food_catalog (id) ON DELETE SET NULL,
    item_name            VARCHAR(100) NOT NULL,
    category_id          UUID REFERENCES food_categories (id) ON DELETE SET NULL,
    quantity             NUMERIC(10, 2) NOT NULL CHECK (quantity > 0),
    unit                 VARCHAR(20) NOT NULL CHECK (unit IN ('g', 'kg', 'ml', 'L', 'pcs', 'pkt', 'bunch')),
    price_inr            NUMERIC(10, 2) NOT NULL CHECK (price_inr >= 0),
    associated_batch_id  UUID REFERENCES inventory_batches (id) ON DELETE SET NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_purchase_items_purchase ON purchase_items (purchase_id);

-- 3.18 food_waste_logs --------------------------------------------------------
CREATE TABLE food_waste_logs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kitchen_id          UUID NOT NULL REFERENCES kitchens (id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    batch_id            UUID REFERENCES inventory_batches (id) ON DELETE SET NULL,
    item_name           VARCHAR(100) NOT NULL,
    category_id         UUID REFERENCES food_categories (id) ON DELETE SET NULL,
    quantity_wasted     NUMERIC(10, 2) NOT NULL CHECK (quantity_wasted > 0),
    unit                VARCHAR(20) NOT NULL CHECK (unit IN ('g', 'kg', 'ml', 'L', 'pcs', 'pkt', 'bunch')),
    estimated_loss_inr  NUMERIC(10, 2) NOT NULL CHECK (estimated_loss_inr >= 0),
    waste_reason        VARCHAR(30) NOT NULL DEFAULT 'expired'
                        CHECK (waste_reason IN ('expired', 'spoiled', 'cooked_too_much', 'forgotten_leftover', 'bad_taste')),
    logged_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_food_waste_kitchen_time ON food_waste_logs (kitchen_id, logged_at DESC);
CREATE INDEX idx_food_waste_category ON food_waste_logs (category_id);

-- 3.19 notifications ----------------------------------------------------------
CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kitchen_id  UUID NOT NULL REFERENCES kitchens (id) ON DELETE CASCADE,
    type        VARCHAR(30) NOT NULL CHECK (type IN ('expiry_warning', 'item_expired', 'stock_low', 'leftover_alert', 'waste_summary')),
    title       VARCHAR(150) NOT NULL,
    message     TEXT NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'unread' CHECK (status IN ('unread', 'read', 'archived')),
    action_url  VARCHAR(255),
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at     TIMESTAMPTZ
);
CREATE INDEX idx_notifications_user_status ON notifications (user_id, status, created_at DESC);

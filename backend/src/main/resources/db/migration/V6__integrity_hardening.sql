-- Integrity guards that complement the documented schema (additive only).

-- Persist onboarding completion server-side so the wizard shows once per user, across devices.
ALTER TABLE users ADD COLUMN onboarding_completed_at TIMESTAMPTZ;

-- Exactly one active shopping list per kitchen, even under concurrent "get or create".
CREATE UNIQUE INDEX uq_shopping_lists_one_active ON shopping_lists (kitchen_id) WHERE status = 'active';

-- Scheduler idempotency: a notification carrying a dedupe key is created at most once per user.
-- Keys look like 'digest:2026-11-11' or 'expired:<batch-id>' (set by the application in metadata).
CREATE UNIQUE INDEX uq_notifications_dedupe ON notifications (user_id, (metadata ->> 'dedupe_key'))
    WHERE (metadata ->> 'dedupe_key') IS NOT NULL;

-- Fast catalog autosuggest on lower-cased names.
CREATE INDEX idx_food_catalog_name_en_lower ON food_catalog (lower(name_en));

-- Analytics lookups.
CREATE INDEX idx_inventory_events_kitchen_time ON inventory_events (kitchen_id, created_at DESC);
CREATE INDEX idx_meal_plans_recipe ON meal_plans (recipe_id);

-- Records whether expiry_date came from validated catalog guidance ("Estimated" badge) or from the user (FR-EXP-001).
ALTER TABLE inventory_batches ADD COLUMN expiry_is_estimated BOOLEAN NOT NULL DEFAULT false;

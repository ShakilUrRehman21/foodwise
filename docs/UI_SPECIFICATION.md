# FoodWise — UI & Interaction Specification

**Document Version:** 1.1.3  
**Status:** Pending Technical Lead Approval — Final M0 Documentation Consistency Review  
**Author:** FoodWise Product & Architecture Team  
**Reviewer / Technical Lead:** Technical Lead (ChatGPT)  
**Design Philosophy:** Modern Consumer Kitchen Companion (Warm, Editorial, Functional, Anti-Enterprise)  
**Changelog:** v1.1.3 — Replaced '< 24 hours' expiry descriptions with date-based wording (due today / SQL DATE precision); confirmed expiry boundary is expiry_date < CURRENT_DATE (items expiring today remain Expiring Today, not Expired); explicitly constrained Daily Expiry Digest time picker to 15-minute intervals matching backend scheduler precision; marked Milestone M0 as pending final Technical Lead approval.

---

## 1. Visual Identity & Design System Tokens

FoodWise embodies the warmth, freshness, and deliberate calm of a well-organized home kitchen. The visual aesthetic avoids generic corporate dashboard styling, high-contrast neon gradients, and synthetic glassmorphism. Instead, it relies on an earthy, organic palette, generous negative space, crisp typography, and tactile feedback.

### 1.1 Color Tokens

```css
:root {
  /* Brand Core */
  --primary: #668568;              /* Sage Green - Primary actions, active highlights */
  --primary-hover: #556F57;        /* Deeper Sage for hover states */
  --primary-light: #EEF3EE;        /* Tinted sage surface for active tabs and chips */

  /* Surfaces & Backgrounds */
  --background: #F8F7F2;           /* Warm Cream - Canvas background */
  --surface: #FFFFFF;              /* Pure White - Cards, dialogs, dropdowns */
  --surface-subtle: #F1EFEA;       /* Muted Warm Cream - Input backgrounds, table headers */
  --surface-elevated: #FFFFFF;     /* Elevated modals and floating sheets */

  /* Typography */
  --text-primary: #26372C;         /* Deep Forest Green - Headings and primary body copy */
  --text-secondary: #788278;       /* Muted Grey-Green - Captions, subtext, timestamps */
  --text-inverse: #FFFFFF;         /* White text on sage buttons */

  /* Borders & Dividers */
  --border-subtle: #E6E3DA;        /* Delicate dividing lines and card outlines */
  --border-focus: #668568;         /* Active form field ring */

  /* Semantic Expiry & Health Accents */
  --badge-fresh-bg: #EAF3EC;
  --badge-fresh-text: #2D6B3C;     /* Fresh (> 3 days remaining) */

  --badge-warning-bg: #FDF4E7;
  --badge-warning-text: #B47823;   /* Warm Amber - Use Soon (2-3 days) */
  --warning: #E9AC55;

  --badge-urgent-bg: #FCEDE8;
  --badge-urgent-text: #C04334;    /* Muted Red/Coral - Expiring Today (Due today / SQL DATE) */
  --danger: #D76F65;

  --badge-expired-bg: #F2F1ED;
  --badge-expired-text: #8C8A84;   /* Dim grey - Past date */
}
```

### 1.2 Typography Hierarchy

- **Font Family:** `Plus Jakarta Sans`, `-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif`
- **Headings & Display:**
  - `Display / Hero`: 36px (2.25rem) / Line-height 1.2 / Weight 700 / Letter-spacing -0.02em
  - `H1 (Page Title)`: 26px (1.625rem) / Line-height 1.25 / Weight 700 / Letter-spacing -0.015em
  - `H2 (Section Header)`: 20px (1.25rem) / Line-height 1.3 / Weight 600
  - `H3 (Card Title / Subhead)`: 16px (1.0rem) / Line-height 1.4 / Weight 600
- **Body & Controls:**
  - `Body Regular`: 14px (0.875rem) / Line-height 1.5 / Weight 400
  - `Body Medium`: 14px (0.875rem) / Line-height 1.5 / Weight 500
  - `Caption / Subtext`: 12px (0.75rem) / Line-height 1.4 / Weight 400
  - `Badge / Label / Overline`: 11px (0.6875rem) / Line-height 1.2 / Weight 600 / Letter-spacing +0.03em / Uppercase

### 1.3 Spacing & Layout Rhythm
- **Base Grid Unit:** 4px
- **Spacing Scale:** `xs: 4px`, `sm: 8px`, `md: 12px`, `base: 16px`, `lg: 24px`, `xl: 32px`, `2xl: 48px`, `3xl: 64px`
- **Corner Radii:**
  - `sm: 6px` (badges, tags, chips)
  - `md: 10px` (inputs, buttons, select menus)
  - `lg: 16px` (cards, dialogs, sheets)
  - `full: 9999px` (pills, avatar circles, floating action buttons)
- **Shadows (Soft Ambient Light):**
  - `shadow-subtle`: `0 1px 3px rgba(38, 55, 44, 0.05), 0 1px 2px rgba(38, 55, 44, 0.03)`
  - `shadow-card`: `0 4px 12px rgba(38, 55, 44, 0.04), 0 1px 3px rgba(38, 55, 44, 0.02)`
  - `shadow-elevated`: `0 12px 32px rgba(38, 55, 44, 0.08), 0 2px 6px rgba(38, 55, 44, 0.04)`

---

## 2. Reusable Core UI Components

### 2.1 Buttons (`<Button />`)
- **Primary:** Solid Sage (`#668568`), White text, subtle 1px border (`#556F57`). Hover: `#556F57`. Active scale: `0.98`. Focus ring: 2px offset Sage.
- **Secondary / Outline:** Background White (`#FFFFFF`), Border 1px (`#E6E3DA`), Text Deep Forest (`#26372C`). Hover: `#F1EFEA`.
- **Ghost:** Transparent background, Text Deep Forest. Hover: `#EEF3EE`.
- **Danger / Discard:** Soft red tint background (`#FCEDE8`), Text Muted Red (`#D76F65`). Hover: `#F8D8D3`.
- **Floating Action Button (FAB):** 56px circle, Sage green, white camera or plus icon, `shadow-elevated`, bottom-right fixed on mobile.

### 2.2 Form Inputs & Controls
- **Text Input / Select:** Height 44px (touch friendly), Background White (`#FFFFFF`), Border 1px (`#E6E3DA`), Radius 10px, Padding 12px 14px. Focus: Border `#668568` + subtle 2px glow.
- **Quantity Stepper:** Segmented pill with `[ - ] [ Value + Unit ] [ + ]`. Prevents accidental zeroing with a confirmation popup on drop to zero.
- **Location Selector Tabs:** Segmented pill control: `[ ❄️ Fridge ] [ 🧊 Freezer ] [ 🥫 Pantry ]`.

### 2.3 Status & Expiry Badges (`<StatusBadge />`)
- Visual pill with 6px dot indicator:
  - `Fresh`: Green dot + "5 days left" (`#EAF3EC` / `#2D6B3C`)
  - `Use Soon`: Amber dot + "2 days left" (`#FDF4E7` / `#B47823`)
  - `Expiring Today`: Red pulsating dot + "Expires Today" (`#FCEDE8` / `#C04334`)
  - `Expired`: Grey dot + "Expired 1d ago" (`#F2F1ED` / `#8C8A84`)

### 2.4 Modals & Mobile Drawers
- **Desktop:** Centered dialog (`max-w-lg`), 16px radius, backdrop blur (mild 2px opacity), click-outside dismiss.
- **Mobile (< 768px):** Bottom sheet drawer (`Drawer` primitive) with top drag pill, swipe-down to dismiss, full width, 20px top border radius.

### 2.5 Navigation Shell
- **Desktop (≥ 1024px):** Fixed left sidebar (width 260px) containing logo, kitchen selector, main navigation links (Dashboard, Inventory, Scan, Recipes, Leftovers, Shopping, Analytics, Settings), and user profile card.
- **Mobile (< 768px):** Fixed bottom navigation bar (height 64px) with 5 primary touch items:
  1. `Home` (Dashboard)
  2. `Inventory` (Kitchen batches)
  3. `Scan` (Elevated center Sage FAB)
  4. `Recipes` (What can I cook)
  5. `More` (Leftovers, Shopping, Analytics, Settings)

---

## 3. Screen Specifications (14 Required Screens)

---

### Screen 1: Landing Page (Public)

#### 1. Purpose & Target Audience
Welcome new visitors, articulate the FoodWise value proposition tailored to urban Indians, demonstrate the crisper drawer problem, and drive conversion to signup.

#### 2. Layout & Structure
- **Top Header:** Clean white bar with FoodWise leaf mark, navigation links (*Features*, *How It Works*, *Waste Calculator*, *Pricing/Free*), and *"Sign In"* + *"Get Started"* CTA.
- **Hero Section:** Asymmetric split layout (Text + CTA on left; interactive simulated Fridge Scanner preview on right).
- **Interactive Waste Calculator Widget:** Sliders for household size and weekly grocery spend in INR, showing annual projected waste savings (e.g. *"You could save ₹14,400/year"*).
- **Core Value Highlights:** 4 editorial feature blocks with natural imagery:
  1. Instant Receipt Scanning (OCR-Powered Bill Reader).
  2. Expiry Radar (Zero forgotten vegetables).
  3. Cook With What You Have (Zero grocery runs).
  4. Leftover Dabba Countdown.
- **Social Proof / Testimonials:** Cards featuring quotes from college flatmates, young tech workers, and couples in Bangalore/Pune.
- **Footer:** Minimal warm footer with copyright, links, and Indian kitchen food storage tips guide.

#### 3. Textual Wireframes

**Desktop View:**
```
+-----------------------------------------------------------------------------------+
| [Leaf] FoodWise        Features    How It Works    Calculator       [Sign In] [Start] |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  KNOW WHAT YOU HAVE.                       +-----------------------------------+  |
|  KNOW WHAT TO EAT.                         |  GROCERY SCAN PREVIEW             |  |
|  WASTE LESS.                               |  [Photo of Blinkit Receipt]       |  |
|                                            |  -------------------------------- |  |
|  The intelligent kitchen companion         |  * Coriander (1 bunch) - 4d left  |  |
|  built for busy Indian households.         |  * Paneer 200g - 2d left [URGENT] |  |
|  Track groceries, rescue veggies, and      |  * Amul Butter 500g - 60d left    |  |
|  cook delicious meals from your pantry.    |  [Added to Fridge in 6 seconds]   |  |
|                                            +-----------------------------------+  |
|  [ Start Free Today -> ]  (No CC required)                                        |
|                                                                                   |
+-----------------------------------------------------------------------------------+
|  [ INTERACTIVE RUPEE WASTE CALCULATOR ]                                           |
|  Weekly Grocery Spend: [ ₹1,500 ]  ====O======                                    |
|  Estimated Waste Discarded: 22%  ==>  YOU LOSE ₹17,160 / YEAR                     |
|  [ Start Saving Now ]                                                             |
+-----------------------------------------------------------------------------------+
```

**Mobile View:**
```
+-----------------------------------+
| [Leaf] FoodWise            [Menu] |
+-----------------------------------+
| Know what you have.               |
| Know what to eat.                 |
| Waste less.                       |
|                                   |
| [ Start Free Today -> ]           |
|                                   |
| +-------------------------------+ |
| | LIVE PREVIEW CARD             | |
| | Paneer (200g)                 | |
| | Status: Expiring in 2 days    | |
| | Suggested: Paneer Bhurji (15m)| |
| +-------------------------------+ |
|                                   |
| [Rupee Waste Calculator Slider]   |
| Weekly Spend: ₹1,500              |
| Rescuable: ₹1,400/month           |
|                                   |
| (Features)                        |
| 1. Snap bills in 3 seconds        |
| 2. Refrigerator countdown timer   |
| 3. Smart Indian recipes           |
+-----------------------------------+
```

#### 4. States & Behaviors
- **Loading State:** Instant static HTML SSR load; calculator interactive immediately without layout shift.
- **User Actions:** Clicking CTA opens Clerk signup modal or navigates to `/sign-up`.
- **Responsive:** Mobile collapses header to hamburger menu or direct "Sign In" button; hero cards stack vertically.

---

### Screen 2: Login and Registration

#### 1. Purpose & Flow
Frictionless onboarding using Clerk. Supports Google One-Tap, Email Magic Links, and Passwordless authentication.

#### 2. Layout & Structure
- **Desktop:** Centered minimal card on Warm Cream background. Left side features an inspiring culinary illustration with the copy: *"Your kitchen, in sync."* Right side embeds Clerk’s authentication component styled with FoodWise design tokens (Sage buttons, clean inputs).
- **Mobile:** Full-screen responsive card, top FoodWise logo, direct Google One-Tap button, divider *"or continue with email"*, single email input, and *"Continue"* button.

#### 3. States
- **Loading:** Button exhibits a smooth spinner while verifying authentication.
- **Error:** Clear inline message: *"We couldn't find an account with that email. Please check and try again."*

---

### Screen 3: Onboarding Wizard

#### 1. Purpose & Flow
A 3-step setup (under 60 seconds) establishing the user's initial kitchen baseline.

#### 2. Steps Breakdown
- **Step 1: Kitchen Profile**
  - Prompt: *"Tell us about your home"*
  - Options: `Living Alone (Bachelor/PG)` | `Flatmates / Rented Flat` | `Couple` | `Family Kitchen`
  - Kitchen Name Input: Default populated (e.g. *"Rohan's Kitchen"*).
- **Step 2: Dietary Preferences**
  - Options: `Vegetarian` | `Eggetarian` | `Non-Vegetarian` | `Jain` | `Vegan`
- **Step 3: Quick Pantry Starter**
  - Tap-to-select common Indian staples already in their kitchen to avoid starting with an empty screen:
  - Pills: `[+ Salt] [+ Turmeric / Haldi] [+ Mustard Oil] [+ Toor Dal] [+ Atta] [+ Rice] [+ Jeera] [+ Red Chilli Powder]`
  - Button: *"Finish Setup & Go to Kitchen"*

#### 3. Textual Wireframe (Mobile & Desktop)
```
+-------------------------------------------------------+
| FoodWise                                   Step 2 of 3 |
|                                                       |
| How do you eat at home?                               |
| Choose your dietary preference to tune recipes.       |
|                                                       |
| ( ) Vegetarian                                        |
|     Strictly veg, paneer, and plant-based foods       |
|                                                       |
| (*) Eggetarian                                        |
|     Vegetarian + Eggs (Omelettes, boiled eggs, etc.)  |
|                                                       |
| ( ) Non-Vegetarian                                    |
|     Chicken, mutton, seafood, and eggs                |
|                                                       |
| ( ) Jain                                              |
|     No root vegetables (onion, garlic, potatoes)      |
|                                                       |
| [ Back ]                           [ Continue -> ]    |
+-------------------------------------------------------+
```

---

### Screen 4: Main Dashboard (Kitchen Pulse)

#### 1. Purpose
The primary home screen providing an at-a-glance operational view of the refrigerator and pantry. Answers: *"What is expiring soon?", "What can I cook right now?", "What is running low?"*.

#### 2. Content Hierarchy
1. **Top Bar:** Greeting (*"Good morning, Rohan"*), Kitchen Selector (*"HSR Flat"*), Notification Bell with red counter badge, Profile Avatar.
2. **Urgent Expiry Radar (High Priority Banner):** Highlights items needing immediate action today (e.g., *"2 items require attention today"*).
3. **Quick Action Shortcuts:** `[ 📷 Scan Groceries ]` `[ ➕ Quick Add ]` `[ 🍲 What to Cook ]`.
4. **Kitchen Health Metrics:**
   - Active Items Count (e.g. `28 items in kitchen`)
   - Expiring Soon (`3 items < 48 hrs`)
   - Leftovers Stored (`2 containers`)
5. **Urgent Perishables Carousel:** Horizontal swipeable cards for items expiring within 3 days with direct action buttons (`Cook`, `Freeze`, `Discard`).
6. **Suggested Dinner Tonight:** 1 hero recipe card matching available pantry items with `100% Match` badge.

#### 3. Textual Wireframe (Desktop)
```
+-----------------------------------------------------------------------------------+
| [Leaf] FoodWise  Dashboard  Inventory  Scan  Recipes  Leftovers  Shopping   (🔔 2) [R] |
+-----------------------------------------------------------------------------------+
| Good evening, Rohan! 🌿                                [ + Quick Add ] [ 📷 Scan ] |
|                                                                                   |
| +-- [ URGENT EXPIRY RADAR ] ----------------------------------------------------+ |
| | ⚠️ 2 items expire today and need to be used to prevent ₹140 waste.              | |
| | [ View Expiring Items ]                                                       | |
| +-------------------------------------------------------------------------------+ |
|                                                                                   |
|  EXPIRING SOON                                                  [See all 4 items] |
|  +---------------------+  +---------------------+  +----------------------------+ |
|  | Paneer (Mother D.)  |  | Coriander (Dhaniya) |  | Brown Bread                | |
|  | 200g * Fridge       |  | 1 Bunch * Fridge    |  | 6 slices * Pantry          | |
|  | [🔴 Expires Today]  |  | [🟠 1 day left]     |  | [🟠 2 days left]           | |
|  | [Cook]   [Freeze]   |  | [Cook]   [Discard]  |  | [Cook]   [Discard]         | |
|  +---------------------+  +---------------------+  +----------------------------+ |
|                                                                                   |
|  TONIGHT'S RECIPE SUGGESTION                          KITCHEN HEALTH              |
|  +--------------------------------------------------+ +-------------------------+|
|  | [Image: Palak Paneer]                            | | Total Items:   28       ||
|  | Homestyle Palak Paneer                           | | Refrigerator:  14       ||
|  | 🟢 100% Ingredients Available (You have all 6)   | | Pantry:        12       ||
|  | Time: 25 mins * Easy                             | | Freezer:        2       ||
|  | Uses: Paneer, Spinach, Tomatoes from fridge      | | Leftovers:      2 dabbas||
|  | [ Start Cooking Now ]                            | | [ View Inventory -> ]   ||
|  +--------------------------------------------------+ +-------------------------+|
+-----------------------------------------------------------------------------------+
```

#### 4. Textual Wireframe (Mobile)
```
+-----------------------------------+
| [Rohan's Kitchen v]        (🔔 2) |
+-----------------------------------+
| Good evening, Rohan!              |
|                                   |
| +-------------------------------+ |
| | ⚠️ 2 items expire today!      | |
| +-------------------------------+ |
|                                   |
| [ 📷 Scan Bill ]   [ ➕ Add Food ] |
|                                   |
| EXPIRING SOON                     |
| <- [Paneer 200g] [Coriander] ->   |
|    Expires Today  1d left         |
|    [Cook Now]     [Cook Now]      |
|                                   |
| TONIGHT'S DINNER                  |
| +-------------------------------+ |
| | Palak Paneer (25m)            | |
| | 🟢 100% Match (All items in)  | |
| | [ Cook This Recipe ]          | |
| +-------------------------------+ |
|                                   |
| [Home] [Inventory] [📷] [Recipes] [More]
+-----------------------------------+
```

#### 5. States & Behaviors
- **Loading:** Shimmer skeleton placeholders for the carousel and recipe cards.
- **Empty State (New Kitchen):** Warm illustration of an empty kitchen with the title *"Your fridge is a blank slate"*, accompanied by two primary buttons: `[ Scan First Grocery Receipt ]` or `[ Add Common Staples ]`.
- **Error:** Subtle banner with retry button: *"Could not sync latest items. Tap to retry."*

---

### Screen 5: Kitchen Inventory

#### 1. Purpose
Comprehensive management of all active items and batches across the household.

#### 2. Layout & Structure
- **Storage Location Tabs:** `All (28)` | `Fridge (14)` | `Freezer (2)` | `Pantry (12)`.
- **Search & Filter Bar:** Instant fuzzy search input (*"Search paneer, dal, milk..."*), Category filter dropdown, Sort by: `Expiry Date (Soonest first)`, `Recently Added`, `Name (A-Z)`.
- **Inventory Batch Cards Grid (List on mobile):**
  - Item name + vernacular label (e.g. *Coriander / Dhaniya*).
  - Storage location chip (`Fridge`).
  - Remaining quantity and unit with stepper controls (`[ - ] 350g [ + ]`).
  - Expiry status badge (`Fresh`, `Use Soon`, etc.).
  - Action overflow menu (`Move to Freezer` — recalculates expiry only when validated freezer guidance exists, otherwise preserves user date; `Mark Consumed`; `Log Waste`; `Edit Details`).
- **Batch Multi-Select Mode:** Allows bulk actions (e.g., selecting 3 items and clicking `Move to Freezer` or `Mark as Consumed`).

#### 3. Mobile Behavior
- Full-width card rows with left-swipe action to **Quick Consume** (green checkmark) and right-swipe to **Discard / Waste** (red trash can).

---

### Screen 6: Add Food Form (Manual Entry)

#### 1. Purpose
Fast, single-item manual entry when the user buys 1–2 items from a local vendor or vegetable cart (*thela*).

#### 2. Form Fields & Interactions
1. **Item Name (Autosuggest Combobox):** Typing *"To"* suggests *"Toor Dal"*, *"Tomato"*, *"Tofu"*. Selecting an item auto-fills default category and, only if validated guidance exists, suggests an estimated shelf life. Unvalidated shelf-life values remain `NULL`, and selecting an item must not automatically populate an unvalidated expiry date.
2. **Category Selector:** Grid of category icons (`Vegetables`, `Dairy`, `Pulses`, etc.).
3. **Storage Location:** 3-way toggle button (`Fridge` | `Pantry` | `Freezer`). Changing storage location dynamically recalculates an expiry estimate only when validated guidance exists for the new location. When no validated guidance exists for the new location, existing user-entered dates are preserved and the field prompts the user for manual entry without inventing dates.
4. **Quantity & Unit:** Stepper input with unit selector (`g`, `kg`, `ml`, `L`, `pcs`, `pkt`).
5. **Purchase Date & Expiry Date:** Datepicker defaulting purchase date to `Today`. If validated catalog guidance exists, expiry date is populated as `Today + Validated Shelf Life` and visibly flagged with an `"Estimated"` badge. If catalog shelf-life is `NULL`, the date field remains blank with a required manual datepicker prompt (`"Select expiry date *"`). The UI clearly distinguishes catalog-derived estimates from user-entered dates; the system never invents or silently assumes an expiry date.
6. **Price in ₹ (Optional):** Input field for spending tracking.
7. **Action Buttons:** `[ Cancel ]` and `[ Save & Add Another ]` / `[ Save Item ]`.

---

### Screen 7: Grocery Scan Confirmation (Human-in-the-Loop Review)

#### 1. Purpose
Review and refine items extracted by the **local OCR pipeline (Tesseract)** for receipt scans, or by the **local computer vision model (ONNX/YOLO)** for countertop grocery haul photos, before writing anything to the database. **This is the critical trust-building screen. No item is ever written to inventory without explicit user approval.**

The two scan modes surface different confidence signals:
- **Receipt scan (OCR mode):** Confidence reflects the fuzzy catalog-match score between the OCR-extracted text token and a `food_catalog` entry (JaroWinkler similarity, 0.0–1.0).
- **Grocery haul scan (computer vision mode):** Confidence reflects the YOLO object detection score for each detected bounding box (0.0–1.0). The entire review table is prefixed with a banner: *"Experimental — Computer vision recognition. Please review every item carefully."*

#### 2. Layout & Structure
- **Top Summary:**
  - Image thumbnail preview.
  - For receipt scans: detected store name (e.g. *"Blinkit - Order #9182"*), detected date, total recognized amount (*"₹642.00"*) — extracted from OCR text. Shown as *"Not detected"* if OCR could not parse these fields.
  - For grocery haul scans: *"Experimental Computer Vision Scan"* label replacing store/date fields.
- **Parsed Items Review Table:**
  - Column 1: Checkbox (Select to include/exclude each item).
  - Column 2: Item Name (Editable text input; pre-filled from OCR token or YOLO detection label matched against catalog).
  - Column 3: Quantity & Unit (Editable stepper; pre-filled from OCR parsing or left at default `1 pcs` for haul scans).
  - Column 4: Storage Location (Dropdown: Fridge, Pantry, Freezer; pre-filled from catalog default).
  - Column 5: Expiry Date (Editable date chip; pre-filled as `today + validated catalog shelf life` with an `"Estimated"` badge ONLY when approved guidance exists. If catalog shelf-life is `NULL` / unvalidated, the chip requires manual user date entry showing `[Set Date *]`, preventing any invented or assumed dates).
  - Column 6: Cost in ₹ (Editable; pre-filled from OCR price extraction; blank for haul scans).
  - **Confidence Indicator:** Rows where recognition confidence is below 0.75 are highlighted with an amber left border and a `⚠️` badge showing the score (e.g., `64%`). These rows require user attention before confirmation is permitted.
- **Bottom Fixed Action Bar:**
  - Summary count: *"9 items selected for import (₹642.00)"*
  - `[ Discard Scan ]` (Secondary — cancels session, no inventory changes)
  - `[ Confirm & Add to Inventory ]` (Primary Sage Button — disabled until all amber-flagged rows are reviewed and all items without validated catalog guidance have user-provided expiry dates)

#### 3. Textual Wireframe (Desktop)
```
+-----------------------------------------------------------------------------------+
| GROCERY SCAN REVIEW                                            [ X Cancel Scan ]  |
+-----------------------------------------------------------------------------------+
| Source: Blinkit Receipt (OCR) | Total: ₹642.00 detected | 9 items recognized     |
|                                                                                   |
| [✓] ITEM NAME          QTY    UNIT    LOCATION   EXPIRY DATE     PRICE     CONF   |
| --------------------------------------------------------------------------------- |
| [✓] Amul Taaza Milk     2     pkt     [Fridge v] 03 Nov (2d)[Est] ₹64.00    98%   |
| [✓] Paneer Fresh       200    g       [Fridge v] [Set Date *]     ₹95.00    95%   |
| [✓] Coriander Bunch     1     bunch   [Fridge v] 05 Nov (4d)[Est] ₹15.00    88%   |
| [✓] Toor Dal (Tata)     1     kg      [Pantry v] [Set Date *]     ₹175.00   99%   |
| [✓] Tomatoes (Hybrid)  500    g       [Fridge v] 08 Nov (7d)[Est] ₹38.00    91%   |
|▌[?] White Bread (?)     1     pkt     [Pantry v] [Set Date *]     ₹45.00    64% ⚠️|
|     ↑ Low confidence — OCR token did not clearly match catalog. Please verify.    |
|                                                                                   |
| [ + Add Missing Item Manually ]                                                   |
+-----------------------------------------------------------------------------------+
| 8 items selected · 3 items need expiry date   [ Discard ]  [ Confirm & Import (8) ]|
+-----------------------------------------------------------------------------------+
```
*Note on wireframe: Items with approved catalog defaults display estimated duration with `[Est]`. Items where catalog shelf-life is NULL require explicit user date selection (`[Set Date *]`) before `[ Confirm & Import ]` is enabled, ensuring no expiry dates are invented.*

---

### Screen 8: Expiry Management (Urgent Actions Queue)

#### 1. Purpose
Dedicated triage workstation for items approaching or exceeding shelf-life thresholds.

#### 2. Layout & Views
- **Timeline / Kanban Grouping:**
  1. `Expiring Today (2)`: Red urgent badge (derived from `expiry_date = CURRENT_DATE`).
  2. `Next 2-3 Days (4)`: Amber warning badge.
  3. `This Week (6)`: Green calm badge.
  4. `Expired (1)`: Grey overdue badge (derived from `expiry_date < CURRENT_DATE`; items expiring today remain `Expiring Today`, not `Expired`; batch status remains `'active'` until user action).
- **Action Triggers on Each Card:**
  - `[ 🍲 Cook Tonight ]`: Immediately opens Recipe Matcher filtered for this specific ingredient.
  - `[ 🧊 Freeze ]`: Moves storage location to Freezer (calls `PATCH /location`). Recalculates the expiry date only when approved, validated freezer-storage guidance exists for that item; otherwise preserves the existing user-entered expiry date (or prompts for manual date entry if unset), never automatically extending shelf life using unvalidated guidance.
  - `[ 🗑️ Discard & Log Waste ]`: Opens 1-tap waste logging modal to record reason (e.g. *spoiled*) and log INR loss (transitions batch status to `'discarded'`).

---

### Screen 9: Recipe Discovery & Pantry Matcher

#### 1. Purpose
Solves *"What can I cook with what I have?"* by sorting dishes by ingredient availability and expiring items.

#### 2. Layout & Filters
- **Smart Filter Chips:** `All Recipes` | `🟢 100% Cookable Now` | `⚡ 15-Minute Express` | `🥬 Vegetarian Only` | `🍲 Uses Leftovers`.
- **Sort Priority:** Default sorts by `Expiring Perishables First` (recipes utilizing items marked `Use Soon` or `Expiring Today`).
- **Recipe Cards Grid:**
  - Dish photograph.
  - Cuisine badge (*North Indian, South Indian, Quick Breakfast*).
  - Cooking time and difficulty (*"20 min • Easy"*).
  - **Availability Badge:**
    - `🟢 100% Match`: *"You have all 5 ingredients"*
    - `🟡 80% Match`: *"Missing 1 item: Ginger-garlic paste"* -> `[+ Add to List]`
  - Used perishable tags: Tagged with orange dot if using expiring items.

---

### Screen 10: Recipe Details & Cooking Mode

#### 1. Purpose
Step-by-step cooking guide with pantry verification and automatic inventory deduction.

#### 2. Layout & Structure
- **Header:** Hero image of dish, title, total time, servings stepper (`[ - ] 2 [ + ]`), dietary tag.
- **Ingredients Checklist:**
  - Available ingredients marked with green checkmark (`✔ 200g Paneer (In fridge)`).
  - Missing ingredients marked with red warning (`+ 1 tbsp Kasuri Methi (Missing)`) with button `[+ Add to Shopping List]`.
- **Step-by-Step Instructions:** Clean ordered steps with integrated countdown timers (e.g. *"Simmer on medium flame for 8 minutes [Start 8m Timer]"*).
- **Completion Trigger:** Prominent button at the bottom: `[ 🍳 I Cooked This! ]`.
  - Clicking displays a confirmation modal: *"Deduct ingredients from inventory? (200g Paneer, 2 Tomatoes, 1 Onion)"*.
  - User confirms -> batches are automatically updated in the background.

---

### Screen 11: Leftover Management

#### 1. Purpose
Track prepared foods (*dabbas*, cooked curries, boiled rice) with conservative storage guidance countdowns to prevent spoiled leftovers and enable rule-based culinary repurposing.

#### 2. Layout & Features
- **Active Leftovers Grid:**
  - Dish name (e.g. *Dal Tadka*, *Jeera Rice*, *Aloo Gobi*).
  - Time prepared (e.g. *"Yesterday, 9:00 PM"*, recorded as `cooked_at` TIMESTAMPTZ).
  - Visual consumption window badge showing date-level remaining guidance (e.g., *"Expires today"*, *"1 day remaining"*, *"Expires 04 Nov"*), aligned with the SQL `DATE` precision of `inventory_batches.expiry_date`; never promising unsupported hour-level countdown precision. Advisory examples (such as cooked rice ~2 days, dal ~3 days, dairy curry ~2 days) serve as illustrative examples and must not be treated as hardcoded production safety defaults until validated.
  - Storage container identifier (*"Glass square dabba, top shelf"*).
- **Repurposing Assistant Drawer:**
  - Tapping *"Repurpose"* on Dal Tadka suggests rule-based culinary transformations:
    - *Idea 1: Dal Paratha (Mix leftover dal into atta with ajwain).*
    - *Idea 2: Sambar Twist (Add drumsticks and tamarind pulp).*
- **Quick Action:** `[ Consumed for Lunch ]` or `[ Discard ]`.

---

### Screen 12: Smart Grocery Shopping List

#### 1. Purpose
Consolidated shopping list grouped by store aisle, auto-replenished from depleted inventory and missing recipe ingredients.

#### 2. Layout & Structure
- **Top Bar:** Quick add text input (*"Add milk, bread, bananas..."*) + category tagger.
- **Category-Grouped Checklist:**
  - `Vegetables & Fruits (3)`
  - `Dairy & Eggs (2)`
  - `Pantry & Spices (4)`
- **Item Row:**
  - Checkbox to cross off item while walking aisles.
  - Item name + quantity.
  - Origin badge (`From Recipe: Palak Paneer` or `Auto-Replenish: Low Salt`).
  - Estimated price in ₹.
- **In-Store Shopping Mode:** Fullscreen high-contrast toggle maximizing text size and contrast for one-handed supermarket navigation.
- **Transfer to Inventory Prompt:** Checking all items surfaces a banner: *"Done shopping? Import 5 checked items into your Kitchen Inventory with 1 tap."*

---

### Screen 13: Spending & Food Waste Analytics

#### 1. Purpose
Monthly financial transparency showing rupee expenditure, quantified waste, and savings impact.

#### 2. Content & Visuals
1. **Summary KPI Cards:**
   - **Total Grocery Spend:** `₹6,420` (Current month)
   - **Rupee Waste Loss:** `₹380` (Down 35% from last month 🟢)
   - **Rescue Savings:** `₹1,250` (Value of groceries saved before expiry)
2. **Category Waste Donut Chart:**
   - Breakdown of waste by food group (e.g. Vegetables: 55%, Dairy: 25%, Bakery: 15%, Staples: 5%).
3. **Top Wasted Items Leaderboard:**
   - List showing repeat offenders (e.g. *Coriander: Wasted 3 times, ₹45 loss; Bread: Wasted 2 times, ₹90 loss*).
4. **Behavioral Tip of the Month:**
   - *"Tip: Storing coriander in an airtight container with a paper towel extends freshness by 6 days."*

---

### Screen 14: User & Kitchen Settings

#### 1. Purpose
Manage kitchen profile, member list, notification schedules, and export options.

#### 2. Settings Sections
- **Kitchen Profile:** Rename kitchen, view existing kitchen members (shared membership via database records). *Note: Shareable flatmate invitation links / tokenized invites are future functionality outside the MVP scope and are not supported in the MVP.*
- **Dietary & Preferences:** Update diet profile (`Vegetarian`, `Jain`, etc.), default currency (`INR ₹`).
- **Notification Preferences:**
  - Daily Expiry Digest time picker constrained to 15-minute intervals (e.g., `08:00`, `08:15`, `08:30`, `08:45`) matching backend scheduler precision (stored per-user in `user_preferences.notify_daily_digest_time`, default: `08:00 AM`).
  - Notification Channels: In-App (always on), Browser Push (toggle), Email Digest (toggle).
- **Data Management:** `[ Export Inventory as CSV ]` and `[ Reset Kitchen Data ]`.

---

## 4. Textual Wireframes: Summary Architecture

```
DESKTOP SHELL (>= 1024px)
+-----------------------------------------------------------------------------------+
| [Leaf] FoodWise  |  Header: [Kitchen Selector v]           (🔔 3) [User Profile] |
| ---------------- | -------------------------------------------------------------- |
| 📊 Dashboard     |                                                                |
| 🥫 Inventory     |  [ SCREEN CONTENT AREA ]                                       |
| 📷 Scan Groceries|  (Renders selected page: Dashboard, Inventory, Recipes, etc.)  |
| 🍲 Recipes       |                                                                |
| 🥘 Leftovers     |                                                                |
| 🛒 Shopping      |                                                                |
| 📈 Analytics     |                                                                |
| ⚙️ Settings      |                                                                |
+-----------------------------------------------------------------------------------+

MOBILE SHELL (< 768px)
+-----------------------------------+
| FoodWise [Kitchen v]        (🔔)  |
+-----------------------------------+
|                                   |
| [ MOBILE SCREEN CONTENT AREA ]    |
| (Scrollable full-width content)   |
|                                   |
+-----------------------------------+
| [🏠 Home] [🥫 Inv] [📷] [🍲 Rec] [•••]|
+-----------------------------------+
```

---
*End of UI & Interaction Specification v1.1.3. Screen layouts, design tokens, and interaction patterns preserved. Freeze action, leftover date precision, per-user digest preferences in 15-minute intervals, and date-based expiry wording aligned for Technical Lead M0 approval.*


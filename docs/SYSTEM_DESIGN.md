# FoodWise — System Design & Architecture Specification

**Document Version:** 1.1.4  
**Status:** Pending Technical Lead Approval — Final M0 Documentation Consistency Review  
**Author:** FoodWise Product & Architecture Team  
**Reviewer / Technical Lead:** Technical Lead (ChatGPT)  
**Architecture Paradigm:** Next.js Frontend + Java 21 / Spring Boot Modular Monolith Backend  
**Changelog:** v1.1.4 — Standardized expiry boundary strictly to expiry_date < CURRENT_DATE across derived queries (items expiring today remain Expiring Today, not Expired); replaced remaining fixed 08:00 IST scheduler references with periodic per-user digest scheduler; documented 15-minute time picker alignment and due-time window deduplication; marked Milestone M0 as pending final Technical Lead approval.

---

## 1. High-Level Architecture Overview

FoodWise is a **decoupled two-tier application**:

- **Frontend:** Next.js 15+ (React, TypeScript, Tailwind CSS, shadcn/ui) — purely a presentation and UI layer. **No business logic, database access, or scheduled tasks live in Next.js.**
- **Backend:** Java 21 + Spring Boot 3.x — owns all business logic, database operations, authentication/authorization enforcement, scheduled jobs, and ML/OCR orchestration.
- **Communication:** All frontend-to-backend communication is via REST APIs (JSON over HTTPS). No Next.js Server Actions, Route Handlers, or Next.js API routes are used as a substitute backend.

### 1.1 Architecture Diagram

```
+-----------------------------------------------------------------------------------+
|                               CLIENT DEVICES                                      |
|   Mobile Browser (PWA-Ready)                Desktop / Tablet Browser             |
+-----------------------------------------------------------------------------------+
                                        |
                                        | HTTPS (TLS 1.3)
                                        v
+-----------------------------------------------------------------------------------+
|                     NEXT.JS FRONTEND TIER (Vercel / CDN)                          |
|                                                                                   |
|  [Presentation Layer — ONLY]                                                      |
|   - React components (shadcn/ui + Tailwind CSS + Framer Motion)                   |
|   - Client-side form state (react-hook-form + Zod client schemas)                 |
|   - Clerk <ClerkProvider> session management                                      |
|   - Calls Spring Boot REST API via fetch/Axios with Bearer JWT                    |
|   - No Next.js Server Actions; No Next.js Route Handlers as backend               |
|                                                                                   |
|  Directory layout:                                                                |
|   foodwise/frontend/src/                                                          |
|     app/            (Next.js App Router — UI pages only)                          |
|     components/     (UI primitives + feature components)                          |
|     features/       (Feature-level view logic)                                    |
|     services/       (Typed API client wrappers calling Spring Boot)               |
|     hooks/          (Custom React hooks for SWR/TanStack data fetching)            |
|     lib/            (Config, constants, formatters)                               |
+-----------------------------------------------------------------------------------+
                                        |
                                        | REST API (HTTPS / JSON)
                                        | Authorization: Bearer <Clerk JWT>
                                        v
+-----------------------------------------------------------------------------------+
|                   SPRING BOOT APPLICATION TIER (Java 21)                          |
|                                                                                   |
|  [Security Layer]                                                                 |
|   - Spring Security: Clerk JWT verification (JWKS endpoint)                      |
|   - Kitchen-level tenancy authorization guard                                     |
|                                                                                   |
|  [Presentation / API Layer — Controllers]                                         |
|   - @RestController beans per domain module                                       |
|   - Input validation: Jakarta Bean Validation @Valid                              |
|   - Standard ApiResponse<T> envelope                                              |
|   - Springdoc OpenAPI documentation                                               |
|                                                                                   |
|  [Application / Service Layer]                                                    |
|   +--------------------+  +-----------------+  +-------------------------------+  |
|   | InventoryService   |  | RecipeService   |  | ScanService (OCR + CV)        |  |
|   +--------------------+  +-----------------+  +-------------------------------+  |
|   | ExpiryService      |  | LeftoverService |  | AnalyticsService              |  |
|   +--------------------+  +-----------------+  +-------------------------------+  |
|   | ShoppingService    |  | NotificationSvc |  | CatalogService                |  |
|   +--------------------+  +-----------------+  +-------------------------------+  |
|                                                                                   |
|  [Data Access Layer — Repositories]                                               |
|   - Spring Data JPA + Hibernate                                                   |
|   - @Transactional on all mutating service methods                                |
|   - JPQL / Criteria API for complex queries                                       |
|                                                                                   |
|  [Scheduled Jobs — Spring @Scheduled]                                             |
|   - Periodic expiry digest job (per-user scheduled digest time)                   |
|                                                                                   |
|  [ML / OCR Layer]                                                                 |
|   - Tesseract 5 via Tess4J (Receipt OCR)                                          |
|   - YOLO inference via ONNX Runtime for Java (Haul image recognition)            |
|   - Catalog fuzzy matching (Apache Commons Text)                                  |
+-----------------------------------------------------------------------------------+
            |                                               |
            | JPA / JDBC                                    | File I/O
            v                                               v
+-------------------------+                   +-----------------------------+
|    POSTGRESQL 16        |                   |   IMAGE & MODEL STORAGE     |
|  (Primary datastore)    |                   |  - Uploaded receipt images  |
|  - Flyway migrations    |                   |  - Grocery haul images      |
|  - ACID transactions    |                   |  - ONNX model file(s)       |
|  - Indexed queries      |                   |  (Local server storage or   |
+-------------------------+                   |   Cloudinary CDN)           |
            ^                                 +-----------------------------+
            | Webhook user.created
+-------------------------+
|       CLERK AUTH        |
|  - Google OAuth         |
|  - Passwordless magic   |
|  - JWKS JWT signing     |
+-------------------------+
```

---

## 2. Frontend Architecture (Next.js — Presentation Only)

### 2.1 Strict Responsibilities
The Next.js frontend is a **pure UI layer**. Its only runtime responsibilities are:

1. Render pages and components using React.
2. Call Spring Boot REST API endpoints using fetch or Axios with the Clerk JWT in the `Authorization: Bearer` header.
3. Cache and mutate server state using TanStack Query (React Query) or SWR.
4. Handle client-side form state and validation via `react-hook-form` + Zod (client-only schemas for UX — not a substitute for backend validation).
5. Display Clerk authentication UI and pass session tokens to the API client.

**Explicitly NOT responsible for:**
- Database queries of any kind.
- Business rule enforcement.
- Scheduled or background tasks.
- ML inference or OCR processing.

### 2.2 Directory Structure
```
foodwise/frontend/src/
├── app/                          # Next.js App Router — pages and layouts only
│   ├── (auth)/                   # Sign-in, sign-up (Clerk UI)
│   ├── (dashboard)/              # Authenticated shell
│   │   ├── layout.tsx            # App shell: Sidebar (desktop) + Bottom Nav (mobile)
│   │   ├── page.tsx              # Dashboard — calls GET /api/v1/dashboard/pulse
│   │   ├── inventory/            # Inventory pages
│   │   ├── scan/                 # Upload + review UI (calls POST /api/v1/scans)
│   │   ├── expiry/               # Expiry triage screen
│   │   ├── recipes/              # Recipe discovery + detail
│   │   ├── leftovers/            # Leftover tracking
│   │   ├── shopping/             # Shopping list
│   │   ├── analytics/            # Analytics dashboard
│   │   └── settings/             # User & kitchen settings
│   └── page.tsx                  # Public landing page (static SSG)
├── components/
│   ├── ui/                       # shadcn/ui base primitives
│   ├── shared/                   # StatusBadge, EmptyState, PageHeader
│   └── features/                 # Feature-specific components
│       ├── inventory/
│       ├── scan/
│       ├── recipes/
│       └── leftovers/
├── services/                     # Typed API client wrappers
│   ├── api-client.ts             # Base axios instance with auth interceptor
│   ├── inventory.service.ts      # Calls /api/v1/inventory/**
│   ├── scan.service.ts           # Calls /api/v1/scans/**
│   ├── recipe.service.ts         # Calls /api/v1/recipes/**
│   └── ...
├── hooks/                        # Custom React hooks wrapping TanStack Query
│   ├── use-inventory.ts
│   ├── use-expiring-items.ts
│   └── ...
└── lib/
    ├── clerk.ts                  # Clerk client config
    ├── constants.ts
    └── formatters.ts             # INR currency, date display
```

### 2.3 API Client & Authentication Flow
Protected API requests carry a Clerk JWT as a Bearer token. Public endpoints (`/api/v1/health` and `/api/webhooks/clerk`) do not require user authentication, while all `/api/v1/**` protected routes mandate JWT verification. Frontend route protection is solely for user navigation and is not a substitute for backend authentication:

```typescript
// services/api-client.ts
const apiClient = axios.create({ baseURL: process.env.NEXT_PUBLIC_API_URL });

apiClient.interceptors.request.use(async (config) => {
  const token = await getAuth().getToken(); // Clerk SDK
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
```

### 2.4 State Management & Data Flow
1. **Server State:** TanStack Query (`useQuery`, `useMutation`) manages REST API data fetching, caching, revalidation, and optimistic updates.
2. **Client Transient State:** Zustand or React `useState` for temporary, non-persisted UI state (file upload staging, scan review draft before POST confirmation).
3. **Form State:** `react-hook-form` with Zod for client-side UX validation. The backend performs authoritative validation independently.

---

## 3. Backend Architecture (Spring Boot — All Business Logic)

### 3.1 Repository Structure
```
foodwise/backend/src/main/java/com/foodwise/
├── config/             # Spring beans, CORS, security config, OpenAPI config
├── security/           # JWT filter, Clerk JWKS verifier, KitchenAuthGuard
├── common/             # ApiResponse wrapper, exception hierarchy, base entities
├── user/               # UserEntity, UserRepository, UserService, UserController
├── kitchen/            # KitchenEntity, KitchenMemberEntity, KitchenService, KitchenController
├── inventory/          # InventoryBatchEntity, InventoryEventEntity, InventoryService, InventoryController
├── expiry/             # ExpiryClassificationService, ExpiryScheduler, ExpiryController
├── recipe/             # RecipeEntity, RecipeIngredientEntity, RecipeService (matcher), RecipeController
├── shopping/           # ShoppingListEntity, ShoppingListItemEntity, ShoppingService, ShoppingController
├── analytics/          # AnalyticsService, AnalyticsController
├── ai/                 # ScanService (OCR + CV), OnnxInferenceService, CatalogMatcherService
└── resources/
    ├── application.yml
    ├── application-prod.yml
    └── db/migration/   # Flyway SQL migration scripts (V1__init.sql, V2__seed_catalog.sql, ...)
```

### 3.2 Layered Architecture Per Module

Every domain module follows the same strict layered pattern:

```
HTTP Request  (JSON over HTTPS)
      │
      ▼
@RestController          → Input DTO validation (@Valid, Jakarta Bean Validation)
      │                    → Extracts authenticated userId from SecurityContext
      │                    → Calls service layer only
      ▼
@Service                 → Business rules and orchestration
      │                    → Enforces kitchen authorization via KitchenAuthGuard
      │                    → Marked @Transactional on mutating methods
      │                    → Maps between Entities ↔ DTOs (MapStruct or manual)
      ▼
@Repository              → Spring Data JPA interfaces + @Query JPQL
      │                    → Never called directly from controllers
      ▼
JPA / Hibernate          → SQL generation, connection pool (HikariCP)
      │
      ▼
PostgreSQL 16            → ACID transactions, indexed queries
```

### 3.3 Service Module Responsibilities

| Service | Key Responsibilities |
| :--- | :--- |
| `UserService` | User profile lookup, synchronization, and idempotent fallback provisioning |
| `KitchenService` | Creates & retrieves kitchen; validates kitchen membership for every request |
| `InventoryService` | Batch CRUD, transactional quantity deductions, event ledger writes, low-stock detection |
| `ExpiryService` | Classifies all active batches into expiry buckets (Fresh/Use Soon/Expiring/Expired) |
| `ExpiryScheduler` | Periodic Spring `@Scheduled` job evaluating per-user digest preferences (15-min intervals); queries expiring items and creates user-specific notifications |
| `ScanService` | Orchestrates OCR pipeline (preprocessing → Tesseract → parser → catalog match); orchestrates CV model inference |
| `CatalogMatcherService` | Fuzzy string matching between raw OCR/CV tokens and `food_catalog` entries |
| `RecipeService` | Ingredient availability scoring, expiry-weighted ranking, transactional "cook" deduction |
| `LeftoverService` | Tracks cooked food with validated, approved storage guidance (or user-provided expiry dates); generates rule-based repurposing matches |
| `ShoppingService` | Manages shopping lists, auto-adds low-stock and recipe-missing items, bulk inventory import |
| `AnalyticsService` | Aggregates monthly spend / waste in ₹; computes category breakdown |
| `NotificationService` | Creates in-app notification records; called by ExpiryScheduler and triggered actions |

---

## 4. Authentication & Authorization Architecture

### 4.1 Authentication Provider: Clerk
Clerk manages user identity, session management, Google OAuth, and passwordless email authentication on the **frontend**. The Next.js app never handles passwords or session secrets.

### 4.2 JWT Verification in Spring Boot (Spring Security)
The Spring Boot backend treats Clerk as an external OIDC identity provider. Protected API endpoints require JWT verification through Spring Security's `JwtAuthenticationFilter`. Public exceptions are strictly limited to `/api/v1/health` and the Clerk webhook endpoint (`/api/webhooks/clerk`). Frontend route protection is solely for navigation and is not a substitute for backend authentication:

```
[Next.js Client]
      │ Protected HTTP Request with "Authorization: Bearer <clerk_jwt>"
      ▼
[JwtAuthenticationFilter]  (Spring Security OncePerRequestFilter)
      │ 1. Extract Bearer token from Authorization header
      │ 2. Fetch Clerk JWKS public keys from:
      │    https://api.clerk.dev/v1/jwks (cached with JwkSet)
      │ 3. Verify JWT signature, expiry, and issuer claim
      │ 4. Extract sub (Clerk userId) and email claims
      │ 5. Load local User record from users table
      │    (Triggers idempotent JIT fallback provisioning if user record missing)
      │ 6. Set UsernamePasswordAuthenticationToken in SecurityContext
      ▼
[Controller method executes with authenticated principal]
```

**Configuration:** `SecurityFilterChain` is declared in `config/SecurityConfig.java` using Spring Security 6 lambda DSL. All protected `/api/v1/**` endpoints require authentication. Public access is strictly limited to `GET /api/v1/health` (public Actuator health check) and `POST /api/webhooks/clerk` (Clerk webhook with Svix signature verification). Unauthenticated access to any protected route returns `401 UNAUTHORIZED`.

### 4.3 User Provisioning Architecture: Authoritative Webhook with Idempotent JIT Fallback

To ensure zero race conditions and robust provisioning, FoodWise implements a **Primary Authoritative Webhook with Idempotent Just-In-Time (JIT) Fallback**:

```
[Clerk Signup Event]
         │
         ├─────────────────────────────────────────┐
         │ (Asynchronous Webhook)                  │ (Immediate Frontend Redirect)
         ▼                                         ▼
[POST /api/webhooks/clerk]             [Frontend API Call with Bearer JWT]
  - Verify Svix signature                - JwtAuthenticationFilter validates token
  - Primary Authoritative Path           - Looks up user by clerk_id in Postgres
  - Provisions user, kitchen,               │
    membership, and preferences             ├─► User exists: proceed immediately
                                            │
                                            └─► User NOT found (webhook delayed):
                                                Idempotent JIT fallback triggers
                                                UserService.provisionUserIfAbsent()
```

1. **Authoritative Mechanism (Clerk Webhooks):**
   - The primary, authoritative user onboarding occurs via the Clerk `user.created` webhook received at `POST /api/webhooks/clerk`.
   - Spring Boot verifies the Svix cryptographic signature using `CLERK_WEBHOOK_SECRET`.
   - The webhook listener calls `UserService.provisionUser()` inside an atomic `@Transactional` boundary, creating the `users` row, the default `kitchens` row (e.g. *"Rohan's Kitchen"*), the `kitchen_members` row (`role = 'owner'`), and default `user_preferences`.

2. **Idempotent Recovery Mechanism (First-Authenticated-Request JIT Fallback):**
   - In rare scenarios where network latency or webhook delivery retries delay the webhook receipt past the moment the frontend redirects the user into the dashboard, the user's first API call might arrive before the webhook completes.
   - When `JwtAuthenticationFilter` extracts the `sub` claim (Clerk ID) and finds no corresponding `users` row in the database, it dispatches an idempotent fallback routine: `UserService.provisionUserIfAbsent(clerkId, email, firstName, lastName)`.
   - This method utilizes database-level unique constraints (`UNIQUE(clerk_id)`) with an `ON CONFLICT DO NOTHING` / exception-safe pattern. If the webhook completes simultaneously, the second attempt gracefully yields, ensuring that exactly one user, kitchen, and preference set is created without duplication or 404 errors.

### 4.4 Kitchen-Level Tenancy Authorization
A reusable guard is applied in every service method that touches kitchen-scoped data:

```java
// security/KitchenAuthGuard.java
@Component
public class KitchenAuthGuard {

    private final KitchenMemberRepository memberRepo;

    public KitchenMemberEntity requireMembership(UUID kitchenId, UUID userId) {
        return memberRepo.findByKitchenIdAndUserId(kitchenId, userId)
            .orElseThrow(() -> new ForbiddenException(
                "User " + userId + " is not a member of kitchen " + kitchenId));
    }
}
```

This guard is called at the **service layer**, not the controller layer, to ensure business logic cannot be bypassed even through internal service-to-service calls.

---

## 5. REST API Design Principles & Contract Conventions

### 5.1 URL Structure
All endpoints are versioned under `/api/v1/`:

| Domain | Base Path | Example Endpoints |
| :--- | :--- | :--- |
| Inventory | `/api/v1/kitchens/{kitchenId}/inventory` | `GET /batches`, `POST /batches`, `PATCH /batches/{id}/quantity` |
| Expiry | `/api/v1/kitchens/{kitchenId}/expiry` | `GET /urgent`, `GET /timeline` |
| Scan | `/api/v1/kitchens/{kitchenId}/scans` | `POST /` (upload + process), `POST /{id}/confirm` |
| Recipes | `/api/v1/kitchens/{kitchenId}/recipes` | `GET /recommendations`, `POST /{id}/cook` |
| Shopping | `/api/v1/kitchens/{kitchenId}/shopping` | `GET /`, `POST /items`, `PATCH /items/{id}/check` |
| Analytics | `/api/v1/kitchens/{kitchenId}/analytics` | `GET /monthly-summary`, `GET /waste-breakdown` |
| Webhooks | `/api/webhooks/clerk` | `POST /` |

### 5.2 Standard Response Envelope
All API responses use a consistent wrapper:

```json
// Success
{
  "success": true,
  "data": { ... },
  "message": "Batch deducted successfully.",
  "timestamp": "2026-11-01T08:30:00Z"
}

// Error
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Quantity delta exceeds current batch quantity.",
    "details": { "quantityDelta": ["Must be <= 350.00"] }
  },
  "timestamp": "2026-11-01T08:30:01Z"
}
```

Implemented via `common/ApiResponse.java` and a global `@RestControllerAdvice` exception handler.

### 5.3 Standard Error Codes
| Code | HTTP Status | Description |
| :--- | :--- | :--- |
| `UNAUTHORIZED` | 401 | Missing or invalid JWT |
| `FORBIDDEN` | 403 | Kitchen membership not found |
| `NOT_FOUND` | 404 | Resource does not exist |
| `VALIDATION_ERROR` | 422 | Jakarta Bean Validation failure |
| `CONFLICT` | 409 | Insufficient stock or state conflict |
| `INTERNAL_ERROR` | 500 | Unhandled server error |

### 5.4 Transactional Integrity
All batch quantity mutations are executed inside a Spring `@Transactional` block. The service method locks the batch row pessimistically (`SELECT ... FOR UPDATE`) before deducting. If `currentQuantity - delta < 0`, a `ConflictException` is thrown and the transaction rolls back, guaranteeing zero negative stock.

### 5.5 API Documentation
Springdoc OpenAPI (`springdoc-openapi-starter-webmvc-ui`) auto-generates interactive Swagger UI at `/api/docs` in the development profile.

---

## 6. ML & OCR Integration (No Generative AI)

All intelligent recognition features run **entirely on the Spring Boot backend** using open-source libraries. No external AI API is called.

### 6.1 Receipt OCR Pipeline (Tesseract via Tess4J)

```
[Frontend: User uploads receipt image]
              │  HTTP POST multipart/form-data → /api/v1/kitchens/{id}/scans
              ▼
[ScanController] → validates auth + kitchen membership
              │
              ▼
[ScanService.processReceiptScan()]
   Step 1: Save raw image to server-side temp storage
   Step 2: Image preprocessing (OpenCV or Imgscalr)
            - Grayscale conversion
            - Adaptive thresholding / binarization
            - Deskew (straighten rotated receipt)
            - Despeckle / noise removal
   Step 3: Tesseract OCR (Tess4J 5.x)
            - Language: eng (Indian receipts use English numerals/Latin text)
            - PSM mode: 6 (single uniform block of text)
            - OEM mode: 3 (default — LSTM + legacy)
            → Raw text string output
   Step 4: Text Parsing
            - Line-by-line tokenization
            - Regex patterns for price detection (₹, Rs., digits)
            - Quantity/unit extraction patterns (e.g., "500g", "1 kg", "2 Pkt")
   Step 5: CatalogMatcherService
            - JaroWinkler or Levenshtein fuzzy match each token against food_catalog.name_en / name_hi
            - Confidence threshold: 0.75 minimum
            - Matched items get: catalog_item_id, category, default_location, and validated shelf_life guidance where available (or NULL if unvalidated, prompting the user for manual entry without inventing dates)
   Step 6: Persist draft ScanSessionEntity in scan_sessions table (status = PENDING) with parsed_items JSONB
              │
              ▼
[Response to Frontend: ScanSessionDto with candidateItems[] ]
              │
              ▼
[Frontend: Human-in-the-Loop Review Table — user edits & confirms; sets expiry date if catalog guidance is NULL]
              │  HTTP POST → /api/v1/kitchens/{id}/scans/{sessionId}/confirm
              ▼
[ScanService.confirmScan()] → @Transactional
   - Validates confirmed item list (enforcing non-null expiry_date per schema; user-entered date required if guidance is NULL)
   - Creates InventoryBatch records for each confirmed item
   - Creates InventoryEvent (event_type = CREATED)
   - Updates ScanSessionEntity.status = CONFIRMED in scan_sessions table
```

**Dependency:** Tess4J 5.x (Java wrapper for Tesseract 5). Tesseract `tessdata` (eng trained data) bundled with the Spring Boot application JAR or provided via server-side volume mount.

### 6.2 Grocery Haul Image Recognition (ONNX Runtime + YOLO)

```
[Frontend: User uploads countertop photo]
              │  HTTP POST → /api/v1/kitchens/{id}/scans (scan_type=GROCERY_HAUL)
              ▼
[ScanService.processHaulScan()]
   Step 1: Save image to temp storage
   Step 2: Image resize / normalization to model input dimensions (e.g., 640×640 for YOLOv8)
   Step 3: OnnxInferenceService.detect(imageTensor)
            - Loads pre-exported ONNX model (onnxruntime-java 1.x)
            - Runs forward pass: float[1][84][8400] output (YOLOv8 standard output)
            - NMS (Non-Maximum Suppression) post-processing
            - Filters detections below 0.5 confidence threshold
            → List<Detection>(label, boundingBox, confidence)
   Step 4: Map YOLO class labels → food_catalog entries via CatalogMatcherService (optional validated shelf-life guidance mapped if approved; NULL if unvalidated)
   Step 5: Persist draft ScanSessionEntity in scan_sessions table (status = PENDING)
              │
              ▼
[Frontend: Human-in-the-Loop Review — items flagged "Experimental"; requires user-provided expiry date when catalog shelf-life is NULL]
```

**ONNX Model Source:** YOLOv8n or YOLOv8s fine-tuned on a food object detection dataset (see Section 6.5). The trained model is exported to ONNX format offline and bundled with the application deployment.

**Dependency:** `com.microsoft.onnxruntime:onnxruntime` (Maven). No Python runtime is required on the production server.

### 6.3 ML Inference Architecture: ONNX Runtime for Java vs Python FastAPI

**Recommendation: ONNX Runtime for Java (embedded in Spring Boot)**

| Criterion | ONNX Runtime for Java | Separate Python FastAPI Service |
| :--- | :--- | :--- |
| **Operational complexity** | Runs inside the existing Spring Boot JVM — zero extra process | Requires maintaining a separate Python service, Docker container, health checks, and inter-service HTTP |
| **Solo developer maintainability** | Single deployable artifact, single log stream, single JVM process | Two separate services, two deployment units, inter-service failure modes |
| **Latency** | In-process method call (< 1ms overhead) | HTTP roundtrip adds 5–50ms minimum plus serialization overhead |
| **Ecosystem compatibility** | ONNX Runtime 1.x Java API supports YOLOv8 ONNX export natively | Full PyTorch/NumPy ecosystem, but unnecessary if ONNX export is available |
| **Training** | Training occurs offline in Python; only the exported `.onnx` file is deployed | Python service would also need offline training anyway |
| **Verdict** | ✅ **Recommended for MVP** | ❌ Premature for solo developer — unjustified complexity |

The Python environment is used only during the **offline training and model export phase** (see Section 6.4). The production server runs Java only.

**Exception trigger for revisiting:** If a future model architecture is incompatible with ONNX export (e.g., complex custom ops unsupported by the Java runtime), a Python inference sidecar may be justified. This will be evaluated before Milestone M4.

### 6.4 Dataset Candidates for ML & OCR

> **Verification & Approval Status Key:**
> - ⚠️ **Pending Verification & Approval** — Candidate dataset identified from public documentation. Licensing terms, underlying image copyright, annotation licenses, attribution obligations, and commercial-use permissions must be independently verified against authoritative source repositories and the actual downloaded archives. **No dataset ingestion, redistribution, training, or deployment is authorized prior to formal Technical Lead approval.**
> - ❌ **Prohibited / Do Not Use** — License explicitly restricts commercial use, contains incompatible clauses, or presents unacceptable legal ambiguity.

> **Mandatory Policy on Dataset Approval:**
> All dataset entries documented below are research candidates. None are currently authorized for ingestion, redistribution, model training, or deployment. Prior to initiating any training pipeline:
> 1. The actual downloaded archive files, license texts, and sample annotations must be inspected.
> 2. Specific commercial-use rights and attribution obligations must be documented with primary evidence.
> 3. Formal sign-off from the Technical Lead must be granted.

---

#### 6.4.1 Grocery / Food Object Detection (Computer Vision — Haul Recognition)

**Dataset 1: Open Images V7 — Food Subset**
- **URL:** https://storage.googleapis.com/openimages/web/index.html
- **Status:** ⚠️ Pending Verification & Approval. Public documentation indicates images are licensed under Creative Commons Attribution 4.0 (CC BY 4.0) and annotations under Apache 2.0. However, individual image licenses, third-party rights, attribution requirements, and commercial-use terms must be verified against the actual downloaded archives and authoritative documentation before use.
- **Content:** Large-scale dataset with annotated images across 600+ object classes. Food-relevant classes include: Apple, Banana, Tomato, Lemon, Mango, Orange, Pineapple, Strawberry, Bread, Egg, Cheese, Salad, and others.
- **Relevance to FoodWise:** Image dataset for grocery/food image recognition and object-detection training (countertop haul recognition) for common fruits, vegetables, and standard packaged items. It is strictly a visual training corpus, NOT a source of refrigerator/freezer storage guidance.
- **Known limitations:**
  - Class set does not cover India-specific produce: bitter gourd (*karela*), drumstick (*sahjan*), ridge gourd (*turai*), tinda, *arbi*, *methi* leaves, green banana (*kela*), raw papaya.
  - Images originate from diverse web sources with varying lighting, scale, and background clutter.
  - **Action required before use:** Independent license and archive verification, followed by selective extraction of the food subset and label mapping to FoodWise catalog categories upon Technical Lead sign-off.
- **Approximate size of food subset:** Estimated 50,000–200,000 candidate images depending on selected classes (exact count to be determined at download time).

**Dataset 2: Grocery Store Dataset**
- **URL:** https://github.com/marcusklasson/GroceryStoreDataset
- **Status:** ⚠️ Pending Verification & Approval. Repository indicates an MIT License, but the actual repository license file, underlying packaging imagery rights, and commercial-use permissions must be inspected and approved by the Technical Lead before use.
- **Content:** 5,125 images across 81 grocery item classes (fruits, vegetables, cartons, packaged items). Provides image-level classification annotations; **it does not provide object detection bounding boxes**.
- **Relevance to FoodWise:** Demonstrates packaged grocery items and fresh produce in realistic retail and tabletop contexts.
- **Known limitations:**
  - Sourced exclusively from Swedish supermarkets (ICA chain). Packaging design, labeling, and produce visual appearance differ markedly from Indian retail environments.
  - Contains no Indian grocery items.
  - **Object detection compatibility constraint:** Because this is a classification dataset lacking bounding-box annotations, assigning a whole-image bounding box as a "pseudo-annotation" is strictly prohibited, as it misrepresents classification data as object detection and degrades spatial localization in YOLO models.
  - **Valid technical alternatives:**
    1. Utilize this dataset strictly for classification or backbone pre-training (feature extractor representation learning).
    2. Manually annotate true object bounding boxes for a targeted, representative subset of images.
    3. Supplement or replace with a dedicated collection of locally captured, properly annotated Indian grocery images.
  - **Action required before use:** Formal license verification of the downloaded repository, selection of the appropriate integration strategy (classification pre-training vs. manual bounding-box annotation), and Technical Lead approval.

**Dataset 3: UNIMIB2016 Food Recognition Dataset**
- **URL:** http://www.ivl.disco.unimib.it/activities/food-recognition/
- **License:** ❌ Academic/non-commercial use only. Do not use for FoodWise.
- **Relevance to FoodWise:** None. Dish-level plated meal classification — not grocery ingredient detection.

**Identified gap — Indian-specific grocery images:**
The datasets above do not collectively cover the Indian grocery vocabulary required for reliable haul scan performance. The following categories have no suitable representation in available open datasets and **require locally collected, manually annotated images**:

| Missing Category | Example Items |
| :--- | :--- |
| Indian leafy greens | *Methi*, *Palak*, *Sarson*, *Bathua*, *Curry leaves* |
| Indian gourds | *Karela*, *Turai*, *Tinda*, *Lauki*, *Parwal* |
| Indian legumes (raw) | *Cluster beans (Guar)*, *Flat beans (Sem)*, *Raw peas* |
| Indian root vegetables | *Arbi*, *Suran*, *Raw banana*, *Kachcha papita* |
| South Indian produce | *Drumstick (Sahjan)*, *Raw jackfruit*, *Ash gourd* |
| Indian dairy | *Paneer block*, *Amul butter packs*, *Dahi pouches*, *Lassi packets* |
| Indian branded staples | *Aashirvaad atta bags*, *Tata dal packets*, *Fortune oil cans* |

**Action required before M4 (Milestone M4 pre-requisite):**
1. Define a target class list (minimum 30 classes) drawn from `food_catalog` that are high-priority for haul recognition.
2. Collect 50–100 representative photographs per Indian-specific class using household/market conditions (varying lighting, loose produce on countertops, packaged goods).
3. Annotate collected images with bounding boxes using a free annotation tool (LabelImg or Roboflow free tier).
4. Combine with the Open Images and Grocery Store Dataset subsets for a unified training corpus.
5. Confirm licensing of any third-party annotation tool or hosting platform before use.

**This image collection and annotation work must be explicitly scoped and authorized before Milestone M4 begins.** Without it, YOLO fine-tuning will have inadequate Indian grocery coverage.

---

#### 6.4.2 Shelf-Life & Food Safety Data

**Dataset 4: USDA FoodData Central**
- **URL:** https://fdc.nal.usda.gov/download-datasets.html
- **License:** ✅ Verified — US Federal Government public domain. Commercial use permitted.
- **Content:** Nutritional composition data for 300,000+ food items. Does not contain shelf-life information.
- **Relevance to FoodWise:** Useful for canonical English food name normalization and category taxonomy mapping (to assist catalog fuzzy-matching).
- **Known limitations:** Not a shelf-life source. Coverage of Indian-specific items (toor dal, moringa, drumstick) is limited or absent.

**Dataset 5: StillTasty.com**
- **URL:** https://www.stilltasty.com/
- **License:** ⚠️ Unverified. Web content governed by site Terms of Service. Bulk scraping likely prohibited. **Do not scrape. Do not ingest.**
- **Alternative:** Manually reference individual shelf-life figures for the food catalog items, citing StillTasty as an informational source in internal documentation only.

**Dataset 6: USDA FoodKeeper**
- **URL:** https://www.foodsafety.gov/food-safety-charts/cold-food-storage-charts  
  App dataset: https://www.foodsafety.gov/keep-food-safe/foodkeeper-app
- **License:** ✅ Verified — US Federal Government public domain. Commercial use permitted.
- **Content:** Structured shelf-life guidelines for common food categories across refrigerator and freezer storage. Available as a structured JSON dataset via the FDA FoodKeeper app API (terms of the API use require separate verification).
- **Relevance to FoodWise:** Reference dataset for candidate refrigerator and freezer storage guidance. Any values considered for the Indian food catalog must be cross-checked against credible, India-relevant food-safety sources and explicitly approved by the Technical Lead before being used as defaults.
- **Known limitations:** Does not comprehensively cover pantry/room-temperature shelf life for Indian staples. The following items require manual curation from FSSAI (Food Safety and Standards Authority of India) guidelines or food science references: *ghee*, *atta*, *toor dal*, *chana dal*, *paneer* (fresh), *dahi* (home-set), *homemade pickle*, *dried spices*.
- **Action required:** Cross-reference FoodKeeper data with FSSAI storage guidelines for the Indian-specific items in the `food_catalog`. Document each shelf-life figure with its source reference before loading via Flyway seed migration.

---

#### 6.4.3 Recipe Data (Ingredient Matching)

> **Clarification:** The recipe recommendation engine is a **deterministic weighted scoring algorithm** — it does not use ML training. Dataset use here is limited to sourcing the seed recipe data that populates the `recipes` and `recipe_ingredients` tables via Flyway migration. No model is trained on recipe data.

**Dataset 7: RecipeNLG**
- **URL:** https://recipenlg.cs.put.poznan.pl/
- **License:** ✅ Verified — CC BY 4.0. Commercial use permitted with attribution.
- **Content:** 2.2 million recipes with ingredient lists, titles, and instructions.
- **Relevance to FoodWise:** Useful only for expanding the ingredient vocabulary used in catalog-matching normalization. The recipe corpus is >99% Western cuisine and is not suitable as a direct seed source for Indian recipes.
- **Usage plan:** Use ingredient token lists (not full recipes) to supplement catalog name variants. Extract common ingredient aliases to improve OCR catalog-matching coverage.

**Dataset 8: Indian Food 101 (Kaggle)**
- **URL:** https://www.kaggle.com/datasets/nehaprabhavalkar/indian-food-101
- **License:** ✅ Verified — CC0 Public Domain. Commercial use permitted.
- **Content:** 255 Indian dishes with fields: `name`, `ingredients` (text string), `diet`, `prep_time`, `cook_time`, `flavor_profile`, `course`, `state`, `region`.
- **Relevance to FoodWise:** High — the primary seed source for the curated Indian recipe database.
- **Known limitations:**
  - Ingredient quantities are absent from the dataset. Every recipe requires manual enrichment with per-serving ingredient quantities (in grams, ml, or count units) before it can be used by the ingredient matching algorithm.
  - No step-by-step cooking instructions. Instructions must be authored manually for the 75–100 priority recipes.
  - Ingredient text strings require tokenization and normalization against `food_catalog.name_en / name_hi` before seeding.
- **Action required before M5:** Curate and manually enrich the top 75–100 Indian dishes from this dataset with tested quantities and instructions. Load via `V4__seed_recipes.sql` Flyway migration.

**Dataset 9: Epicurious Recipes (Kaggle)**
- **URL:** https://www.kaggle.com/datasets/hugodarwood/epirecipes
- **License:** ⚠️ Unverified. Underlying content is owned by Condé Nast (Epicurious). The Kaggle uploader's authority to license the dataset for commercial redistribution has not been established. **Do not ingest for FoodWise until licensing is confirmed by the technical lead.**

---

#### 6.4.4 Proposed Dataset-to-FoodWise-Class Mapping

The following table documents the intended mapping from external dataset labels to FoodWise `food_catalog` category slugs. This mapping must be validated against the actual class lists at download time.

| Source Dataset | Source Label(s) | FoodWise Catalog Category | FoodWise Example Items |
| :--- | :--- | :--- | :--- |
| Open Images V7 | `Apple`, `Banana`, `Mango`, `Orange`, `Lemon`, `Pineapple` | `fruits` | Apple, Banana, Mango, Orange |
| Open Images V7 | `Tomato`, `Salad` | `vegetables` | Tomato, Lettuce |
| Open Images V7 | `Bread` | `bakery` | Bread loaf, Pav |
| Open Images V7 | `Egg`, `Milk` | `dairy-eggs` | Egg, Milk packet |
| Open Images V7 | `Cheese` | `dairy-eggs` | Paneer (approximate) |
| Grocery Store Dataset | Fruit classes (Apple, Banana, etc.) | `fruits` | (same as above) |
| Grocery Store Dataset | Vegetable classes (Carrot, Avocado, etc.) | `vegetables` | Carrot |
| Grocery Store Dataset | Dairy / Juice carton classes | `dairy-eggs`, `beverages` | Dahi pouch (approximate) |
| Locally collected | *Methi*, *Palak*, *Coriander* | `vegetables` | Leafy greens |
| Locally collected | *Karela*, *Turai*, *Lauki* | `vegetables` | Indian gourds |
| Locally collected | Paneer block, Amul butter | `dairy-eggs` | Paneer, Butter |
| Locally collected | Atta bag, Dal packet | `grains-pulses` | Whole wheat flour, Toor dal |

> **Gap note:** All Indian-specific items (rows with "Locally collected" as source) require the data collection and annotation work described in Section 6.4.1 before training can begin.

### 6.5 Model Training, Evaluation & Versioning Plan (Offline — Pre-Deployment)

The following workflow executes **offline by the developer** prior to deployment. No training infrastructure runs in production.

```
[Offline ML Workflow — Developer Machine / Colab]

1. DATA INGESTION
   - Ingest candidate datasets only after primary verification of downloaded archives and explicit Technical Lead approval
   - Download Indian Food 101 from Kaggle upon verification
   - Research and validation references: FoodKeeper and FSSAI are candidate references for research and validation only; no values may become production defaults without credible, India-relevant validation and explicit Technical Lead approval

2. DATA PREPROCESSING
   - Image normalization (resize to 640×640, normalize pixel values)
   - Annotation format conversion to YOLO format (class_id, bbox_center_x, bbox_center_y, w, h)
   - Label mapping: Dataset class labels → FoodWise food_catalog category slugs
   - Train/Val/Test split: 70% / 15% / 15%

3. MODEL TRAINING
   - Base model: YOLOv8n (nano) or YOLOv8s (small) from Ultralytics
   - Fine-tune on food object detection split using transfer learning
   - Hyperparameters: epochs=50, batch=16, imgsz=640, lr0=0.01

4. EVALUATION
   - Metrics: mAP@0.5, mAP@0.5:0.95, Precision, Recall per class
   - Minimum acceptable mAP@0.5: 0.60 for MVP (haul scan is experimental)
   - Per-class failure analysis for Indian produce categories

5. ONNX EXPORT
   - Export trained model: yolo export format=onnx simplify=True opset=17
   - Validate ONNX output parity against PyTorch model using test set samples

6. MODEL VERSIONING
   - Store ONNX file with naming convention: foodwise-haul-v{MAJOR}.{MINOR}.onnx
   - Version metadata recorded in database or application config (model name, training date, mAP, dataset sources)
   - Model files stored in a versioned directory; active model path injected via application.yml

7. INFERENCE INTEGRATION
   - OnnxInferenceService loads model at Spring Boot startup via OrtEnvironment + OrtSession
   - Model path configured via: ai.model.haul-detection-path
   - Hot-swap: Updating model file + restarting Spring Boot is sufficient for model updates in MVP
```

---

## 7. Database Architecture

### 7.1 Migration Strategy: Flyway
All schema changes are managed exclusively through **Flyway versioned SQL migrations** stored in `backend/src/main/resources/db/migration/`:

| File | Purpose |
| :--- | :--- |
| `V1__create_core_tables.sql` | All 19 schema tables (users, kitchens, inventory, etc.) |
| `V2__seed_food_categories.sql` | Insert 10 food category rows |
| `V3__seed_food_catalog.sql` | Insert ~200 Indian food catalog items; seed validated shelf-life values only where approved sources exist, leaving unvalidated values NULL |
| `V4__seed_recipes.sql` | Insert 75+ curated Indian recipes and recipe_ingredients |
| `V5__seed_leftover_repurposing_rules.sql` | Insert leftover transformation rules |

Flyway runs automatically on Spring Boot startup (`spring.flyway.enabled=true`). No manual schema application is ever permitted.

### 7.2 JPA Entity Conventions
- All primary keys: `UUID` generated via `@GeneratedValue(strategy = GenerationType.UUID)`.
- All entities extend a `BaseEntity` superclass annotated with `@CreationTimestamp` and `@UpdateTimestamp`.
- Soft deletes are NOT used. Lifecycle states are controlled via status columns:
  - `inventory_batches.status` permits strictly: `'active'`, `'consumed'`, `'discarded'`, `'spoiled'`. There is **NO `expired` database status**. Expiry is a derived query/UI state based on `expiry_date < CURRENT_DATE` and `status = 'active'` (items where `expiry_date = CURRENT_DATE` remain `Expiring Today`, not `Expired`). Scheduled jobs and triage services query expiring batches without mutating or persisting an unsupported lifecycle status.
- **Java Enum and Database Enum Mapping:**
  - Database check constraints and column values remain strictly **lowercase** across all tables:
    - Notification types: `expiry_warning`, `item_expired`, `stock_low`, `leftover_alert`, `waste_summary`
    - Scan types: `receipt`, `grocery_haul`, `fridge_shelf`
    - Scan statuses: `pending`, `confirmed`, `rejected`, `failed`
    - Batch statuses: `active`, `consumed`, `discarded`, `spoiled`
    - Storage locations: `fridge`, `freezer`, `pantry`
  - Java domain entities use conventional **UPPERCASE** enum constants (e.g., `NotificationType.EXPIRY_WARNING`, `ScanStatus.CONFIRMED`, `BatchStatus.ACTIVE`).
  - Mapping between Java uppercase enums and database lowercase strings is handled authoritatively via JPA `@Converter(autoApply = true)` or custom `AttributeConverter` implementations (e.g., `NotificationTypeConverter` mapping `EXPIRY_WARNING` <-> `"expiry_warning"`), ensuring type-safe domain logic without violating database check constraints.
- Concurrency & locking: Pessimistic locking (`SELECT ... FOR UPDATE` via `batchRepo.findByIdAndKitchenIdForUpdate()`) is the primary concurrency control strategy for inventory mutations, ensuring strict serialization and preventing negative stock or race conditions during simultaneous deductions.

### 7.3 Transactional Inventory Deduction Contract
(Identical logic to DATABASE_SCHEMA.md Section 4 — implemented in Spring Boot `@Transactional` service):

```java
// inventory/InventoryService.java
@Transactional
public InventoryBatchDto deductQuantity(UUID kitchenId, UUID batchId, BigDecimal delta, UUID userId) {
    kitchenAuthGuard.requireMembership(kitchenId, userId);

    InventoryBatch batch = batchRepo.findByIdAndKitchenIdForUpdate(batchId, kitchenId)
        .orElseThrow(() -> new NotFoundException("Batch not found"));

    if (batch.getCurrentQuantity().compareTo(delta) < 0) {
        throw new ConflictException("Insufficient quantity. Current: "
            + batch.getCurrentQuantity() + ", Requested: " + delta);
    }

    BigDecimal newQty = batch.getCurrentQuantity().subtract(delta);
    batch.setCurrentQuantity(newQty);
    if (newQty.compareTo(BigDecimal.ZERO) == 0) batch.setStatus(BatchStatus.CONSUMED);

    InventoryEvent event = new InventoryEvent();
    event.setBatch(batch);
    event.setKitchen(batch.getKitchen());
    event.setEventType(InventoryEventType.CONSUMED_PARTIALLY);
    event.setQuantityDelta(delta.negate());
    event.setQuantityAfter(newQty);
    event.setPerformedByUser(userRepo.getReferenceById(userId));
    eventRepo.save(event);

    return batchMapper.toDto(batchRepo.save(batch));
}
```

---

## 8. Scheduled Jobs (Spring @Scheduled)

Spring's built-in `@EnableScheduling` mechanism drives all background tasks. No external cron service or job scheduler is required.

### 8.1 Daily Expiry Digest Job
The scheduled digest process respects each user's configured `notify_daily_digest_time` preference (stored in `user_preferences.notify_daily_digest_time` as a SQL `TIME`, defaulting to `08:00:00`).

To guarantee user-selected digest times are reliably delivered and never missed by the 15-minute scheduler:
1. **UI Precision Alignment:** The UI time picker in Settings (Screen 14) is explicitly constrained to 15-minute intervals (e.g., `08:00`, `08:15`, `08:30`, `08:45`).
2. **Idempotent Due-Time Window:** The scheduler runs periodically every 15 minutes (`cron = "0 0/15 * * * *"`) and queries eligible active users whose `notify_daily_digest_time <= currentWindow` and who have not yet received an `expiry_warning` notification for today (`CURRENT_DATE`). Daily deduplication guarantees exactly one morning digest per user per day even across scheduler restarts or transient delays.

It queries urgent and expiring active batches across the user's authorized kitchens, and creates distinct, user-specific `notifications` records (`notifications.user_id = user.id`) with lowercase `type = 'expiry_warning'`, avoiding duplicate notifications for the same user, date, and event:

```java
// expiry/ExpiryScheduler.java
@Component
@EnableScheduling
public class ExpiryScheduler {

    // Runs periodically every 15 minutes to evaluate users whose configured digest time is due
    @Scheduled(cron = "0 0/15 * * * *") // Evaluates every 15 minutes
    public void processDailyExpiryDigests() {
        LocalTime currentWindow = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDate today = LocalDate.now();
        
        // 1. Find users whose notify_daily_digest_time <= currentWindow
        //    and who have not already received an 'expiry_warning' notification for today.
        // 2. For each eligible user, query their kitchen's active batches expiring within notify_expiry_days_before.
        // 3. If urgent items exist:
        //    Insert user-specific Notification record:
        //    notification.setUserId(user.getId());
        //    notification.setKitchenId(kitchen.getId());
        //    notification.setType(NotificationType.EXPIRY_WARNING); // maps to 'expiry_warning'
        // 4. (Future: dispatch push notification or email digest if enabled)
    }
}
```

### 8.2 Expired Batch Notification Job
```java
@Scheduled(cron = "0 0 3 * * *", zone = "UTC") // 08:30 IST
public void notifyExpiredBatches() {
    // 1. Query active batches where expiry_date < CURRENT_DATE AND status = 'active'
    // 2. Note: Does NOT persist an unsupported 'expired' status to inventory_batches.status
    //    (status remains 'active' until user marks 'consumed', 'spoiled', or 'discarded')
    // 3. For each eligible member of the owning kitchen who has notifications enabled:
    //    Create user-specific in-app notification with type = 'item_expired' (NotificationType.ITEM_EXPIRED)
}
```

---

## 9. Image Storage & Asset Management

### 9.1 Strategy
- **Receipt Images and Haul Photos:** Uploaded to the Spring Boot backend as `multipart/form-data`. After OCR/CV processing completes, the original image is retained for the `scan_sessions` record (`ScanSessionEntity`, for audit) and stored in Cloudinary (or local filesystem for development).
- **Temp Processing Files:** Immediately deleted after OCR text extraction and ONNX inference complete, minimizing disk usage.
- **Cloudinary Integration:** The Spring Boot backend (not the Next.js frontend) manages Cloudinary uploads using the Cloudinary Java SDK, keeping API secrets server-side.

---

## 10. Security & Data Protection

1. **JWT Verification:** All protected API endpoints are verified by Spring Security's JwtAuthenticationFilter. Public exceptions are limited to /api/v1/health and the Clerk webhook.
2. **Tenant Isolation:** `KitchenAuthGuard.requireMembership()` is called at the service layer. Database queries include `kitchen_id` in all WHERE clauses.
3. **Input Validation:** Jakarta Bean Validation (`@Valid`, `@NotNull`, `@Size`, `@Positive`) on all request DTOs at the controller layer. Server-side validation is the single source of truth; frontend Zod validation is UX-only.
4. **Rate Limiting:** Spring `HandlerInterceptor` or a lightweight token-bucket per user for `/api/v1/*/scans` endpoints (maximum 20 scans per hour per kitchen).
5. **SQL Injection Prevention:** Spring Data JPA parameterized queries and JPQL prevent raw SQL injection. Native queries, if used, use `@Query` with named parameters only.
6. **Sensitive Configuration:** All secrets (`CLERK_SECRET_KEY`, `DATABASE_URL`, `CLOUDINARY_API_SECRET`, `CLERK_WEBHOOK_SECRET`) injected via environment variables. Spring `application.yml` contains no hardcoded secrets.
7. **CORS:** `SecurityConfig` allows only the configured frontend origin (`FRONTEND_URL` env var). Wildcard CORS is disabled in production.

---

## 11. Error Handling & Observability

### 11.1 Global Exception Handler
A single `@RestControllerAdvice` class (`common/GlobalExceptionHandler.java`) maps all exceptions to the standard `ApiResponse` error envelope:

| Exception Class | HTTP Status | Error Code |
| :--- | :--- | :--- |
| `UnauthorizedException` | 401 | `UNAUTHORIZED` |
| `ForbiddenException` | 403 | `FORBIDDEN` |
| `NotFoundException` | 404 | `NOT_FOUND` |
| `ConflictException` | 409 | `CONFLICT` |
| `MethodArgumentNotValidException` | 422 | `VALIDATION_ERROR` |
| `Exception` (catch-all) | 500 | `INTERNAL_ERROR` |

### 11.2 Frontend Error Handling
React Error Boundaries wrap each major route group. TanStack Query surfaces API errors to the UI with retry logic (3 retries with exponential backoff for GET requests; no auto-retry for mutations).

### 11.3 Structured Logging
Spring Boot configured with `logback-spring.xml` outputting JSON-formatted logs in production for compatibility with log aggregation services (e.g., Grafana Loki, Papertrail). Log entries include correlation ID (injected via `MDC`) for tracing a request through the entire stack.

---

## 12. Deployment & Operational Architecture

| Layer | Provider | Configuration |
| :--- | :--- | :--- |
| **Frontend Hosting** | Vercel | Next.js static export + Edge Network (Mumbai region preferred) |
| **Backend Hosting** | Railway / Render / Fly.io | Spring Boot JAR in Docker container (Java 21 slim base image) |
| **Relational Database** | Supabase PostgreSQL / Railway PostgreSQL | PostgreSQL 16, connection pooling via PgBouncer or HikariCP |
| **Authentication** | Clerk | Production Cloud Instance |
| **Media Storage** | Cloudinary | Managed via Spring Boot Cloudinary Java SDK |
| **Scheduled Jobs** | Spring `@Scheduled` (embedded) | Runs inside the Spring Boot JVM — no external cron service |
| **API Documentation** | Springdoc OpenAPI | `/api/docs` available in dev profile; disabled or access-controlled in prod |

> **Unresolved Decision:** Specific backend hosting provider (Railway vs Render vs Fly.io) is not yet finalized. All three support containerized JVM deployments. Fly.io is preferred for the Mumbai/Singapore region proximity to the Indian user base. Requires technical lead confirmation.

---
*End of System Design v1.1.4. Updated to reflect AD-001 (Spring Boot backend) and AD-002 (no generative AI — Tesseract OCR + ONNX Runtime). Awaiting final Technical Lead M0 approval.*

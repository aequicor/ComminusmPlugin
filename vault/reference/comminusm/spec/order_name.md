---
genre: reference
module: comminusm
title: Technical Specification — Order Name
topic: order-name
status: Draft
date: 07.05.2026
author: "@SystemAnalyst"
related:
  - vault/concepts/comminusm/requirements/order_name.md
  - vault/concepts/comminusm/plans/order_name-corner-cases.md
  - vault/reference/comminusm/test-cases/order_name-test-cases.md
  - vault/reference/comminusm/spec/communes.md
---

# Technical Specification — Order Name

**Module:** comminusm
**Feature:** order_name
**Status:** Draft
**Date:** 07.05.2026
**Author:** @SystemAnalyst
**Requirements:** [[concepts/comminusm/requirements/order_name]]

---

## Overview

This feature introduces a customizable, human-readable **name field** to the `Order` entity. Previously, orders were identified only by their internal ID. The name field enables players to assign and edit meaningful names that reflect their group's identity.

**Core capabilities:**
- Default name assigned on creation (owner's player nickname, sanitized)
- Editable via Anvil GUI (leader-only, with pre-filled current name)
- Displayed in-world on the order's ArmorStand entity
- Displayed in all order-related menus
- Persisted to database; survives server restart
- Max 20 characters; alphanumeric + Cyrillic/Latin + hyphen/underscore; no spaces

---

## Data Models

### Order (existing entity — augmented)

The existing `Order` data class is augmented with one new field:

```
Field         Type        Required  Description
-----         ----        --------  -----------
[existing]    [...]       [...]     id, ownerUuid, members, etc. — unchanged
name          String      yes       Display name for the order. Max 20 chars.
                                    Charset: A-Z/a-z/а-я/А-Я/0-9/-/_
                                    No spaces or other characters.
                                    Default: owner's nickname at creation (sanitized).
                                    Example: "Warriors", "Дружина", "Team-42"
```

**Constraints:**
- Not null (always has a value at creation)
- Length: 1 to 20 characters inclusive
- Character set: Latin letters (A-Z, a-z), Cyrillic letters (а-я, А-Я), digits (0-9), hyphen (-), underscore (_)
- No leading/trailing whitespace
- No Unicode normalization required (byte-level String.equals used for comparison)

**Notes:**
- Multiple orders may have the same name (no uniqueness constraint)
- No character limit enforcement in Anvil UI (Minecraft input allows any chars); server validates on confirm
- Hyphen, underscore at any position are allowed (e.g., `_Name_`, `-Guild`, `Order-`)
- Single-character names valid (e.g., `A`, `Я`, `1`)

### orders (SQL table — schema change)

**New column:**

```
Column          SQL Type         Constraints        Description
------          --------         -----------        -----------
name            VARCHAR(20)      NOT NULL           Order display name (max 20 chars).
                                 DEFAULT 'Order'    Fallback default if sanitization fails.
```

**Migration:**
- For existing orders without a name (pre-feature), populate `name` with `'Order'` as fallback.
- For new orders, `name` is set during creation (step 1 of "Create Order" flow in Business Logic).

---

## Public API Contracts

### Service Methods

#### OrderService.createOrder(ownerUuid: UUID, ... , name: String?): Order

**Input:**
- `ownerUuid` — UUID of the order owner (player)
- `...` — existing Order creation parameters
- `name` (optional) — proposed order name. If null, auto-generated from owner's nickname.

**Logic:**
1. If `name` is null or blank:
   - Retrieve owner's player nickname via Bukkit Player lookup
   - Use nickname as candidate name
2. If candidate name contains invalid characters (not in charset above):
   - Replace invalid chars with underscore (`_`)
3. If result is empty or all underscores/whitespace:
   - Use fallback: `"Order"`
4. If result length > 20:
   - Truncate to first 20 characters
5. Assign sanitized name to new Order
6. Notify owner (via action bar): `"Название вашего ордера установлено на '[Name]'"`

**Return:**
- New `Order` with `id`, `ownerUuid`, `name` set
- Persisted to database

**Error scenarios:**
- Owner UUID not found / player offline → use fallback name `"Order"` and log warning (not a hard error)

#### OrderService.renameOrder(orderId: Long, newName: String, requesterUUID: UUID): Result<Unit, Exception>

**Input:**
- `orderId` — ID of order to rename
- `newName` — proposed new name
- `requesterUUID` — UUID of player requesting the rename

**Logic:**
1. **Permission gate:** Call `OrderService.isLeader(requesterUUID)` to verify requester is the owner. If not → return `Failure(UnauthorizedException("..."))`.
2. **Order existence gate:** Fetch order by ID. If missing → return `Failure(NotFoundException("..."))`.
3. **Validation:** Apply name validation (charset, length, non-empty). If invalid → return `Failure(ValidationException(...))` with specific error message.
4. **No-op check:** If newName equals current order.name (byte-level comparison) → return `Success(Unit)` without DB write.
5. **Optimistic update (main thread only):**
   - **Thread affinity note:** `renameOrder()` is always called from the Anvil confirm event handler (InventoryClickEvent), which fires on the main server thread. All steps 5a–5d execute on the main thread — no explicit scheduler bounce is needed for ArmorStand mutation.
   - 5a. Cache old name before mutation
   - 5b. Update in-memory order.name = newName
   - 5c. Update ArmorStand entity (if exists) with `setCustomName(newName)` and `setCustomNameVisible(true)`. If entity is null or throws exception → log warning, continue (CC-03). This mutation is safe because it runs on the main thread.
   - 5d. Persist to database asynchronously via DatabaseManager (async dispatch happens AFTER in-memory + entity updates complete on main thread)
6. **Rollback on DB failure:** If database write fails → restore in-memory name to cached old value, close any open menu for requester.

**Return:**
- `Success(Unit)` on successful rename
- `Failure(UnauthorizedException(...))` if not leader
- `Failure(NotFoundException(...))` if order missing
- `Failure(ValidationException(...))` with specific error message (charset, length, empty)
- `Failure(DatabaseException(...))` if DB persistence fails (with rollback applied)

**Concurrency:**
- No locking or serialization (MVP: last-write-wins). If two leaders rename simultaneously, last database commit wins silently (CC-16, CC-20).

#### OrderRepository.renameOrderInDatabase(orderId: Long, newName: String): CompletableFuture<Unit>

**Input:**
- `orderId` — order ID
- `newName` — new name

**Implementation:**
- Parameterized SQL: `UPDATE orders SET name = ? WHERE id = ?`
- Async via DatabaseManager

**Error:**
- Throws exception on DB failure; caller handles retry / rollback

### Bukkit Events

#### In-Progress Rename Tracking

**Structure:**
- Type: `ConcurrentHashMap<UUID, Long>` (playerUUID → orderId)
- Location: Stored as a field in the menu/handler class responsible for Anvil interactions (e.g., the class that handles InventoryClickEvent for Rename button)
- Purpose: Prevent double-click or rapid successive Anvil opens for the same player+order combination (CC-06)

**Lifecycle:**
1. **Entry added:** When player clicks Rename button and Anvil is successfully opened. Entry: `playerUUID → orderId`
2. **Entry removed:** On any of:
   - Anvil confirm (success or validation failure)
   - Anvil cancel (player presses Escape or clicks X)
   - InventoryCloseEvent fired for the Anvil inventory
   - PlayerQuitEvent fired for the player
3. **No timeout:** Anvil auto-closes on player disconnect, triggering close event, so no explicit timeout timer is needed.

**Check before opening Anvil:**
- Before step "open Anvil GUI", check: `if (inProgressRenames.containsKey(playerUUID)) { reject open, send message "Закройте текущее переименование" or similar }`
- After Anvil is successfully opened, call `inProgressRenames.put(playerUUID, orderId)`

#### (Implicit) InventoryClickEvent in OrderMenu / CommuneOrderMenu

**Trigger:** Player clicks Rename button (material: ANVIL, material not finalized in spec — implementation plan will specify slot)

**Handler logic:**
1. Permission check: `OrderService.isLeader(playerUUID)` → if not leader, cancel event and send tooltip message
2. **In-progress check:** Check if `inProgressRenames.containsKey(playerUUID)`. If yes → reject open, send message (double-click prevention, CC-06)
3. If leader and no in-progress rename: open Anvil GUI (via AnvilGUI library or Paper API) with current order.name pre-filled
4. On successful Anvil open: add entry to `inProgressRenames.put(playerUUID, orderId)`
5. **Anvil confirm handler:**
   a. Retrieve text from Anvil output slot
   b. Call `OrderService.renameOrder(orderId, text, playerUUID)`
   c. Remove entry from `inProgressRenames` (on success or failure)
   d. On success: send confirmation message `"Название ордера изменено на '[newName]'"` and close menu
   e. On permission failure: send message `"Вы больше не лидер этого ордера"`
   f. On validation failure: send message from ValidationException (e.g., `"Недопустимые символы. Используйте: буквы, цифры, дефис (-), подчёркивание (_)"`)
   g. On order not found: send message `"Этот ордер был расформирован"`
   h. On DB failure: send message `"Ошибка при сохранении названия. Попробуйте позже"` and restore in-memory name
6. **Anvil cancel handler:** remove entry from `inProgressRenames`, close Anvil without changes (silent, no confirmation message — per AC-08)

**Availability:**
- Rename button visible to all players (leader and non-leader)
- Button disabled with tooltip `"Только лидер может менять название"` for non-leaders (CC-09, AC-11, AC-12)

#### PlayerQuitEvent Handler

**Trigger:** Player disconnects (PlayerQuitEvent fired by Bukkit)

**Handler logic:**
1. Retrieve player UUID from event
2. Check if entry exists in `inProgressRenames` for this UUID
3. If yes: remove entry from `inProgressRenames`
4. If no: do nothing (silent)

**Purpose:** Cleanup orphaned in-progress rename tracking if the player disconnects before an InventoryCloseEvent fires (edge case, CC-07). Ensures the map does not grow unbounded and stale entries do not block future renames for the same player (in case they rejoin and attempt rename again).

**Note:** Anvil auto-closes on disconnect, typically triggering InventoryCloseEvent first. This handler is a defense-in-depth backstop for edge cases where InventoryCloseEvent may not fire.

---

## Business Logic

### 1. Create Order (default name assignment)

Triggered when a player creates a new order via `/order create` or equivalent command.

**Steps:**
1. Get owner's Bukkit Player object (may be offline-mode → nickname only)
2. Extract player nickname (display name preferred, fallback to name field)
3. **Sanitize nickname:**
   - Replace any char not in `[A-Z a-z а-я А-Я 0-9 \- \_]` with underscore (`_`)
   - If result is empty or all underscores → use fallback `"Order"`
   - If result length > 20 → truncate to first 20 characters
4. Assign sanitized name to new Order
5. Call `OrderService.createOrder(..., sanitizedName)` → persists to DB
6. Send owner confirmation: `"Название вашего ордера установлено на '[name]'"` (action bar or chat)

**Example:**
- Player nickname: `"Player#123_Cool"` → sanitized: `"Player_123_Cool"` (21 chars, exceeds limit) → truncated: `"Player_123_Cool"` (first 20 chars) → stored as-is, truncation logged or noted in notification
- Player nickname: `"Игрок123"` → all valid chars → stored as `"Игрок123"`
- Player nickname: `"!!!"` → all invalid → fallback: `"Order"`

### 2. Rename Order (Anvil flow)

Triggered when a leader clicks the Rename button in OrderMenu or CommuneOrderMenu.

**Steps:**

#### 2.1 Open Anvil GUI
1. Verify player is leader (`OrderService.isLeader(playerUUID)`)
2. If not leader: disable button, show tooltip
3. Check if player already has an in-progress rename in `inProgressRenames` map (CC-06):
   - If yes: reject open, send message `"Закройте текущее переименование"` (double-click prevention)
   - If no: proceed
4. If leader and no in-progress rename: open Anvil GUI with:
   - Title: `"Переименовать ордер"` or similar
   - Input slot pre-filled with current `order.name`
5. On successful Anvil open: add entry to `inProgressRenames.put(playerUUID, orderId)`

#### 2.2 Validate and Apply Rename
1. On Anvil confirm (output slot interaction):
   - Extract text from output slot
   - Call `OrderService.renameOrder(orderId, text, playerUUID)`
   - Remove entry from `inProgressRenames` (regardless of success or failure)
   - Handle result as per API contract (success / permission / validation / not-found / DB error cases)
2. On Anvil cancel (Escape or close button):
   - Remove entry from `inProgressRenames`
   - Close Anvil without changes (silent, no message per AC-08)

#### 2.3 Post-Rename Actions (on success)
1. **In-memory update:** Order.name already updated in `renameOrder()` step 5
2. **ArmorStand update:** Already attempted in `renameOrder()` step 5 (graceful skip if entity missing per CC-03)
3. **Menu refresh:** Close the rename Anvil. Close the owner's currently-open menu (if any) so they must re-open to see the updated name (AC-09)
4. **Confirmation message:** Send to player `"Название ордера изменено на '[newName]'"` (action bar, or chat as fallback if action bar unavailable)

#### 2.4 Post-Rename Actions (on failure)
- Anvil closes
- In-memory name rolled back (if DB error)
- Appropriate error message sent to player (action bar or chat)
- No menu state change (menu stays open or closed per original state)

### 3. Display Order Name

#### 3.1 In-World (ArmorStand entity)

- On order creation or rename, set ArmorStand's custom name: `entity.setCustomName(order.name)` and `entity.setCustomNameVisible(true)`
- Plain text format (no color codes, no formatting)
- If ArmorStand entity missing (chunk deleted, admin removed) → log warning, continue; next chunk load or entity re-spawn will have updated name (CC-03, CC-08)

#### 3.2 In Menus (OrderMenu, CommuneOrderMenu, CommuneMenu, etc.)

- Display order.name as plain text (no prefix, no suffix, no ID)
- If name is truncated by UI space → truncate with ellipsis (`...`) or wrap (implementation choice, not spec-constrained)

### 4. Concurrent Rename Handling (last-write-wins)

- If two leaders of the same order both submit renames simultaneously:
  - Both calls succeed from their perspective (both receive confirmation message)
  - Database receives both writes; **last commit wins** (no row locking, no optimistic locking in MVP)
  - One rename is silently overwritten; the overwritten player is unaware (CC-16, CC-20, noted as tech debt)
  - Behavior acceptable for MVP

### 5. Permission Checks (re-validation)

**At Anvil-open time (visual gate):**
- `OrderService.isLeader(playerUUID)` → disable button if not leader

**At Anvil-confirm time (behavioral gate — CRITICAL, CC-01):**
- `OrderService.isLeader(playerUUID)` again → reject rename if permission changed
- Detect if order no longer exists (CC-02) → reject with specific message
- **Rationale:** Player status can change between opening the Anvil and confirming (e.g., leader demoted by another player). The confirm handler must re-verify to prevent unauthorized renames.

---

## Edge Cases

| # | Corner Case | Handling |
|---|-------------|----------|
| CC-01 | Leader demoted while Anvil open, then confirms rename | Permission re-check at confirm time rejects rename. Message: `"Вы больше не лидер этого ордера"`. No DB write, no ArmorStand update. |
| CC-02 | Order deleted/disbanded while Anvil open, then confirms rename | Rename handler fetches order by ID; detects missing order; rejects rename. Message: `"Этот ордер был расформирован"`. No DB write. |
| CC-03 | ArmorStand entity missing when rename is confirmed | DB write succeeds. Entity update is skipped (no NullPointerException thrown). Warning logged. Next chunk load or entity re-spawn displays updated name. |
| CC-04 | Name is only hyphens/underscores (e.g., `---`, `___`) | Syntactically valid per charset rules; semantically meaningless. Business decision: accept as-is (no content filter in scope). Stored and displayed. |
| CC-05 | Single character name (e.g., `A`, `Я`, `1`) | Valid per character set (no minimum length constraint beyond non-empty). Accepted and persisted. |
| CC-06 | Double-click Rename button opens Anvil twice in rapid succession | In-progress rename tracking per (playerUUID, orderId): second Anvil open is ignored/rejected. Only one rename interaction per player at a time. |
| CC-07 | Player disconnects while Anvil open before confirming | Anvil close event (or connection close) treated as cancel. No partial rename stored. Name stays at previous value. |
| CC-08 | ArmorStand in unloaded chunk when rename confirmed | DB write succeeds; entity is not in memory to update. Update deferred/skipped. On chunk load, entity spawn uses latest DB value. Behavior: DB is source of truth. |
| CC-09 | Non-leader bypasses button guard via crafted packet, calls rename confirm directly | Server-side confirm handler re-verifies leader status (CC-01 rule applies). Crafted interaction rejected. No permission bypass possible. |
| CC-10 | Name has leading/trailing hyphens/underscores (e.g., `_Name_`, `-Guild`, `Order-`) | All chars valid per charset. Names stored as-is; no auto-trimming of edge hyphens/underscores. |
| CC-11 | Owner's default nickname at creation is exactly 20 chars, all valid | Full 20-char nickname set as default name. No truncation needed (≤ 20). |
| CC-12 | Owner's default nickname at creation is longer than 20 chars (possible with display names) | Truncate to first 20 characters on creation (in addition to invalid-char replacement). Owner notified of truncated name. |
| CC-13 | Rename to same name with different Unicode normalization (composed vs decomposed Cyrillic) | Treated as no-op per byte-level String.equals comparison. No DB write. No re-set of ArmorStand. |
| CC-14 | Open management menu while Anvil already open for same player | Second menu open blocked or Anvil remains active. Player cannot have two competing GUIs open simultaneously. |
| CC-15 | Open management menu immediately after rename before DB write completes | Menu shows optimistically updated in-memory name (new name). If DB write subsequently fails, rollback occurs; next menu open shows old name. |
| CC-16 | Two leaders of same order rename simultaneously, both receive success, one overwrites | Last-write-wins: database commit determines final value. Both players receive success message unaware of overwrite. Tech debt: noted for future optimistic locking. |
| CC-17 | DB write acknowledges but actual row not updated (engine state anomaly) | In-memory name diverges from DB during session. On server restart, DB value is re-read (DB is source of truth). Acceptable for MVP. |
| CC-19 | 1000 orders renamed simultaneously | No rate limit in scope; DB handles concurrent writes at column level. No application-level throttle needed. |

---

## Error Handling

| Error Scenario | Detection | Response | User-Visible Message | Recovery |
|---|---|---|---|---|
| Player is not leader | Permission check at confirm time (`OrderService.isLeader()` call) | Reject rename, log event | `"Вы больше не лидер этого ордера"` | None — player must re-request as leader |
| Order no longer exists | Order fetch by ID returns null | Reject rename, log warning | `"Этот ордер был расформирован"` | None — order disbanded |
| Name contains invalid characters | Regex / charset validation on confirm | Reject rename, close Anvil | `"Недопустимые символы. Используйте: буквы, цифры, дефис (-), подчёркивание (_)"` | Player re-opens Anvil to retry |
| Name is empty or whitespace-only (including Unicode whitespace) | Trim and length check on confirm | Reject rename, close Anvil | `"Название не может быть пустым"` | Player re-opens Anvil to retry |
| Name longer than 20 characters | Length check on confirm | Reject rename, close Anvil | `"Максимум 20 символов"` | Player re-opens Anvil to retry |
| ArmorStand entity is null/missing | Null check before `setCustomName()` call | Log warning, skip entity update, continue DB write | (no message to player; internal log only) | DB is persisted; entity updates on next load |
| Database write fails | Exception thrown by DatabaseManager | Rollback in-memory name to cached old value, close Anvil | `"Ошибка при сохранении названия. Попробуйте позже"` | On server restart, DB value re-read (source of truth); player may retry rename |
| Anvil cancelled (Escape or close button) | Anvil cancel event fired | Close Anvil, discard input | (silent, no message per AC-08) | No rename applied; player may re-open Anvil |
| Player disconnects while Anvil open | Connection close or PlayerQuitEvent | Close Anvil, discard state | (silent — player not online) | On rejoin, no rename in progress; player may re-open Anvil |

**Validation message output location:**
- Primary: action bar (above hotbar)
- Fallback: chat message (if action bar unavailable or hidden)

---

## Security Considerations

### Authentication
- Permission gate uses existing `OrderService.isLeader(playerUUID)` mechanism (same as order management)
- Player UUID obtained from Bukkit event context (trusted source, cannot be spoofed from Bukkit events)
- Crafted packets that bypass GUI button guard are defended by server-side permission re-check at confirm time (CC-09)

### Authorization
- **Rename operation:** Leader-only (owner of the order)
- **View operation:** Any player (name is public, displayed in-world and menus)
- **Defenses:**
  - Button disabled for non-leaders (visual gate)
  - Permission re-check at confirm time (behavioral gate, CC-01, CC-09)

### Data Sensitivity
- Order name is **not sensitive data** — it is public information displayed to all players
- No PII, tokens, secrets, or credentials handled
- No logging of names (only standard Bukkit event/debug logs)

### Input Validation
- **Character whitelist** (not blacklist): only A-Z, a-z, а-я, А-Я, 0-9, hyphen, underscore allowed
- **Length constraint:** max 20 characters
- **Non-empty:** at least 1 character (after trim)
- **Parameterized SQL:** all DB writes via parameterized queries (DatabaseManager), no string concatenation
- **No eval / code injection risk:** name is string data only, never executed or interpreted as code

### Concurrency
- No locking in MVP (last-write-wins per AC-20)
- Multiple simultaneous renames possible; database enforces row-level atomicity
- Noted as potential tech debt for future optimistic locking or serialization (not in scope)

---

## Dependencies

### Internal (within comminusm module)

| Component | Type | Role |
|---|---|---|
| `OrderService` | service | Read leader status via `isLeader(UUID)`; read order by ID via `getOrderById(Long)` |
| `OrderRepository` | repository | SQL CRUD for orders table; includes new `renameOrderInDatabase()` method |
| `DatabaseManager` | utility | Async parameterized SQL execution; handles `order_name` column writes |
| `ArmorStand entity system` | model | Order's in-world display entity; updated via `setCustomName()` on rename |
| `OrderMenu` / `CommuneOrderMenu` | menu | GUI integration point; Rename button added (slot TBD in implementation plan); hosts in-progress rename tracking map |
| In-progress rename tracker | data structure | `ConcurrentHashMap<UUID, Long>` mapping playerUUID → orderId; prevents double-click Anvil opens (CC-06); managed in menu/handler class |
| Bukkit `Player` API | library | Retrieve player nickname for default name assignment |
| Bukkit `PlayerQuitEvent` | event | Cleanup in-progress renames on disconnect (CC-07) |
| AnvilGUI library (or Paper API equivalent) | library | Anvil GUI widget; text input, confirm/cancel handlers |

### External (Bukkit/Paper)

| Dependency | Version | Usage |
|---|---|---|
| Bukkit / Paper API | (per project config) | `InventoryClickEvent`, `PlayerQuitEvent`, `Player.getName()`, entity APIs, action bar messaging |

### Database

| Table | Change | Description |
|---|---|---|
| `orders` | ADD COLUMN `name VARCHAR(20) NOT NULL DEFAULT 'Order'` | Store order display name |

---

## Implementation Notes

### Key Decisions

1. **No uniqueness constraint:** Multiple orders can have the same name. No UNIQUE constraint in DB. Rationale: allows flexibility, avoids rename collision logic.

2. **Byte-level string comparison:** No Unicode normalization. String.equals() used for equality checks (AC-19, CC-13). Rationale: simple, avoids normalization overhead.

3. **Last-write-wins concurrency:** No locking or optimistic locking in MVP. Concurrent renames overwrite silently (CC-16, CC-20). Noted as tech debt. Rationale: reduces complexity; acceptable for initial release.

4. **ArmorStand update on rename:** If entity is missing, warning is logged and DB write continues (CC-03). Rationale: order name is persisted in DB (source of truth); entity is display-only and can be re-created on chunk load.

5. **No confirm message on Anvil cancel:** Per AC-08, Escape/close is silent. Rationale: standard Minecraft UI behavior.

6. **Sanitization at creation only:** Default name is sanitized from player nickname at creation time. After creation, if player's nickname changes, the order's stored name does NOT auto-update (OQ-01 resolved: frozen at creation). Rationale: order name is player-controlled; nickname changes are outside order's scope.

7. **Fallback name "Order":** If sanitization results in empty/all-underscores or nickname unavailable, `"Order"` is used as fallback. Rationale: ensures no order is left without a name; allows graceful degradation if player offline at creation.

### Slot Assignment (TBD in implementation plan)

- Rename button (ANVIL material) slot location in OrderMenu / CommuneOrderMenu **not finalized in this spec**. Implementation plan will specify (e.g., "Slot 35" or relative to menu layout).
- Button should be grouped with other management buttons per existing menu design patterns.

### Anvil GUI Library

- Spec uses AnvilGUI library pattern; implementation may use Paper's native adventure-API Anvil or a plugin like AnvilGUI (depends on project dependencies).
- Spec does NOT mandate a specific library — contract is: open Anvil with pre-filled text, receive confirm/cancel events, extract text from output slot.

### Tooltips and Localization

- Tooltips and messages are in Russian (per project locale setting)
- All user-visible strings defined in this spec are in Russian (e.g., `"Только лидер может менять название"`)
- Implementation may use a message bundle / locale system; this spec fixes the strings for the `ru` locale

### Menu Auto-Close on Rename

- Per AC-09: owner's menu auto-closes after successful rename, forcing re-open to see updated name
- Rationale: avoids stale menu display; encourages player to confirm the new name visually
- Implementation: `closeInventory()` call on successful rename

### No Rate Limiting

- No cooldown or rate limit on rename operations in scope (AC-04 marked as open question)
- If implemented later, would be added to `renameOrder()` as a separate gate before permission check

---

## Unresolved Items

The following open questions from the requirements remain **unresolved** and require PO decision:

| OQ | Question | Impact | Resolution Path |
|---|---|---|---|
| OQ-01 | If owner's nickname changes after order creation, should stored name auto-update or stay frozen? | If frozen: order name diverges from owner's current identity. If auto-update: adds complexity. | **Current decision (implicit in AC-15):** name is frozen at creation time. If PO wants retroactive updates, add a separate sync/migrate feature. |
| OQ-03 | Should Anvil cancel (Escape) show a confirmation message (e.g., `"Переименование отменено"`)? | UX clarity — some players may expect feedback. | **Current decision (per AC-08):** cancel is silent (standard Minecraft Anvil behavior). If PO wants feedback, add message in implementation. |
| OQ-04 | Should rename have a cooldown (e.g., once per minute) to prevent abuse? | Prevents spam, but adds complexity and friction. | **Current decision:** no cooldown in scope. If PO wants rate limiting, add to `renameOrder()` method. |
| OQ-05 | If player UUID changes (offline-mode → online-mode migration), does order ownership and name persist? | Data integrity risk on server migration. | **Current decision:** outside spec scope. Order ownership tied to ownerUuid; if UUID changes, migration scripts must update the foreign key. Name persists separately. |

**Note:** All open questions are marked for PO review. The spec assumes the "current decision" path for each. If PO wants different behavior, update the corresponding section and re-run this spec through SystemAnalyst.

---

## Spec Alignment with Corner Cases

All **Critical** (CC-01, CC-02, CC-03) and **High** (CC-04 through CC-09) corner cases are explicitly addressed in the Edge Cases table above. Implementation MUST provide:

- Permission re-check at confirm time (CC-01)
- Graceful order-not-found handling (CC-02)
- Null-safe ArmorStand update (CC-03)
- Charset validation (CC-05, CC-09)
- Double-click prevention (CC-06)
- Disconnect handling (CC-07)
- Unloaded chunk handling (CC-08)
- Crafted packet defense (CC-09)

All other corner cases (Medium, Low) are documented for awareness but not blockers for spec approval.

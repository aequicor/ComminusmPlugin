---
genre: reference
module: comminusm
title: Technical Specification — Communes
topic: communes
status: Ready for PO sign-off
date: 2026-05-06
author: "@SystemAnalyst"
related:
  - vault/concepts/comminusm/requirements/communes.md
  - vault/concepts/comminusm/plans/communes-corner-cases.md
  - vault/reference/comminusm/test-cases/communes-test-cases.md
  - vault/reference/comminusm/spec/flag-stability.md
  - vault/reference/comminusm/spec/order-home-spawn.md
---

# Technical Specification — Communes

**Module:** comminusm
**Feature:** communes
**Status:** Ready for PO sign-off
**Date:** 2026-05-06
**Author:** @SystemAnalyst
**Requirements:** [[concepts/comminusm/requirements/communes]]

---

## 1. Overview

A **Commune** is an alliance of two or more Orders. It enables three gameplay mechanics across its member orders:

1. **Cross-order territory access** — a leader of Order A may grant a player from Order B the Member role inside Order A (cross-order membership), allowing that player to modify Order A's privates and work fronts.
2. **Commune chat** — all online players whose native order belongs to the same Commune share a dedicated commune chat channel (`/cc`), separate from global and intra-order chats.
3. **Friendly-fire protection** — players whose *native* order belongs to the same Commune do not deal damage to each other.

**Native Member subsystem is introduced as part of this feature.** The Member role (`order_members` table, `OrderMembershipService`, `OrderMembersRepository`) does not exist prior to this feature. Cross-order membership is an extension of native membership via the same `order_members` table, distinguished by the `granted_via` column (`'native'` vs `'commune'`).

**Open-Closed constraint:** the existing `Order` data class, `OrderService`, and `OrderRepository` are not modified. All new functionality is added through new components that observe existing Bukkit events and call the public read API of the Order system. `OrderService.kt` is **not modified at all** — cascade handling is achieved via `CommuneOrderDestroyListener`, which subscribes to the existing `FlagDeactivatedEvent` that `OrderService.deleteByOwner` already publishes (at lines 125 and 143 of `OrderService.kt`). This is an ideal open-closed design: zero modifications to `OrderService.kt`.

A Commune has no name, no flag, no territory, no spawn point, and no designated "commune leader". All order leaders within a commune are equal (`Addresses: AC-40, AC-42`).

---

## 2. Architecture Overview

### 2.1 New components

| Class | Package | Responsibility |
|-------|---------|---------------|
| **Native Member Subsystem** | | |
| `OrderMembersRepository` | `repository` | SQL CRUD for `order_members` table. Async reads/writes via DatabaseManager. |
| `OrderMembershipService` | `service` | High-level API: invite/accept/remove/leave native member; isNativeMember; isMemberOf; listMembers. Publishes `OrderMemberAddedEvent`, `OrderMemberRemovedEvent`. |
| `OrderMemberInvitation` | `model` | In-memory model for pending native membership invitations. SQL-persisted in `order_member_invitations` (§3.12). |
| `OrderMembersMenu` | `menu` | GUI section for viewing/managing native members. Opened from CommuneMenu or CommuneOrderMenu. |
| `CommuneOrderMenu` | `menu` | **Decorator** wrapping `OrderMenu`. Opens instead of `OrderMenu` at the `/order` entry point (analogous to `CommunePartyMenu`). Renders all original `OrderMenu` slots via delegation, plus adds one "Участники" button that opens `OrderMembersMenu`. `OrderMenu.kt` is not modified. |
| **Events** | | |
| `OrderMemberAddedEvent` | `event` | Custom Bukkit event carrying `orderId`, `playerUUID`, `grantedVia`. |
| `OrderMemberRemovedEvent` | `event` | Custom Bukkit event carrying `orderId`, `playerUUID`, `grantedVia`. |
| **Commune Subsystem** | | |
| `CommuneService` | `service` | Core commune lifecycle: create, dissolve, add/remove order, existence checks. In-memory source of truth. |
| `CrossOrderMembershipService` | `service` | Grant / revoke cross-order Member roles. Atomic in-memory write + async SQL persist. Calls `OrderMembershipService`. |
| `CommuneInvitationService` | `service` | Manage commune invitations: create, cancel, expire (500 s timer), replace. |
| `CommuneChatService` | `service` | Route `/cc` messages. Manage toggle state per player. Enforce plain-text wrapping (CC-09). |
| `FriendlyFireListener` | `listener` | Handle `EntityDamageByEntityEvent` at priority `HIGH`, `ignoreCancelled=false`. |
| `CommuneOrderDestroyListener` | `listener` | Observe existing `FlagDeactivatedEvent` (published by `OrderService.deleteByOwner`); trigger cascade commune exit (CC-01, CC-S01). Zero modifications to `OrderService.kt`. |
| `CommuneMembershipListener` | `listener` | Observe `OrderMemberRemovedEvent`; trigger cross-order recalculation (AC-25, CC-S04, CC-S06). |
| `CommunePlayerListener` | `listener` | Observe `PlayerJoinEvent`: run per-player consistency check (AC-47), deliver offline notifications (CC-14). |
| `CommuneStartupTask` | `startup` | Async task on `onEnable`: load storage, full consistency scan (AC-47), graceful-degrade on storage error (CC-05). |
| `CommuneMenu` | `menu` | Inventory GUI: displays commune member orders, management buttons (invite, leave confirmation), incoming invitation block. |
| `CommuneCommand` | `command` | Handles `/cc [text]` command: toggle mode, single-message dispatch, mute-check (CC-10). |
| `OrderCommuneInfoCommand` | `command` | Handles `/order commune <orderName>` (or extends `/order info`): displays order commune membership info to any player (AC-23). Read-only; no auth required. |
| `CommunePartyMenu` | `menu` | **Decorator** wrapping `PartyMenu`. Opens instead of PartyMenu at the `/party` entry point. Renders all original PartyMenu slots via delegation plus adds a "Коммуна" button. `PartyMenu.kt` is not modified. |

### 2.2 Dependency diagram

```
[OrderService]          (existing, NOT modified — zero changes to OrderService.kt)
[OrderRepository]       (existing, NOT modified)
        |
        | publishes (existing mechanism, lines 125 & 143 of OrderService.kt)
[FlagDeactivatedEvent] ──► [CommuneOrderDestroyListener] ──► [CommuneService cascade]
                                                          ──► [CrossOrderMembershipService cascade]

[OrderMembersRepository]  (new SQL CRUD, uses DatabaseManager)
        ^
        | uses
[OrderMembershipService]  (new, publishes OrderMember{Added|Removed}Event)
        ^
        | uses
[CrossOrderMembershipService] (new, cross-order grant/revoke via OrderMembershipService)

[OrderMemberRemovedEvent] ──► [CommuneMembershipListener] ──► AC-25 recalculation

[CommuneService]  ◄──── [CommunePartyMenu / CommuneMenu / CommuneCommand]
[CommuneInvitationService]
[CommuneChatService]
[FriendlyFireListener] ──► reads [OrderMembershipService.isNativeMember / listNativeOrders]

[CommuneOrderMenu] ──decorator──► [OrderMenu] (OrderMenu.kt NOT modified)
        | "Участники" button
        ▼
[OrderMembersMenu]
```

### 2.3 Open-closed integration points (read-only to existing Order system)

| Point | Direction | Description |
|-------|-----------|-------------|
| `OrderService.getOrderByPlayer(UUID): Order?` | Read | Determine a player's owned order (ownerUuid). |
| `OrderService.getOrderById(Long): Order?` | Read | Look up order by id. |
| `OrderService.isLeader(UUID): Boolean` | Read | Check whether a player is the owner of their order. |
| `FlagDeactivatedEvent` (observe) | Event subscription | `CommuneOrderDestroyListener` subscribes to this existing event (already published by `OrderService.deleteByOwner`). No write integration — zero modifications to `OrderService.kt`. |

---

## 3. Data Models

### 3.1 `order_members` table (new SQL table)

Persisted via `OrderMembersRepository` using `DatabaseManager`. Source of truth after startup load; in-memory cache (`ConcurrentHashMap<Long, Set<OrderMember>>` keyed by `orderId`) is the runtime source of truth (AC-46).

```
Column          SQL Type         Constraints                  Description
------          --------         -----------                  -----------
order_id        BIGINT           NOT NULL, FK → orders(id)    The order this player is a member of.
player_uuid     VARCHAR(36)      NOT NULL                     Player UUID (string form).
granted_at      TIMESTAMP        NOT NULL                     Timestamp of membership grant.
granted_via     VARCHAR(16)      NOT NULL                     'native' or 'commune'.
                                 CHECK(granted_via IN ('native','commune'))

PRIMARY KEY (order_id, player_uuid)
UNIQUE (order_id, player_uuid)   — enforced at DB level to prevent duplicates (CC-S08)
```

**Invariants:**
- `player_uuid` MUST NOT equal `Order.ownerUuid` for the same `order_id` (AC-58). Enforced at service level before insert; the DB UNIQUE constraint covers duplicate grants.
- A player may hold at most one record per `(order_id, player_uuid)` pair. Attempting to insert a second record is rejected at service level (AC-35 for native, AC-44 for commune).
- A player may hold records in multiple different orders simultaneously (AC-54).

### 3.2 OrderMember (in-memory model)

```
Field          Type       Required   Description
-----          ----       --------   -----------
orderId        Long       yes        The order this record belongs to.
playerUUID     UUID       yes        Player UUID.
grantedAt      Instant    yes        Timestamp of grant.
grantedVia     String     yes        "native" or "commune".
```

### 3.3 OrderMemberInvitation (in-memory model — native membership invitation)

Held in `OrderMembershipService` in `ConcurrentHashMap<UUID, OrderMemberInvitation>` keyed by target `playerUUID` + `orderId` composite. At most one pending invite per `(orderId, playerUUID)` pair. **SQL-persisted** in `order_member_invitations` (see §3.12). Timers re-hydrated from `expires_at` on startup (§8.2 step 3e).

```
Field              Type      Required   Description
-----              ----      --------   -----------
id                 UUID      yes        Internal invitation identifier.
orderId            Long      yes        Order the player is being invited to join as native member.
targetPlayerUUID   UUID      yes        Player being invited.
invitedByLeader    UUID      yes        UUID of the leader who sent the invitation.
expiresAt          Instant   yes        Creation time + 500 seconds (AC-55).
```

### 3.4 Commune (in-memory)

Held in `CommuneService` in `ConcurrentHashMap<UUID, Commune>`. Persisted to SQL table `communes`.

```
Field          Type             Required   Description
-----          ----             --------   -----------
id             UUID             yes        Internal commune identifier. Never shown in UI.
                                           Generated via UUID.randomUUID() (CC-18).
orderIds       Set<Long>        yes        IDs of member orders. Mutable; invariant: |orderIds| >= 1
                                           after creation; commune with 0 orders → delete (CC-02).
createdAt      Instant          yes        Timestamp of commune creation.
version        Long             yes        Monotonic counter. Starts at 0 on creation. Incremented by 1
                                           on every mutation: order join, order leave, dissolve.
                                           Used by stale-state guard in §5.4 (CC-Q3).
```

**Version increment rule:** any call to `CommuneService` that mutates `orderIds` (add order, remove order) must atomically increment `version`. The pre-mutation value is captured in menu render snapshots and compared on action (§5.4).

### 3.5 `communes` SQL table

```
Column        SQL Type      Constraints     Description
------        --------      -----------     -----------
commune_uuid  VARCHAR(36)   PRIMARY KEY     Internal commune UUID.
created_at    TIMESTAMP     NOT NULL        Creation timestamp.
```

### 3.6 `commune_orders` SQL table

```
Column        SQL Type      Constraints                 Description
------        --------      -----------                 -----------
commune_uuid  VARCHAR(36)   NOT NULL, FK → communes     Commune identifier.
order_id      BIGINT        NOT NULL, FK → orders(id)   Order identifier.

PRIMARY KEY (commune_uuid, order_id)
UNIQUE (order_id)   — enforces one commune per order (AC-05, AC-02)
```

### 3.7 CrossOrderMembership (in-memory)

Held in `CrossOrderMembershipService` in `ConcurrentHashMap<UUID, Set<CrossOrderMembership>>` keyed by `playerUUID`. These correspond to `order_members` rows where `granted_via='commune'`.

```
Field              Type      Required   Description
-----              ----      --------   -----------
playerUUID         UUID      yes        Player who holds the cross-order member role.
hostOrderId        Long      yes        The order in which the player holds the Member role.
nativeOrderId      Long      yes        The player's native order at time of grant. Used for
                                        consistency check (AC-47).
grantedByLeader    UUID      yes        UUID of the leader who granted the membership (audit).
grantedAt          Instant   yes        Timestamp of grant.
```

**Invariant:** A `CrossOrderMembership` is valid if and only if the player still has at least one native order (ownerUuid OR granted_via='native') within the same commune as `hostOrderId`. Any record violating this is auto-revoked on startup (AC-47) and on player join.

### 3.8 `commune_invitations` SQL table

```
Column            SQL Type      Constraints                  Description
------            --------      -----------                  -----------
invitation_uuid   VARCHAR(36)   PRIMARY KEY                  Internal invitation UUID.
from_order_id     BIGINT        NOT NULL, FK → orders(id)    Inviting order.
to_order_id       BIGINT        NOT NULL, FK → orders(id)    Target order.
commune_uuid      VARCHAR(36)   NOT NULL, FK → communes      Commune the target is invited to.
target_leader_uuid VARCHAR(36)  NOT NULL                     Leader UUID at invite creation time.
expires_at        TIMESTAMP     NOT NULL                     Expiry timestamp (AC-43).
```

**Uniqueness:** only one active invitation per `to_order_id`. Enforced at both service level (`CommuneInvitationService` cancel-then-insert) and DB level:

```
UNIQUE (to_order_id)   — enforces one pending invite per target order at DB level (CC-Q11)
```

**Performance index:**

```
INDEX idx_invitations_from_order ON commune_invitations(from_order_id)
```

This index supports the "cancel all pending invitations sent by leaving order" query in §6.5 step 9. Without it, cancellation on leave would require a full table scan over `commune_invitations`. The combined filter `(commune_uuid, from_order_id)` is not used — the cancellation query filters only by `from_order_id`, so a single-column index is sufficient. If future queries filter by both columns, a composite index `(commune_uuid, from_order_id)` may be added.

The DB UNIQUE constraint is the final safety net; service-level replace (AC-27) runs before insert to avoid constraint violations during normal flow. If a constraint violation does occur, `CommuneInvitationService` treats it as "already invited" and returns the existing invitation.

### 3.9 Invitation (in-memory)

```
Field              Type      Required   Description
-----              ----      --------   -----------
id                 UUID      yes        Internal invitation identifier.
fromOrderId        Long      yes        Order that sent the invitation.
targetOrderId      Long      yes        Order that received the invitation.
communeId          UUID      yes        Commune the target order is being invited to join.
targetLeaderUUID   UUID      yes        UUID of the target order's leader at invite creation (AC-42).
expiresAt          Instant   yes        Creation time + 500 seconds (AC-43).
version            Long      yes        Snapshot of the commune's `version` at the moment the invitation
                                        was created. Captured in menu render; stale-state guard in §5.4
                                        rejects if commune version has since changed (CC-Q3).
```

### 3.10 CommuneChatToggleState (in-memory only)

`HashSet<UUID>` in `CommuneChatService` — set of playerUUIDs with toggle mode active. Not persisted; reset on plugin reload/restart (CC-12).

### 3.11 OfflineNotificationQueue (SQL-persistent)

Stored in table `commune_offline_notifications`. Delivered and cleared on `PlayerJoinEvent` (CC-14).

```
Column          SQL Type      Constraints   Description
------          --------      -----------   -----------
id              BIGINT        PK, AUTO      Row id.
player_uuid     VARCHAR(36)   NOT NULL      Player to notify on next login.
message_key     VARCHAR(128)  NOT NULL      Message template key (e.g. "commune.left").
parameters      TEXT          nullable      JSON map of template parameters.
created_at      TIMESTAMP     NOT NULL      Timestamp for ordering/audit.
```

### 3.12 `order_member_invitations` SQL table (native membership invitations — CC-Q6)

Native member invitations are **SQL-persisted** (not in-memory-only). This ensures consistency with the startup re-hydration model (§8.2 step 3e) and prevents invitation loss on reload. Timers are re-hydrated from `expires_at` on startup.

```
Column              SQL Type      Constraints                  Description
------              --------      -----------                  -----------
invitation_id       VARCHAR(36)   PRIMARY KEY                  Internal invitation UUID.
order_id            BIGINT        NOT NULL, FK → orders(id)    Order the player is being invited to.
target_player_uuid  VARCHAR(36)   NOT NULL                     Player being invited.
invited_by          VARCHAR(36)   NOT NULL                     UUID of the leader who sent the invite.
expires_at          TIMESTAMP     NOT NULL                     Expiry timestamp (creation + 500 s).

UNIQUE (order_id, target_player_uuid)   — at most one pending invite per (order, player) pair
```

**Lifecycle:** created on invite send; deleted on accept, decline, or expiry. If the `orders` row for `order_id` is deleted, all related rows are cascade-deleted via the FK constraint.

---

## 4. Commands

### 4.1 `/cc [text]`

| Attribute | Value |
|-----------|-------|
| **Command** | `/cc` |
| **Permission** | None (all players; commune membership enforced at runtime) |
| **Arguments** | Optional: `text` — the message to send |

**Variants:**

| Input | Behaviour |
|-------|-----------|
| `/cc <text>` (non-empty after trim) | Single-message mode: delivers message to commune chat once; does not change toggle state. |
| `/cc` (no arguments or whitespace-only after trim) | Toggle mode: if NOT in toggle → activate (AC-18b); if IN toggle → deactivate (AC-18c). CC-21: whitespace-only treated as no-argument. |

**Pre-conditions checked in order:**
1. Player is a commune member (native order is in a commune) → else: "Вы не состоите ни в одной коммуне" (AC-19).
2. Player is not muted (CC-10): check via `AsyncPlayerChatEvent` cancellation or permission node. If muted → reject with appropriate message.
3. For single-message: text length ≤ 256 characters after trim → else: "Сообщение слишком длинное" (CC-08).
4. User text wrapped as `Component.text(rawInput)` — never parsed as MiniMessage (CC-09).

**Response format (MiniMessage server-side template):**
```
[Коммуна] <playerName>: <plainText>
```
Delivered to all online players whose native order is in the same commune.

### 4.2 Commune Menu (GUI — not a slash command)

Opened via:
- The "Коммуна" button in `CommunePartyMenu` (`/party` flow — `CommunePartyMenu` is the decorator wrapping `PartyMenu`).
- Direct trigger by `CommuneCommand` if needed (secondary path, out of scope for base implementation).

### 4.3 `/order commune <orderName>`

| Attribute | Value |
|-----------|-------|
| **Command** | `/order commune <orderName>` |
| **Permission** | None — available to all players |
| **Arguments** | Required: `orderName` — the name of the order to query |
| **Handler** | `OrderCommuneInfoCommand` |

**Behavior:**

| Condition | Response |
|-----------|----------|
| `orderName` not found | "Ордер не найден" |
| Order exists, not in a commune | "Ордер «X» не состоит ни в одной коммуне" |
| Order exists, in a commune | Chat message listing: order name, owner username, commune membership = true, names of all peer orders in the same commune |

**Data access:** `CommuneService.getCommuneByOrder(orderId)` — in-memory cache only, no SQL queries at request time. Startup guard: `startupComplete = false` → "Система коммун инициализируется, попробуйте снова через несколько секунд".

`Addresses: AC-23`

### 4.4 `/order members` — REMOVED

The `/order members` sub-command is **not implemented**. It was previously described as one access path for `OrderMembersMenu`; this has been superseded by the `CommuneOrderMenu` decorator approach (§5.8). `OrderMembersMenu` is now accessible via:

- Button "Участники" in `CommuneOrderMenu` (always available for the order leader and native members of the order).
- Button "Участники ордера" in `CommuneMenu` for communards (existing path — §5.6).

Removing the `/order members` command eliminates the need for a separate command registration and avoids modifying `OrderMenu.kt`.

---

## 5. GUI Contracts

### 5.1 CommunePartyMenu — decorator pattern

`CommunePartyMenu(delegate: PartyMenu)` implements the same interface/open API as `PartyMenu`. When `/party` is invoked, the DI registry/command handler is wired to instantiate `CommunePartyMenu` rather than `PartyMenu` directly. `PartyMenu.kt` is not modified.

`CommunePartyMenu` renders:
- All original PartyMenu inventory slots by calling `delegate.onClick(event)` / `delegate.open(player)` for all non-commune slots.
- An additional "Коммуна" button in a dedicated slot.

"Коммуна" button state matrix:

| Player role | Order in commune? | Button state | Action |
|-------------|------------------|--------------|--------|
| Order leader | Yes | Enabled | Opens `CommuneMenu` (full member view — AC-13) |
| Order leader | No | Enabled | Opens `CommuneMenu` empty state with "Создать коммуну" (AC-14) |
| Non-leader (rank member) | Yes | Enabled | Opens `CommuneMenu` in **read-only mode** (AC-13, AC-29) |
| Non-leader (rank member) | No | Disabled | Lore: "Коммуну создаёт лидер ордера" (AC-14b) |

`Addresses: U-02, AC-13, AC-14, AC-14b, AC-29`

### 5.2 CommuneMenu states

| Player State | Menu State | Visible Elements |
|-------------|------------|-----------------|
| Order leader, order in commune | **Member view (leader)** | List of member orders; "Invite order" button; "Leave commune" button (confirmation screen — CC-06); no commune name. |
| Non-leader, order in commune | **Member view (read-only)** | List of member orders; no management buttons (AC-29). |
| Order leader, order NOT in commune | **Empty state** | "Ваш ордер не состоит ни в одной коммуне"; "Создать коммуну" button (CC-07, AC-14). |
| Non-leader, order NOT in commune | Inaccessible — button disabled in `CommunePartyMenu` (AC-14b). | — |
| Leader, pending commune invitation exists | **Invitation block** | Above member list (or alone): "Входящее приглашение" with inviting commune's order list; "Принять" / "Отклонить" buttons (AC-30). Non-leaders do NOT see this block (AC-38). |

### 5.3 Leave Commune Confirmation Screen (CC-06)

- Slot A (confirm): red dye — "Подтвердить выход"; lore: "Все cross-order права будут отозваны".
- Slot B (cancel): barrier — "Отмена".

Clicking Slot A triggers leave flow. Clicking Slot B or closing inventory cancels. `Addresses: CC-06`

### 5.4 Stale-state guard (AC-34, AC-45)

When a player clicks any action button in `CommuneMenu` or invitation block, the handler re-validates server state against the snapshot captured at menu render time. The snapshot contains:

- `invitationId: UUID` — the specific invitation the player is acting on.
- `communeVersion: Long` — value of `Commune.version` at render time.

On action: re-read `Commune.version` from `CommuneService`. If `communeVersion != Commune.version` → close menu, send "Приглашение устарело" (or "Данные коммуны изменились, откройте меню снова"). Same pattern for invitation actions: re-check `invitationId` is still present in `CommuneInvitationService`.

Follows the `OrderMenu` invalidation pattern. Addresses CC-Q3.

### 5.5 Cross-Order Member Management sub-view

Accessible from `CommuneMenu` for order leaders. Displays:
- List of players from partner orders (in the same commune).
- "Добавить cross-order member" button per player not yet granted.
- "Удалить cross-order member" button per player already granted.

Operation serialization: per-order `ReentrantLock` (CC-11); see Section 7.

### 5.6 OrderMembersMenu — access paths (CC-Q10)

`OrderMenu.kt` is **not modified**. `OrderMembersMenu` is opened via two paths:

- **(a) From `CommuneMenu`** — button "Участники ордера", visible to commune members of the order (existing path).
- **(b) From `CommuneOrderMenu`** — button "Участники" (always visible to the order leader and native members of the order, regardless of commune membership). `CommuneOrderMenu` is the decorator wrapper around `OrderMenu` (§5.8).

The `/order members` slash command is **not registered** (see §4.4). `CommuneOrderMenu` provides the primary access path for day-to-day use.

**Rationale:** integrating `OrderMembersMenu` directly into `OrderMenu` would require modifying `OrderMenu.kt`, violating the open-closed constraint (§1). The decorator `CommuneOrderMenu` achieves the same UX without touching `OrderMenu.kt`.

### 5.7 Order Commune Info Display — read-only command (AC-23)

**Command:** `/order commune <orderName>` (alternatively, `/order info <orderName>` if that command exists — extend via open-closed wrapper; do not modify existing command handler).

**Class:** `OrderCommuneInfoCommand` (new class).

**Authorization:** none — available to any player, no role or auth checks.

**Behavior:**

| Condition | Response |
|-----------|----------|
| Order `<orderName>` does not exist | "Ордер не найден" |
| Order exists, not in a commune | "Ордер «X» не состоит ни в одной коммуне" |
| Order exists, in a commune | Display: order name, owner username, commune membership flag = true, list of all order names (or UUIDs) that are members of the same commune |

**Data source:** `CommuneService.getCommuneByOrder(orderId)` — reads from in-memory cache. No SQL queries at request time.

**Startup guard:** `startupComplete = false` → "Система коммун инициализируется, попробуйте снова через несколько секунд".

`Addresses: AC-23`

### 5.8 CommuneOrderMenu — decorator pattern (AC-60)

`CommuneOrderMenu(delegate: OrderMenu)` implements the same interface/open API as `OrderMenu`. When the `/order` command is invoked (or when `OrderMenu` would normally be opened), the DI registry/command handler is wired to instantiate `CommuneOrderMenu` rather than `OrderMenu` directly. `OrderMenu.kt` is **not modified**.

`CommuneOrderMenu` renders:
- All original `OrderMenu` inventory slots by calling `delegate.onClick(event)` / `delegate.open(player)` for all non-member slots.
- An additional "Участники" button in a dedicated slot.

"Участники" button behaviour:

| Situation | Button state | Action |
|-----------|-------------|--------|
| Player is the order leader | Enabled | Opens `OrderMembersMenu` (full management view) |
| Player is a native member of the order | Enabled | Opens `OrderMembersMenu` (read-only view, as per AC-60) |
| Player has no order membership | Not shown / Disabled | — |

`Addresses: AC-60, U-02 (OrderMenu extension via decorator)`

`OrderMembersMenu` provides:

- "Пригласить участника" button — visible to leader only (AC-51).
- List of native members — visible to all members and communards (AC-60).
- "Исключить" button per native member — visible to leader only (US-14).

`OrderMenu.kt` is not modified at all — no button registry lookup, no inheritance, no modification. This is a hard rule (CC-Q10). Any future integration into `OrderMenu` requires a separate architectural decision.

---

## 6. Business Logic

### 6.1 Create Commune (US-01)

1. Verify player is a leader of an order (`OrderService.isLeader(UUID)`) (AC-03).
2. Verify player's order is NOT already in a commune (AC-02).
3. Atomically create `Commune{id=randomUUID(), orderIds={orderId}, createdAt=now()}`.
4. Register commune in `CommuneService` in-memory map.
5. Insert into `communes` and `commune_orders` tables asynchronously.
6. Send success notification to the leader.

### 6.2 Invite Order to Commune (US-02)

1. Verify caller is a leader of an order in the commune (AC-40, AC-50).
2. Verify `fromOrderId != targetOrderId` (AC-37).
3. Verify `targetOrderId` is not already in any commune (AC-05, AC-06).
4. If an active invitation already exists for `targetOrderId` from this commune: replace it (AC-27). Cancel existing timer. Notify caller of replacement.
5. Create `Invitation{id=randomUUID(), fromOrderId, targetOrderId, communeId, targetLeaderUUID=currentLeaderOf(targetOrder), expiresAt=now()+500s}`.
6. Register invitation in `CommuneInvitationService`. Schedule expiry timer for 500 s.
7. Persist invitation to `commune_invitations` asynchronously.
8. Send invitation notification to `targetLeaderUUID`.
9. Broadcast invitation-sent notification to all online commune members (AC-48).

### 6.3 Accept Commune Invitation (US-03)

**Thread guarantee (CC-Q12):** the entire sequence of steps 1–9 executes on the **main server thread**. If the entry path is via an async context (e.g., a `InventoryClickEvent` dispatched through an async handler), the handler must bounce back to main thread via `Bukkit.getScheduler().runTask(plugin, ...)` before step 1. Single-threaded execution on main thread eliminates race windows between steps 3 and 4 — no concurrent thread can change commune membership between the validation check and the lock acquisition.

1. Verify caller is `targetLeaderUUID` of the invitation (AC-42).
2. Re-validate invitation is still active (not expired, not replaced — AC-45). On failure → close menu, "Приглашение устарело".
3. Verify `targetOrderId` is still not in any commune (AC-26). On failure → cancel invitation, "Ваш ордер уже состоит в другой коммуне".
4. Lock commune management queue (CC-13).
5. Add `targetOrderId` to commune's `orderIds`. Increment `commune.version`. Insert into `commune_orders`.
6. Cancel the expiry timer; remove invitation record; delete from `commune_invitations`.
7. Schedule async persist.
8. Broadcast "Ордер X вступил в коммуну" to all online commune members (AC-07). Recipient list fixed at step 4 start (AC-39).
9. Release lock.

### 6.4 Decline Commune Invitation (US-03)

1. Verify caller is `targetLeaderUUID` of the invitation.
2. Remove invitation record (AC-08). Cancel timer. Delete from `commune_invitations`.
3. Notify caller (decline confirmation).

### 6.5 Leave Commune (US-04)

1. Verify caller is a leader of an order in a commune (AC-50).
2. Show leave confirmation screen (CC-06). Wait for explicit confirm click.
3. On confirm: lock commune management queue (CC-13).
4. Verify order is still in commune (idempotency — AC-28). If not → "Вы уже не состоите в коммуне".
5. Execute cascade cross-order rights revocation: revoke all `CrossOrderMembership` records where `nativeOrderId == leavingOrderId` OR `hostOrderId == leavingOrderId` (AC-10, AC-24).

   **Cascade batch mode (CC-Q5 — O(N²) prevention):** Before iterating removals, set `CrossOrderMembershipService.inCascadeMode = true` (thread-local flag). While `inCascadeMode` is `true`, each individual `removeMember` call **suppresses** the `recalculateCrossOrderRights` trigger. After all removals complete:
   - Reset `inCascadeMode = false`.
   - Call `recalculateCrossOrderRights` **once** per unique `playerUUID` encountered during the cascade. This reduces N×N triggers to N triggers (one per affected player).

   Per-record steps remain:
   a. Remove record from in-memory map (source of truth — AC-46).
   b. Delete `order_members` row (granted_via='commune') via `OrderMembershipService.removeMember`.
   c. If player is online: rights effective immediately (AC-33). If GUI open: next action is rejected (AC-49).
   d. Schedule async persist.

6. Remove `leavingOrderId` from commune's `orderIds`. Increment `commune.version`. Delete from `commune_orders`.
7. If `orderIds` is now empty → dissolve commune (Section 6.7).
8. Otherwise: broadcast "Ордер X покинул коммуну" to remaining online commune members.
9. Cancel any pending commune invitations sent by `leavingOrderId`. Broadcast revocation notification to remaining commune members (AC-48, AC-36).
10. Schedule async persist.
11. Release lock.

### 6.6 Order Destroyed — Cascade (CC-01, CC-S01)

Triggered by `CommuneOrderDestroyListener` observing the **existing** `FlagDeactivatedEvent` (already published by `OrderService.deleteByOwner` at lines 125 and 143 of `OrderService.kt`). **Zero modifications to `OrderService.kt`** — this is the open-closed ideal.

Cascade executes **after** physical order deletion (Variant A: post-delete cascade). The order entity is already removed from `OrderService` storage when the event arrives at the listener.

Cascade order (CC-S01 — mandatory ordering):

- **Step 0 (outside our code's scope):** `OrderService.deleteByOwner` physically deletes the order and publishes `FlagDeactivatedEvent(orderId)`. This step is handled entirely by the existing `OrderService.kt` — no modification.

**Cascade-mode scope (CC-Q5 iter-2 fix):** Set `CrossOrderMembershipService.inCascadeMode = true` at the very start of this cascade (before step 1) and reset it only at the end (after step 5). This ensures that native member removals in step 3 do not re-trigger `recalculateCrossOrderRights` independently. After step 5, call `recalculateCrossOrderRights` once per unique `playerUUID` affected across all steps 1–3. (Steps within §6.5 that also set/reset `inCascadeMode` are no-ops in this context because the flag is already set at a higher scope.)

1. `CommuneOrderDestroyListener` receives the event; reads `orderId` from `FlagDeactivatedEvent`.
2. If the destroyed order is in a commune (by in-memory cache `CommuneService.getCommuneByOrder(orderId)`) → execute commune-leave cascade (Section 6.5 steps 5–11), treating the destroyed order as the leaving order. This revokes all cross-order rights in both directions. `inCascadeMode` is already `true` from above — do not reset within step 2.
3. Delete all native `order_members` records for this order (`granted_via='native'` rows) via `OrderMembershipService.removeMemberSilently` (Internal API). Notify online players of order dissolution (AC-59). `inCascadeMode` is still `true` — individual removals do not trigger `recalculateCrossOrderRights`.
4. If commune has 0 orders after step 2 → dissolve commune (Section 6.7).
5. Reset `inCascadeMode = false`. Call `recalculateCrossOrderRights` once per unique `playerUUID` collected during steps 2–3. Log the cascade at INFO level.

**Cascade error handling (CC-Q7):**
- (a) Each individual `removeMember` call inside the cascade is wrapped in a try/catch block. A caught exception logs ERROR with the affected `playerUUID` and `orderId`, then **continues** to the next record (best-effort cascade — never aborts mid-cascade on a single-record failure).
- (b) After the cascade completes, the startup-style consistency check (AC-47) on next startup will detect and clean up any records that were not successfully removed due to errors.
- (c) **Critical failure exception:** if `DatabaseManager` is unavailable (connection dead, pool exhausted), a `DatabaseUnavailableException` aborts the entire cascade immediately. Log at CRITICAL level: "Cascade aborted — DatabaseManager unavailable during FlagDeactivatedEvent (order destroy cascade) for orderId=X. Admin intervention required." In-memory state may be partially updated; AC-47 on next startup will reconcile.

`Addresses: CC-01, CC-S01, CC-Q7`

### 6.7 Dissolve Commune (AC-11, AC-24, AC-41)

Triggered when `orderIds` becomes empty (last order left or cascade reduced to 0).

1. Revoke ALL remaining `CrossOrderMembership` records for all orders that were in the commune (AC-24). For each: remove in-memory record; call `OrderMembershipService.removeMember`; delete from `order_members`.
2. Cancel all pending commune invitations for this commune. No notification (AC-36 — senders no longer in commune).
3. Reset toggle mode for all online players whose native order was in this commune (CC-15, CC-20). Send "Режим чата коммуны выключен: ваш ордер больше не в коммуне".
4. Queue offline notifications for all members who were offline at dissolution time (CC-14).
5. Increment `commune.version` **before** removing from in-memory map. This ensures any concurrent reader that holds a stale snapshot detects a version mismatch (§5.4) and receives "commune dissolved" rather than a null-reference response.
6. Remove commune from in-memory map. Delete from `communes` and `commune_orders`.

### 6.8 Invite Player as Native Member (US-12)

1. Verify caller is a leader of the order (`OrderService.isLeader(UUID)`) (AC-51).
2. Verify `targetPlayerUUID != callerUUID` (AC-52: leader cannot be own member).
3. Verify `targetPlayerUUID` is not already a native member of the order (AC-53): check `order_members` where `(order_id, player_uuid, granted_via='native')`.
4. Verify no existing pending native invitation for `(orderId, targetPlayerUUID)`.
5. Create `OrderMemberInvitation{id=randomUUID(), orderId, targetPlayerUUID, invitedByLeader=callerUUID, expiresAt=now()+500s}` (AC-55).
6. Register invitation in `OrderMembershipService` in-memory map. Schedule expiry timer.
7. Persist to `order_member_invitations` table (§3.12) asynchronously (CC-Q6).
8. Notify `targetPlayerUUID`.

### 6.9 Accept / Decline Native Member Invitation (US-16)

**Accept:**
1. Verify caller is the `targetPlayerUUID` of the invitation.
2. Re-validate invitation is still active (not expired, not replaced).
3. Verify `callerUUID` is not `Order.ownerUuid` of the target order (AC-58).
4. Insert `order_members(order_id, player_uuid, granted_at=now(), granted_via='native')`.
5. Update in-memory cache atomically.
6. Publish `OrderMemberAddedEvent(orderId, playerUUID, grantedVia='native')`.
7. Cancel invitation timer; remove invitation record.
8. Notify leader and new member.

**Decline:**
1. Remove invitation record. Cancel timer.
2. Notify caller.

### 6.10 Remove Native Member — Voluntary Leave (US-13)

1. Verify `callerUUID` has a `granted_via='native'` record in the target order's `order_members`.
2. Delete record from `order_members`. Update in-memory cache.
3. Publish `OrderMemberRemovedEvent(orderId, playerUUID, grantedVia='native')`.
4. Trigger cross-order recalculation (Section 6.13) for `playerUUID`.
5. Notify order leader.
6. If player is online: rights revoked immediately (AC-57).

`Addresses: AC-25, AC-57`

### 6.11 Exclude Native Member — Leader Action (US-14)

1. Verify caller is the leader of the order (`OrderService.isLeader(callerUUID)`).
2. Verify `targetPlayerUUID` has a `granted_via='native'` record in this order.
3. Delete record from `order_members`. Update in-memory cache.
4. Publish `OrderMemberRemovedEvent(orderId, targetPlayerUUID, grantedVia='native')`.
5. Trigger cross-order recalculation (Section 6.13) for `targetPlayerUUID`.
6. Notify `targetPlayerUUID` if online (AC-57).

`Addresses: AC-25, AC-57`

### 6.12 Cross-Order Rights Recalculation — `recalculateCrossOrderRights(playerUUID)` (AC-25, CC-S02, CC-S04, CC-S06)

Called whenever a player's native membership changes (leave/exclude events, or order destroyed cascade).

**Algorithm (snapshot-iteration pattern — Q6):**

1. Acquire **write lock** on the player-keyed map in `CrossOrderMembershipService` (the `ConcurrentHashMap<UUID, Set<CrossOrderMembership>>` keyed by `playerUUID`).
2. Create an immutable snapshot of the player's current `Set<CrossOrderMembership>` via `.toList()`. This is the working copy; the original collection is not iterated directly.
3. Release the write lock immediately after snapshot creation. The lock is held only long enough to copy — it does not block concurrent `grantCrossOrderMembership` calls on other orders during iteration.
4. Collect `nativeOrders(playerUUID)` = all orders where player is `ownerUuid` OR has `granted_via='native'` in `order_members`.
5. Iterate the **snapshot** list. For each `CrossOrderMembership` record:
   a. Identify `hostOrderId` and look up which commune it belongs to (if any).
   b. Check if `nativeOrders` contains at least one order that is also in the same commune as `hostOrderId`.
   c. If YES → cross-order access is valid; no change.
   d. If NO → revoke the cross-order membership via **Internal API** (`removeMemberSilently` — see §6 API contract below). This call does not publish events and does not re-enter `recalculateCrossOrderRights`. Operations per invalid record:
      - Remove `CrossOrderMembership` record from in-memory map.
      - Delete `order_members(hostOrderId, playerUUID, granted_via='commune')` row.
      - If player is online: revoke immediately (AC-33).
      - Schedule async persist.
6. Log recalculation result at DEBUG level.

**Guarantees:** (a) The snapshot ensures no `ConcurrentModificationException` during iteration. (b) Releasing the write lock before iteration (step 3) means `grantCrossOrderMembership` on unrelated orders is not blocked for the duration of the scan. (c) All revocations for a given `playerUUID` in one recalculation call complete before control returns.

`Addresses: AC-25, CC-S02, CC-S04, CC-S06, Q6`

### 6.13 Grant Cross-Order Membership (US-07)

1. Verify caller is a commune leader.
2. Verify `targetPlayerUUID`'s native order (ownerUuid OR granted_via='native') is a member of the same commune as caller's order (AC-16).
3. Verify `targetPlayerUUID` is NOT already a native member of caller's order (AC-35): check `granted_via='native'`.
4. Verify `targetPlayerUUID` is NOT already a cross-order member of caller's order (AC-44): check `granted_via='commune'`.
5. Capture `snapshotVersion = commune.version` (the version read during the pre-lock checks in steps 1–4).
6. Acquire per-order write lock for `callerOrderId` (CC-11).
7. **Re-validate all conditions under lock (combined locking guard):**
   a. Re-check conditions 2–4 (commune membership of target, native-member exclusion, cross-order duplicate).
   b. **Commune snapshot version check:** read `currentVersion = commune.version`. If `currentVersion != snapshotVersion` → release lock, abort with "Данные коммуны изменились — попробуйте снова" (retry-or-error). This protects against concurrent commune-leave by `targetPlayerUUID`'s native order between the pre-lock read (steps 2–4) and lock acquisition. The version is incremented on every commune mutation (§3.4), so any intervening join or leave invalidates the snapshot.
8. Insert `order_members(callerOrderId, targetPlayerUUID, granted_at=now(), granted_via='commune')` into DB asynchronously.
9. Update `CrossOrderMembership` in-memory map atomically. Visible immediately (CC-04, AC-46).
10. Release lock.
11. Confirm to caller.

`Addresses: CC-04, CC-11`

### 6.14 Revoke Cross-Order Membership (US-08)

1. Verify caller is a commune leader.
2. Verify `targetPlayerUUID` has a `CrossOrderMembership` for caller's order.
3. Remove from in-memory map (immediate — AC-33, AC-46).
4. Delete `order_members` row (granted_via='commune') asynchronously.
5. If player is online and has open privates GUI: next action rejected (AC-49).
6. Confirm to caller.

### 6.15 Commune Chat — single message (AC-18)

1. Pre-conditions: player is commune member, not muted, text ≤ 256 chars, non-empty after trim.
2. Wrap text as plain-text `Component` via `Component.text(rawInput)` — never `MiniMessage.deserialize` (CC-09).
3. Prepend `[Коммуна] <playerName>: ` using MiniMessage for the prefix template only.
4. Broadcast to all online players whose native order is in the same commune.
5. Player's chat mode unchanged.

`Addresses: CC-08, CC-09, CC-10`

### 6.16 Commune Chat — toggle mode (AC-18b, AC-18c)

- `/cc` (no arg) when NOT in toggle → add to `CommuneChatToggleState`. Send "Вы в режиме чата коммуны. Введите /cc для выхода" (AC-18b).
- `/cc` (no arg) when IN toggle → remove from set. Send "Вы вышли из режима чата коммуны".
- While in toggle: `io.papermc.paper.event.player.AsyncChatEvent` (lowest priority, after cancel check) intercepts messages, routes to commune chat, cancels original event (CC-Q15, Paper 1.19+ API).
- Toggle reset: when order leaves commune or commune dissolves (CC-15, CC-20).

### 6.17 Friendly-Fire Protection (US-10, AC-20, AC-21, AC-22, AC-32)

Handled in `FriendlyFireListener`:

1. `EntityDamageByEntityEvent`, priority `HIGH`, `ignoreCancelled=false`.
2. Resolve attacker: if `Projectile` with `Player` shooter, use shooter. Never re-enable already-cancelled events.
3. Verify both attacker and victim are `Player` instances.
4. Determine native orders of attacker: all orders where player is `ownerUuid` OR has `granted_via='native'` in `order_members`.
5. Determine native orders of victim: same.
6. If there exist at least one native order of attacker and at least one native order of victim that are both in the same commune → cancel event. Send "Это ваш союзник" to attacker. Return.
7. Cross-order member status (`granted_via='commune'`) is NOT used in friendly-fire determination (AC-22).
8. Otherwise: do not modify the event (PvP proceeds — AC-21).

`Addresses: CC-03, AC-22`

---

### 6.18 `OrderMembershipService` — API Contract: Public vs Internal (Q1, Q4)

`OrderMembershipService` exposes two distinct API tiers with different guarantees:

#### (a) Public API — event-publishing, main-thread-only, startup-guarded

Used by command handlers, menu actions, and Bukkit event listeners.

| Method | Behaviour |
|--------|-----------|
| `addMember(orderId, playerUUID, grantedVia): Result` | Validates `startupComplete`. Runs on main thread. Publishes `OrderMemberAddedEvent`. |
| `removeMember(orderId, playerUUID): Result` | Validates `startupComplete`. Runs on main thread. Publishes `OrderMemberRemovedEvent`. Triggers `recalculateCrossOrderRights`. |
| `isNativeMember(orderId, playerUUID): Boolean` | Validates `startupComplete`. Read-only. Safe from any thread (read lock). |
| `isMemberOf(orderId, playerUUID): Boolean` | Validates `startupComplete`. Read-only. |
| `listMembers(orderId): List<OrderMember>` | Validates `startupComplete`. Read-only. |

**Rules:** all Public API methods throw or return an error result if `startupComplete = false`. They MUST NOT be called from `onDisable` or `CommuneStartupTask`. They publish Bukkit events; calling them from an async context requires bouncing to the main thread first.

#### (b) Internal API — silent, startup/shutdown only, no events, no guards

Used exclusively by `CommuneStartupTask` (startup consistency check, §8.2 step 3) and `onDisable` flush (§14). Never called from command handlers or Bukkit event listeners.

| Method | Behaviour |
|--------|-----------|
| `removeMemberSilently(orderId, playerUUID)` | Does NOT check `startupComplete`. Does NOT publish events. Does NOT trigger `recalculateCrossOrderRights`. Modifies in-memory map and schedules async SQL deletion. |
| `addMemberSilently(orderId, playerUUID, grantedVia)` | Does NOT check `startupComplete`. Does NOT publish events. Modifies in-memory map only (used during load phase). |

**Visibility:** Internal API methods are `internal` in Kotlin (package-private equivalent) — not accessible from outside the `service` package. Callers outside the package MUST use Public API. This is enforced at compile time, not by convention.

**Cascade usage:** `recalculateCrossOrderRights` (§6.12) also uses `removeMemberSilently` when revoking invalid cross-order records (step 5d), because it runs mid-cascade and must not re-publish events that would re-trigger the cascade.

### 6.19 `CrossOrderMembershipService` — API Contract: Public vs Internal (Q1, Q4)

Mirrors the pattern of §6.18.

#### (a) Public API — event-publishing, main-thread-only, startup-guarded

| Method | Behaviour |
|--------|-----------|
| `grantCrossOrderMembership(callerOrderId, targetPlayerUUID): Result` | Validates `startupComplete`. Main thread. Publishes member events via `OrderMembershipService.addMember`. |
| `revokeCrossOrderMembership(callerOrderId, targetPlayerUUID): Result` | Validates `startupComplete`. Main thread. Publishes member events via `OrderMembershipService.removeMember`. |

#### (b) Internal API — silent, no guards, no events

| Method | Behaviour |
|--------|-----------|
| `removeMemberSilently(orderId, playerUUID)` | Does NOT check `startupComplete`. Does NOT publish events. Used in cascade batch removals (§6.5 step 5, §6.6 step 1), in `recalculateCrossOrderRights` (§6.12 step 5d), and in `onDisable` flush. |

**Cascade scope:** `inCascadeMode` (ThreadLocal flag, see §7 and §6.5) gates whether `removeMember` Public API calls suppress post-removal triggers. Internal API (`removeMemberSilently`) never triggers post-removal recalculation regardless of `inCascadeMode`.

---

## 7. Concurrency and Serialization (CC-04, CC-11, CC-13)

| Operation type | Serialization mechanism |
|---------------|------------------------|
| Commune management (create, join, leave, dissolve) | Main server thread. BukkitScheduler-dispatched event handlers are single-threaded on main thread. `CommuneService` operations do not run from async contexts. |
| Accept Commune Invitation | Main server thread; if entry via async handler, bounce via `Bukkit.getScheduler().runTask(plugin, ...)` before step 1 (§6.3, CC-Q12). |
| Native member grant/remove | Per-order `ReentrantReadWriteLock` keyed by `orderId`. **Write lock** held for the insert-to-DB + in-memory-update step (both primary and secondary index updated atomically inside one write-lock block). **Read lock** held by `FriendlyFireListener` for secondary-index reads. |
| Cross-order membership grant/revoke | Per-order `ReentrantReadWriteLock` keyed by `hostOrderId`. **Write lock** held for in-memory write + async DB dispatch. |
| Cross-order recalculation (`recalculateCrossOrderRights`) | Called on main thread from `CommuneMembershipListener` (which handles `OrderMemberRemovedEvent` on main thread). Suppressed per-record during cascade batch mode; called once per affected player after cascade (CC-Q5). |
| Cascade batch mode (`inCascadeMode`) | `ThreadLocal<Boolean>` in `CrossOrderMembershipService`. All cascade operations execute on the main thread; `ThreadLocal` is used to allow future off-main-thread paths (e.g., `onDisable`, startup) to call Internal API methods without interfering with a cascade in progress on main thread. While set, individual `removeMember` Public API calls suppress `recalculateCrossOrderRights`. Reset after cascade; one recalculate call per unique affected `playerUUID`. Off-main-thread paths (`onDisable` flush, startup consistency check) do **not** use event-publishing Public API — they use Internal API methods (`removeMemberSilently`) and therefore never touch `inCascadeMode`. |
| SQL writes | `OrderMembersRepository.scheduleWrite()` dispatches via `BukkitScheduler.runTaskAsynchronously`. In-memory is authoritative; no blocking wait on SQL. |
| Invitation timers | `BukkitScheduler.runTaskLater`; expiry callback on main thread. |
| Startup consistency check | `CommuneStartupTask` runs async. In-memory loaded synchronously before async check. Plugin fully operational only after `startupComplete = true` (§8.2, CC-Q1). |

### Lock Acquisition Order Rule (Q2)

When both `CrossOrderMembershipService` and `OrderMembershipService` write locks must be held simultaneously, they MUST always be acquired in this fixed order:

1. `CrossOrderMembershipService` write lock (outer lock).
2. `OrderMembershipService` write lock (inner lock, per `orderId`).

**Never reverse this order.** Although all current cascade and grant/revoke paths execute on the single-threaded main server thread (making deadlock structurally impossible today), this rule is codified explicitly to protect against future changes that could introduce off-main-thread invocation of these services. Any new code path that acquires both locks MUST follow this ordering. Violating this rule is a code-review blocker.

**Code review checklist entry:** when reviewing any method that acquires more than one `ReentrantReadWriteLock` from these two services, verify the acquisition order matches: Cross → Order.

### FriendlyFireListener — Read-Only Invariant (Q3)

`FriendlyFireListener` is a **strictly read-only consumer** of the membership state. The following operations are **forbidden** inside any `EntityDamageByEntityEvent` handler or any method called from it:

- `OrderMembershipService.addMember` / `removeMember`
- `CrossOrderMembershipService.grantCrossOrderMembership` / `revokeCrossOrderMembership`
- `CommuneService.createCommune` / `addOrder` / `removeOrder` / `dissolve`
- Any method that modifies `order_members` table or in-memory membership maps.

The handler only reads the secondary index (`playerUUID → Set<orderId>`) under a read lock and cancels the damage event if both players share a commune. It MUST NOT escalate to a write lock, publish events, or mutate any state.

**Code review checklist entry:** any change to `FriendlyFireListener` that introduces a call to a non-read method is a CRITICAL review blocker.

### In-memory index structure for FriendlyFireListener (CC-Q8)

`OrderMembershipService` maintains **two synchronized maps**:

- **Primary index:** `ConcurrentHashMap<Long, Set<OrderMember>>` keyed by `orderId` — used for all membership queries by order.
- **Secondary index:** `ConcurrentHashMap<UUID, Set<Long>>` keyed by `playerUUID` — maps a player to all `orderId`s they are a native member of.

**Locking strategy (CC-Q8 iter-2 fix — `ReentrantReadWriteLock`):** Both primary and secondary indices are protected by a `ReentrantReadWriteLock` per `orderId` (replacing the plain `ReentrantLock` from earlier iterations). The rationale: `FriendlyFireListener` is a hot path (called on every damage event) and performs only reads. Concurrent readers should not block each other.

- **Write lock** (`writeLock().lock()` / `writeLock().unlock()`): acquired by every `addMember` / `removeMember` call. Both primary and secondary index updates happen inside a single write-lock critical section, guaranteeing atomic visibility of the pair.
- **Read lock** (`readLock().lock()` / `readLock().unlock()`): acquired by `FriendlyFireListener` for its secondary-index lookup (`playerUUID → Set<orderId>`). Multiple concurrent damage events can proceed in parallel.

The secondary index is never written without also writing the primary — both updates are always inside the same write-lock block. A reader from `FriendlyFireListener` observes either the state fully before or fully after the write; never a partial update.

`FriendlyFireListener` uses the secondary index for O(1) `playerUUID → Set<orderId>` lookup instead of a full scan across all orders. This reduces per-damage-event cost from O(N) (N = number of orders) to O(1) for player lookup, then O(K) where K = number of native orders for that player (typically 1–2).

`Addresses: CC-04, CC-11, CC-13, CC-Q5, CC-Q8, CC-Q12`

---

## 8. Persistence and Startup (AC-46, AC-47, CC-02, CC-05)

### 8.1 SQL schema

All commune and member data is stored in SQL via `DatabaseManager` (same pattern as `WorkdaysRepository`, `OrderRepository`, `WorkFrontRepository`). Tables:

- `order_members` — see Section 3.1
- `communes` — see Section 3.5
- `commune_orders` — see Section 3.6
- `commune_invitations` — see Section 3.8
- `commune_offline_notifications` — see Section 3.11
- `order_member_invitations` — see Section 3.12

Schema DDL is applied via `DatabaseManager` migration mechanism on `onEnable`.

### 8.2 Startup sequence

`CommuneStartupTask` executes on `onEnable`. **The plugin is not operational** (all commune service calls are blocked) until the startup completes and sets `startupComplete = true`. Any player attempt to use commune functions before `startupComplete = true` receives: "Система коммун инициализируется, попробуйте снова через несколько секунд" (CC-Q1). This check is enforced at the entry point of every public method in `CommuneService`, `CommuneInvitationService`, `CrossOrderMembershipService`, `OrderMembershipService`, and `CommuneCommand`.

1. **Schema migration (synchronous):** apply any pending DDL migrations via `DatabaseManager`.

2. **Load phase (async):**
   a. Load all rows from `communes`, `commune_orders`, `commune_invitations`, `order_members`, `commune_offline_notifications`, `order_member_invitations`. **Soft limit: 100,000 rows per table** (CC-Q14). If any table exceeds 100k rows, log WARN and load in batches of 10,000 rows.
   b. **Partial load failure handling (CC-Q9):** each table is loaded independently. If loading of any individual table raises a `SQLException`:
      - Log WARN identifying the specific table that failed (e.g., "Failed to load commune_invitations — table may be incomplete").
      - Set `storageLoadFailed = true`.
      - `storageLoadFailed = true` **blocks all commune operations** (both read and write) until admin resolves. The partial data already loaded is discarded — no partially-loaded state is used. All in-memory collections are reset to empty.
      - If the main `communes` or `commune_orders` load fails → log ERROR (higher severity than WARN, as core data is unavailable).
      - **Step 3 (consistency check) is completely skipped** when `storageLoadFailed = true`. Running a consistency check against incomplete data would produce false positives and corrupt valid records. Step 3 can only execute after a successful full reload (see step 4 below).
      - All commune services respond with "Система коммун находится в деградированном состоянии, обратитесь к администратору" (see §8.3) until `storageLoadFailed` is cleared.
   c. On complete success of all tables: proceed to step 3.

3. **Consistency check phase (async, after load):**
   a. For each `Commune`: verify each `orderId` exists. **Batch fetch strategy:** collect all unique `orderId` values across all loaded communes, then execute a single SQL query `SELECT id FROM orders WHERE id IN (...)` for all unique IDs at once (or delegate to `OrderService` if it exposes a batch API). Use the resulting in-memory set for existence checks — do not call `OrderService.getOrderById(id)` per row. At the expected scale of ≤ 500 orders in communes, a single `SELECT … IN (500 IDs)` is one SQL round-trip. Remove phantom order refs from in-memory commune map. If `orderIds.isEmpty()` after purge → delete commune from in-memory and SQL. Log WARN for each removed phantom ref (CC-02).
   b. For each `CrossOrderMembership` (granted_via='commune'): verify `nativeOrders(playerUUID)` contains at least one order in the same commune as `hostOrderId`. If not → call **`removeMemberSilently`** (Internal API — §6.18, §6.19); delete row (AC-47). **Do NOT call Public API `removeMember`** here — the startup guard (`startupComplete = false` at this point) would block it, and events must not be published during the load/consistency phase.
   c. For each `order_members` row (granted_via='native'): verify `orderId` still exists. If not → delete row via `removeMemberSilently` (Internal API — same rationale as step 3b).
   d. Re-hydrate commune invitation timers from `expires_at`.
   e. Re-hydrate native member invitation timers from `expires_at` (from `order_member_invitations` table, §3.12).
   f. Log completion counts at INFO (CC-17).
   g. Set `startupComplete = true`. Log INFO "Commune system ready."

4. **Recovery from persistent storage failure (CC-Q2):** there is no admin runtime command for reload or sync (`/admin commune reload` and `/admin commune sync` are **out of scope** — see Out of Scope in requirements). When `storageLoadFailed = true`, the only recovery path is a **server restart**. On restart, `CommuneStartupTask` re-runs the full startup sequence (steps 1–3). If the underlying storage issue is resolved before restart, the load phase will succeed and `startupComplete = true` will be set after the consistency check. This restart-triggered re-run is the AC-47 consistency check path.

**Expected scale (informational — CC-Q14):** ≤ 50 communes, ≤ 500 orders in communes, ≤ 5,000 cross-order memberships. The 100k soft limit is a safety guard, not the designed operating range.

`Addresses: AC-46, AC-47, CC-02, CC-05, CC-Q1, CC-Q9, CC-Q14`

### 8.3 Storage failure degradation (CC-05)

When `storageLoadFailed = true` (which also implies `startupComplete = false`):
- Plugin operates with empty commune set — no in-memory data is used.
- All commune operations (read and write) are blocked.
- On any player attempt to use any commune function: "Данные коммун временно недоступны, обратитесь к администратору".
- Log ERROR once per access attempt (not per-message spam); suppress repeats for the same playerUUID within the same tick.
- **Recovery path: server restart only.** There is no runtime admin reload command (out of scope). On restart, `CommuneStartupTask` re-runs the full startup sequence. If the underlying storage issue has been resolved, the load phase succeeds and the consistency check (AC-47) runs in full, setting `startupComplete = true`.

`Addresses: CC-05, CC-Q1, CC-Q9`

### 8.4 Async write with retry (AC-46)

`OrderMembersRepository` and commune SQL write paths:
1. Dispatch async via `BukkitScheduler.runTaskAsynchronously`.
2. Attempt SQL write (prepared statement, parameterized — no string concatenation).
3. On `SQLException`: log WARN, schedule retry after 5 s, max 3 retries.
4. **On persistent failure (all 3 retries exhausted) (CC-Q2):**
   - Log ERROR with full details: failed operation type, affected entity IDs, timestamp.
   - Send an in-game alert to all online players with the `commune.admin` permission node: "Commune SQL write failed permanently for [operation]. In-memory state is now diverged from SQL. Server restart required."
   - **Divergence situation**: in-memory state remains authoritative; SQL is stale. After server restart, `CommuneStartupTask` will load the stale SQL, potentially missing recent mutations. This is a **degraded state requiring a server restart**.
   - **Recovery strategy: server restart only.** No admin runtime reload command exists (out of scope). On restart, `CommuneStartupTask` re-runs the full startup sequence including the AC-47 consistency check, which reconciles any divergence. Manual SQL correction of the affected rows before restart may optionally be performed to recover lost mutations.
   - There is no "auto-heal" — the divergence persists until the server is restarted.

`Addresses: AC-46, CC-Q2`

---

## 9. Edge Cases

| # | Corner Case | Addresses | Expected Behavior |
|---|------------|-----------|-------------------|
| CC-01 | Ордер уничтожается (deleteByOwner) пока состоит в коммуне | CC-01 (Critical) | `OrderService.deleteByOwner` publishes existing `FlagDeactivatedEvent` (zero changes to `OrderService.kt`). `CommuneOrderDestroyListener` triggers cascade Section 6.6: commune-leave first (steps 5–11 of §6.5), then native member cleanup via Internal API. Order is CC-S01-compliant: (1) revoke cross-order both directions, (2) delete native member records, (3) commune dissolved if 0 orders remain. |
| CC-02 | Старт: коммуна с 0 ордеров или ссылкой на несуществующий ордер | CC-02 (Critical) | Startup consistency check (Section 8.2 step 3a): phantom refs purged, empty communes deleted, WARN logged. No admin action required. |
| CC-03 | Другой плагин отменяет/принудительно применяет урон через `EntityDamageByEntityEvent` | CC-03 (Critical) | `FriendlyFireListener` at `HIGH`, `ignoreCancelled=false`. Never re-enables cancelled events; only adds its own cancellation for ally pairs. Documented limitation: if another plugin re-enables damage after `HIGH`, protection may not apply. |
| CC-04 | Cross-order права выданы в тике N; проверка прав в том же тике | CC-04 (Critical) | In-memory `CrossOrderMembership` map updated before returning from grant operation. Any subsequent read in the same tick sees the new state. SQL write is async and non-blocking. |
| CC-05 | Постоянное хранилище повреждено при старте | CC-05 (Critical) | Graceful degradation (Section 8.3): empty state, feature blocked with user notification, ERROR logged. Plugin does not crash. |
| CC-S01 | `deleteByOwner` выполняется пока ордер в коммуне и имеет нативных участников | CC-S01 (Critical) | `FlagDeactivatedEvent` triggers `CommuneOrderDestroyListener`. Cascade order enforced (§6.6): (1) commune-leave logic (Section 6.5 steps 5–11) — revoke all cross-order rights both directions; (2) delete all `order_members` native rows via `removeMemberSilently`; (3) dissolve commune if 0 orders remain. Atomic on main thread. `Addresses: CC-S01` |
| CC-S02 | Игрок owner ордера X + native member ордера Y + cross-order member ордера Z; все в одной коммуне; ордер X выходит из коммуны | CC-S02 (Critical) | `recalculateCrossOrderRights(playerUUID)` called. Player still has native order Y in the same commune → cross-order Z is preserved. Cross-order is revoked only when player has NO native order in the same commune as hostOrderId. `Addresses: CC-S02` |
| CC-06 | Лидер случайно нажимает «Выйти из коммуны» | CC-06 (High) | Leave confirmation screen required (Section 5.3). Leave executes only after explicit confirm click. |
| CC-07 | Лидер (не в коммуне) открывает CommuneMenu | CC-07 (High) | Empty-state view (Section 5.2): message + "Создать коммуну" button. No member list or invitation list. |
| CC-08 | `/cc <текст>` длиннее 256 символов | CC-08 (High) | Rejected with "Сообщение слишком длинное". Not silently truncated. |
| CC-09 | `/cc <текст>` содержит MiniMessage-теги или §-коды | CC-09 (High) | User text wrapped as `Component.text(rawInput)`. Never passed through MiniMessage parser. §-codes are literal characters. |
| CC-10 | Замьюченный игрок пишет через `/cc` | CC-10 (High) | `CommuneCommand` checks mute status before routing. Rejected with same feedback as normal muted chat. Known limitation: full cross-plugin mute API integration is best-effort via `AsyncPlayerChatEvent.isCancelled()`. `Addresses: U-04` |
| CC-11 | Двойной клик «Добавить cross-order member» до завершения первой операции | CC-11 (High) | Per-order `ReentrantReadWriteLock` write lock (Section 7): second click waits for write lock, then re-validates conditions (§6.13 step 7a) and sees AC-44 "уже member" → rejected. UNIQUE constraint on `order_members` at DB level also prevents duplicates. |
| CC-12 | Администратор выполняет `/reload` плагина | CC-12 (High) | `onDisable`: flush in-memory state to SQL synchronously; close all open menus; reset toggle states with notification; log WARN "Plugin reload detected: commune state flushed." `onEnable`: full startup sequence. Invitation timers re-hydrated from `expires_at`. |
| CC-13 | Два лидера одновременно: один приглашает, другой выходит | CC-13 (High) | Commune management executes on main thread (Section 7). In single-tick race: processed in arrival order. Invite from already-left order: rejected at step 1 of Section 6.2. |
| CC-14 | Игрок входит; его ордер вышел из коммуны пока он был оффлайн | CC-14 (High) | `CommunePlayerListener` on `PlayerJoinEvent` delivers queued `commune_offline_notifications`. After delivery: rows deleted. One-time notification. |
| CC-15 | Игрок в `/cc` toggle; ордер выходит из коммуны | CC-15 (High) | Toggle reset immediately during leave/dissolve processing (Sections 6.5, 6.7). Notification sent to player if online. |
| CC-S03 | Игрок получил нативное приглашение в ордер (US-12) И коммунальное приглашение (US-02) одновременно | CC-S03 (High) | Два типа приглашений независимы и отображаются раздельно: нативные в `OrderMenu/OrderMembersMenu`, коммунальные в `CommuneMenu`. Они не конфликтуют (AC-56). `Addresses: CC-S03` |
| CC-S04 | Игрок — native member ордера X; ордер X в коммуне с Y; лидер Y добавил игрока как cross-order в Y; игрок добровольно выходит из X; но игрок — также native member ордера Z в той же коммуне | CC-S04 (High) | `OrderMemberRemovedEvent` triggers `recalculateCrossOrderRights(playerUUID)`. Player still has native order Z → cross-order Y preserved. Cross-order Y revoked only if Z also not in that commune. `Addresses: CC-S04` |
| CC-S05 | Ордер X удаляется (deleteByOwner); игрок — native участник X; у игрока открыт OrderMenu | CC-S05 (High) | `FlagDeactivatedEvent` triggers `CommuneOrderDestroyListener` cascade, which deletes all native member records via Internal API. Online player notified (AC-59). Any open menu (OrderMenu, CommuneOrderMenu, PartyMenu) is invalidated per AC-34 pattern on next interaction. No forced GUI close (consistent with AC-49). `Addresses: CC-S05` |
| CC-S06 | Лидер исключает native member M из ордера X; M также native member ордера Z в той же коммуне; M имеет cross-order в ордере Y | CC-S06 (High) | `OrderMemberRemovedEvent` triggers `recalculateCrossOrderRights(playerUUID)`. Since M still has native order Z in the commune → cross-order Y preserved. Only if M has no other native orders in the commune → cross-order Y revoked. `Addresses: CC-S06` |
| CC-Q1 | Игрок вызывает commune-функцию во время асинхронной загрузки `CommuneStartupTask` | CC-Q1 (Critical) | `startupComplete = false` while startup is in progress. All public methods in commune services check this flag at entry. Response: "Система коммун инициализируется, попробуйте снова через несколько секунд". Set `startupComplete = true` only after full load + consistency check (§8.2 step 3g). |
| CC-Q2 | Все 3 retry async-сохранения исчерпаны; in-memory авторитетен; SQL stale | CC-Q2 (Critical) | Log ERROR. Notify online admins (`commune.admin` permission) in-game. Recovery: **server restart only** (нет admin runtime reload-команды — out of scope). При рестарте `CommuneStartupTask` загружает SQL-состояние (изменения после сбоя могут быть потеряны); AC-47 consistency check reconciles расхождения. Опционально: ручное исправление SQL до рестарта. `Addresses: CC-Q2` |
| CC-Q5 | N ордеров в коммуне выходят каскадом; каждый вызывает `recalculateCrossOrderRights` → O(N²) | CC-Q5 (High) | `inCascadeMode` thread-local flag подавляет per-record trigger. После cascade — один `recalculateCrossOrderRights` per unique `playerUUID` (§6.5 step 5, §7). |
| CC-Q7 | Ошибка во время Order Destroyed cascade при недоступном DatabaseManager | CC-Q7 (High) | Single-record failures: best-effort (log ERROR + continue). `DatabaseUnavailableException`: abort entire cascade, log CRITICAL, require admin. AC-47 при следующем старте reconciles. |
| CC-Q8 | FriendlyFireListener вызывается на каждый damage event; O(N) scan для N ордеров | CC-Q8 (High) | Secondary index `ConcurrentHashMap<UUID, Set<Long>>` keyed by playerUUID обеспечивает O(1) lookup. Обновляется синхронно с primary при каждом add/remove. |
| CC-Q9 | Partial load failure: `communes` loaded, но `commune_invitations` упал | CC-Q9 (High) | `storageLoadFailed = true`, все коллекции сброшены в empty, все операции заблокированы. Log WARN с указанием провалившейся таблицы. Recovery: **server restart** — `CommuneStartupTask` re-runs full startup sequence after underlying storage issue is resolved. |
| CC-Q12 | Accept Commune Invitation вызывается из async-контекста | CC-Q12 (High) | Вся последовательность §6.3 steps 1–9 выполняется на main thread. Если вход через async handler — обязательный bounce via `Bukkit.getScheduler().runTask(plugin, ...)` перед step 1. |
| CC-R3-Q1 | Dissolve commune: клиент держит устаревший snapshot пока коммуна удаляется | HIGH | `commune.version` инкрементируется в §6.7 step 5 **до** удаления из in-memory map. Любой reader с устаревшим version получает mismatch → "коммуна расформирована" вместо null-reference. |
| CC-R3-Q2 | Order Destroyed cascade: native member removals (step 2) повторно запускают `recalculateCrossOrderRights` пока `inCascadeMode` уже сброшен после §6.5 | HIGH | `inCascadeMode = true` выставляется в начале всего §6.6 cascade, сбрасывается только после step 5. Весь cascade (steps 1–2) подавляет per-record recalculate. Один финальный recalculate per unique playerUUID после step 5. |
| CC-R3-Q3 | FriendlyFireListener читает secondary index одновременно с mutation `addMember`/`removeMember` | HIGH | `ReentrantReadWriteLock` на per-order уровне: FriendlyFireListener берёт read lock, мутации берут write lock. Несколько concurrent damage events не блокируют друг друга. Ни один read не видит частичный update. |
| CC-R3-Q4 | Consistency check (§8.2 step 3) запускается при `storageLoadFailed = true` — работает на неполных данных | MEDIUM | Step 3 полностью пропускается при `storageLoadFailed = true`. Запускается только после успешного server restart — `CommuneStartupTask` выполняет steps 1+2+3 полностью при старте. |
| CC-R3-Q5 | Concurrent commune-leave между pre-lock проверкой (§6.13 steps 2–4) и acquire write lock (step 6) | HIGH | Commune snapshot version захватывается до lock acquisition (step 5). Под write lock (step 7b): `commune.version` перечитывается и сравнивается со snapshot. Version mismatch → abort с "Данные коммуны изменились — попробуйте снова". |
| CC-R3-Q6 | "Cancel pending invitations sent by leaving order" (§6.5 step 9) — full table scan по `commune_invitations` | MEDIUM | INDEX `idx_invitations_from_order ON commune_invitations(from_order_id)` обеспечивает index scan вместо full scan. |
| CC-R3-Q7 | Startup consistency check (§8.2 step 3a) вызывает `OrderService.getOrderById` в цикле — O(N) round-trips | MEDIUM | Batch fetch: все уникальные order_id из всех communes собираются, затем один SQL `SELECT id FROM orders WHERE id IN (...)`. O(1) round-trips вместо O(N). |

---

## 10. Error Handling

| Error Scenario | Handler | User-visible Message |
|---------------|---------|---------------------|
| Non-leader tries to manage commune | `CommuneService` / `CommuneMenu` | "У вас нет прав на это действие" |
| Order already in commune — create attempt | `CommuneService.createCommune` | "Ваш ордер уже состоит в коммуне" |
| Invite: target in another commune | `CommuneInvitationService` | "Указанный ордер уже состоит в другой коммуне" |
| Invite: target in same commune | `CommuneInvitationService` | "Указанный ордер уже в вашей коммуне" |
| Invite: target is own order | `CommuneInvitationService` | "Нельзя пригласить собственный ордер" |
| Accept commune invite: invitation stale | `CommuneInvitationService` + menu | "Приглашение устарело" — menu closes/refreshes |
| Accept commune invite: order joined another commune | `CommuneService` | "Ваш ордер уже состоит в другой коммуне" |
| Leave: idempotent duplicate call | `CommuneService` | "Вы уже не состоите в коммуне" |
| Grant cross-order: commune version changed between pre-lock check and lock acquisition | `CrossOrderMembershipService` | "Данные коммуны изменились — попробуйте снова" |
| Add cross-order: target not in commune | `CrossOrderMembershipService` | "Игрок не является участником ордеров этой коммуны" |
| Add cross-order: target is native member | `CrossOrderMembershipService` | "Игрок уже является участником вашего ордера" |
| Add cross-order: already cross-order member | `CrossOrderMembershipService` | "Игрок уже добавлен как cross-order участник" |
| Native invite: self-invite (leader) | `OrderMembershipService` | "Вы не можете быть участником своего ордера — вы его владелец" |
| Native invite: target already native member | `OrderMembershipService` | "Игрок уже является участником вашего ордера" |
| Accept native invite: player is ownerUuid | `OrderMembershipService` | "Вы не можете стать участником собственного ордера" |
| Chat: player not in commune | `CommuneCommand` | "Вы не состоите ни в одной коммуне" |
| Chat: message > 256 chars | `CommuneCommand` | "Сообщение слишком длинное" |
| Chat: player is muted | `CommuneCommand` | Standard mute message (best-effort) |
| Chat: whitespace-only text | `CommuneCommand` | "Пустое сообщение" |
| Storage load failure on startup | `CommuneStartupTask` | Server log ERROR + first-use: "Данные коммун временно недоступны, обратитесь к администратору" |
| SQL write failure (async) | Repository | Log WARN + retry up to 3 times. No user-visible message unless all retries fail (log ERROR). |
| Commune system not yet initialized (`startupComplete=false`) | All commune service entry points | "Система коммун инициализируется, попробуйте снова через несколько секунд" |
| Async write — all retries failed (permanent divergence) | Repository | Log ERROR. In-game alert to `commune.admin` players. Recovery: server restart (AC-47 reconciles on next startup). |
| Cascade abort — DatabaseManager unavailable | `CommuneOrderDestroyListener` | Log CRITICAL. In-memory partially updated; AC-47 reconciles on next startup. |
| Partial startup load failure (one table failed) | `CommuneStartupTask` | Log WARN with table name. `storageLoadFailed=true`. All collections reset. All operations blocked. |
| `onDisable` flush timeout (5 s exceeded) | `onDisable` | Log WARN "partial flush". Startup AC-47 reconciles. |

---

## 11. Security Considerations

- **Authentication:** All commune operations require a live `Player` object (Bukkit event context). No unauthenticated access path.
- **Authorization:** Leader-only operations (create commune, invite, leave, manage cross-order members, invite/exclude native member) verified by `OrderService.isLeader(playerUUID)` at the start of every operation. Member/communar operations (commune chat, friendly-fire) verified by membership check.
- **Input sanitization (CC-09):** User-provided chat text is NEVER parsed through MiniMessage or any component deserializer. Only `Component.text(rawInput)` is used. Prevents injection of clickable commands, hover events, or arbitrary formatting via `/cc`.
- **SQL injection prevention:** All SQL in `OrderMembersRepository` and commune repositories uses parameterized prepared statements. No string concatenation with user input. Ever.
- **AC-58 enforcement:** Owner/member mutual exclusion is enforced at service level before any `order_members` insert. DB UNIQUE constraint provides secondary defense against duplicates.
- **Cross-order escalation guard:** `CrossOrderMembershipService` re-validates commune membership under `ReentrantReadWriteLock` (write lock) before writing to both in-memory and DB. Double-grant impossible.
- **Data sensitivity:** No PII beyond player UUIDs and order identifiers. SQL tables contain no tokens, passwords, or secrets.
- **Event priority (CC-03):** `FriendlyFireListener` at `HIGH`. Never re-enables cancelled events; only cancels.
- **No admin bypass:** No administrative commands in scope. Cross-order and native membership cannot be granted by non-leaders.

---

## 12. Dependencies

### 12.1 Internal — existing Order system (read-only)

| API | Required For | Notes |
|-----|-------------|-------|
| `OrderService.getOrderByPlayer(UUID)` | Order lookup | Returns null if no owned order |
| `OrderService.getOrderById(Long)` | Startup consistency check, invite validation | Returns null for non-existent |
| `OrderService.isLeader(UUID)` | Authorization gate | |
| `FlagDeactivatedEvent` (subscribe) | Order destroy cascade trigger | Existing event published by `OrderService.deleteByOwner`. `CommuneOrderDestroyListener` subscribes. Zero modifications to `OrderService.kt`. |

### 12.2 Internal — new components (introduced by this feature)

| Component | Notes |
|-----------|-------|
| `OrderMembersRepository` | New SQL CRUD for `order_members` |
| `OrderMembershipService` | New high-level member API; publishes member events |
| `OrderMemberAddedEvent` / `OrderMemberRemovedEvent` | Published by `OrderMembershipService` |
| `CommuneOrderMenu` | New decorator wrapping `OrderMenu`; adds "Участники" button |

### 12.3 Internal — other features

| Feature | Dependency |
|---------|-----------|
| `flag-stability` | Flags identify territories; cross-order members access privates/fronts tied to flag-based territory. No direct API call from commune code. |
| `privates-orders-fronts` | Cross-order members interact via the Member role in `order_members`. No direct call from commune code. |
| `PartyMenu` | `CommunePartyMenu` wraps `PartyMenu` via decorator. `PartyMenu.kt` is not modified. The entry point for `/party` is wired to `CommunePartyMenu` via DI/command registration in `ComminusmPlugin.onEnable`. |
| `OrderMenu` | `OrderMembersMenu` is a new separate menu. `OrderMenu.kt` is **not modified at all** (CC-Q10). `CommuneOrderMenu` wraps `OrderMenu` via decorator (§5.8). `OrderMembersMenu` is opened via the "Участники" button in `CommuneOrderMenu` or the "Участники ордера" button in `CommuneMenu`. No `/order members` sub-command is registered (see §4.4). |

**`ComminusmPlugin.onEnable` wiring (CC-Q4):** registering the decorator menus and new commands requires the following wiring lines in `ComminusmPlugin.onEnable`:

1. Wire the `/party` entry point to `CommunePartyMenu` (instead of `PartyMenu`).
2. Wire the `/order` entry point to `CommuneOrderMenu` (instead of `OrderMenu`) — adds "Участники" button.
3. Register `CommuneOrderDestroyListener` as a Bukkit listener for `FlagDeactivatedEvent` (standard Bukkit listener registration — no modification to `OrderService.kt`).
4. Register `OrderCommuneInfoCommand` for `/order commune` (§4.3 / §5.7).

No `/order members` command is registered (see §4.4). No admin runtime commune commands are registered (`/admin commune reload` and `/admin commune sync` are out of scope). No other changes to `ComminusmPlugin.kt` are made. This is not a violation of open-closed — it is the standard wiring location for new command handlers and listeners in a Paper plugin.

### 12.4 External / Bukkit API

**Target Paper version: 1.19+** (CC-Q15). This plugin targets Paper API 1.19 or newer. Features and events available only on Paper (not vanilla Bukkit/Spigot) are acceptable dependencies.

| API | Usage | Notes |
|-----|-------|-------|
| `EntityDamageByEntityEvent` | Friendly-fire protection | |
| `io.papermc.paper.event.player.AsyncChatEvent` | Toggle-mode chat interception; mute-check (CC-10) | **Modern Paper API (1.19+).** Use `AsyncChatEvent` (Paper). `AsyncPlayerChatEvent` (legacy Bukkit) is used only if a compatibility shim for pre-1.19 Paper builds is explicitly required — document in implementation notes if so. Default: `AsyncChatEvent`. |
| `PlayerJoinEvent` | Per-player consistency check (AC-47), offline notification delivery (CC-14) | |
| `InventoryClickEvent` | CommuneMenu, OrderMembersMenu interactions | |
| `PlayerQuitEvent` | Supplemental order-destroy detection if needed | |
| `BukkitScheduler.runTaskAsynchronously` | SQL writes, startup consistency check | |
| `BukkitScheduler.runTaskLater` | Invitation expiry timers | |
| `DatabaseManager` | SQL connection; schema migration; parameterized queries | |

### 12.5 Resolved former unresolved items

| Former ID | Resolution |
|-----------|-----------|
| U-01 (OrderDissolvedEvent) | Resolved: `CommuneOrderDestroyListener` subscribes to the **existing** `FlagDeactivatedEvent` already published by `OrderService.deleteByOwner` (lines 125, 143). No wrapper class needed. `OrderService.kt` is not modified. `OrderLifecycleService` and `OrderDestroyedEvent` are **not introduced**. |
| U-02 (PartyMenu extension) | Resolved: `CommunePartyMenu` decorator pattern (Section 5.1). PartyMenu.kt is not modified. Entry point `/party` wired to `CommunePartyMenu` via DI/command registration. |
| U-03 (isNativeMember) | Resolved: `OrderMembershipService.isNativeMember(orderId, playerUUID)` is introduced as part of this feature (not taken from an existing service). |
| U-04 (mute-check) | Resolved as best-effort: check `AsyncPlayerChatEvent.isCancelled()` before routing in toggle mode; for `/cc` direct command, check mute via permission node if available. Documented as known limitation. |

---

## 13. Resolved Former Unresolved Items (iteration 2)

| # | Item | Resolution |
|---|------|-----------|
| U-05 | OrderMenu button registry existence | Resolved (CC-Q10): `OrderMembersMenu` is **not** integrated into `OrderMenu`. `OrderMenu.kt` is not modified. `OrderMembersMenu` is opened via `/order members` command or a button in `CommuneMenu`. No button registry lookup needed. |
| U-06 | Native member invitation persistence | Resolved (CC-Q6): `OrderMemberInvitation` is **SQL-persisted** in `order_member_invitations` table (§3.12). Timers re-hydrated from `expires_at` on startup (§8.2 step 3e). |

**All former unresolved items are now resolved. No open unresolved items remain.**

---

## 15. Resolved Former Unresolved Items (iteration 3 — CCR TECHNICAL loop)

| # | Question (CCR iter-2) | Sev | Resolution |
|---|----------------------|-----|-----------|
| R3-Q1 | Version increment в dissolve path | HIGH | §6.7 step 5 добавляет явный `commune.version++` перед удалением из in-memory map. §9 Edge Cases: CC-R3-Q1. |
| R3-Q2 | Cascade re-trigger в §6.6 | HIGH | `inCascadeMode = true` охватывает весь §6.6 cascade (до step 1 — после step 5). Native member removals (step 2) подавляются. Один финальный recalculate per playerUUID. §9: CC-R3-Q2. |
| R3-Q3 | Visibility/atomicity primary+secondary index | HIGH | Per-order lock заменён на `ReentrantReadWriteLock`. FriendlyFireListener — read lock; мутации — write lock. Оба index обновляются внутри одного write-lock блока. §7 таблица обновлена. §9: CC-R3-Q3. |
| R3-Q4 | Skipped step 3 при storageLoadFailed | MEDIUM | §8.2 step 2b явно указывает: step 3 полностью пропускается при `storageLoadFailed = true`. Recovery — server restart: `CommuneStartupTask` выполняет steps 1+2+3 полностью. §9: CC-R3-Q4. |
| R3-Q5 | Re-validate commune membership под per-order lock | HIGH | §6.13 step 5 захватывает `snapshotVersion`; step 7b под write lock сравнивает текущий `commune.version` со snapshot; mismatch → abort. §9: CC-R3-Q5. §10 error table обновлена. |
| R3-Q6 | Index на commune_invitations(from_order_id) | MEDIUM | §3.8 добавлен `INDEX idx_invitations_from_order ON commune_invitations(from_order_id)` с обоснованием. §9: CC-R3-Q6. |
| R3-Q7 | OrderService.getOrderById — SQL или in-memory | MEDIUM | §8.2 step 3a уточнён: batch fetch — один `SELECT id FROM orders WHERE id IN (...)` для всех уникальных order_id. O(1) round-trips. §9: CC-R3-Q7. |

**All 7 CCR iteration-3 questions resolved. No open unresolved items remain.**

---

## 14. Implementation Notes

- **`order_members` is the single table** for both native and cross-order memberships. `granted_via` column distinguishes them. This allows one consistent read path for "is this player a member of this order?" checks.
- **Native member invitations are SQL-persisted** in `order_member_invitations` (§3.12). U-06 is resolved. Timers re-hydrated from `expires_at` on startup.
- **Commune storage is SQL** (not YAML), consistent with `OrderRepository`, `WorkdaysRepository`, `WorkFrontRepository` patterns in this project.
- **`recalculateCrossOrderRights(playerUUID)`** must be called whenever native membership changes: on leave (US-13), on exclusion (US-14), and on order destruction cascade (CC-S01). It runs on the main thread; it is not dispatched async.
- **`onDisable` flush uses Internal API exclusively** (`removeMemberSilently`, `addMemberSilently` — §6.18, §6.19). It must not call Public API methods: `startupComplete` is effectively `false` during shutdown and events must not be published during teardown. The flush iterates in-memory maps and persists to SQL synchronously; it does not trigger `recalculateCrossOrderRights`.
- All user-facing commune text uses MiniMessage for the server-owned template prefix. User-provided chat input via `/cc` is always `Component.text(rawInput)`.
- Commune UUID generated via `UUID.randomUUID()`. No collision check needed (CC-18).
- `CommuneService`, `CrossOrderMembershipService`, and `CommuneInvitationService` hold state in `ConcurrentHashMap` for safe read from async contexts. All mutation paths run on main thread (commune management) or under explicit `ReentrantReadWriteLock` (per-order operations): write lock for mutations, read lock for hot-path reads such as `FriendlyFireListener`.
- `onDisable`: synchronously flush all in-memory state to SQL; close open menus; reset toggles with notification; log WARN if reload detected. **Flush timeout (CC-Q13):** the flush operation has a configurable timeout of **5 seconds** (default; configurable in plugin config). If flush does not complete within the timeout: log WARN "onDisable flush timed out — partial flush. Startup consistency check (AC-47) will reconcile on next start." Proceed with shutdown. No data corruption: startup AC-47 scan handles any resulting stale SQL rows.
- Schema versioning: each table includes a `schema_version` or DDL migration numbered migration in `DatabaseManager`. On version mismatch: log ERROR and apply migration.
- AC-54 compatibility with AC-58: a player may be owner of order A AND native member of order B simultaneously. AC-58 only prohibits being owner AND member of the **same** order. Confirmed compatible (CC-S07).
- Parallel native member invitations (CC-S08): primary key `(order_id, player_uuid)` + UNIQUE constraint ensures that even if two concurrent inserts are attempted, the DB rejects the duplicate. Service-level guard (AC-53 check) runs before insert but UNIQUE constraint is the final safety net.

---

## 16. Resolved Former Unresolved Items (iteration 4 — PO concurrency decisions)

| # | Question | Sev | Resolution |
|---|----------|-----|-----------|
| Q1 | `inCascadeMode` type и off-main-thread paths | CRITICAL | `inCascadeMode` объявлен как `ThreadLocal<Boolean>`. Все cascade-операции — main thread. Off-main-thread пути (`onDisable` flush, startup consistency check) используют **Internal API** (`removeMemberSilently`), который не публикует события и не затрагивает `inCascadeMode`. Два уровня API задокументированы в §6.18 (`OrderMembershipService`) и §6.19 (`CrossOrderMembershipService`). |
| Q2 | Lock acquisition order rule | HIGH | Зафиксировано в §7 как именованный подраздел «Lock Acquisition Order Rule»: всегда `CrossOrderMembershipService` write lock → затем `OrderMembershipService` write lock. Нарушение — блокер code review. |
| Q3 | Read-to-write upgrade в FriendlyFireListener | HIGH | Зафиксировано в §7 как именованный подраздел «FriendlyFireListener — Read-Only Invariant». Любой вызов mutating-метода внутри damage event handler — CRITICAL блокер code review. |
| Q4 | Разграничение Public API vs Internal API | HIGH | §6.18 и §6.19 добавлены с явными таблицами Public API (с event-publishing, с startupComplete guard) и Internal API (без событий, без guard, `internal` Kotlin visibility). §8.2 step 3b, 3c обновлены: consistency check использует `removeMemberSilently`. §14 обновлён: `onDisable` использует Internal API. |
| Q5 | Опечатка в §11: `ReentrantLock` → `ReentrantReadWriteLock` | MEDIUM | Исправлено в §11 (Security Considerations). |
| Q6 | `recalculateCrossOrderRights` snapshot-iteration pattern | HIGH | §6.12 полностью переписан: (1) acquire write lock; (2) snapshot via `.toList()`; (3) release write lock; (4) iterate snapshot; (5) invalid records revoked через `removeMemberSilently` (Internal API). Гарантирует consistent snapshot, no CME, не блокирует concurrent grant на других orders. |

**Все 6 PO-решений зафиксированы. Открытых нерешённых вопросов: 0.**

---

## 17. Resolved Former Unresolved Items (iteration 5 — PO consistency-checker fixes)

| # | Conflict | Resolution |
|---|----------|-----------|
| C-1 | AC-23 без соответствующей секции в spec | Добавлен `OrderCommuneInfoCommand` в §2.1, §4.3, §5.7, §12.3. Команда `/order commune <orderName>` доступна любому игроку, читает из in-memory cache `CommuneService.getCommuneByOrder`. |
| C-2 | Admin-команды (`/admin commune reload`, `/admin commune sync`) нарушают Out of Scope | Удалены из §8.2 step 4, §8.3, §8.4, §10, §12.3. Recovery путь — **только server restart** → AC-47 reconciles при старте. Edge Cases CC-Q2, CC-Q9, CC-R3-Q4 обновлены. |
| C-3 | §5.1 disabled для non-leader contradicts AC-13 (рядовой в коммуне) | §5.1 state matrix заменена на 4-строчную таблицу: лидер+коммуна → CommuneMenu full; лидер+нет коммуны → CreateCommune; рядовой+коммуна → CommuneMenu read-only (AC-13, AC-29); рядовой+нет коммуны → disabled (AC-14b). |
| C-4 | §8.4 recovery через admin reload противоречит AC-46 (memory — source of truth, restart → AC-47) | Закрывается через C-2: admin reload удалён. §8.4 recovery описывает только: persistent failure → degraded state → server restart required → AC-47 reconciles при старте. |

**4/4 consistency-checker конфликта разрешены. Открытых нерешённых вопросов: 0.**

---

## 18. Resolved Former Unresolved Items (iteration 6 — PO final sign-off fixes)

| # | Конфликт | Resolution |
|---|---------|-----------|
| F-1 (Конфликт 1) | OrderMembersMenu доступ только через `/order members` или CommuneMenu — не через OrderMenu | `CommuneOrderMenu` введён как decorator-обёртка вокруг `OrderMenu` (§5.8, §2.1). Добавляет один слот «Участники», открывающий `OrderMembersMenu`. Команда `/order members` удалена (§4.4). `OrderMembersMenu` открывается двумя путями: (a) из `CommuneMenu` (§5.6 path a), (b) из `CommuneOrderMenu` (§5.6 path b). `OrderMenu.kt` не изменён. AC-60 выполнен. §12.3 wiring обновлён (wiring line 2 — `CommuneOrderMenu`). |
| F-2 (Конфликт 5) | `OrderLifecycleService` + `OrderDestroyedEvent` — лишний слой; `FlagDeactivatedEvent` уже публикуется `OrderService.deleteByOwner` | `OrderLifecycleService` и `OrderDestroyedEvent` **удалены из spec**. `CommuneOrderDestroyListener` подписывается на существующий `FlagDeactivatedEvent` (§2.1, §2.2, §2.3, §6.6). §6.6 переписан: Step 0 = `OrderService.deleteByOwner` публикует событие (вне нашего кода); Steps 1–5 = listener cascade. Нет модификаций `OrderService.kt`. §9 CC-01, CC-S01, CC-S05 обновлены. §12.1 write integration point заменён на event subscription. §12.2 `OrderDestroyedEvent` удалён. §12.3 wiring line 3 = регистрация `CommuneOrderDestroyListener` как Bukkit listener. §12.5 U-01 resolution обновлено. §1 open-closed constraint параграф обновлён. |
| F-3 (Конфликт 3+4) | User-facing русские фразы содержали «member» вместо «участник» | §10 Error Handling: все player-visible русские фразы обновлены: «member'ом» → «участником», «member своего» → «участником своего», «cross-order member» → «cross-order участник». Code identifiers (`OrderMembershipService`, `OrderMember`, `granted_via`, `order_members`) — без изменений. §9 CC-S01, CC-S05 narrative: «native member'ов» → «нативных участников». §5.6 button label «Пригласить member» → «Пригласить участника» (§5.7 `OrderMembersMenu` provides block). |

**3/3 финальных PO-конфликта разрешены. Открытых нерешённых вопросов: 0. Spec готова к sign-off.**

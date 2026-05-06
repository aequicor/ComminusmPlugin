---
genre: concept
module: comminusm
title: Implementation Plan — Communes
topic: communes
date: 2026-05-06
author: "@Main"
status: Pending approval
related:
  - vault/concepts/comminusm/requirements/communes.md
  - vault/concepts/comminusm/plans/communes-corner-cases.md
  - vault/reference/comminusm/spec/communes.md
  - vault/reference/comminusm/test-cases/communes-test-cases.md
---

# Implementation Plan — Communes

**Module:** comminusm  
**Feature:** communes  
**Status:** Pending approval  
**Author:** @Main  
**Date:** 2026-05-06

---

## Overview

Implement a commune system that allows Order leaders to form alliances of multiple orders with:
- Cross-order membership (enabling players from allied orders to modify each other's territories)
- Commune chat (`/cc` command)
- Friendly-fire protection (no damage between commune members)

**Key constraint:** Zero modifications to `OrderService.kt` — all new functionality through new components observing existing Bukkit events.

---

## Architecture Summary

### New Components (from spec §2.1)

| Component | Type | Responsibility |
|-----------|------|-----------------|
| **OrderMembersRepository** | Repository | SQL CRUD for `order_members` table (native + commune members) |
| **OrderMembershipService** | Service | High-level API for member invite/accept/remove/leave |
| **CommuneService** | Service | Commune lifecycle (create, dissolve, add/remove orders) |
| **CrossOrderMembershipService** | Service | Grant/revoke cross-order Member roles |
| **CommuneInvitationService** | Service | Manage commune invitations (create, cancel, expire) |
| **CommuneChatService** | Service | Route `/cc` messages, manage toggle state |
| **CommuneOrderMenu** | Menu | Decorator wrapping `OrderMenu` (add "Участники" button) |
| **OrderMembersMenu** | Menu | View/manage native and cross-order members |
| **CommunePartyMenu** | Menu | Decorator wrapping `PartyMenu` (add "Коммуна" button) |
| **CommuneMenu** | Menu | Main commune management GUI |
| **FriendlyFireListener** | Listener | Handle `EntityDamageByEntityEvent`, enforce no-damage-between-commune-members |
| **CommuneOrderDestroyListener** | Listener | Observe `FlagDeactivatedEvent`, cascade commune exit |
| **CommuneMembershipListener** | Listener | Observe `OrderMemberRemovedEvent`, recalculate cross-order rights |
| **CommunePlayerListener** | Listener | Observe `PlayerJoinEvent`, consistency check, offline notifications |
| **CommuneCommand** | Command | `/cc [text]` — toggle mode, single-message dispatch |
| **OrderCommuneInfoCommand** | Command | `/order commune <orderName>` — read-only info |
| **CommuneStartupTask** | Startup | Load storage, full consistency scan, graceful degrade |

### Data Models

| Table | Purpose |
|-------|---------|
| `communes` | Commune identity (id, createdAt, version) |
| `commune_orders` | Order membership in communes (commune_id, order_id) |
| `commune_invitations` | Pending invitations (id, from_order_id, target_order_id, communeId, expiresAt) |
| `order_members` | **Modified existing concept** — NEW table with (order_id, player_uuid, granted_at, granted_via: 'native'\|'commune') |
| `order_member_invitations` | Pending native member invitations (not in scope of this feature, prepared for US-12..US-16) |

### Open-Closed Integration (read-only to Order system)

- `OrderService.getOrderByPlayer(UUID)` — read player's owned order
- `OrderService.getOrderById(Long)` — look up order
- `OrderService.isLeader(UUID)` — check if player is order owner
- `FlagDeactivatedEvent` (observe) — trigger cascade on order destruction

**Zero modifications to `OrderService.kt`** — it publishes `FlagDeactivatedEvent` already at lines 125 & 143.

---

## Implementation Stages

### Stage 01 — Foundation: Data Layer & Services

**Scope:** Implement storage, repositories, and core services (no GUI, no commands yet)

**Components:**
1. `OrderMembersRepository` (CRUD for `order_members` table via DatabaseManager)
2. `OrderMember` model (in-memory data class)
3. `OrderMembershipService` (high-level API: invite, accept, remove, leave, list members)
4. `Commune` model (in-memory: id, orderIds set, version, createdAt)
5. `CommuneService` (in-memory source of truth for communes, lifecycle)
6. `CommuneInvitation` model (id, fromOrderId, targetOrderId, targetLeaderUUID, expiresAt)
7. `CommuneInvitationService` (manage invitations, schedule expiry timers)

**Key invariants:**
- Communes exist only in memory (loaded from DB at startup, persisted asynchronously)
- `Commune.version` increments on every membership change (stale-state detection, CC-06)
- In-memory caches: `ConcurrentHashMap<Long, Set<OrderMember>>` (by orderId), `Map<UUID, Commune>` (by communeId)
- `OrderMembershipService` publishes `OrderMemberAddedEvent` / `OrderMemberRemovedEvent` for cross-order recalc

**Tests:** unit tests for membership logic, invitation expiry, in-memory state consistency

**DoD:** All tests GREEN, build clean, no lint errors

---

### Stage 02 — Core Business Logic & Listeners

**Scope:** Implement commune management logic (create, invite, accept, leave, dissolve), cross-order grants, and event handlers

**Components:**
1. `CrossOrderMembershipService` (grant/revoke cross-order member roles, atomic in-memory + async persist)
2. `CommuneStartupTask` (async load at `onEnable()`, full consistency scan AC-47, graceful degrade on DB error)
3. `CommuneOrderDestroyListener` (observe `FlagDeactivatedEvent`, cascade exit when order destroyed)
4. `CommuneMembershipListener` (observe `OrderMemberRemovedEvent`, recalculate cross-order rights)
5. `CommunePlayerListener` (observe `PlayerJoinEvent`, consistency check, offline notifications)
6. `FriendlyFireListener` (handle `EntityDamageByEntityEvent`, prevent damage between commune members)

**Key logic (from spec §6):**
- Create Commune (§6.1) — verify leader, not already in commune, atomic create
- Invite Order (§6.2) — verify leader of commune member, replace existing invite, schedule expiry
- Accept Invitation (§6.3) — verify caller is target leader, re-validate, lock, add order, increment version
- Decline Invitation (§6.4) — remove invitation
- Leave Commune (§6.5) — verify leader, show confirm screen, cascade revoke cross-order rights, dissolve if empty
- Order Destroyed (§6.6) — cascade exit when order flagdeactivated, best-effort error handling
- Dissolve Commune (§6.7) — revoke all cross-order rights, cancel invitations, reset toggles
- Invite Native Member (§6.8) — verify leader, target not already member

**Concurrency (CC-11, CC-13):**
- Per-order `ReentrantLock` for member management
- Per-commune queue (ReentrantLock) for membership changes
- `CrossOrderMembershipService.inCascadeMode` (thread-local flag) to prevent N² recalculations during cascade

**Tests:** integration tests for cascades, concurrency stress tests, permission verification

**DoD:** All tests GREEN, build clean

---

### Stage 03 — Commands

**Scope:** Implement chat and info commands

**Components:**
1. `CommuneCommand` (handles `/cc [text]` — toggle mode, single-message dispatch, mute-check)
2. `OrderCommuneInfoCommand` (handles `/order commune <orderName>` — read-only, no auth)

**Behavior:**
- `/cc <text>` — send one message to all online commune members
- `/cc` (no args) — toggle "always in commune chat" mode
- `/order commune <name>` — display commune info (order names, no name for commune itself)

**Tests:** command parsing, chat routing, info display

**DoD:** All tests GREEN

---

### Stage 04 — GUI: Menus

**Scope:** Implement all inventory menus (CommunePartyMenu, CommuneMenu, OrderMembersMenu, etc.)

**Components:**
1. `CommunePartyMenu` — decorator wrapping `PartyMenu`, adds "Коммуна" button (slot TBD, after design)
2. `CommuneMenu` — main GUI (list of commune members, invite/leave buttons, incoming block)
3. `OrderMembersMenu` — view/manage native and cross-order members of an order
4. `CommuneOrderMenu` — decorator wrapping `OrderMenu`, adds "Участники" button

**Decorators (open-closed):**
- `CommunePartyMenu`: reads from cache, renders original PartyMenu slots + new "Коммуна" button
- `CommuneOrderMenu`: delegates to OrderMenu for all original slots, adds "Участники"
- **`PartyMenu.kt` and `OrderMenu.kt` are NOT modified**

**GUI Invariants:**
- Slot layout: 45 slots (9×5), border at edges, center content
- Colors: §8 (dark bg), §e (yellow), §7 (gray), §a (green), §c (red)
- Icons: PAPER (info), BANNER (identity), GLASS (structure), BARRIER (cancel), RED_DYE (danger)
- Back button: slot 39 (bottom-right)
- Paging: if > 7 items, use Prev/Next navigation

**Stale-state guard (§5.4):**
- Snapshot commune version at render time
- On action, re-validate version matches current (close menu + "Данные устарели" if mismatch)

**Tests:** GUI interaction tests, decoration pattern tests, event propagation

**DoD:** All tests GREEN

---

### Stage 05 — Chat Service

**Scope:** Route `/cc` messages, enforce rules

**Components:**
1. `CommuneChatService` — route messages, manage per-player toggle state, plain-text enforcement

**Behavior:**
- `/cc <text>` routes to all online commune members
- Per-player toggle: "always send to commune chat" (toggle on `/cc`)
- Plain-text wrapping: wrap special chars to prevent code injection (CC-09)
- Mute checks: if sender muted, don't deliver (CC-10)

**Tests:** message routing, toggle state, mute enforcement

**DoD:** All tests GREEN

---

### Stage 06 — Integration & Final Verification

**Scope:** Wire everything together, run full test suite, verify corner cases

**Activities:**
1. Wire DI: register all beans in `ComminusmPlugin.onEnable()`
2. Register listeners: CommuneStartupTask, all listeners, command executors
3. Database schema: create tables via migration or at startup
4. Integration tests: full workflows (create commune, invite, accept, leave, order destroyed)
5. Corner case verification: all CC-* test cases pass
6. Consistency checks: startup AC-47 scan, online consistency

**Tests:** end-to-end scenarios, CC verification, data consistency

**DoD:** All tests GREEN, no lint errors, spec coverage complete

---

## Pre-Mortem Risk Analysis

| Area | Risk | Likelihood | Impact | Mitigation |
|------|------|------------|--------|-----------|
| **Concurrency** | Race between invite expiry and manual accept | MEDIUM | HIGH | Snapshot invite ID + version at render; re-validate on action |
| **Cascade** | O(N²) recalculation on order destroy | MEDIUM | MEDIUM | Batch mode flag in CrossOrderMembershipService (CC-Q5) |
| **Storage** | DB unavailable during cascade | LOW | CRITICAL | Abort cascade, log CRITICAL, AC-47 on startup reconciles |
| **Member invite expiry** | Timer leaks memory if not canceled | LOW | HIGH | Explicitly cancel timer on accept/decline/replace |
| **Cross-order conflicts** | Player in both orders of same commune gets confused | LOW | LOW | Use `granted_via` marker; AC-25 handles multi-membership |
| **GUI stale data** | User clicks old invitation that expired | MEDIUM | LOW | Version + ID snapshot; re-validate on action |
| **DI wiring** | Service dependencies missing at startup | LOW | MEDIUM | Unit tests verify all @Autowired/inject points |
| **Friendly-fire false positive** | Damage blocked unintentionally | MEDIUM | MEDIUM | Double-check: native orders in same commune (not cross-order status) |

---

## Schedule & Dependencies

### Stage Sequence
1. **Stage 01** (Foundation) — 1 day — blocking all others
2. **Stage 02** (Logic) — 1-2 days — depends on Stage 01
3. **Stage 03** (Commands) — 0.5 days — depends on Stage 02
4. **Stage 04** (GUI) — 1-2 days — depends on Stage 01 & 02
5. **Stage 05** (Chat) — 0.5 days — depends on Stage 02 & 03
6. **Stage 06** (Integration) — 1 day — depends on all others

**Critical path:** Stage 01 → Stage 02 → (Stage 03, 04, 05 in parallel) → Stage 06

**Estimated total:** 4-5 days (serial work)

---

## Acceptance Criteria Coverage

✅ All 16 user stories (US-01..US-16) mapped to stages  
✅ All 63 acceptance criteria (AC-01..AC-60) mapped to stages  
✅ All 7 Critical corner cases (CC-01..CC-07, CC-S01..CC-S02) have test cases  
✅ All 14 High corner cases have test cases or explicit handlers  

---

## Definition of Done

A stage is complete when:
- [ ] All related test cases PASS (unit + integration)
- [ ] Build compiles: `./gradlew compileKotlin`
- [ ] Lint passes: `./gradlew detekt ktlintCheck`
- [ ] Code reviewed by @CodeReviewer
- [ ] Security-relevant stages reviewed by @SecurityReviewer (Stage 04, 05 — chat, permissions)
- [ ] Spec endpoints traced to tests (TraceabilityChecker)
- [ ] Corner cases verified (CornerCaseReviewer IMPLEMENTATION mode)

---

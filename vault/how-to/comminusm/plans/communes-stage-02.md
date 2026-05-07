---
genre: how-to
module: comminusm
title: Stage 02 — Core Business Logic & Listeners
topic: communes
stage: 02
date: 2026-05-06
author: "@Main"
status: Ready for implementation
related:
  - vault/concepts/comminusm/plans/communes-plan.md
  - vault/reference/comminusm/spec/communes.md
  - vault/reference/comminusm/test-cases/communes-test-cases.md
---

# Stage 02 — Core Business Logic & Listeners

**Scope:** Implement commune management logic (create, invite, accept, leave, dissolve), cross-order grants, and event handlers

**Depends on:** Stage 01 (Foundation: models, repos, services)

---

## Components to Implement

### 1. CrossOrderMembershipService (Service)
Grant and revoke cross-order member roles (commune ↔ order mapping).

**Methods:**
- `grantCommuneMember(orderIdTuple: Pair<Long, Long>, playerUuid: UUID): Result<Unit>` — add player to both orders with grantedVia="commune"
- `revokeCommuneMember(orderIdTuple: Pair<Long, Long>, playerUuid: UUID): Result<Unit>` — remove player from both
- `revokeAllCommuneMembers(communeId: UUID): Result<Unit>` — cascade revoke when commune dissolves
- `getOnlineMembers(orderId: Long): Set<UUID>` — for friendly-fire checks (Stage 05)

**Concurrency:**
- `inCascadeMode: ThreadLocal<Boolean>` — prevents O(N²) recalculation during cascade (CC-Q5)
- Batch mode flag prevents redundant updates during order removal

**Invariants:**
- A player can be both native + commune member of same order (one entry per grantedVia)
- Revoke-during-cascade is idempotent (no errors if already revoked)

### 2. CommuneStartupTask (Startup Listener)
Load communes from DB at plugin startup, consistency scan (AC-47).

**Methods:**
- `onEnable()` — async load from DB, full consistency check
- Check invariants: communes exist, orders valid, members exist, no orphaned invitations
- Graceful degrade: if DB unavailable, use in-memory empty state

**Behavior:**
- Called during `ComminusmPlugin.onEnable()` before registering listeners
- Loads `communes` table, `commune_orders` table, `commune_invitations` table
- Populates `CommuneService.communes` and `OrderMembersRepository` cache
- Logs warnings for inconsistencies; does NOT fail startup

### 3. CommuneOrderDestroyListener (Listener)
Observe `FlagDeactivatedEvent`, cascade commune exit when order destroyed.

**Event Handler:**
- `onFlagDeactivated(FlagDeactivatedEvent)` — triggered when order flag is broken/destroyed
- Find commune containing this order
- Revoke all cross-order members from this order (cascade via CrossOrderMembershipService)
- Remove order from commune
- Dissolve commune if empty
- Log: "Order [id] exited commune [communeId]"

**Concurrency:**
- Uses `inCascadeMode` flag to batch revokes (prevent N² queries)

### 4. CommuneMembershipListener (Listener)
Observe `OrderMemberRemovedEvent`, recalculate cross-order rights.

**Event Handler:**
- `onOrderMemberRemoved(OrderMemberRemovedEvent)` — triggered when player leaves order
- If grantedVia="native": check if player is also commune member (grantedVia="commune")
- If commune member: revoke cross-order membership (player no longer in any order in commune)
- If grantedVia="commune": player was removed via cascade (do nothing)

**Constraint:**
- Called AFTER player removed from order (reactive cleanup, not preventive)

### 5. CommunePlayerListener (Listener)
Observe `PlayerJoinEvent`, consistency check and offline notifications.

**Event Handler:**
- `onPlayerJoin(PlayerJoinEvent)` — check if player is commune member (online notifications)
- If player is in multiple orders (native + commune): show "You are member of commune [names]"
- Check for stale state (AC-47): if commune version changed during player offline, show consistency warning

**Notifications:**
- Send offline messages: "You were invited to commune [name] while offline"
- Send sync warnings if version mismatch detected

### 6. FriendlyFireListener (Listener)
Handle `EntityDamageByEntityEvent`, prevent damage between commune members.

**Event Handler:**
- `onEntityDamageByEntity(EntityDamageByEntityEvent)` — if damager and damagee are both players
- Check: are both players in same commune? (query each player's communes via CommuneService)
- If yes AND both in same native order: cancel damage, send "Friendly fire disabled!"
- Concurrency: snapshot commune membership, re-validate (AC-25 multi-membership)

**Constraint:**
- Uses "native orders in same commune" (not just "commune member"). Prevents abuse where cross-order members avoid friendly-fire rules.

---

## Key Logic (Spec §6)

### 6.1 Create Commune
- **Precondition:** Player is leader (AC-03 check via OrderService.isLeader)
- **Validation:** Order not already in commune
- **Action:** Create Commune(id=UUID, orderIds={leadingOrderId}, version=0, createdBy=player)
- **Result:** Commune now exists in-memory, async persist to DB

### 6.2 Invite Order to Commune
- **Precondition:** Caller is leader of commune member order
- **Validation:** Target order not already member, invite not already pending
- **Action:** If existing invite to target → replace (cancel old, create new). Schedule 30-min expiry.
- **Result:** Invitation pending; target leader gets notification on next login (Stage 05+)

### 6.3 Accept Invitation
- **Precondition:** Caller is leader of target order
- **Validation:** Invitation not expired, commune still exists, order not already in commune
- **Action:** Atomic: increment commune.version, add order to commune, schedule cross-order grants
- **Result:** Order now in commune; all players in target order get commune-member grants in all native orders within commune

### 6.4 Decline Invitation
- **Action:** Remove invitation, cancel expiry timer

### 6.5 Leave Commune
- **Precondition:** Caller is leader of order in commune
- **Action:** Show confirm screen (GUI, Stage 04). On confirm: atomically revoke all cross-order members from this order, remove order from commune
- **Result:** If commune now empty → dissolve (§6.7)

### 6.6 Order Destroyed
- **Trigger:** FlagDeactivatedEvent (order flag broken)
- **Action:** Cascade leave (§6.5 without confirm screen)

### 6.7 Dissolve Commune
- **Precondition:** Commune empty (zero orders) OR last order leaving
- **Action:** Revoke all cross-order rights, cancel pending invitations, reset all players' commune-chat toggle
- **Result:** Commune removed from memory, async delete from DB

### 6.8 Invite Native Member
- **Precondition:** Caller is leader of order
- **Action:** Add player to order with grantedVia="native" (via OrderMembershipService)
- **Result:** Player can now modify order territory

---

## Concurrency & Atomicity

- **Per-order locks:** OrderMembersRepository uses ReentrantReadWriteLock
- **Per-commune locks:** CommuneService uses ReentrantLock for version increments
- **Cascade mode:** ThreadLocal<Boolean> inCascadeMode flag prevents O(N²) recalculations
  - Set before cascade, unset after
  - All revokes during cascade are batched, single DB flush at end

---

## Testing Strategy

### Unit Tests
- CommuneService: create, add/remove orders, version increments, dissolution
- CrossOrderMembershipService: grant/revoke operations, cascade mode
- CommuneOrderDestroyListener: order destroyed → cascade exit

### Integration Tests
- Full workflow: create commune, invite, accept, leave
- Cascade on order destroy: multiple orders, cleanup validation
- Concurrency: multiple accepts/leaves in parallel, version consistency
- Offline sync: player offline during invite, notification on login

---

## Definition of Done

- [ ] All 6 components (services + listeners) compiled
- [ ] Unit tests: all GREEN
- [ ] Integration tests: all GREEN
- [ ] `./gradlew compileKotlin` succeeds
- [ ] @CodeReviewer approves
- [ ] Spec coverage: AC-01..AC-60, CC-01..CC-07 traced to code

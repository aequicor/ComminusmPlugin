---
genre: concept
module: comminusm
title: Corner Case Register — Order Name
topic: order-name
status: Draft
date: 07.05.2026
author: "@CornerCaseRefinement"
requirements: vault/concepts/comminusm/requirements/order_name.md
---

# Corner Case Register — Order Name

> **Generated during business requirements phase.**
> Critical and High items are mandatory inputs for spec and implementation plan.

---

### Critical (data loss, security, broken invariant)

| # | Category | Condition | Expected Business Behavior | Spec Addressed |
|---|----------|-----------|---------------------------|----------------|
| CC-01 | Business Integrity | Order leader is **demoted to member or removed from order** while the Anvil rename GUI is open; they then confirm a new name | Rename must be rejected. The permission check (is leader?) must execute at **confirm time**, not at Anvil-open time. Player receives: "Вы больше не лидер этого ордера" [You are no longer the leader of this order]. No write to DB, no ArmorStand update. | ❌ |
| CC-02 | Business Integrity | Order is **deleted or disbanded** while the Anvil rename GUI is open; player then confirms a new name | Rename must fail gracefully. Server detects the order no longer exists, discards the rename event, closes the Anvil, notifies the player: "Этот ордер был расформирован" [This order has been disbanded]. No DB write, no NPE. | ❌ |
| CC-03 | External Dependency | **ArmorStand entity is permanently removed** from the world (e.g. chunk deleted, admin command) while the order name record still exists in DB | On name update, `entity.setCustomName()` call must not throw NullPointerException. System should log a warning and continue — DB name is updated, ArmorStand update is skipped. The order name in menus is correct; only the in-world display is missing. | ❌ |

---

### High (user-visible incorrect result, broken user story)

| # | Category | Condition | Expected Business Behavior | Spec Addressed |
|---|----------|-----------|---------------------------|----------------|
| CC-04 | Input Integrity | Name consists **entirely of hyphens and/or underscores** (e.g., `---`, `___`, `-_-_`); all chars are technically valid per AC-05 | These names are syntactically valid but semantically meaningless. Business decision: accept (no content filter is in scope per Out of Scope). A name like `---` is stored as-is. PO may promote to Medium if acceptable. | ❌ |
| CC-05 | Input Integrity | **Minimum name length** not specified. Can the name be 1 character? (e.g., `A`, `Я`, `1`) | Single character names are valid: they pass AC-05 character set, pass AC-07 non-empty check. Business decision: accept. No minimum length constraint beyond non-empty. | ❌ |
| CC-06 | Business Process | **Double-click** the Rename button in the management menu opens the Anvil twice in rapid succession | Second Anvil open must be ignored if an Anvil is already open for this order+player pair. Only one rename interaction per player at a time. | ❌ |
| CC-07 | Business Process | Player opens the Rename Anvil and **loses connection** (disconnect, timeout) before confirming | Anvil close event (or connection close) must treat this as a cancel. No partial rename is stored. Name stays at previous value. | ❌ |
| CC-08 | External Dependency | **ArmorStand entity is in an unloaded chunk** when the rename is confirmed | DB write succeeds. Entity update is deferred: either skip silently (entity loads with stale name until next chunk load triggers a refresh) or schedule an async re-check after chunk load. Business behavior: DB is authoritative; display catches up when chunk loads. | ❌ |
| CC-09 | Business Rule | **Non-leader opens the OrderMenu via a direct inventory-open call** bypassing the button click guard (e.g., via another plugin, /openinv, or crafted packet) | The server-side confirm handler must re-verify leader status regardless of whether the button was clickable in the GUI. No permission bypass via crafted interaction. | ❌ |

---

### Medium (degraded experience, workaround exists)

| # | Category | Condition | Expected Business Behavior | Spec Addressed |
|---|----------|-----------|---------------------------|----------------|
| CC-10 | Input Integrity | Name has **leading or trailing hyphens/underscores** (e.g., `_Name_`, `-Guild`, `Order-`) | All chars are valid per AC-05. Names like `_Name_` are stored as-is; no auto-trimming of edge hyphens/underscores. (If business wants to forbid this, a rule must be added to AC-05.) | ❌ |
| CC-11 | Input Integrity | Player's **default nickname at creation is exactly 20 chars** and all chars are valid | Name is set to the full 20-char nickname as-is. No truncation needed (≤20). | ❌ |
| CC-12 | Input Integrity | Player's **default nickname at creation is longer than 20 chars** (possible with display names or cracked-mode) | AC-15 only covers invalid chars, not length. Length truncation at 20 must also be applied when setting the default name. Result: first 20 valid chars. Owner notified of the truncated name. | ❌ |
| CC-13 | Business Process | Player confirms a rename that is identical to the current name in terms of content but with **different Unicode normalization** (e.g., composed vs decomposed Cyrillic) | Treat as a no-op per AC-19 using byte-level (JVM String.equals) comparison. No DB write. No normalization step needed for MVP. | ❌ |
| CC-14 | User Journey | Player opens the Rename Anvil from the **management menu**, then **opens the management menu again** from a different action (e.g. keybind, another plugin trigger) while the Anvil is still open | Second menu open is blocked or the Anvil remains active. The player cannot have two competing GUIs open simultaneously. | ❌ |
| CC-15 | User Journey | Owner renames the order and **immediately opens the management menu** before the DB write confirms | Menu shows the optimistically updated in-memory name (new name), not the old DB value. If DB write subsequently fails and rollback occurs (AC-13), the menu will show the old name on next open. | ❌ |
| CC-16 | Temporal & Concurrency | Two leaders of the **same order** rename simultaneously (possible if order has multiple leaders) and both receive "name changed" success messages, but one rename is silently overwritten by AC-20 | The overwritten player receives a success confirmation but the persisted name is the other player's. Business decision: acceptable for MVP (last-write-wins). Noted as tech debt (optimistic locking). | ❌ |
| CC-17 | External Dependency | Database write for the name change **succeeds partially** (e.g. commit acknowledged but actual row not updated due to DB engine state) | On next server restart, the DB is re-read (source of truth per AC-13). The in-memory name may diverge from DB during the session. Business decision: acceptable for MVP. | ❌ |

---

### Low (cosmetic, extreme edge of edge)

| # | Category | Condition | Expected Business Behavior | Spec Addressed |
|---|----------|-----------|---------------------------|----------------|
| CC-18 | Input Integrity | Name is exactly `"Order"` (the fallback name defined in AC-15) — player manually sets this | Valid name, accepted. No collision rule. Multiple orders can be named "Order" (no uniqueness). | ❌ |
| CC-19 | Scale | 1000 orders renamed simultaneously by 1000 players | No rate limit in scope. DB handles concurrent writes at column level. No application-level throttle needed for MVP. | ❌ |
| CC-20 | User Journey | Player uses **browser autofill / system clipboard paste** to fill the Anvil text field | Anvil text field is a standard Minecraft text input. Paste results in the same char-set validation as typed input. No special handling needed. | ❌ |
| CC-21 | User Journey | ArmorStand display name uses **color or format codes** from a previous system; rename overwrites them with plain text | After rename, the ArmorStand displays the plain-text name only. Previous formatting is lost. Business decision: acceptable (AC-02 specifies plain text). | ❌ |

---

## Open Questions (Needs PO Decision)

These questions were surfaced during the BA phase and remain unresolved. They are restated here for spec authoring context:

| OQ | Question | Impact |
|----|----------|--------|
| OQ-01 | If the owner's nickname changes after order creation, does the stored name auto-update or stay frozen? | If frozen: the displayed name diverges from the current owner's identity. |
| OQ-02 | (Moot — max 20 chars, fits on one ArmorStand line.) Clarified: no wrapping needed for 20-char max. | Low impact. |
| OQ-03 | When Anvil cancel/Escape fires, should a confirmation message appear (e.g. "Переименование отменено")? | UX clarity. |
| OQ-04 | Should there be a cooldown on rename operations (e.g., once per minute)? | Prevents abuse. |
| OQ-05 | UUID change on server migration: does order ownership (and name) survive? | Data integrity. |

---

## Coverage Summary

| Category | Rows | % of total |
|----------|------|-----------|
| 1 — Input Integrity | 7 (CC-04, CC-05, CC-10, CC-11, CC-12, CC-13, CC-18) | 33% |
| 2 — Business Process | 5 (CC-01, CC-02, CC-06, CC-07, CC-13) | 24% |
| 3 — Domain Invariants | 2 (CC-09, CC-18) | 10% |
| 4 — External Dependency | 3 (CC-03, CC-08, CC-17) | 14% |
| 5 — Scale | 1 (CC-19) | 5% |
| 6 — Temporal & Concurrency | 2 (CC-16, CC-17) | 10% |
| 7 — User Journey | 4 (CC-14, CC-15, CC-20, CC-21) | 19% |

**Balance check:** Categories 1+2+3+7 = 18 rows. Categories 4+5+6 = 6 rows. ✅ User-journey side dominates — not over-weighted on infrastructure.

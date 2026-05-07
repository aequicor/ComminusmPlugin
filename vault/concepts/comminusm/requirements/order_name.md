---
genre: concept
module: comminusm
title: Business Requirements — Order Name
topic: order-name
status: Draft
date: 07.05.2026
author: "@BusinessAnalyst"
related:
  - vault/concepts/comminusm/requirements/communes.md
  - vault/reference/comminusm/spec/communes.md
---

# Requirements: Order Name

**Module:** comminusm
**Status:** Draft
**Date:** 07.05.2026
**Author:** @BusinessAnalyst

---

## Business Context

Currently, orders have no customizable display name — they are identified only by their internal ID or the leader's username. As the commune system expands and orders become more complex social units, players need a way to give their orders distinct, memorable names that reflect their community's identity.

**Problem:** Without customizable names, orders feel impersonal and generic. Players cannot distinguish their order visually from others on server lists or in menus without remembering internal IDs.

**Solution:** Introduce an editable `name` field on orders:
- Default value: the leader's (creator's) player nickname at the time of order creation
- Display: in-world on the order's ArmorStand entity and in all order-related menus
- Edit mechanism: Anvil GUI (familiar Minecraft interaction pattern) accessible only to the order owner/leader
- Constraints: max 20 characters, alphanumeric + Cyrillic/Latin + hyphen/underscore, no spaces
- Persistence: saved with order data; survives server restart

---

## User Stories

| # | As a... | I want to... | So that... |
|---|---------|-------------|-----------|
| US-01 | Order owner/leader | See the default order name (my nickname) when I create an order | I immediately have a meaningful display name without extra work |
| US-02 | Order owner/leader | Open a menu to rename my order using an Anvil GUI | I can customize the order's identity to match our group's theme or culture |
| US-03 | Order owner/leader | Confirm the new name in the Anvil GUI | The rename takes effect and is visible everywhere (in-world and menus) |
| US-04 | Any player | See the order name on the ArmorStand entity in the world | I can quickly identify orders at a glance while exploring |
| US-05 | Any player | See the order name in order-related menus | I understand which order I am viewing or interacting with |
| US-06 | Non-leader order member | Not be able to rename the order | Only the owner maintains control over the order's public identity |

---

## Acceptance Criteria

| ID | Story | Given | When | Then |
|----|-------|-------|------|------|
| AC-01 | US-01 | A new order is created with ownerUuid = Player A (nickname "Alice") | Order creation completes | Order.name is set to "Alice" (player nickname at creation time) |
| AC-02 | US-01 | An order exists with default name | Order is displayed in any menu or command output | The order name "Alice" is shown as plain text, without any extra prefix or suffix, and truncated to max 20 chars if displayed |
| AC-03 | US-02 | Order owner opens the OrderMenu (or CommuneOrderMenu) | Owner presses the Rename button (material: ANVIL) | An Anvil GUI opens with a text input field; the current order name at Anvil-open time is pre-filled in the input (if name changed between menu open and Anvil open, the pre-filled value is the name at Anvil-open time) |
| AC-04 | US-02 | The Anvil GUI is open and shows the current order name | Owner types a new name | The new text is accepted in the input field; the Minecraft Anvil does not enforce character limit, but server will reject on confirm if >20 chars with message "Максимум 20 символов" [Max 20 characters] |
| AC-05 | US-02 | Order owner is viewing the Anvil GUI with name input | Owner enters a name containing invalid characters (anything other than: Latin letters A-Z/a-z, Cyrillic letters а-я/А-Я, digits 0-9, hyphen (-), underscore (_); also rejects all whitespace including Unicode whitespace, emoji, accented Latin, and all other scripts) | The name is rejected; the Anvil closes; owner receives a message: "Недопустимые символы. Используйте: буквы, цифры, дефис (-), подчёркивание (_)" [Invalid characters. Use: letters, digits, hyphen (-), underscore (_)] |
| AC-06 | US-02 | Order owner is viewing the Anvil GUI with name input | Owner enters a name longer than 20 characters | The name is rejected; the Anvil closes; owner receives a message: "Максимум 20 символов" [Max 20 characters] |
| AC-07 | US-02 | Order owner is viewing the Anvil GUI with name input | Owner enters a name containing only spaces (including Unicode whitespace such as non-breaking space, zero-width space, tab) or leaves the field empty | The name is rejected; the Anvil closes; owner receives a message: "Название не может быть пустым" [Name cannot be empty] |
| AC-08 | US-02 | Order owner is viewing the Anvil GUI | Owner presses Escape or the close button (Anvil cancel action) | The Anvil closes without applying changes; the order name remains unchanged |
| AC-03, AC-03 | US-03 | Order owner has typed a valid new name in the Anvil GUI | Owner presses Enter or the confirm button (Anvil output slot interaction) | The Anvil closes; the order name is updated in-memory and persisted to database; owner receives confirmation message: "Название ордера изменено на '[NewName]'" [Order name changed to '[NewName]'] |
| AC-09 | US-03 | The order name has just been changed to "Warriors" | Both in-world and in any open menu | The ArmorStand entity's display name updates to "Warriors"; menu is not refreshed in real-time; the menu is rebuilt on next open. If menu is currently open by the owner (who just renamed), the menu auto-closes and owner must re-open to see the new name |
| AC-10 | US-03 | The order name has been changed to "Warriors"; the owner has closed the menu | Server restarts | On next server boot, the order name is still "Warriors" (persisted correctly) |
| AC-04 | US-04 | An order "MyGuild" exists in the world | Any player explores and sees the order's ArmorStand entity | The ArmorStand displays the text "MyGuild" (via setDisplayName / setCustomNameVisible) |
| AC-05 | US-05 | Any player opens a menu that displays orders (OrderMenu, CommuneMenu, etc.) | Player views the order list | The order is identified by its current name (e.g. "MyGuild"), not by ID or default owner nickname |
| AC-11 | US-06 | A non-leader member opens the OrderMenu | Member views the menu buttons | The Rename button (ANVIL) is visible but disabled with tooltip "Только лидер может менять название" [Only the leader can change the name]; member cannot open the Anvil GUI |
| AC-12 | US-06 | A non-leader player (not even a member) opens the OrderMenu for an order they don't own | Player views the menu buttons | The Rename button is visible but disabled with tooltip "Только лидер может менять название" [Only the leader can change the name]; no interaction is possible |
| AC-13 | US-03 | The order name was changed; in-memory update succeeds, but database persistence fails with an error | Owner's perspective | Old name is cached in a local variable before the DB call. On DB error, in-memory name is rolled back to the cached old value; Anvil closes; owner receives error message: "Ошибка при сохранении названия. Попробуйте позже" [Error saving name. Try again later]. On server crash, the name is re-read from the database on next boot (DB is source of truth) |
| AC-14 | US-03 | Order name "OldName" is displayed in an open menu; another player (the owner) renames the order to "NewName" | First player's menu is still open and they view the order name | Last-write-wins: the new name "NewName" is immediately written to the database. First player's menu will show the new name on the next menu open (no real-time push refresh). Menu staleness is acceptable; refresh occurs on next menu interaction or next server start |
| AC-15 | US-01 | An order is created with owner nickname containing special characters, e.g. "Player#123" | Order creation completes | Invalid characters (anything not in the AC-05 character set) are replaced with underscore. Result: "Player_123". If the result is all underscores or empty, the fallback name "Order" is used instead. The owner receives a notification: "Название вашего ордера установлено на '[DefaultName]'" [Your order name is set to '[DefaultName]'] |
| AC-16 | US-02 | Order name is exactly 20 characters long (at the character limit) | Owner confirms the name in the Anvil | The name is accepted and persisted (length constraint check allows up to 20 inclusive) |
| AC-17 | US-02 | Order name contains both Latin and Cyrillic letters, e.g. "Warriors Дружина" | Owner confirms in the Anvil | The name is accepted and persisted (mixed alphabets are permitted). Only Latin + Cyrillic + digits + hyphen + underscore are allowed; all other scripts (Greek, Hebrew, Chinese, Arabic, etc.) are invalid |
| AC-18 | US-03 | Validation error is triggered (invalid characters, too long, empty) | Validation fails on server | Error feedback is displayed in the action bar (above hotbar) in the same style as other menu validation messages, not in chat or title. If action bar is unavailable or hidden, degrade to chat message as fallback |
| AC-19 | US-02 | A player attempts to rename the order to the same name it already has (no-op) | Owner submits the unchanged name in the Anvil (case-sensitive comparison, e.g. "Warriors" ≠ "warriors") | The name is accepted silently without error. The database is not updated, and the ArmorStand display name is not re-set (no-op optimization). If two concurrent renames both target the same value as the current name, both skip the write — result is consistent (same value, no write needed) |
| AC-20 | US-03 | Two players with leader permissions attempt to rename the same order simultaneously (concurrent renames on same order) | Both submit different new names within milliseconds | Last-write-wins: the rename is determined by server-received time (event handler execution). The first database commit wins (optimistic: no locking, last committed value is stored). The other player's rename is silently overwritten. No serialization or optimistic locking is needed for MVP |

---

## Out of Scope

- Order name search or sorting by name (no search index required for this feature)
- Uniqueness enforcement — multiple orders can have the same name (no UNIQUE constraint)
- Order name history or audit log (rename events are not tracked separately)
- Special formatting of order names (colors, formatting codes, gradients)
- Translation or localization of default names (default = raw player nickname)
- Offline rename via console commands (no admin rename command in scope)
- Name validation based on content filters or profanity lists (only character-set validation)
- Order name templates or prefixes based on order tier, faction, or status
- Real-time menu refresh across all players when an order is renamed (last-write-wins, menu refreshes on next open)
- Optimistic locking or serialization for concurrent renames (MVP uses last-write-wins; proper concurrent rename handling noted as tech debt)

---

## Dependencies

- **Communes feature** — order management menu (OrderMenu, CommuneOrderMenu) is the primary UI entry point for renaming. The Rename button integrates into the existing order menu structure.
- **ArmorStand entity system** — orders already use ArmorStand for in-world display. The name update must propagate to the entity's display name.
- **Order data persistence (DatabaseManager)** — new `name` column added to `orders` table.
- **OrderMenu / CommuneOrderMenu** — button integration point. Rename button is added alongside existing management buttons.
- **Player permission system** — leader-only gate is enforced via existing `OrderService.isLeader(playerUUID)` check.

---

## Open Questions

- [ ] **OQ-01** — What should happen if the owner's nickname changes on the server after the order is created? Should the default name update retroactively, or does it stay frozen at creation time? → **Assigned to: PO**
- [ ] **OQ-02** — When displaying the order name on the ArmorStand, what should the maximum line length be before text wrapping or truncation occurs? Should names longer than 40 chars be wrapped onto two lines, or truncated with "..."? → **Assigned to: PO**
- [ ] **OQ-03** — If the Anvil close/cancel happens (player presses Escape), should a confirmation message appear, or should it silently revert? → **Assigned to: PO**
- [ ] **OQ-04** — Should the rename button have any cooldown or rate limiting (e.g., "can only rename once per minute")? → **Assigned to: PO**
- [ ] **OQ-05** — If a player's UUID changes (e.g. due to server migration from offline-mode to online-mode), how is the order owner relationship validated? Does the name persist? → **Assigned to: PO**

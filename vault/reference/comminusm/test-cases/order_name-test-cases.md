---
genre: reference
module: comminusm
title: Test Cases — Order Name
topic: order-name
status: Draft
date: 07.05.2026
author: "@QA"
last_updated: 2026-05-07 (AUTO_VERIFY cycle 3: Stage 03 @TestExecutor verdicts applied — TC-02,13,14,15,16 PEND→PASS)
spec: vault/reference/comminusm/spec/order_name.md
requirements: vault/concepts/comminusm/requirements/order_name.md
---

# Test Cases: Order Name

**Module:** comminusm
**Generated:** 2026-05-07 by @QA
**Spec:** `[[reference/comminusm/spec/order_name]]`
**Requirements:** `[[concepts/comminusm/requirements/order_name]]`

---

## Status legend

PEND  •  PASS  •  FAIL  •  SKIP

> Filled by AI agents. AI fills ID/Status/Type/Description/To be.
> Notes is owned by the manual tester — written only when a TC fails.

| ID    | Status | Notes | Type        | Description                                                                              | To be                                               |
|-------|--------|-------|-------------|------------------------------------------------------------------------------------------|-----------------------------------------------------|
| TC-01 | PASS   | —     | happy path  | [AC-01] Create order, verify default name is set to owner's nickname                     | Order.name equals player nickname at creation       |
| TC-02 | PASS   | —     | acceptance  | [AC-02] Display order name in menu, verify plain text without prefix/suffix (impl: src/test/kotlin/ru/kyamshanov/comminusm/gui/OrderMenuTest.kt) | Order name visible in-menu as plain text            |
| TC-03 | PEND   | —     | happy path  | [AC-03] Order owner opens Rename button (ANVIL), Anvil GUI opens with current name       | Anvil opens, current name pre-filled in input       |
| TC-04 | PEND   | —     | acceptance  | [AC-04] New text input accepted in Anvil field; >20 chars rejected with error             | Anvil rejects >20 chars, message: "Максимум 20..."  |
| TC-05 | PASS   | —     | acceptance  | [AC-05] Invalid characters rejected (not A-Z/a-z/а-я/А-Я/0-9/-/_); error shown (impl: src/test/kotlin/ru/kyamshanov/comminusm/application/usecases/order/RenameOrderUseCaseTest.kt)          | Invalid chars rejected, message: "Недопустимые..."  |
| TC-06 | PASS   | —     | acceptance  | [AC-06] Name >20 chars rejected on confirm with "Максимум 20 символов" (impl: src/test/kotlin/ru/kyamshanov/comminusm/application/usecases/order/RenameOrderUseCaseTest.kt)                   | Anvil closes, error message shown, name unchanged   |
| TC-07 | PASS   | —     | acceptance  | [AC-07] Empty or whitespace-only name rejected with "Название не может быть пустым" (impl: src/test/kotlin/ru/kyamshanov/comminusm/application/usecases/order/RenameOrderUseCaseTest.kt)     | Anvil closes, error message shown, name unchanged   |
| TC-08 | PEND   | —     | acceptance  | [AC-08] Pressing Escape/close button cancels rename without applying changes             | Anvil closes, order name remains unchanged          |
| TC-09 | PASS   | —     | happy path  | [AC-03, AC-03] Owner enters valid name, presses Enter/confirm; Anvil closes, DB updated (impl: src/test/kotlin/ru/kyamshanov/comminusm/application/usecases/order/RenameOrderUseCaseTest.kt) | Anvil closes, name updated, confirmation message    |
| TC-10 | PEND   | —     | acceptance  | [AC-09] After name change, ArmorStand display name updates to new value                  | ArmorStand entity shows new custom name in-world    |
| TC-11 | PEND   | —     | acceptance  | [AC-09] After name change, owner's open menu auto-closes, must re-open to see new name   | Menu auto-closes after rename, new name on re-open  |
| TC-12 | PEND   | —     | acceptance  | [AC-10] Server restart after rename persists the new order name                          | After server restart, name matches DB persisted     |
| TC-13 | PASS   | —     | acceptance  | [AC-04, AC-04] ArmorStand entity displays order name "MyGuild" in-world (impl: src/test/kotlin/ru/kyamshanov/comminusm/application/usecases/order/OrderNameTest.kt) | ArmorStand custom name visible to all players       |
| TC-14 | PASS   | —     | acceptance  | [AC-05, AC-05] Order list in menu shows order by current name, not ID or old nickname (impl: src/test/kotlin/ru/kyamshanov/comminusm/application/usecases/order/OrderNameTest.kt) | Order identified by name, not internal ID           |
| TC-15 | PASS   | —     | acceptance  | [AC-11] Non-leader opens OrderMenu, Rename button disabled with tooltip (impl: src/test/kotlin/ru/kyamshanov/comminusm/gui/OrderMenuTest.kt) | Rename button disabled, tooltip shown               |
| TC-16 | PASS   | —     | acceptance  | [AC-12] Non-member opens OrderMenu, Rename button disabled with tooltip (impl: src/test/kotlin/ru/kyamshanov/comminusm/gui/OrderMenuTest.kt) | Rename button disabled, tooltip: "Только лидер..."  |
| TC-17 | PEND   | —     | error       | [AC-13] DB persistence fails, in-memory name rolled back to cached old value             | Anvil closes, error message, name restored to old   |
| TC-18 | PEND   | —     | acceptance  | [AC-14] Another player renames order while first player views menu; on next open new name | Menu shows new name on next open, no real-time push |
| TC-19 | PASS   | —     | acceptance  | [AC-15] Owner nickname contains invalid chars (e.g. "Player#123"), default name sanitized| Invalid chars replaced with _, result: "Player_123" |
| TC-20 | PASS   | —     | acceptance  | [AC-15] Default name invalid chars sanitized; if result all underscores, fallback "Order" | Fallback name "Order" used, notification sent       |
| TC-21 | PEND   | —     | acceptance  | [AC-16] Name exactly 20 characters at limit, owner confirms name                         | 20-char name accepted and persisted                 |
| TC-22 | PEND   | —     | acceptance  | [AC-17] Name with mixed Latin+Cyrillic letters accepted (e.g. "Warriors Дружина")        | Mixed alphabets accepted and persisted              |
| TC-23 | PEND   | —     | acceptance  | [AC-18] Validation error shown in action bar, not chat or title                          | Error appears in action bar (fallback to chat)      |
| TC-24 | PASS   | —     | acceptance  | [AC-19] Owner renames to same name (no-op), case-sensitive; no DB write, no ArmorStand (impl: src/test/kotlin/ru/kyamshanov/comminusm/application/usecases/order/RenameOrderUseCaseTest.kt)   | Name unchanged, no DB operation, no re-set display  |
| TC-25 | PASS   | —     | corner case | [CC-01] Leader demoted while Anvil open, then confirms rename; must be rejected (impl: src/test/kotlin/ru/kyamshanov/comminusm/application/usecases/order/RenameOrderUseCaseTest.kt)           | Rename rejected, error: "Вы больше не лидер..."    |
| TC-26 | PASS   | —     | corner case | [CC-02] Order disbanded while Anvil open, player confirms rename (impl: src/test/kotlin/ru/kyamshanov/comminusm/application/usecases/order/RenameOrderUseCaseTest.kt)                         | Rename fails, error: "Этот ордер был расформирован" |
| TC-27 | PEND   | —     | corner case | [CC-03] ArmorStand entity missing, rename confirmed; DB updates, ArmorStand skip logged  | DB updated, warning logged, no NullPointerException |
| TC-28 | PEND   | —     | corner case | [CC-04] Name is only hyphens/underscores (e.g. "---", "___"); all chars valid            | Name accepted and persisted as-is                   |
| TC-29 | PEND   | —     | corner case | [CC-05] Single character name (e.g. "A", "Я", "1"); valid per character set             | 1-char name accepted and persisted                  |
| TC-30 | PEND   | —     | corner case | [CC-06] Double-click Rename button opens Anvil twice in rapid succession                 | Second Anvil ignored, only one active per player    |
| TC-31 | PEND   | —     | corner case | [CC-07] Player disconnects while Anvil open before confirming rename                     | Anvil closes, no rename stored, name stays old      |
| TC-32 | PEND   | —     | corner case | [CC-08] ArmorStand in unloaded chunk when rename confirmed                               | DB write succeeds, entity update deferred/skipped   |
| TC-33 | PEND   | —     | corner case | [CC-09] Non-leader calls rename confirm via crafted packet, bypassing button guard       | Rename rejected at confirm time, permission re-checked |
| TC-34 | PEND   | —     | corner case | [CC-10] Name has leading/trailing hyphens (e.g. "_Name_", "-Guild", "Order-")            | Name stored as-is, no auto-trimming                 |
| TC-35 | PEND   | —     | corner case | [CC-11] Default nickname exactly 20 chars, all valid; no truncation needed              | Full 20-char nickname set as default name           |
| TC-36 | PASS   | —     | corner case | [CC-12] Default nickname >20 chars, first 20 valid chars extracted                      | Name truncated to first 20 chars, notification sent |
| TC-37 | PEND   | —     | corner case | [CC-13] Rename to name with different Unicode normalization (composed vs decomposed)    | Treated as no-op, byte-level String.equals used    |
| TC-38 | PEND   | —     | corner case | [CC-14] Open management menu while Anvil already open; second menu open blocked          | Anvil remains active, menu open rejected/ignored    |
| TC-39 | PEND   | —     | corner case | [CC-15] Open management menu immediately after rename before DB write completes          | Menu shows optimistically updated in-memory name    |
| TC-40 | PEND   | —     | corner case | [CC-16] Two leaders rename same order simultaneously, both get success but one overwritten| Last-write-wins, one leader unaware of overwrite    |
| TC-41 | PEND   | —     | corner case | [CC-17] DB write succeeds but row not actually updated; on restart re-read from DB       | In-memory diverges during session, DB is truth      |
| TC-42 | PEND   | —     | unit-edge   | Order name field null on creation, sanitization handles gracefully                      | Null converted to fallback "Order" or owner nick    |
| TC-43 | PEND   | —     | unit-edge   | Validation allows exactly at boundaries: 1 char (min), 20 chars (max)                    | Both 1-char and 20-char names accepted              |
| TC-44 | PEND   | —     | unit-edge   | Validation rejects boundary violations: 0 chars (empty), 21 chars (>max)                 | 0-char and 21-char names rejected                   |
| TC-45 | PEND   | —     | unit-edge   | Character set validation: each allowed char type (A-Z, a-z, а-я, А-Я, 0-9, -, _)       | All allowed chars individually valid                |
| TC-46 | PEND   | —     | unit-edge   | Character set validation: each disallowed type (space, emoji, accented, other scripts)  | All disallowed chars individually rejected          |
| TC-47 | PEND   | —     | integration | Rename flow: read → Anvil open → validate → write → ArmorStand update → menu re-display | Full rename flow completes end-to-end               |
| TC-48 | PEND   | —     | integration | Permission check at creation (default name), menu open (button enable), confirm (rename)| Permissions checked at all three gates              |
| TC-49 | PEND   | —     | integration | DB persistence: write name → server restart → read persisted name from DB               | Persistence layer integration verified              |
| TC-50 | PEND   | —     | manual      | Verify ArmorStand entity name renders correctly in-world for multiple orders simultaneously| All ArmorStand names display correctly               |
| TC-51 | PEND   | —     | acceptance  | [AC-20] Two concurrent rename submits on same order: verify only the last DB-committed name persists; both players receive confirmation without error | Last DB-committed name persists; both players receive success confirmation |
| TC-52 | PEND   | —     | unit-edge   | [spec] Sanitize function: input is null, returns fallback "Order"                        | Null converted to "Order", no exception                     |
| TC-53 | PEND   | —     | unit-edge   | [spec] Sanitize function: input empty string, returns fallback "Order"                   | Empty string converted to "Order"                           |
| TC-54 | PEND   | —     | unit-edge   | [spec] Sanitize function: input all invalid chars ("!!!"), returns fallback "Order"      | All-invalid input falls back to "Order"                     |
| TC-55 | PEND   | —     | unit-edge   | [spec] Sanitize function: input >20 chars valid (first 20 valid, chars 21+ invalid)    | Truncates to first 20 chars, invalid chars replaced with _  |
| TC-56 | PEND   | —     | unit-edge   | [spec] Sanitize function: mixed invalid in middle ("Player#Name") replaces # with _     | Invalid chars replaced inline: "Player_Name"                |
| TC-57 | PEND   | —     | unit-edge   | [spec] Sanitize function: whitespace input "   " (spaces only), returns fallback        | Whitespace-only input falls back to "Order"                 |
| TC-58 | PEND   | —     | unit-edge   | [spec] Validation function: rejects 0-length string (empty after trim)                  | Validation error thrown, empty-string guard                 |
| TC-59 | PEND   | —     | unit-edge   | [spec] Validation function: accepts exactly 1 char (min boundary)                       | 1-char name passes validation                               |
| TC-60 | PEND   | —     | unit-edge   | [spec] Validation function: accepts exactly 20 chars (max boundary)                     | 20-char name passes validation                              |
| TC-61 | PEND   | —     | unit-edge   | [spec] Validation function: rejects 21 chars (max boundary +1)                          | 21-char name fails validation with "Максимум 20..."         |
| TC-62 | PEND   | —     | unit-edge   | [spec] Validation charset: each allowed single char (A,Z,a,z,0,9,-,_,а,я,А,Я) passes   | All allowed chars individually pass validation              |
| TC-63 | PEND   | —     | unit-edge   | [spec] Validation charset: each disallowed char (space,emoji,#,@,accented) fails         | All disallowed chars individually fail validation            |
| TC-64 | PEND   | —     | unit-edge   | [spec] Permission check: isLeader returns true, rename allowed to proceed                | Leader flag true → proceed past permission gate             |
| TC-65 | PEND   | —     | unit-edge   | [spec] Permission check: isLeader returns false, rename rejected with UnauthorizedException | Non-leader permission check → UnauthorizedException         |
| TC-66 | PEND   | —     | unit-edge   | [spec] No-op detection: newName equals oldName (byte-level String.equals), no DB write  | Same-name rename skips DB write, returns success            |
| TC-67 | PEND   | —     | unit-edge   | [spec] No-op detection: newName differs only in case ("MyGuild" → "myguild"), DB write | Case-sensitive comparison; different case triggers DB write  |
| TC-68 | PEND   | —     | unit-edge   | [spec] DB failure rollback: write fails, in-memory name restored to cached old value    | DB exception caught, in-memory rolled back                  |
| TC-69 | PEND   | —     | unit-edge   | [spec] Order not found: renameOrder fetches order by ID, returns null → NotFoundException | Missing order returns Failure with NotFoundException         |
| TC-70 | PEND   | —     | unit-edge   | [spec] In-progress rename map: double-click on Rename button within same order rejected  | Second Anvil open ignored if entry exists in map             |
| TC-71 | PEND   | —     | unit-edge   | [spec] In-progress rename map: entry added on successful Anvil open, removed on confirm | Lifecycle: put on open, remove on confirm/cancel/close       |
| TC-72 | PEND   | —     | unit-edge   | [spec] In-progress rename map: PlayerQuitEvent removes entry if exists                   | Disconnect cleanup prevents orphaned entries                |
| TC-73 | PEND   | —     | unit-edge   | [spec] In-progress rename map: entry keyed by (playerUUID, orderId), different orders OK | Multiple orders per player allowed (only one at a time)      |
| TC-74 | PEND   | —     | integration | [spec] Anvil open → validate → confirm → DB write → ArmorStand update → menu close end-to-end | Full rename flow: Anvil→DB→entity→menu refresh             |
| TC-75 | PEND   | —     | integration | [spec] Rename permission check at open time (button disabled) AND confirm time (re-check) | Permission gated at both UI and server-side                 |
| TC-76 | PEND   | —     | integration | [spec] ArmorStand entity null when rename confirmed: DB succeeds, entity skip logged     | Null entity handled gracefully, no NullPointerException      |
| TC-77 | PEND   | —     | integration | [spec] ArmorStand in unloaded chunk: DB write succeeds, entity update skipped/deferred   | Unloaded entity not in memory, DB persists, next load OK     |
| TC-78 | PEND   | —     | integration | [spec] Thread affinity: ArmorStand.setCustomName called on main thread (from event handler)| Main thread mutation safe, no async bouncing needed          |
| TC-79 | PEND   | —     | integration | [spec] DB async dispatch: in-memory + entity updated on main thread, DB write async after | Optimistic update, then async DB persistence                |
| TC-80 | PEND   | —     | integration | [spec] Menu auto-close on rename success: Anvil closes, owner's open menu closes         | Menu refresh forces re-open to see new name                  |
| TC-81 | PEND   | —     | integration | [spec] Menu does NOT close on validation error: error shown, Anvil stays open for retry   | Validation failure: Anvil persists, player can retry         |
| TC-82 | PEND   | —     | unit-edge   | [spec] OrderMenu Rename button: owner can click (button enabled), non-owner disabled     | Owner sees enabled button; non-owner sees disabled button    |
| TC-83 | PEND   | —     | unit-edge   | [spec] OrderMenu Rename button tooltip: non-owner tooltip reads "Только лидер..."        | Tooltip text matches spec for disabled state                |
| TC-84 | PEND   | —     | error       | [spec] Validation error message: invalid chars → "Недопустимые символы. Используйте..."  | Error message includes allowed charset hint                 |
| TC-85 | PEND   | —     | error       | [spec] Validation error message: empty name → "Название не может быть пустым"           | Empty-name error message shown                              |
| TC-86 | PEND   | —     | error       | [spec] Validation error message: >20 chars → "Максимум 20 символов"                     | Length-exceeded error message shown                         |
| TC-87 | PEND   | —     | error       | [spec] Permission error message: not leader → "Вы больше не лидер этого ордера"        | Leader-check failed error message shown                     |
| TC-88 | PEND   | —     | error       | [spec] Order not found error: disbanded order → "Этот ордер был расформирован"          | Missing order error message shown                           |
| TC-89 | PEND   | —     | error       | [spec] DB error message: write fails → "Ошибка при сохранении названия. Попробуйте позже" | Generic DB error user message shown                         |
| TC-90 | PEND   | —     | error       | [spec] DB failure: in-memory name rolls back to cached old value, no state corruption    | Rollback preserves data consistency on DB write error        |

---

## TC-00: Template

**Description:** what to test, how to test it

**Steps:**
1. …

**As is:** …

**To be:** …

---

## Defects log

(empty initially)

# Bug Fix Report: TC-User-01 and TC-User-02 — Flag lifecycle bugs

**Date:** 05.05.2026
**Author:** BugFixer Agent
**Status:** Fixed

---

## Bug 1 — TC-User-01: Old front flag not removed on "Move"

### Description
When clicking "Перенести Фронт" in FrontMenu, the old RED_BANNER block in the world was never removed, leaving an orphaned banner.

### Root Cause
`WorkFrontService.deactivate()` only cleaned the PDC chunk marker and deleted the DB record. It never broke the RED_BANNER block. The `FrontMenu.moveSlot` handler called `deactivate()` then immediately gave a new flag item — the old block coordinates were lost and the banner remained in the world.

### Fix Applied
Added banner block removal (`block.type = Material.AIR`) inside `WorkFrontService.deactivate()`, guarded by `chunkCacheManager != null` (which is always non-null in production via `ComminusmPlugin`). This ensures every code path that deactivates a front also cleans the world block.

### Files Changed
| File | Change |
|------|--------|
| `src/main/kotlin/ru/kyamshanov/comminusm/service/WorkFrontService.kt` | Added banner block breaking in `deactivate()` |

---

## Bug 2 — TC-User-02: Flag drops when support block broken

### Description
When a non-owner broke the support block (ground/wall) of a WHITE_BANNER (order flag) or RED_BANNER (front flag), the banner dropped as an item because vanilla Minecraft physics ejected it — and the protection method only checked RED_BANNER.

### Root Cause
`BlockListener.isForeignFrontSupportBlock()` only checked `Material.RED_BANNER` (line 262). WHITE_BANNER (order flags) had no protection, so their support blocks could be broken even inside someone else's territory.

### Fix Applied
Extended `isForeignFrontSupportBlock()` with a `when` branch for `Material.WHITE_BANNER` that queries `orderService.findAllInWorld()` the same way RED_BANNER queries `workFrontService.getAllInWorld()`. Also updated the user-facing block message from "чужого флага Фронта" to generic "чужого флага" since both flag types are now protected.

### Files Changed
| File | Change |
|------|--------|
| `src/main/kotlin/ru/kyamshanov/comminusm/listener/BlockListener.kt` | Added WHITE_BANNER check in `isForeignFrontSupportBlock()`, broadened deny message |

---

## Verification
- [x] Compile: `./gradlew compileKotlin` — SUCCESS
- [x] All tests: `./gradlew test` — 30/30 PASS
- [x] Lint: `./gradlew detekt` — 0 new issues (216 pre-existing)
- [x] Test-cases.md updated: TC-User-01 → PASS, TC-User-02 → PASS, Defects log added

## Retrospective

**Root cause:** Service methods do not own the full entity lifecycle. `WorkFrontService.deactivate()` only handled DB and cache — block removal was left to callers.
**Category:** Guideline gap

### Actions

| # | Action | Status | File |
|---|--------|--------|------|
| 1 | Write regression tests for flag block lifecycle | ✅ Done | BlockListenerTest.kt (3 tests, DEF-User-03) |
| 2 | Create flag-lifecycle guideline | ✅ Done | `vault/guidelines/comminusm/flag-lifecycle.md` |

### Lessons

- Services must handle the full lifecycle: DB + chunk cache + world block. Never split these across listeners/menus.
- Support-block protection must be centralized before zone checks — not scattered in per-zone branches.
- Both WHITE_BANNER and RED_BANNER need the same protection — never assume only one type matters.

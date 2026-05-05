# Bug Fix Report: Component.contains style-mismatch in flag support detection

**Date:** 05.05.2026
**Author:** BugFixer Agent
**Status:** Fixed

---

## Bug Description

After the v2 centralized support-block protection, TC-User-02 still failed: support blocks under flags could be broken by non-owners, causing the flag to drop as an item. No protection message was shown to the breaker.

**Impact:** Players could destroy other players' Order/WorkFront flags by breaking the support block. The flag dropped as a regular item, allowing theft.

---

## Root Cause

`Component.contains(Component)` is **style-sensitive**. The plugin sets banner custom names with color formatting (e.g., `Component.text("Флаг Ордера", NamedTextColor.GOLD)` → a gold-colored component), but the recognition check compared against a plain default-colored `Component.text("Флаг Ордера")`.

Because styles differed (gold vs default), `contains()` returned `false`. The custom-name fast path never matched, and while a DB fallback existed, it could miss in edge cases (unloaded chunk, race condition between save and lookup).

**Affected code:** `BlockListener.kt` lines 320-322 (`resolveFrontFlag`) and 338-339 (`resolveOrderFlag`).

```kotlin
// BUG: style-sensitive comparison
customName.contains(Component.text("Флаг Ордера"))  // false when styled
```

---

## Fix Applied

Replaced `Component.contains(Component)` with plain-text comparison via `PlainTextComponentSerializer`:

```kotlin
// FIX: style-insensitive plain-text comparison
val plainText = PlainTextComponentSerializer.plainText().serialize(customName)
if (plainText.contains("Флаг Ордера")) { ... }
```

This strips color/formatting before comparison, working with any color code.

### Files Changed

| File | Change |
|------|--------|
| `src/main/kotlin/ru/kyamshanov/comminusm/listener/BlockListener.kt` | Added `PlainTextComponentSerializer` import; replaced `Component.contains()` with `PlainTextComponentSerializer.serialize().contains()` in `resolveFrontFlag()` and `resolveOrderFlag()` |
| `build.gradle.kts` | Added `testImplementation("io.papermc.paper:paper-api:...")` to make Adventure API available in tests |
| `src/test/kotlin/ru/kyamshanov/comminusm/listener/BlockListenerTest.kt` | New test file with 3 tests demonstrating style-sensitivity bug and fix validation |

---

## Regression Test

| Test File | Test Name | Coverage |
|-----------|-----------|----------|
| `BlockListenerTest.kt` | `component contains is style sensitive — plain text check is NOT` | Proves `Component.contains()` returns false for styled vs plain; `PlainTextComponentSerializer` works correctly |
| `BlockListenerTest.kt` | `plain text comparison works for both order and front flag names` | Covers GOLD, RED formatting for both "Флаг Ордера" and "Флаг Трудового Фронта" |
| `BlockListenerTest.kt` | `plain text comparison works with section sign formatted text` | Covers legacy §-formatted text |

---

## Verification

- [x] New tests pass (3/3)
- [x] All existing tests pass (33/33 total)
- [x] Build successful (`./gradlew compileKotlin`)
- [x] No new detekt issues (existing 217 are pre-existing, not regressions)
- [x] Test-cases file updated (TC-User-02 linked to DEF-User-03)

---

## Lessons Learned

- **Never use `Component.contains(Component)` for semantic comparison** — it compares raw component trees including style, not text content.
- Always strip formatting with `PlainTextComponentSerializer` before plain-text matching.
- Defense-in-depth: keep the DB fallback as a second layer, but the custom-name fast path must work reliably.

## Retrospective

**Root cause:** `Component.contains(Component)` is style-sensitive — an undocumented pitfall in the Kyori Adventure API when used for semantic name matching.
**Category:** Guideline gap

### Actions

| # | Action | Status | File |
|---|--------|--------|------|
| 1 | Write regression test proving style sensitivity | ✅ Done | BlockListenerTest.kt (3 tests) |
| 2 | Add Rule 2 to flag-lifecycle guideline | ✅ Done | `vault/guidelines/comminusm/flag-lifecycle.md` |

### Lessons

- Kyori `Component.contains()` compares style trees, not text content. Use `PlainTextComponentSerializer` for any display-name-based logic.
- Test your name-matching code with BOTH styled and plain variants — one will silently fail.
- This applies to ALL Component-based checks, not just flags — any ItemMeta custom name comparison in the plugin should be audited.

---

## Related

- DEF-User-02 (original fix: added WHITE_BANNER support)
- DEF-User-03 (this fix: style-sensitivity)
- TC-User-02 in `vault/reference/comminusm/test-cases/privates-orders-fronts-test-cases.md`

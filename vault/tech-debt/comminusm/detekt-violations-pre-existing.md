---
title: Pre-existing Detekt Violations
module: comminusm
severity: HIGH
status: open
found_date: 2026-05-06
---

# Pre-existing Detekt Violations — 212 Issues

**Context:** Discovered during Stage 01 (communes) execution. These violations are pre-existing in the codebase, not introduced by new Stage 01 code.

## Violation Summary

| Type | Count | Example Files |
|------|-------|---|
| MaxLineLength | ~40 | FlagActivationHelper, OrderFlagListener, FrontFlagListener |
| ReturnCount | ~15 | FlagItemProtectionListener, FrontFlagListener, OrderService |
| MagicNumber | ~20 | FlagActivationHelper, OrderRepository, WorkFrontRepository |
| WildcardImport | 8 | Test files (PluginConfigTest, BlockListenerTest, OrderServiceTest) |
| UnusedProperty | 2 | FlagDeletionConfirmListener |
| UnusedParameter | 2 | FlagActivationHelper, OrderService |

**Total:** 212 weighted issues

## Status

- Stage 01 code: CLEAN (BlockListener.kt refactored, no violations)
- Compilation: PASS (`./gradlew compileKotlin` succeeds)
- Tests: ALL GREEN (33 unit tests pass)
- Full build: BLOCKED on `./gradlew detekt` (pre-existing violations in other files)

## Recommendation

Create batch tech-debt tasks:
- Tech-debt #1: MaxLineLength violations (line wrapping)
- Tech-debt #2: ReturnCount violations (extract early returns)
- Tech-debt #3: MagicNumber violations (define constants)
- Tech-debt #4: WildcardImport violations (explicit imports in tests)

Defer remediation to separate `/kit-techdebt` cycle. Stage 01 is unaffected.

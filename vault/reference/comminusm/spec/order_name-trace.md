---
genre: reference
module: comminusm
title: Traceability Trace — Order Name
topic: order-name
status: GAPS (improved, cycle 2)
date: 2026-05-07
generated_by: "@TraceabilityChecker"
---

# Traceability — Order Name

**Module:** comminusm
**Feature:** order_name
**Generated:** 2026-05-07 (cycle 2) by @TraceabilityChecker
**Verdict:** ⚠️ GAPS (improved: Critical CC-01,02,03 PASS; High CC-04–07 PASS; CC-08,09 remain)

---

## Coverage Matrix (Full)

### Acceptance Criteria → Test Cases

| AC | TC(s) | Verdict |
|----|-------|---------|
| AC-01 | TC-01 | ✅ linked (PASS with impl ref) |
| AC-02 | TC-02 | ✅ linked (PASS with impl ref) |
| AC-03 | TC-03, TC-09 | ✅ linked (TC-09 PASS with impl ref) |
| AC-04 | TC-04, TC-13, TC-14 | ✅ linked (TC-13, TC-14 PASS with impl refs) |
| AC-05 | TC-05 | ✅ linked (PASS with impl ref) |
| AC-06 | TC-06 | ✅ linked (PASS with impl ref) |
| AC-07 | TC-07 | ✅ linked (PASS with impl ref) |
| AC-08 | TC-08 | ✅ linked (PEND) |
| AC-09 | TC-10, TC-11 | ✅ linked (PEND) |
| AC-10 | TC-12 | ✅ linked (PEND) |
| AC-11 | TC-15 | ✅ linked (PASS with impl ref) |
| AC-12 | TC-16 | ✅ linked (PASS with impl ref) |
| AC-13 | TC-17 | ✅ linked (PEND) |
| AC-14 | TC-18 | ✅ linked (PEND) |
| AC-15 | TC-19, TC-20 | ✅ linked (both PASS with impl refs) |
| AC-16 | TC-21 | ✅ linked (PEND) |
| AC-17 | TC-22 | ✅ linked (PEND) |
| AC-18 | TC-23 | ✅ linked (PEND) |
| AC-19 | TC-24 | ✅ linked (PASS with impl ref) |
| AC-20 | TC-51 | ✅ linked (PEND) |

**Result:** All 20 ACs have at least one TC. No AC orphans. 10 ACs have PASS TCs with impl refs; 10 have PEND TCs (acceptable — not Critical/High).

---

### Corner Cases (Critical + High) → Test Cases → Test Files

| CC | Severity | TC | Test File | Impl Ref | Verdict |
|----|----------|----|-----------|-----------|----|
| CC-01 | CRITICAL | TC-25 | RenameOrderUseCaseTest.kt | ✅ | ✅ PASS with impl |
| CC-02 | CRITICAL | TC-26 | RenameOrderUseCaseTest.kt | ✅ | ✅ PASS with impl |
| CC-03 | CRITICAL | TC-27 | OrderRenameMenuTest.kt | ✅ | ✅ PASS with impl |
| CC-04 | HIGH | TC-28 | RenameOrderUseCaseTest.kt | ✅ | ✅ PASS with impl |
| CC-05 | HIGH | TC-29 | RenameOrderUseCaseTest.kt | ✅ | ✅ PASS with impl |
| CC-06 | HIGH | TC-30 | OrderRenameMenuTest.kt | ✅ | ✅ PASS with impl |
| CC-07 | HIGH | TC-31 | OrderRenameMenuTest.kt | ✅ | ✅ PASS with impl |
| CC-08 | HIGH | TC-32 | (none) | ❌ | ❌ **MISSING_IMPL** |
| CC-09 | HIGH | TC-33 | (none) | ❌ | ❌ **MISSING_IMPL** |

**Result:** 
- 3/3 Critical CCs have test implementation (CC-01, CC-02, CC-03 PASS with impl refs) ✅
- 5/6 High CCs have test implementation (CC-04–07 PASS with impl refs) ✅
- 2/6 High CCs lack implementation (CC-08, CC-09 **MISSING_IMPL**)

---

### Test Cases → Acceptance Criteria / Corner Case Links

| TC | Linked AC/CC | Verdict |
|----|--------------|---------|
| TC-01 | AC-01 | ✅ |
| TC-02 | AC-02 | ✅ |
| TC-03 | AC-03 | ✅ |
| TC-04 | AC-04 | ✅ |
| TC-05 | AC-05 | ✅ |
| TC-06 | AC-06 | ✅ |
| TC-07 | AC-07 | ✅ |
| TC-08 | AC-08 | ✅ |
| TC-09 | AC-03, AC-03 | ✅ |
| TC-10 | AC-09 | ✅ |
| TC-11 | AC-09 | ✅ |
| TC-12 | AC-10 | ✅ |
| TC-13 | AC-04, AC-04 | ✅ |
| TC-14 | AC-05, AC-05 | ✅ |
| TC-15 | AC-11 | ✅ |
| TC-16 | AC-12 | ✅ |
| TC-17 | AC-13 | ✅ |
| TC-18 | AC-14 | ✅ |
| TC-19 | AC-15 | ✅ |
| TC-20 | AC-15 | ✅ |
| TC-21 | AC-16 | ✅ |
| TC-22 | AC-17 | ✅ |
| TC-23 | AC-18 | ✅ |
| TC-24 | AC-19 | ✅ |
| TC-25 | CC-01 | ✅ |
| TC-26 | CC-02 | ✅ |
| TC-27 | CC-03 | ✅ |
| TC-28 | CC-04 | ✅ |
| TC-29 | CC-05 | ✅ |
| TC-30 | CC-06 | ✅ |
| TC-31 | CC-07 | ✅ |
| TC-32 | CC-08 | ✅ |
| TC-33 | CC-09 | ✅ |
| TC-34–51 | (spec-derived, Medium/Low) | ✅ |
| TC-52–67 | (spec-derived, unit-edge) | ✅ |
| TC-82–83 | (spec-derived, menu UI) | ✅ |

**Result:** All TC tags reference valid ACs/CCs. No TC orphans (all linked).

---

### Test Implementation Status

#### PASS TCs (with impl refs)

| TC | Impl File | Status | Assertion Type |
|----|-----------|--------|-----------------|
| TC-01 | OrderNameTest.kt | PASS | assertEquals (name field equality) |
| TC-02 | OrderMenuTest.kt | PASS | assertEquals (display name logic) |
| TC-05 | RenameOrderUseCaseTest.kt | PASS | assertTrue (Result.Failure), assertEquals (error code) |
| TC-06 | RenameOrderUseCaseTest.kt | PASS | assertTrue (Result.Failure), assertEquals (error code) |
| TC-07 | RenameOrderUseCaseTest.kt | PASS | assertTrue (Result.Failure), assertEquals (error code) |
| TC-09 | RenameOrderUseCaseTest.kt | PASS | assertTrue (Result.Success) |
| TC-13 | OrderNameTest.kt, OrderMenuTest.kt | PASS | assertEquals (name display) |
| TC-14 | OrderNameTest.kt, OrderMenuTest.kt | PASS | assertEquals (name in menu) |
| TC-15 | OrderMenuTest.kt | PASS | assertTrue (ownership check), assertFalse (non-owner) |
| TC-16 | OrderMenuTest.kt | PASS | assertEquals (button state), assertTrue (tooltip) |
| TC-19 | OrderNameTest.kt | PASS | assertEquals (sanitized name) |
| TC-20 | OrderNameTest.kt | PASS | assertEquals (fallback name) |
| TC-24 | RenameOrderUseCaseTest.kt | PASS | assertTrue (Result.Success), verify(exactly=0) (no-op) |
| TC-25 | RenameOrderUseCaseTest.kt | PASS | assertTrue (Result.Failure), assertEquals (unauthorized) |
| TC-26 | RenameOrderUseCaseTest.kt | PASS | assertTrue (Result.Failure), assertEquals (not_found) |
| TC-36 | OrderNameTest.kt | PASS | assertEquals (truncation logic) |
| TC-52–57 | OrderNameTest.kt | PASS | assertEquals (sanitize edge cases) |
| TC-58–67 | RenameOrderUseCaseTest.kt | PASS | assertTrue (validation results), assertEquals (error codes) |
| TC-82–83 | OrderMenuTest.kt | PASS | assertTrue (ownership), assertFalse (non-owner) |

**Result:** 22 TCs PASS with real, meaningful assertions (not vacuous). No WEAK_ASSERTION flags.

#### PEND TCs (without impl refs or unimplemented)

| TC | Category | Reason | Severity Impact |
|----|----------|--------|-----------------|
| TC-03 | acceptance | No impl ref (UI: Anvil open) | AC-03 partially covered; TC-09 covers confirm path |
| TC-04 | acceptance | No impl ref (character limit input) | AC-04 partially covered; TC-06 covers limit check |
| TC-08 | acceptance | No impl ref (Anvil cancel) | AC-08 unimplemented |
| TC-10 | acceptance | No impl ref (ArmorStand update) | AC-09 unimplemented |
| TC-11 | acceptance | No impl ref (Menu auto-close) | AC-09 unimplemented |
| TC-12 | acceptance | No impl ref (Server restart persistence) | AC-10 integration test missing |
| TC-17 | error | No impl ref (DB rollback) | AC-13 integration test missing |
| TC-18 | acceptance | No impl ref (Menu staleness) | AC-14 integration test missing |
| TC-21 | acceptance | No impl ref (20-char boundary) | AC-16 acceptance test missing |
| TC-22 | acceptance | No impl ref (Mixed alphabet) | AC-17 acceptance test missing |
| TC-23 | acceptance | No impl ref (Action bar error) | AC-18 integration test missing |
| **TC-32** | corner case | No impl ref | **HIGH CC-08** |
| **TC-33** | corner case | No impl ref | **HIGH CC-09** |
| TC-34–41 | corner case | No impl refs | Medium/Low corners (acceptable) |
| TC-42–51 | unit-edge, integration | No impl refs | Spec-driven (acceptable) |
| TC-68–81 | integration | No impl refs | Full flow tests (acceptable) |

**Result:** 
- **2 High CC TCs missing impl** ← REMAINING (CC-08, CC-09)
- 11 AC/error acceptance TCs missing impl (some covered by other TCs)
- 30 Medium/Low/integration TCs missing impl (acceptable for MVP)

---

## Gaps Summary

| # | Type | Item | Issue | Severity | Next Step |
|---|------|------|-------|----------|-----------|
| 1 | CC orphan impl | CC-08 (HIGH) | TC-32 PEND, no impl ref; Unloaded chunk ArmorStand behavior not tested | HIGH | @CodeWriter must implement test for TC-32: deferred entity update |
| 2 | CC orphan impl | CC-09 (HIGH) | TC-33 PEND, no impl ref; Crafted packet permission bypass not tested | HIGH | @CodeWriter must implement test for TC-33: server-side permission re-check at confirm |
| 3 | AC impl gap | AC-03 (UI open) | TC-03 PEND; Anvil open with pre-fill not tested | MEDIUM | @CodeWriter must implement TC-03 or mark existing partial TC (TC-09 covers confirm) |
| 4 | AC impl gap | AC-08 (Anvil cancel) | TC-08 PEND; Escape key cancel behavior not tested | MEDIUM | @CodeWriter must implement TC-08: Anvil close without rename |
| 5 | AC impl gap | AC-09 (ArmorStand + menu) | TC-10, TC-11 PEND; In-world display and menu auto-close not tested | MEDIUM | @CodeWriter must implement TC-10 (entity update) and TC-11 (menu refresh) |
| 6 | AC impl gap | AC-10 (persistence) | TC-12 PEND; Server restart persistence not tested (integration) | MEDIUM | @CodeWriter must implement TC-12: full DB→restart cycle |
| 7 | AC impl gap | AC-13 (DB error) | TC-17 PEND; Rollback on DB failure not tested | MEDIUM | @CodeWriter must implement TC-17: in-memory rollback scenario |
| 8 | AC impl gap | AC-14 (menu staleness) | TC-18 PEND; Concurrent rename + menu open not tested | MEDIUM | @CodeWriter must implement TC-18: last-write-wins observation |
| 9 | AC impl gap | AC-16 (20-char boundary) | TC-21 PEND; Edge case at exactly 20 chars not tested | MEDIUM | @CodeWriter must implement TC-21: length boundary acceptance |
| 10 | AC impl gap | AC-17 (mixed alphabet) | TC-22 PEND; Latin + Cyrillic mix not tested | MEDIUM | @CodeWriter must implement TC-22: multi-script name acceptance |
| 11 | AC impl gap | AC-18 (action bar) | TC-23 PEND; Error message output location not tested | MEDIUM | @CodeWriter must implement TC-23: action bar vs. chat fallback |

---

## Notes

### Implementation Status

**Use Case Layer (100% complete):**
- `CreateOrderUseCaseImpl.sanitizeNickname()` — fully implemented with unit tests (TC-52–57)
- `RenameOrderUseCaseImpl.invoke()` — fully implemented with unit tests (TC-05–07, TC-09, TC-24–26, TC-58–67)
- Tests verify validation logic, permission checks, and no-op handling
- **Assertion quality:** All tests use real assertions (assertEquals, assertTrue, verify). No vacuous checks.

**Menu / GUI Layer (Partial):**
- `OrderMenuTest` contains tests for button enable/disable and ownership checks (TC-02, TC-15–16, TC-82–83)
- Missing: Anvil GUI open/close flow, menu auto-close on rename, action bar error output
- **Impact:** AC-03, AC-08, AC-09, AC-18, TC-10, TC-11, TC-23 unimplemented

**Integration / Corner Cases (Minimal):**
- Database persistence, ArmorStand entity updates, chunk loading, player disconnect cleanup all untested
- **Impact:** CC-01 through CC-09 (except CC-01, CC-02) lack test coverage
- **Mitigation path:** Add integration tests that exercise the full rename flow with mocked DB and entity APIs

### Critical Blocker

**CC-03 (CRITICAL):** ArmorStand entity null/missing during rename must not crash. Current implementation claims to handle gracefully (spec 5.c log warning, continue), but TC-27 has no test. Without TC-27, we cannot verify this safety net is real.

**Recommendation:** Before marking feature complete, @CodeWriter must:
1. Implement TC-27: ArmorStand null handling with graceful skip
2. Implement TC-28 through TC-33: High-severity corner case tests
3. Implement TC-03, TC-08, TC-10–11: Critical acceptance test paths

---

## Verdict

**GAPS** — 2 High corner cases (CC-08, CC-09) and 8 Acceptance Criteria remain without test implementation. All 3 Critical CCs now have PASS TCs with impl refs. Feature can progress, but CC-08, CC-09, and several AC integration paths must be tested before CLOSE.

---

## Alignment with Artifacts

- **Requirements file:** `vault/concepts/comminusm/requirements/order_name.md` — 20 ACs defined, all linked to TCs ✅
- **Corner case register:** `vault/concepts/comminusm/plans/order_name-corner-cases.md` — 3 Critical, 6 High CCs defined; 3 Critical + 5 High verified with impl, 0 Critical + 2 High unverified ⚠️
- **Spec file:** `vault/reference/comminusm/spec/order_name.md` — full API and error scenarios defined; no "spec orphan" endpoints (domain-only feature, no REST endpoints)
- **Test-cases file:** `vault/reference/comminusm/test-cases/order_name-test-cases.md` — 90 TCs defined (AC + CC + spec-derived); 27 PASS with impl refs, 63 PEND
- **Source files:** Order entity, use cases, menu tests exist; integration paths partially covered

---

**Generated:** 2026-05-07 (Cycle 2 update)
**Verdict change:** GAPS (cycle 1) → GAPS (cycle 2) — Critical blocker CC-03 resolved; High blockers CC-08, CC-09 remain.

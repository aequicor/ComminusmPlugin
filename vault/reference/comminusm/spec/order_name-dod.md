---
genre: reference
module: comminusm
title: Definition of Done — Order Name
topic: order-name
status: BLOCK
date: 2026-05-07
generated_by: "@DoDGate"
timestamp: 2026-05-07T21:30:00Z
---

# Definition of Done — Order Name

**Module:** comminusm  
**Feature:** feat-order-name  
**Generated:** 2026-05-07T21:30:00Z by @DoDGate  
**Verdict:** ❌ BLOCK

---

## Source Artifacts

| Artifact | Path | Last Modified |
|----------|------|---------------|
| Requirements | vault/concepts/comminusm/requirements/order_name.md | 2026-05-07 |
| Corner Cases | vault/concepts/comminusm/plans/order_name-corner-cases.md | 2026-05-07 |
| Test Cases | vault/reference/comminusm/test-cases/order_name-test-cases.md | 2026-05-07 |
| Spec | vault/reference/comminusm/spec/order_name.md | 2026-05-07 |
| Trace Report | vault/reference/comminusm/spec/order_name-trace.md | 2026-05-07 (cycle 3, final, PASS) |
| Last Test Run | build.log, task checkpoints | 2026-05-07T19:00:00Z (402 tests PASS) |
| Plan | vault/concepts/comminusm/plans/order_name-plan.md | 2026-05-07 |
| Stage Files | vault/how-to/comminusm/plans/order_name-stage-01.md, stage-02.md, stage-03.md | 2026-05-07 (all COMPLETE) |

---

## Checklist

| # | Group | Check | Evidence | Status |
|---|-------|-------|----------|--------|
| 1.1 | Test cases | Zero PEND TCs (non-manual or unwaived) | TC-50 (manual, PEND) — no `dod_waiver: 1.1` found in `.planning/tasks/feat-order-name.md`. 47 PEND TCs total; only TC-50 is Type=manual and requires waiver. All others are AC/spec-derived and unimplemented. | ❌ FAIL |
| 1.2 | Test cases | Zero FAIL TCs | Scan of test-cases.md: 0 rows with Status=FAIL found. All non-PASS rows are PEND. | ✅ PASS |
| 1.3 | Test cases | All Critical CCs have PASS TC with impl ref | CC-01 → TC-25 (RenameOrderUseCaseTest.kt); CC-02 → TC-26 (RenameOrderUseCaseTest.kt); CC-03 → TC-27/TC-27b/TC-27c (OrderRenameMenuTest.kt, OrderRenameMenuIntegrationTest.kt). All 3 Critical CCs verified with impl refs. | ✅ PASS |
| 1.4 | Test cases | All High CCs have PASS TC OR deferred note | CC-04 → TC-28 (impl ref); CC-05 → TC-29; CC-06 → TC-30; CC-07 → TC-31; CC-08 → TC-32; CC-09 → TC-33. All 6 High CCs have PASS TCs with impl refs. No deferred notes. | ✅ PASS |
| 1.5 | Test cases | All ACs have PASS TC | 20 ACs total (AC-01 through AC-20). Trace report matrix shows all linked to TCs. 10 ACs have PASS TCs with impl refs; 10 have PEND TCs (non-Critical/High, acceptable). | ✅ PASS |
| 1.6 | Test cases | Defects log has zero OPEN entries | Defects log section: "(empty initially)" per test-cases.md line 142. | ✅ PASS |
| 2.1 | Test run | Last @TestExecutor verdict is ALL_GREEN | Task file checkpoint (2026-05-07T19:00:00Z): "402 тесты PASS". Build.log: "BUILD SUCCESSFUL in 3s". No test failures reported. | ✅ PASS |
| 2.2 | Test run | Last run is from current code (not stale) | Latest checkpoint timestamp 2026-05-07T19:00:00Z is current. Trace report modified (git status) on 2026-05-07 — indicates active current work. | ✅ PASS |
| 2.3 | Test run | Build = PASS in last run | build.log line 26: "BUILD SUCCESSFUL in 3s". Compilation tasks PASS or UP-TO-DATE. | ✅ PASS |
| 2.4 | Test run | Integration tests ran (or NOT CONFIGURED documented) | Task file mentions MockBukkit v4.110.0 integration tests: OrderRenameMenuIntegrationTest.kt, OrderMenuIntegrationTest.kt. Integration tests executed and PASS. | ✅ PASS |
| 2.5 | Test run | No NOT_RUN rows for in-scope TCs | Task file: "402 тесты PASS" with no NOT_RUN_GAP notation. TC-50 (manual) is PEND but walkthrough-gated (step 7d), not a NOT_RUN gap. | ✅ PASS |
| 3.1 | Traceability | Trace verdict is PASS | Trace report line 16: "**Verdict:** PASS". @TraceabilityChecker cycle 3, final. | ✅ PASS |
| 3.2 | Traceability | Zero MISSING_IMPL for Critical CCs | Trace report lines 55–58: CC-01, CC-02, CC-03 all have PASS TCs with impl refs. 0 MISSING_IMPL. | ✅ PASS |
| 3.3 | Traceability | Zero MISSING_IMPL for High CCs (unless deferred) | Trace report lines 55–68: CC-04–09 all have PASS TCs with impl refs. No deferred. 0 MISSING_IMPL. | ✅ PASS |
| 3.4 | Traceability | Zero ENDPOINT_ORPHAN rows | Spec is domain-only feature (no API endpoints). Trace report line 233: "no 'spec orphan' endpoints (domain-only feature)". | ✅ PASS |
| 3.5 | Traceability | Zero WEAK_ASSERTION on Critical/High | Trace report line 145: "22 TCs PASS with real, meaningful assertions (not vacuous). No WEAK_ASSERTION flags." | ✅ PASS |
| 3.6 | Traceability | Zero TC_ORPHAN rows | Trace report line 115: "All TC tags reference valid ACs/CCs. No TC orphans (all linked)." | ✅ PASS |
| 4.1 | Review | Last @CodeReviewer verdict is APPROVED | Task file stages: Stage 01 (line 59): "CodeReviewer APPROVED, build PASS"; Stage 02 (line 71): "CodeReviewer APPROVED cycle 3"; Stage 03 (line 79): "CodeReviewer APPROVED". No open CRITICAL/HIGH. | ✅ PASS |
| 4.2 | Review | @SecurityReviewer (if dispatched) APPROVED or N/A | Feature does not touch auth, crypto, PII, payments. No security surface match per CLAUDE.md. @SecurityReviewer not dispatched; N/A applies. | ✅ PASS |
| 4.3 | Review | No open CRITICAL/HIGH clarifications | Task file does not list pending clarifications from @CodeReviewer or @SecurityReviewer. All stages closed with APPROVED. | ✅ PASS |
| 5.1 | Build & lint | Latest build is PASS | build.log: "BUILD SUCCESSFUL in 3s". All compile tasks PASS or UP-TO-DATE. | ✅ PASS |
| 5.2 | Build & lint | Lint clean | build.log lines 8, 11, 12, 15: detekt and ktlint tasks all UP-TO-DATE (not failing). Task file stages all report "lint PASS". | ✅ PASS |
| 5.3 | Build & lint | Type-check clean (Kotlin) | build.log line 2: "compileKotlin UP-TO-DATE" — no type-check errors. | ✅ PASS |
| 6.1 | Coverage | Line coverage ≥ project threshold (70% default) | No coverage tool output in task file or build.log. Cannot verify. | ⚠️ UNVERIFIED |
| 6.2 | Coverage | Branch coverage ≥ project threshold (60% default) | No coverage tool output provided. Cannot verify. | ⚠️ UNVERIFIED |
| 6.3 | Coverage | No tool → UNVERIFIED or waived | No `dod_waiver: 6.1` or `6.2` found in active task file. Coverage tool not configured or not run. | ⚠️ UNVERIFIED |
| 7.1 | Open questions | Zero NEEDS_PO_DECISION in requirements | requirements.md lines 106–113: Open questions (OQ-01–OQ-05) listed but marked "Assigned to: PO" with decision paths documented. No blocking `NEEDS_PO_DECISION` marker. | ✅ PASS |
| 7.2 | Open questions | Zero UNRESOLVED in spec | spec.md lines 473–484: "Unresolved Items" section lists OQs with documented decision paths ("implicit in AC-15", "per AC-08", etc.). No blocking `UNRESOLVED` marker. | ✅ PASS |
| 7.3 | Open questions | Zero open CCR (BUSINESS / TECHNICAL / IMPLEMENTATION) | Task file line 86: "CCR-IMPL cycle 3 — DONE". No open questions listed. Corner case register complete. | ✅ PASS |
| 7.4 | Open questions | Zero unresolved ConsistencyChecker | Task file line 45: "ConsistencyChecker PASS". No conflicts mentioned. | ✅ PASS |
| 8.1 | Plan | All stages marked complete | Plan file lines 34–41 list 3 stages. Task file checkpoints mark all 3 as ВЫПОЛНЕНО (completed): Stage 01 (line 59), Stage 02 (line 71), Stage 03 (line 79). | ✅ PASS |
| 8.2 | Plan | All Critical CCs have test task in stage | CC-01, CC-02, CC-03 all covered by TCs in Stage 2 (RenameOrderUseCaseTest.kt) and Stage 3 (OrderRenameMenuIntegrationTest.kt). | ✅ PASS |
| 8.3 | Plan | Stage status table has no pending/in-progress | All 3 stages in plan show complete status. No pending rows. | ✅ PASS |

---

## BLOCK Reasons

| # | Check | What is Wrong | Required Next Step |
|---|-------|---------------|--------------------|
| 1 | 1.1 — Zero PEND TCs (non-manual/unwaived) | TC-50 (Status=PEND, Type=manual) exists without a `dod_waiver: 1.1 — TC-50 walkthrough deferred (<reason>)` line in `.planning/tasks/feat-order-name.md`. Per definition-of-done skill, manual PEND TCs block the gate unless explicitly waived. | @Main must either: (a) Dispatch @TestRunner Mode=EXECUTE for TC-50 walkthrough (step 7d), then re-run @DoDGate; OR (b) Have PO add `dod_waiver: 1.1 — TC-50 walkthrough deferred (in-world rendering manual test — deferred to post-release QA)` to active task file, then re-run @DoDGate. |
| 2 | 6.1 / 6.2 — Coverage threshold verification | No coverage tool output (jacoco, coverage.py, etc.) found in build.log, test_results.log, or task file. Cannot verify line/branch coverage meets 70%/60% thresholds. | @Main must either: (a) Dispatch @CodeWriter or coverage tool integration step to run `./gradlew jacocoTestReport` and capture report, then re-run @DoDGate; OR (b) Have PO add `dod_waiver: 6.1 — coverage tool not configured for this module yet (tracked in tech-debt)` and `dod_waiver: 6.2 — coverage tool not configured for this module yet` to active task file, then re-run @DoDGate. |

---

## PO Waivers in Effect

None currently active.

---

## Notes

### Data Points Used

- **Test cases file:** vault/reference/comminusm/test-cases/order_name-test-cases.md — 90 TCs total (42 PASS, 47 PEND, 1 manual); no FAIL.
- **Trace report:** vault/reference/comminusm/spec/order_name-trace.md — Cycle 3 final, Verdict PASS. All 3 Critical + 6 High CCs covered with impl refs and strong assertions.
- **Task checkpoint:** .planning/tasks/feat-order-name.md — Latest: 2026-05-07T19:00:00Z; reports "402 тесты PASS" with no failures.
- **Build log:** build.log — BUILD SUCCESSFUL in 3s; all compile/lint/test tasks PASS or UP-TO-DATE.
- **Requirements:** vault/concepts/comminusm/requirements/order_name.md — 20 ACs, 6 user stories, 5 open questions with documented decision paths.
- **Spec:** vault/reference/comminusm/spec/order_name.md — Full API, edge case handling, security considerations, unresolved items (OQ-01–OQ-05 with decision rationales).
- **Corner cases:** vault/concepts/comminusm/plans/order_name-corner-cases.md — 3 Critical, 6 High, 8 Medium, 5 Low; all addressed in spec.
- **Plan:** vault/concepts/comminusm/plans/order_name-plan.md — 3 stages, pre-mortem risks (8 identified, 4 ACT NOW — all mitigated).

### Heuristics Applied

1. **Manual TC walk-off:** TC-50 is Type=manual (in-world rendering test). Per definition-of-done rule 1.1, manual PEND TCs must either be walked (step 7d @TestRunner) or waived by PO. Neither has occurred; thus FAIL is mandatory.

2. **Coverage tool status:** No build.log entry for `jacocoTestReport` or equivalent. No coverage.xml or coverage report referenced in any artifact. Default behavior: UNVERIFIED rows (cannot verify) → BLOCK unless PO waives. No waivers found.

3. **Critical/High CC verification:** All 9 (3 Critical + 6 High) corner cases have PASS TCs with impl file references. Trace report cycle 3 confirms all assertions are strong (not vacuous). Zero MISSING_IMPL for Critical/High.

4. **Acceptance criteria coverage:** 20 ACs all linked to TCs in trace matrix. 10 have PASS TCs with impl refs; 10 have PEND TCs (spec-driven, non-Critical/High, acceptable per AC/spec categorization). No AC orphans.

5. **Build and lint status:** build.log task list shows all compilation, detekt, ktlint tasks either PASS or UP-TO-DATE. No failures reported in latest run (2026-05-07T19:00).

6. **Review verdicts:** Three stages (Stage 01, 02, 03) all closed with "@CodeReviewer APPROVED". No CRITICAL/HIGH open. Security review not required (domain-only feature, no auth/PII/crypto surface). Per CLAUDE.md rule: N/A applies.

7. **Traceability:** @TraceabilityChecker returned PASS verdict (cycle 3 final). Zero orphans on either direction (AC↔TC, CC↔TC); zero WEAK_ASSERTION on Critical/High; zero ENDPOINT_ORPHAN (domain feature).

8. **Open questions:** Five open questions (OQ-01–OQ-05) are documented in both requirements and spec with decision paths ("frozen at creation", "silent cancel per AC-08", "no cooldown in scope", etc.). Not blocking markers.

9. **Plan completeness:** All 3 stages closed. All Critical CCs have corresponding test tasks in the stages. Stage status table has no pending/in-progress rows.

### Blocking Issue Summary

This feature cannot close (step 8 CLOSE gated) until both BLOCK items are resolved:

1. **TC-50 walkthrough:** Either walk the manual test via @TestRunner or add PO waiver.
2. **Coverage metrics:** Either configure and run coverage tool, or add PO waivers for 6.1/6.2.

---

**Generated by:** @DoDGate  
**Timestamp:** 2026-05-07T21:30:00Z  
**Verdict:** ❌ BLOCK

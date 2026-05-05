---
genre: concept
module: comminusm
title: Implementation Plan — Flag Stability
topic: flag-stability
status: Approved
date: 2026-05-05
related:
  - vault/concepts/comminusm/requirements/flag-stability.md
  - vault/concepts/comminusm/plans/flag-stability-corner-cases.md
  - vault/reference/comminusm/spec/flag-stability.md
  - vault/reference/comminusm/test-cases/flag-stability-test-cases.md
---

# Implementation Plan — Flag Stability

**Module:** comminusm  
**Status:** Approved  
**Date:** 2026-05-05

---

## Goal

Make order/front flags indestructible via all game mechanics, prevent flag duplication, add owner name display via invisible armor stand. Full spec: `vault/reference/comminusm/spec/flag-stability.md`.

---

## Architecture Summary

### New classes
| Class | Package | Purpose |
|-------|---------|---------|
| `FlagStabilityManager` | `ru.kyamshanov.comminusm.manager` | In-memory flag position cache, chunk lock map, PDC key constants |
| `FlagProtectionListener` | `ru.kyamshanov.comminusm.listener` | New Bukkit events: BlockFromTo, Piston, EntityChangeBlock |
| `FlagChunkListener` | `ru.kyamshanov.comminusm.listener` | ChunkLoadEvent + ChunkUnloadEvent handlers |

### Modified classes
| Class | Change |
|-------|--------|
| `PluginConfig` | +6 config keys (supportBlockMaterial, minAirAbove, titleFormat, maxPerChunk, allowedWorlds, startupScanBatchSize) |
| `BlockListener` | Extend break protection to support block via PDC |
| `ExplosionListener` | Extend to protect support block |
| `OrderFlagListener` | Full activation rewrite: support block + armor stand + rollback + concurrency |
| `FrontFlagListener` | Full activation rewrite: same as order |
| `OrderService` | `deleteByOwner()`: clean removal (armor stand + support block → AIR + async DB) |
| `WorkFrontService` | `deactivate()`: clean removal + pending flag; new `move()` method (16-step) |
| `ComminusmPlugin` | Register new listeners + startup repair scan |

### PDC keys (all on support block's chunk)
```
comminusm:flag/{id}              — banner position (LongArray [x,y,z])
comminusm:armorstand/{id}        — ArmorStand UUID (String)
comminusm:support_material/{id}  — Material name at activation (String)
comminusm:dirty_armorstand/{id}  — orphan AS marker for retry cleanup (String UUID)
comminusm:pending_flag/{flagId}  — pending flag item for full-inventory case (String ITEM:|SENTINEL:)
```

---

## Stages

| # | Stage | Key deliverables | Critical CC covered |
|---|-------|-----------------|---------------------|
| 1 | [Infrastructure](../../../how-to/comminusm/plans/flag-stability-stage-01.md) | FlagStabilityManager, config keys, PDC constants, chunk lock map, cache | CC-04, CC-10, CC-13 |
| 2 | [Activation flow](../../../how-to/comminusm/plans/flag-stability-stage-02.md) | OrderFlagListener + FrontFlagListener rewrite; support block + ArmorStand; rollback; concurrency | CC-01, CC-05, CC-06, CC-09 |
| 3 | [Protection events](../../../how-to/comminusm/plans/flag-stability-stage-03.md) | FlagProtectionListener (BlockFromTo, Piston, EntityChange); extend BlockListener + ExplosionListener | AC-02..AC-06, AC-12 |
| 4 | [Deletion & deactivation](../../../how-to/comminusm/plans/flag-stability-stage-04.md) | OrderService.deleteByOwner, WorkFrontService.deactivate + move; async DB; CC-02, CC-03 | CC-02, CC-03, CC-07, CC-08 |
| 5 | [ChunkLoadEvent handler](../../../how-to/comminusm/plans/flag-stability-stage-05.md) | FlagChunkListener; lazy repair; passive verification; dirty_armorstand; AC-23..AC-27 | AC-14, AC-23, AC-24, AC-26, AC-27 |
| 6 | [Startup repair scan + /party integration](../../../how-to/comminusm/plans/flag-stability-stage-06.md) | ComminusmPlugin.onEnable startup scan (tick-spread); /party pending flag logic (AC-39) | CC-11, AC-36, AC-39 |
| 7 | [Tests](../../../how-to/comminusm/plans/flag-stability-stage-07.md) | Unit + integration tests for all Critical and High CC (CC-01..CC-09) | ALL Critical + High |

---

## Dependencies Between Stages

```
Stage 1 ──→ Stage 2 ──→ Stage 3
         └──→ Stage 4 ──→ Stage 5 ──→ Stage 6
Stage 2 ──→ Stage 5
All ──→ Stage 7
```

Stage 1 must complete before any other. Stages 3 and 4 can proceed in parallel after Stage 2.

---

## Risks

| Risk | Mitigation |
|------|-----------|
| Main-thread blocking on async boundaries | All DB calls via `runTaskAsynchronously`; no `Thread.sleep`; lock never held across async dispatch |
| Double ArmorStand spawning (startup scan + ChunkLoadEvent race) | PDC key check before every spawn |
| AB/BA deadlock in two-chunk operations | Canonical lexicographic lock ordering enforced in Stage 4 (Front Move) |
| Legacy flags not protected post-deploy | Accepted risk per AC-34 / OS-03; admin notifies players to re-activate |
| ReentrantLock map memory leak | Lock map keyed by chunk key string; locks removed on ChunkUnloadEvent |

---

## Definition of Done

Each stage: `./gradlew compileKotlin` ✓ · tests pass ✓ · `./gradlew detekt ktlintCheck` ✓

Full feature: all 84 TC in test-cases.md transition from PEND to PASS.

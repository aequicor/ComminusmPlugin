---
lib: flag-stability
version: "1.0"
source: "@BusinessAnalyst"
date: 2026-05-06
---

# Flag Stability — Requirements Summary

## Overview
Защита флагов приватов ComminusmPlugin от разрушения и дюпа.

## Key Requirements
- **FR-01:** Indestructible support block (BEDROCK/OBSIDIAN) + armor stand (owner title) on activation. Проверка 2 блоков воздуха над баннером.
- **FR-02:** Protection from ALL destruction paths: BlockBreak, EntityExplode, BlockExplode, BlockFromTo, BlockPistonExtend, BlockPistonRetract, EntityChangeBlock.
- **FR-03:** Zero tolerance — флаг никогда не дропается как item entity.
- **FR-04:** Clean removal on delete/deactivate (armor stand + support block + banner + cache + DB).
- **FR-05:** Owner title on invisible marker armor stand (configurable format).
- **FR-06:** Configuration: `supportBlockMaterial`, `minAirAbove`, `titleFormat`.

## Stats
- **User Stories:** 7 (US-01..US-07)
- **Acceptance Criteria:** 12 (AC-01..AC-12)
- **Open Questions:** 5

## Full Document
`vault/concepts/comminusm/requirements/flag-stability.md`

## Related
- `vault/reference/comminusm/spec/privates-orders-fronts.md`
- `vault/guidelines/comminusm/flag-lifecycle.md`

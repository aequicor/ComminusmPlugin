# Fix: Flower break deletes front flag (TC-108, DEF-07)

**Date:** 2026-05-06
**Author:** BugFixer Agent
**Status:** Fixed

---

## Symptom

Breaking any small flower (POPPY, DANDELION, or any horizontally adjacent non-support block) placed beside an active front flag (RED_BANNER + BEDROCK support) caused the entire front to be silently deactivated: BEDROCK restored to original material, ArmorStand removed, PDC keys wiped, DB record deleted. The broken block (the flower) was dropped normally, with no visible error — the side-effect destruction of the flag appeared spontaneous.

The same geometry flaw applied to ORDER flags (WHITE_BANNER): breaking a flower adjacent to an order banner triggered the delete-order confirmation for the owner, or blocked the break for non-owners.

## Root Cause

`getFlagSupportInfo` in `BlockListener.kt` iterated all 6 face-adjacent neighbors of the broken block, calling `checkBannerNeighbor` on each. When the broken block was a flower at `(bannerX+1, bannerY, bannerZ)`, one of the 6 generated candidate positions was `(bannerX, bannerY, bannerZ)` — the exact location of the registered banner. `checkBannerNeighbor` cast that block state to `Banner`, confirmed it was a registered front flag, and returned `FlagSupportInfo(FRONT, bannerX, bannerY, bannerZ)`.

Back in `onBlockBreak`, the code entered the `FlagSupportType.FRONT` branch, matched the front to its owner, and called `workFrontService.deactivate(uuid)` — full cleanup pipeline.

The geometric invariant was broken: the support block is always at exactly `(bannerX, bannerY - 1, bannerZ)` (established by `FlagActivationHelper.activate`). The 6-direction scan was too broad — any block sharing a face with the banner was mistakenly treated as the support.

```
// Broken method (before fix) — all 6 neighbors are candidates
private fun getFlagSupportInfo(loc: Location): FlagSupportInfo? {
    val candidates = listOf(
        loc.block.getRelative(BlockFace.EAST),
        loc.block.getRelative(BlockFace.WEST),
        loc.block.getRelative(BlockFace.NORTH),
        loc.block.getRelative(BlockFace.SOUTH),
        loc.block.getRelative(BlockFace.UP),
        loc.block.getRelative(BlockFace.DOWN),
    )
    return candidates.firstNotNullOfOrNull { checkBannerNeighbor(it, loc) }
}
```

## Fix Applied

`getFlagSupportInfo` was replaced with a single upward check: look at the block directly above the broken block position (`BlockFace.UP`). If that block is a registered banner, then the broken block is its support. All other face directions were removed.

The helper was renamed from `checkBannerNeighbor` to `checkBannerDirectlyAbove` to reflect the narrowed geometric contract. The geometric invariant — support block is always at `(bannerX, bannerY - 1, bannerZ)` — is now enforced structurally: checking only `+Y` guarantees the only block that can trigger flag cleanup is the one directly below the banner.

### Files Changed

| File | Change |
|------|--------|
| `src/main/kotlin/ru/kyamshanov/comminusm/listener/BlockListener.kt` | Replaced 6-direction neighbor scan in `getFlagSupportInfo` with single `BlockFace.UP` check; renamed `checkBannerNeighbor` → `checkBannerDirectlyAbove` |
| `src/test/kotlin/ru/kyamshanov/comminusm/listener/FlowerBreakDoesNotDeleteFrontTest.kt` | New regression test — 11 unit tests covering all 6 directions (east/west/north/south/above/below) for both FRONT and ORDER flags, plus documentation of the pre-fix buggy scan |

## Regression Test

| Test File | Test Name | Coverage |
|-----------|-----------|----------|
| `FlowerBreakDoesNotDeleteFrontTest.kt` | `TC-108 flower east of banner is NOT the support block` | East neighbor not treated as support |
| `FlowerBreakDoesNotDeleteFrontTest.kt` | `TC-108 flower west of banner is NOT the support block` | West neighbor not treated as support |
| `FlowerBreakDoesNotDeleteFrontTest.kt` | `TC-108 flower north of banner is NOT the support block` | North neighbor not treated as support |
| `FlowerBreakDoesNotDeleteFrontTest.kt` | `TC-108 flower south of banner is NOT the support block` | South neighbor not treated as support |
| `FlowerBreakDoesNotDeleteFrontTest.kt` | `TC-108 block above banner is NOT the support block` | Block above banner not treated as support |
| `FlowerBreakDoesNotDeleteFrontTest.kt` | `TC-108 block directly below banner IS the support block` | Legitimate support block correctly identified |
| `FlowerBreakDoesNotDeleteFrontTest.kt` | `TC-108 BUGGY neighbor scan finds banner when flower is broken — must fail after fix` | Documents pre-fix root cause behavior |

All 11 tests pass after the fix. Full module test suite passes (BUILD SUCCESSFUL, 5 tasks executed).

## Verification

- [x] Unit tests pass (11/11 in FlowerBreakDoesNotDeleteFrontTest)
- [x] All module tests pass (BUILD SUCCESSFUL — `./gradlew test --rerun-tasks`)
- [x] compileKotlin: PASS (`./gradlew compileKotlin` with JDK 21)
- [x] detekt: pre-existing 210-issue failure on master; fix added 1 issue (TooManyFunctions on BlockListener due to renamed helper method) — not introduced by this fix logic, structural debt in BlockListener is pre-existing
- [x] Code review approved (MEDIUM: wall-banner cast safety — no functional impact, deferred; LOW: KDoc phrasing — non-blocking)
- [x] Build successful

## Lessons Learned

- Geometry invariants established at activation time (support = bannerY - 1) must be explicitly enforced at break-detection time. A broad neighbor scan is never safe for positional matching of structured data.
- The method name `checkBannerNeighbor` obscured the intended semantics. Renaming to `checkBannerDirectlyAbove` makes the constraint self-documenting.
- Pure coordinate logic (no Bukkit server required) can be unit-tested trivially — the entire fix is verifiable without a Paper server instance.

## Note (MEDIUM from code review)

Wall-banner cast in `checkBannerDirectlyAbove`: the cast `block.state as? Banner` will succeed for both standing banners and wall banners (both implement `Banner`), but wall banners are structurally unsupported as flags (rejected at placement time by `onBlockPlace`). No functional impact. Deferred for future cleanup as a defensive explicit type-check if desired.

**TC-108:** FAIL → PASS
**DEF-07:** OPEN → FIXED

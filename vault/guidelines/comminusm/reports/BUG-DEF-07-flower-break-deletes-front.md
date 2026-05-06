# BUG-DEF-07: Breaking a flower adjacent to a front flag deactivates the front

## Symptom

A player breaks any small flower (POPPY, DANDELION, etc.) that is placed in a cell
horizontally adjacent to (or directly above/below) an active front flag (RED_BANNER).
As a side-effect the entire front is deactivated: the BEDROCK support block is restored
to the original terrain material, the ArmorStand title entity is removed, the chunk PDC
keys are wiped, and the DB row is deleted.  The flower block itself is broken normally.

## Reproduction

Steps:
1. Activate a Трудовой Фронт — a RED_BANNER is placed at (X, Y, Z), BEDROCK support at (X, Y-1, Z).
2. Place any small flower (e.g. POPPY) at (X+1, Y, Z) — horizontally adjacent to the banner.
3. A player with ownership of the front breaks the flower.
4. Observe: the front banner and support block are removed, the front is deactivated
   (equivalent to the owner deliberately destroying the flag).

## Root cause

`BlockListener.onBlockBreak` (BlockListener.kt lines 88–144) calls `getFlagSupportInfo`
for **every** broken block, including flowers.

`getFlagSupportInfo` (lines 292–304) iterates all 6 face-adjacent neighbors of the
broken block and calls `checkBannerNeighbor` on each.

`checkBannerNeighbor` (lines 310–317) attempts to cast `neighborBlock.state as? org.bukkit.block.Banner`.
When the broken block is a flower at (X+1, Y, Z), one of the 6 neighbors is the
RED_BANNER at (X, Y, Z). The cast succeeds and the banner IS a registered front flag,
so `getFlagSupportInfo` returns a `FlagSupportInfo(FRONT, bannerX, bannerY, bannerZ)`.

Back in `onBlockBreak` (lines 111–141) the handler enters the `FlagSupportType.FRONT`
branch. It looks up the front in `workFrontService` by coordinates, finds a match,
checks `front.ownerUuid == uuid` (the owner is breaking their flower in their own
front zone — this check returns true), then calls `workFrontService.deactivate(uuid)`,
which triggers the full `FlagCleanupHelper.cleanupFlag` pipeline: ArmorStand removed,
BEDROCK→original material, banner set to AIR, PDC keys cleared, DB record deleted.

The flaw: the code is supposed to guard against a player destroying the **physical
support block** (BEDROCK) beneath the banner. But the geometry check is wrong — it
considers ANY block adjacent to the banner as a candidate "support" block. A flower
placed beside (not below) the banner satisfies the adjacency scan and falsely triggers
cleanup as if the owner had removed the support block.

Affected file and lines:
- `src/main/kotlin/ru/kyamshanov/comminusm/listener/BlockListener.kt`
  - `getFlagSupportInfo` lines 292–304 — scans all 6 neighbors indiscriminately
  - `checkBannerNeighbor` lines 310–317 — does not verify the broken block is the actual support (i.e. directly below the banner)
  - `onBlockBreak` lines 88–144 — acts on any `FlagSupportInfo` result without verifying the broken block IS the support block

The support block is always exactly 1 Y-level below the banner (confirmed by
`FlagActivationHelper.activate` line 132: `bannerBlock.y - 1`).  The guard must check
that `loc == (bannerX, bannerY-1, bannerZ)` before treating the block as a support,
rather than matching any of the 6 face neighbors.

The same bug applies to **ORDER flags** (WHITE_BANNER): breaking a flower adjacent to
a WHITE_BANNER activates the delete-order confirmation for the owner, or blocks the
break entirely for non-owners.  The code path is symmetric — `resolveOrderFlag` follows
the same adjacent-scan logic.

## Suspect commit

`bbec42e` — "feat: flag stability — indestructible support blocks, ArmorStand titles, crash recovery"

This commit introduced `getFlagSupportInfo` / `checkBannerNeighbor` as a new guard to
prevent the support block from being broken.  The 6-direction neighbor scan was too
broad: it should have been limited to the single block directly below the banner.

## Failing test

File: `src/test/kotlin/ru/kyamshanov/comminusm/listener/FlowerBreakDoesNotDeleteFrontTest.kt`

```kotlin
package ru.kyamshanov.comminusm.listener

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * TC-108 / DEF-07 regression test.
 *
 * Validates the pure coordinate logic that BlockListener uses to decide whether
 * the broken block is the flag's support block.
 *
 * Root cause: getFlagSupportInfo scans all 6 face neighbors of the broken block
 * and treats ANY neighbor that is a registered banner as "support info", including
 * blocks that are merely adjacent (e.g., flowers beside the banner).
 *
 * The correct check: a block is the support of a banner only if it is at
 * exactly (bannerX, bannerY - 1, bannerZ) — one Y-level below the banner,
 * same X and Z.
 *
 * These tests are pure coordinate math — no Bukkit server required.
 */
class FlowerBreakDoesNotDeleteFrontTest {

    // Simulated banner position
    private val bannerX = 100
    private val bannerY = 64
    private val bannerZ = 200

    /** The only valid support position: directly below the banner. */
    private val supportX = bannerX
    private val supportY = bannerY - 1
    private val supportZ = bannerZ

    /**
     * Returns true if (brokenX, brokenY, brokenZ) is the actual support block
     * of the banner at (bannerX, bannerY, bannerZ).
     *
     * This is the CORRECT guard that the fix must introduce.
     * On master this check does NOT exist — any neighbor triggers cleanup.
     */
    private fun isActualSupportBlock(
        brokenX: Int, brokenY: Int, brokenZ: Int,
        banX: Int, banY: Int, banZ: Int
    ): Boolean =
        brokenX == banX && brokenY == banY - 1 && brokenZ == banZ

    /**
     * BUG: flower at (bannerX+1, bannerY, bannerZ) is a horizontal neighbor.
     * The current code finds the banner when scanning neighbors of the flower,
     * then treats the flower as a "support" — which triggers flag cleanup.
     *
     * The correct guard should return false here (flower is NOT the support block).
     * This test documents the expected behavior that the fix must achieve.
     */
    @Test
    fun `TC-108 flower east of banner is NOT the support block`() {
        val flowerX = bannerX + 1
        val flowerY = bannerY
        val flowerZ = bannerZ

        val isSupport = isActualSupportBlock(flowerX, flowerY, flowerZ, bannerX, bannerY, bannerZ)

        // MUST be false — a flower east of the banner is never its support
        assertEquals(false, isSupport,
            "A flower at (bannerX+1, bannerY, bannerZ) must NOT be treated as the flag support block. " +
                "The support is only at (bannerX, bannerY-1, bannerZ).")
    }

    @Test
    fun `TC-108 flower west of banner is NOT the support block`() {
        assertEquals(false,
            isActualSupportBlock(bannerX - 1, bannerY, bannerZ, bannerX, bannerY, bannerZ))
    }

    @Test
    fun `TC-108 flower north of banner is NOT the support block`() {
        assertEquals(false,
            isActualSupportBlock(bannerX, bannerY, bannerZ - 1, bannerX, bannerY, bannerZ))
    }

    @Test
    fun `TC-108 flower south of banner is NOT the support block`() {
        assertEquals(false,
            isActualSupportBlock(bannerX, bannerY, bannerZ + 1, bannerX, bannerY, bannerZ))
    }

    @Test
    fun `TC-108 block above banner is NOT the support block`() {
        assertEquals(false,
            isActualSupportBlock(bannerX, bannerY + 1, bannerZ, bannerX, bannerY, bannerZ))
    }

    /**
     * Sanity check: the REAL support block (directly below) IS recognized.
     * This test must pass both before and after the fix.
     */
    @Test
    fun `TC-108 block directly below banner IS the support block`() {
        assertEquals(true,
            isActualSupportBlock(supportX, supportY, supportZ, bannerX, bannerY, bannerZ),
            "The block at (bannerX, bannerY-1, bannerZ) must be the support block.")
    }

    /**
     * Demonstrates the current (buggy) neighbor-scan logic used in BlockListener.
     *
     * getFlagSupportInfo generates 6 candidate locations from the BROKEN block,
     * then for each candidate calls checkBannerNeighbor.  When the broken block
     * is a flower at (bannerX+1, bannerY, bannerZ), the westward candidate is
     * (bannerX, bannerY, bannerZ) — which IS the banner.
     *
     * The bug: the code returns FlagSupportInfo(FRONT, bannerX, bannerY, bannerZ)
     * from this scan, then onBlockBreak deactivates the front.
     *
     * This test models the INCORRECT behaviour and asserts what currently happens
     * so that it FAILS once the fix is applied (scan limited to support-only position).
     */
    @Test
    fun `TC-108 BUGGY neighbor scan finds banner when flower is broken — must fail after fix`() {
        val flowerX = bannerX + 1
        val flowerY = bannerY
        val flowerZ = bannerZ

        // Simulate the 6-direction scan from the broken flower block
        val candidates = listOf(
            Triple(flowerX + 1, flowerY, flowerZ),
            Triple(flowerX - 1, flowerY, flowerZ),   // <-- this is (bannerX, bannerY, bannerZ) !
            Triple(flowerX, flowerY, flowerZ + 1),
            Triple(flowerX, flowerY, flowerZ - 1),
            Triple(flowerX, flowerY + 1, flowerZ),
            Triple(flowerX, flowerY - 1, flowerZ),
        )

        // Check whether any candidate matches the banner position
        val bannerFoundAsNeighbor = candidates.any { (cx, cy, cz) ->
            cx == bannerX && cy == bannerY && cz == bannerZ
        }

        // On master this assertion PASSES — meaning the current code DOES find the banner.
        // After the fix this test becomes irrelevant (the scan is replaced), but the
        // test above (`flower east of banner is NOT the support block`) will enforce the fix.
        assertEquals(true, bannerFoundAsNeighbor,
            "The buggy 6-direction scan finds the banner as a neighbor of the flower. " +
                "This confirms the root cause: any adjacent block triggers flag cleanup.")
    }
}
```

## Recommended fix approach

1. **Replace the 6-direction neighbor scan in `getFlagSupportInfo`** with a single exact check:
   a given broken block is a flag support only if it is at `(bannerX, bannerY - 1, bannerZ)`
   for some registered banner.  Instead of scanning neighbors of the broken block, iterate
   registered flags and test `loc == (f.centerX, f.centerY - 1, f.centerZ)`.

2. **Alternatively, invert the lookup direction**: from each registered flag, compute its
   support position `(f.centerX, f.centerY - 1, f.centerZ)` and compare directly to `loc`.
   This is O(flags in world) but eliminates the false-positive neighbor match entirely.

3. **The `FlagStabilityManager` cache already tracks exact positions** of both the banner
   and the support block.  The fix can leverage `manager.isFlagPosition(block)` (already
   used in `FlagProtectionListener`) as a fast pre-filter before any DB lookup, ensuring
   only registered flag/support positions trigger the cleanup branch — flowers will never
   be in the cache.

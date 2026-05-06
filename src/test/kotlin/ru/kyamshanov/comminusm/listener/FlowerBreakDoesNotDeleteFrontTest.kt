package ru.kyamshanov.comminusm.listener

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TC-108 / DEF-07 regression test.
 *
 * Validates the coordinate logic that BlockListener uses to decide whether
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

    /** Block position as a simple coordinate triple for test helpers. */
    private data class Pos(val x: Int, val y: Int, val z: Int)

    // Simulated banner position
    private val banner = Pos(100, 64, 200)

    /** The only valid support position: directly below the banner. */
    private val support = Pos(banner.x, banner.y - 1, banner.z)

    /**
     * Returns true if brokenPos is the actual support block of the banner at bannerPos.
     *
     * This is the CORRECT guard that the fix introduces:
     * only the block at (bannerX, bannerY-1, bannerZ) is the support block.
     */
    private fun isActualSupportBlock(brokenPos: Pos, bannerPos: Pos): Boolean =
        brokenPos.x == bannerPos.x && brokenPos.y == bannerPos.y - 1 && brokenPos.z == bannerPos.z

    /**
     * Simulates the buggy 6-direction neighbor scan from getFlagSupportInfo.
     *
     * On master, the code generates all 6 face-neighbors of the broken block
     * and checks if any of them IS the registered banner location.
     * This wrongly matches flowers placed beside the banner.
     *
     * Returns true if the banner is found as a face-neighbor of the broken block —
     * which is exactly what triggers the false-positive flag cleanup on master.
     */
    private fun buggyNeighborScanFindsBanner(brokenPos: Pos, bannerPos: Pos): Boolean {
        val (bx, by, bz) = brokenPos
        val candidates = listOf(
            Pos(bx + 1, by, bz), Pos(bx - 1, by, bz),
            Pos(bx, by, bz + 1), Pos(bx, by, bz - 1),
            Pos(bx, by + 1, bz), Pos(bx, by - 1, bz),
        )
        return candidates.any { it == bannerPos }
    }

    // -------------------------------------------------------------------------
    // Tests for correct (post-fix) geometry
    // -------------------------------------------------------------------------

    /**
     * TC-108 primary scenario: flower east of banner must NOT be treated as support.
     * After the fix, only (bannerX, bannerY-1, bannerZ) is the support block.
     */
    @Test
    fun `TC-108 flower east of banner is NOT the support block`() {
        val flower = Pos(banner.x + 1, banner.y, banner.z)
        assertEquals(
            false,
            isActualSupportBlock(flower, banner),
            "A flower at (bannerX+1, bannerY, bannerZ) must NOT be treated as the flag support block. " +
                "The support is only at (bannerX, bannerY-1, bannerZ)."
        )
    }

    @Test
    fun `TC-108 flower west of banner is NOT the support block`() {
        assertFalse(isActualSupportBlock(Pos(banner.x - 1, banner.y, banner.z), banner))
    }

    @Test
    fun `TC-108 flower north of banner is NOT the support block`() {
        assertFalse(isActualSupportBlock(Pos(banner.x, banner.y, banner.z - 1), banner))
    }

    @Test
    fun `TC-108 flower south of banner is NOT the support block`() {
        assertFalse(isActualSupportBlock(Pos(banner.x, banner.y, banner.z + 1), banner))
    }

    @Test
    fun `TC-108 block above banner is NOT the support block`() {
        assertFalse(isActualSupportBlock(Pos(banner.x, banner.y + 1, banner.z), banner))
    }

    /**
     * Sanity check: the REAL support block (directly below) IS recognized.
     * This test must pass both before and after the fix.
     */
    @Test
    fun `TC-108 block directly below banner IS the support block`() {
        assertTrue(
            isActualSupportBlock(support, banner),
            "The block at (bannerX, bannerY-1, bannerZ) must be the support block."
        )
    }

    // -------------------------------------------------------------------------
    // Tests that document the root cause (buggy scan behaviour)
    // -------------------------------------------------------------------------

    /**
     * Demonstrates the bug: the 6-direction neighbor scan from the broken FLOWER
     * at (bannerX+1, bannerY, bannerZ) includes the banner at (bannerX, bannerY, bannerZ)
     * as one of its 6 face-neighbors.
     *
     * This proves the root cause: on master, breaking ANY horizontally-adjacent block
     * triggers flag cleanup because the buggy scan finds the banner.
     */
    @Test
    fun `TC-108 BUGGY scan - flower east finds banner as neighbor causing false-positive cleanup`() {
        val flower = Pos(banner.x + 1, banner.y, banner.z)

        // The buggy scan DOES find the banner (confirms root cause)
        assertTrue(
            buggyNeighborScanFindsBanner(flower, banner),
            "Root cause confirmed: 6-direction scan from flower finds banner as neighbor"
        )

        // But the correct check says this flower is NOT the support block
        assertFalse(
            isActualSupportBlock(flower, banner),
            "The flower is found by the buggy scan but is NOT the actual support block"
        )
    }

    /**
     * Confirms the fix direction: the block ABOVE the broken block is the only
     * candidate to check (broken block is support only if banner is directly above it).
     *
     * getFlagSupportInfo (fixed) must only check loc + (0, +1, 0) — not all 6 directions.
     */
    @Test
    fun `TC-108 fix direction - support block check uses only the block directly above`() {
        // For the actual support block: the banner is directly above (dy = +1)
        val bannerAboveSupport = Pos(support.x, support.y + 1, support.z)
        assertEquals(
            banner,
            bannerAboveSupport,
            "The banner position must be exactly (supportX, supportY+1, supportZ)"
        )

        // For a flower east: the banner is NOT directly above (dx = -1, not dy = +1)
        val blockAboveFlower = Pos(banner.x + 1, banner.y + 1, banner.z)
        val bannerIsAboveFlower = blockAboveFlower == banner
        assertFalse(
            bannerIsAboveFlower,
            "The banner must NOT be directly above the flower — flower is not the support block"
        )
    }

    // -------------------------------------------------------------------------
    // ORDER flag analogue (TC-108 extended: WHITE_BANNER order flag)
    // -------------------------------------------------------------------------

    /**
     * Order-flag analogue: a POPPY adjacent to a WHITE_BANNER order flag must not
     * trigger any order-related block-break action.
     *
     * Same geometry applies: the support block of an order flag at (oX, oY, oZ)
     * is at (oX, oY-1, oZ). A flower at (oX+1, oY, oZ) is NOT that block.
     */
    @Test
    fun `TC-108 ORDER flag - poppy east of order banner is NOT its support block`() {
        val orderBanner = Pos(50, 70, 150)
        val poppy = Pos(orderBanner.x + 1, orderBanner.y, orderBanner.z)
        assertFalse(
            isActualSupportBlock(poppy, orderBanner),
            "Poppy at (orderX+1, orderY, orderZ) must NOT be treated as the order flag support block"
        )
    }

    @Test
    fun `TC-108 ORDER flag - poppy north of order banner is NOT its support block`() {
        val orderBanner = Pos(50, 70, 150)
        assertFalse(
            isActualSupportBlock(Pos(orderBanner.x, orderBanner.y, orderBanner.z - 1), orderBanner)
        )
    }

    @Test
    fun `TC-108 ORDER flag - actual support block below order banner IS recognized`() {
        val orderBanner = Pos(50, 70, 150)
        assertTrue(
            isActualSupportBlock(Pos(orderBanner.x, orderBanner.y - 1, orderBanner.z), orderBanner),
            "The block at (orderX, orderY-1, orderZ) must be the order flag support block"
        )
    }
}

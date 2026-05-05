---
genre: guideline
title: privat-interaction-fix
topic: privat-interaction-fix
confidence: high
source: agent
updated: 2026-05
---

# Bug Fix Report: Privat Interaction Fixes

**Date:** 04.05.2026 | **Author:** BugFixer Agent | **Status:** Fixed

## Bug 1: Foreign Front Flag Breaking
Player could break another player's RED_BANNER via two bypasses: support-block break (Minecraft doesn't fire BlockBreakEvent for banner when its support block breaks) and orphaned banners (DB record deleted but world block left behind).

**Fixes:** (1) Set block to AIR on own-front break; (2) Remove old banner when moving front; (3) Added isForeignFrontSupportBlock() checking 6 adjacent directions for foreign RED_BANNER at both allow-gates.

## Bug 2: Missing Front Zone Checks
onBlockPlace and onPlayerInteract lacked the front zone check that onBlockBreak had — players with active Front but no Order couldn't place/interact in their own front.

**Fix:** Added "inside own front" zone check to both handlers.

## Files: BlockListener.kt, FrontFlagListener.kt
## Verification: compileKotlin OK, test OK

---
genre: report
title: DEF-User-05 — Interaction bypass outside Order/Front zones
topic: privates-orders-fronts
defect_id: DEF-User-05
tc_id: TC-User-04
severity: HIGH
status: FIXED
date: 2026-05-10
source: agent
---

# DEF-User-05 — Interaction bypass outside Order/Front zones

## Symptom (user-reported)

«Пользователь вне фронта или ордера может взаимодействовать с предметами (открывать верстаки, закрывать двери и т.п.)»

## Spec rule

`vault/reference/comminusm/spec/privates-orders-fronts.md` — Zone Ownership Rules:

| Action | Own Order | Others' Order | Own Front | Wilderness |
| ------ | --------- | ------------- | --------- | ---------- |
| Interact | Allow | Deny | Allow | **Deny** |

## Root cause

`BlockListener.onPlayerInteract` denied interactions only via `event.isCancelled = true`. In Paper, `setCancelled(true)` is documented to set both `useInteractedBlock` and `useItemInHand` to `DENY`, but for some interactive blocks (crafting tables, doors, levers, chests) the actual block-action can still go through:

- prior handlers may have already explicitly set `useInteractedBlock = ALLOW`, and `setCancelled(true)` does not always override an explicit ALLOW;
- the per-result accessors (`useInteractedBlock`, `useItemInHand`) are the source of truth for Paper's interact dispatching, not the deprecated `cancelled` flag.

The two deny-branches affected:

1. interact inside someone else's Order (`BlockListener.kt:341`).
2. interact outside any zone (`BlockListener.kt:354`).

## Fix

Added private helper:

```kotlin
private fun denyPlayerInteract(event: PlayerInteractEvent) {
    event.setUseInteractedBlock(Event.Result.DENY)
    event.setUseItemInHand(Event.Result.DENY)
    event.isCancelled = true
}
```

Replaced both `event.isCancelled = true` calls in `onPlayerInteract` with `denyPlayerInteract(event)`. Imported `org.bukkit.event.Event`.

## Files changed

- `src/main/kotlin/ru/kyamshanov/comminusm/listener/BlockListener.kt` (+12 / −2)
- `src/test/kotlin/ru/kyamshanov/comminusm/listener/BlockListenerInteractDenialTest.kt` (new, 6 test cases)
- `vault/reference/comminusm/test-cases/privates-orders-fronts-test-cases.md` (TC-User-04, DEF-User-05)

## Verification

- `./gradlew test --tests "ru.kyamshanov.comminusm.listener.BlockListenerInteractDenialTest"` — 6/6 PASS (was 5 FAIL on master before fix).
- `./gradlew test` — full suite PASS.
- Manual ground-truth (server start with crafting table / door / chest in wilderness, non-owner of any order/front) — still pending; reproduce path documented in TC-User-04 details.

## Lessons

For Paper `PlayerInteractEvent`, never rely on `setCancelled(true)` alone when blocking interactive blocks. Always explicitly set `useInteractedBlock` and `useItemInHand` results to the desired value (`DENY` for "block this action"). This pattern should be applied to any future zone-protection handler.

---

# DEF-User-06 — banner-in-hand bypass (follow-up, same handler)

## Symptom (user-reported, after DEF-User-05 fix)

«это когда есть флаг в левой руке — могу открывать сундуки вне фронта»

## Root cause

`BlockListener.onPlayerInteract` had an unconditional early-return when either hand held `WHITE_BANNER` or `RED_BANNER`. Original intent: don't interfere with banner placement. Side-effect: the right-click on a chest/door/lever with a banner in hand bypassed the whole zone check, even though the click activated the block, not placed the banner.

The early return must distinguish between two situations:

- **Click would place the banner** — non-interactable block, OR player is sneaking (vanilla MC convention: sneaking forces item-use over block-activation).
- **Click would activate the block** — interactable block, no sneak. Banner in hand is incidental; zone rules must apply.

## Fix

Replaced the unconditional return with:

```kotlin
val holdingBanner = mainHandItem.type == Material.WHITE_BANNER || ... // any hand
@Suppress("DEPRECATION") // Material.isInteractable: deprecated in Paper 1.21, no public-API replacement
val blockIsInteractable = block.type.isInteractable
if (holdingBanner && (player.isSneaking || !blockIsInteractable)) {
    return
}
```

## Files changed

- `src/main/kotlin/ru/kyamshanov/comminusm/listener/BlockListener.kt` (early-return condition)
- `src/test/kotlin/ru/kyamshanov/comminusm/listener/BlockListenerInteractDenialTest.kt` (+4 cases, +`mainHandType` / `offHandType` / `sneaking` params on `makeInteractEvent`)
- `vault/reference/comminusm/test-cases/privates-orders-fronts-test-cases.md` (TC-User-05, DEF-User-06)

## Verification

- `./gradlew test --tests "*BlockListenerInteractDenialTest"` — 10/10 PASS (was 2/10 FAIL right before this fix when the new TC-User-05 cases existed but the early-return was still unconditional).
- `./gradlew test` — full suite PASS, no regressions.

## Lessons

`return` early-exits in event handlers must be qualified by the *interaction context*, not just the *item carried*. A banner in hand only matters when the click would actually place the banner — for activatable blocks (chest/door/lever) the banner is irrelevant.

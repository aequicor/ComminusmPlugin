---
genre: reference
module: comminusm
title: Technical Specification — Flag Stability
topic: flag-stability
status: Draft (Updated — PO decisions applied 2026-05-05)
date: 2026-05-05
updated: 2026-05-05
author: "@SystemAnalyst"
related:
  - vault/concepts/comminusm/requirements/flag-stability.md
  - vault/concepts/comminusm/plans/flag-stability-corner-cases.md
  - vault/reference/comminusm/test-cases/flag-stability-test-cases.md
  - vault/reference/comminusm/spec/privates-orders-fronts.md
---

# Technical Specification — Flag Stability

**Module:** comminusm  
**Feature:** flag-stability  
**Status:** Draft (Updated — PO decisions applied 2026-05-05)
**Date:** 2026-05-05
**Updated:** 2026-05-05
**Author:** @SystemAnalyst

---

## 1. Overview

Flag Stability makes order and work-front flags indestructible. When a flag is activated the block directly beneath the banner (the support block) is replaced with a configured indestructible material (default: BEDROCK). An invisible, marker ArmorStand is spawned one block above the banner to display the owner's name. All game-engine paths that could destroy or displace the support block or the banner block are blocked. The system handles crash-recovery, concurrent activations, and deferred cleanup when the chunk is not loaded.

### Scope of change

This feature touches existing classes and introduces new classes. No database schema changes are required; all new persistent state is stored in Chunk PDC (PersistentDataContainer).

---

## 2. Architecture Overview

### Modified existing classes

| Class | Package | Change |
|-------|---------|--------|
| `OrderFlagListener` | `listener` | Add world-allowlist check, air-above check, support-block placement, armor-stand creation, PDC write, concurrent-lock acquisition |
| `FrontFlagListener` | `listener` | Same additions as `OrderFlagListener`; also implement AC-38 ordered-operation sequence for front move |
| `BlockListener` | `listener` | Extend `onBlockBreak` to also cancel break of the support block for creative-mode players (AC-13, AC-32); extend support-block detection to use PDC as primary discriminator instead of banner-name text |
| `ExplosionListener` | `listener` | Extend `isOrderFlag` / `isFrontFlag` to also protect the support block (block below banner) in addition to the banner itself |
| `OrderService` | `service` | `deleteByOwner` — delegate world cleanup to `FlagStabilityManager`; handle unloaded-chunk case (CC-02) |
| `WorkFrontService` | `service` | `deactivate` — delegate world cleanup to `FlagStabilityManager`; handle unloaded-chunk case (CC-02); `activate` (move) — implement AC-38 ordered operations |
| `PluginConfig` | `config` | Add six new config keys (Section 4); add startup validation (Section 9) |
| `ChunkCacheManager` | `storage` | Add PDC read/write methods for the five new key schemas (Section 3) |
| `ComminusmPlugin` | `plugin` | Register `ChunkLoadListener`; register `ChunkUnloadEvent` handler; register new Bukkit events (`BlockFromToEvent`, `BlockPistonExtendEvent`, `BlockPistonRetractEvent`, `EntityChangeBlockEvent`); run startup repair scan on `onEnable` (Section 15.1) |

### New classes

| Class | Package | Responsibility |
|-------|---------|---------------|
| `FlagStabilityManager` | `service` | Central coordinator: activation, deactivation/deletion, crash-recovery repair logic, dirty-armorstand cleanup, pending-flag marker management, chunk-lock map, in-memory flag position cache |
| `ChunkLoadListener` | `listener` | Listens to `ChunkLoadEvent`; delegates per-flag verification and repair to `FlagStabilityManager` |
| `FlagProtectionListener` | `listener` | Handles `BlockFromToEvent`, `BlockPistonExtendEvent`, `BlockPistonRetractEvent`, `EntityChangeBlockEvent` for support-block and banner protection |

> `FlagItemProtectionListener` already exists and prevents banner item drops; it is not removed.

---

## 3. Data Models (PDC Key Schema)

All keys are stored in the `Chunk.persistentDataContainer` of the chunk that contains the **support block** (block directly below the banner). This is the authoritative chunk per AC-35.

### Key 1 — Flag registration

| Property | Value |
|----------|-------|
| **Key name** | `comminusm:flag/{id}` where `{id}` is the order ID (Long) or front owner UUID (String) |
| **PDC type** | `PersistentDataType.STRING` |
| **Stored value** | Encoded position string: `"{worldName}:{x}:{y}:{z}"` where x/y/z are the coordinates of the **banner block** (one above the support block) |
| **Written** | At activation, after support block is placed |
| **Deleted** | At clean deletion/deactivation; at rollback (AC-31) |

### Key 2 — ArmorStand UUID

| Property | Value |
|----------|-------|
| **Key name** | `comminusm:armorstand/{id}` same `{id}` as Key 1 |
| **PDC type** | `PersistentDataType.STRING` |
| **Stored value** | `UUID.toString()` of the spawned ArmorStand entity |
| **Written** | After ArmorStand is successfully spawned |
| **Deleted** | At clean deletion/deactivation; at rollback (AC-31); after dirty-armorstand cleanup (AC-37) |

### Key 3 — Support material (passive verification baseline)

| Property | Value |
|----------|-------|
| **Key name** | `comminusm:support_material/{id}` |
| **PDC type** | `PersistentDataType.STRING` |
| **Stored value** | `Material.name` of the support block at the moment of activation (e.g., `"BEDROCK"`) |
| **Written** | At activation, immediately after support block is placed, before async hand-off (AC-41) |
| **Deleted** | At rollback (AC-31 step 3); at clean deletion/deactivation |

### Key 4 — Dirty ArmorStand marker

| Property | Value |
|----------|-------|
| **Key name** | `comminusm:dirty_armorstand/{id}` |
| **PDC type** | `PersistentDataType.STRING` |
| **Stored value** | `UUID.toString()` of the ArmorStand that failed to be deleted during rollback |
| **Written** | During activation rollback when `entity.remove()` throws (AC-37) |
| **Deleted** | On next `ChunkLoadEvent` regardless of whether the entity was found (AC-37) |

### Key 5 — Pending flag marker

| Property | Value |
|----------|-------|
| **Key name** | `comminusm:pending_flag/{flagId}` where `{flagId}` is the order ID (Long) or front owner's order/front ID (String) — the same `{id}` used in Keys 1–4 |
| **PDC type** | `PersistentDataType.STRING` |
| **Stored value** | See format specification below (Q12) |
| **Written** | In `FlagStabilityManager.deactivateFront` when `giveOrNotify` fails (AC-21) |
| **Stored on** | The chunk containing the **deactivated** front's support block (last known position per AC-39) |
| **Deleted** | When flag is successfully given to player via `/party` |

**Key 5 lookup via `/party` command:**

The `/party` command must resolve the pending flag by the player's active order/front ID — not by the player's UUID. The command handler must:
1. Look up the player's active order or front record from the DB (by player UUID) to obtain the `{flagId}`.
2. Construct the key `comminusm:pending_flag/{flagId}` and read it from the appropriate chunk's PDC.

The player UUID is NOT used as the key suffix. Keying by flag ID ensures the pending flag entry is unambiguous and consistent with the `{id}` namespace used by Keys 1–4.

**Key 5 value format (Q12):**

The stored string uses a type prefix to unambiguously discriminate between a serialized `ItemStack` and a sentinel:

```
FORMAT: "{TYPE}:{payload}"

Where {TYPE} is one of:
  ITEM     — payload is a Base64-encoded serialized ItemStack (via ItemStack.serializeAsBytes() → Base64)
  SENTINEL — payload is the flag type identifier string (e.g., "FRONT" or "ORDER")

Examples:
  "ITEM:H4sIAAAAAAAA..."     → deserialize payload as Base64 ItemStack
  "SENTINEL:FRONT"           → no item data; notify player to collect via /party command
```

The reader always splits on the first `:` character to extract `{TYPE}`. The following conditions are all treated as corrupted entries — in every case: log ERROR with flag ID and raw stored value, delete the PDC key, do not attempt item delivery, notify player via chat to re-request via `/party` (Q7):

| Corruption condition | Handling |
|----------------------|----------|
| Stored value does not contain `:` | ERROR + delete key + notify player |
| `{TYPE}` is not `ITEM` or `SENTINEL` | ERROR + delete key + notify player |
| `{TYPE}` is `ITEM` and payload is empty string | ERROR + delete key + notify player |
| `{TYPE}` is `ITEM` and Base64 decode fails | ERROR + delete key + notify player |
| `{TYPE}` is `ITEM` and `ItemStack` deserialization throws | ERROR + delete key + notify player |

This format is forward-compatible — new type prefixes can be added without breaking existing parsing logic.

---

## 4. Configuration

New keys added to `PluginConfig` (section `flag` in `config.yml`):

| Config key | Kotlin property | Type | Default | Validation rule |
|------------|-----------------|------|---------|-----------------|
| `flag.supportBlockMaterial` | `flagSupportBlockMaterial` | `Material` | `BEDROCK` | Must resolve to `Material.BEDROCK` or `Material.OBSIDIAN`; otherwise log ERROR and fall back to `BEDROCK` (CC-04) |
| `flag.minAirAbove` | `flagMinAirAbove` | `Int` | `2` | Must be ≥ 1; negative/zero treated as 1 with WARN |
| `flag.titleFormat` | `flagTitleFormat` | `String` | `"§6{type} — §f{player}"` | If format-template parse fails (unmatched braces) — log WARN, use raw string as template (CC-14) |
| `flag.maxPerChunk` | `flagMaxPerChunk` | `Int` | `50` | Must be ≥ 1; if ≤ 0, log WARN and apply default 50 (CC-13) |
| `flag.allowedWorlds` | `flagAllowedWorlds` | `List<String>` | `["world"]` | If list is empty after parsing, log WARN: "flag.allowedWorlds is empty — flag placement is disabled in all worlds" (CC-10) |
| `flag.startupScanBatchSize` | `flagStartupScanBatchSize` | `Int` | `10` | Must be ≥ 1; if ≤ 0, log WARN and apply default 10. Controls how many flags are processed per tick in startup repair scan Phase 2 (Section 15.1). |

Validation is performed once at plugin startup in `ComminusmPlugin.onEnable` before any listener is registered.

---

## 5. Activation Flow

Triggered in `OrderFlagListener.onBlockPlace` (WHITE_BANNER) and `FrontFlagListener.onBlockPlace` (RED_BANNER).

All steps before the async DB call execute on the **main thread**.

```
Step  1. Pre-conditions (all on main thread)
         a. Early-exit guard (Q9): check banner material and display-name match first (existing check).
            If the placed block is not a WHITE_BANNER (for orders) or RED_BANNER (for fronts), or its
            display name does not match the expected flag name pattern → RETURN immediately without
            any PDC reads. This prevents the PDC scan overhead for the vast majority of banner placements
            that are not flag activations.
         b. Check world is in flagAllowedWorlds → if not: cancel event, send AC-20 message. STOP.
         c. Check order/front business preconditions (existing checks: owner exists, not already activated, etc.).
         d. Check position not already occupied by an existing flag:
            - Consult in-memory cache (Section 8.1a): if support-block position is in cache for this chunk
              → cancel, send "position already occupied" (CC-06). STOP.
            - If cache has no entry for this chunk (cold-start miss): fall back to reading
              comminusm:flag/* PDC keys (one-time cost, result is cached per Section 8.1a).
         e. Check flagMaxPerChunk: use cache size for this chunk key if available; otherwise
            count comminusm:flag/* keys in chunk PDC. If count ≥ limit: cancel, send AC-22 message. STOP.
         f. Check minAirAbove: for each Y from bannerY+1 to bannerY+flagMinAirAbove
            (capped at 318 per AC-09 world-border rule): if block is not in
            {AIR, CAVE_AIR, VOID_AIR, WATER, LAVA} → cancel, send AC-09 message. STOP.

Step  2. Acquire chunk lock (main thread — non-blocking, Q1)
         - Lock key: "{worldName}:{chunkX}:{chunkZ}"
         - Call lock.tryLock() with ZERO timeout. Blocking is FORBIDDEN on the main thread.
         - If tryLock() returns false → cancel event, send AC-30 message "Попробуйте ещё раз через секунду". STOP.
         - Once lock is acquired: re-check Step 1d (double-checked locking).

Step  3. Capture original support-block material (main thread, local val — AC-41)
         - originalMaterial: Material = supportBlock.type
         (This is a local immutable variable, not stored in any field.)

Step  4. Write PDC key comminusm:flag/{id} (main thread)
         - value = "{world}:{bannerX}:{bannerY}:{bannerZ}"

Step  5. Replace support block with flagSupportBlockMaterial (main thread)

Step  6. Write PDC key comminusm:support_material/{id} = Material.name (main thread).
         Update in-memory flag position cache: add banner position and support block position to
         `flagPositionCache[chunkKey]` (Q3 — cache populated inside lock scope, before lock release).

Step  7. Release chunk lock (main thread, BEFORE async dispatch — Q2)
         - lock.unlock() is called here, inside a finally block, before any async work.
         - This ensures the main thread is never holding the lock while waiting for async I/O.

Step  8. Async DB call (BukkitScheduler.runTaskAsynchronously)
         - Pass originalMaterial, flagId, bannerPosition, playerUuid as immutable parameters.
         - Save order/front record to DB.
         - On DB error → schedule Rollback (Step R) via BukkitScheduler.runTask (main thread).

Step  9. Back on main thread (BukkitScheduler.runTask callback from Step 8)
         - Re-acquire chunk lock with tryLock() (non-blocking). If fails: schedule self via
           BukkitScheduler.runTask with 1-tick delay. Max 5 retry ticks; on 5th failure → go to Rollback (Step R).
         - Verify comminusm:flag/{id} PDC key still present (concurrent deletion guard).
           If absent: log INFO "flag {id} deleted during async window, skipping armorstand spawn". Return.
         - Spawn ArmorStand at bannerY+2 (one block above banner):
           setVisible(false), setGravity(false), setMarker(true), setCustomNameVisible(true)
           customName = format titleFormat with owner name (Section 6.1 — null-safe).
         - If ArmorStand spawn fails (Bukkit returns null or throws) → go to Rollback (Step R).
         - Write PDC key comminusm:armorstand/{id} = armorStand.uniqueId.toString() (main thread).
         - Release chunk lock.
         - Send success message to player (null-safe: check player.isOnline first — CC-05).

Step  R. Rollback (main thread via BukkitScheduler.runTask)
         Parameters: originalMaterial (immutable, passed from Step 3 or Step 8 context)
         a. Re-acquire chunk lock with tryLock(). If fails: schedule self 1-tick retry (max 5 ticks).
            If 5th retry fails: log CRITICAL "rollback could not acquire lock for flag {id}". Return.
         b. Attempt entity.remove() on ArmorStand (if it was spawned).
            - If remove() fails: write PDC comminusm:dirty_armorstand/{id} = armorStand.uniqueId.toString()
              Log ERROR. Continue rollback (AC-37).
         c. Delete PDC keys: comminusm:armorstand/{id}, comminusm:flag/{id}, comminusm:support_material/{id}.
         d. Restore support block to originalMaterial (AC-29). Never restore to AIR.
         e. Release chunk lock.
         f. Send error message to player if online (CC-05: check player.isOnline).
```

---

## 6. Deletion / Deactivation Flow

Triggered by:
- `OrderService.deleteByOwner` → GUI confirmation via `FlagDeletionConfirmListener`
- `WorkFrontService.deactivate` → `BlockListener` or front-move operation

All steps execute on the main thread unless noted.

**Lock scope:** the chunk lock acquired at Step 1 is held **continuously and without release** through Step 8 (lock release). There is no gap between any two consecutive steps through Step 7. In particular:

- There is no gap between Step 4 (block → AIR) and Step 6 (PDC key deletion): no other thread can observe a state where the support block is AIR but PDC keys still claim the position is an active flag.
- There is no gap between Step 6 (PDC key deletion) and Step 7 (cache eviction): no protection handler can see an absent PDC key while the cache still reports the position as protected.

The DB record deletion (Step 9) is offloaded asynchronously **after** the lock is released. All world-visible state (block → AIR, PDC keys deleted, cache evicted) is committed synchronously under the lock. The async DB deletion cannot be observed by any in-game event; it is purely a persistence operation.

```
Step  1. Acquire chunk lock (non-blocking tryLock(), same key scheme as activation — Q1).
         If tryLock() fails → schedule retry via BukkitScheduler.runTask 1-tick delay (max 5 ticks).
         If chunk is not loaded → see Section 6.2 (unloaded-chunk handling — CC-02).
         [Lock held from here through Step 8]

Step  2. Look up ArmorStand:
         a. Primary: read PDC key comminusm:armorstand/{id} → UUID → find entity by UUID.
         b. Fallback (AC-16): scan ArmorStand entities in bounding box
            [supportBlockPos, supportBlockPos + (0,3,0)].
         c. If neither found → log WARN, continue (graceful degradation — NFR-04).

Step  3. entity.remove() the ArmorStand (if found). Log WARN if removal fails.

Step  4. Replace support block with Material.AIR. (Original material is NOT restored — AC-10.)
         [No gap to Step 5 — lock remains held]

Step  5. Delete banner block if still present (set to AIR).

Step  6. Delete PDC keys: comminusm:armorstand/{id}, comminusm:flag/{id},
         comminusm:support_material/{id}.
         [No gap to Step 7 — lock remains held]

Step  7. Evict in-memory flag position cache entries for the deleted flag's banner and support-block
         positions (Section 8.1a). Clear ChunkCacheManager order/front marker (existing logic).

Step  8. Release chunk lock (BEFORE async dispatch — same pattern as Activation Step 7).

Step  9. Delete DB record asynchronously (BukkitScheduler.runTaskAsynchronously — NFR-01).
         - Pass flagId and all required identifiers as immutable parameters.
         - On DB deletion error: log ERROR "DB record deletion failed for flag {id}. World changes
           already committed (block = AIR, ArmorStand removed, PDC keys deleted). DB record is
           orphaned — manual cleanup required." Log WARN "DB record orphaned for flag {id}."
         - World rollback is NOT possible at this point — the world changes (Steps 4–6) are already
           committed and visible. Do not attempt to restore the support block or PDC keys.
         - No player message on async DB error (player has already been shown the success or error
           message by the calling flow; the async step is transparent to the player).
```

### 6.1 Idempotency for concurrent deactivate calls (CC-03)

Before Step 2, under the acquired chunk lock, check that the PDC key `comminusm:flag/{id}` still exists. If absent, the operation was already completed — return without error.

### 6.1a GUI deletion ownership re-validation (Q11)

Ownership is validated **at both GUI open time AND at confirmation event time**.

- At GUI open: `FlagDeletionConfirmListener.onInventoryOpen` checks that the player is the owner of the order (UUID match from DB). If not → close GUI, send permission-denied message.
- At confirmation click (`onInventoryClick` for the confirm slot): re-query the DB for the order record. If the record no longer exists (concurrent deletion by another path) or the owner UUID no longer matches the clicking player → close GUI, send "Флаг уже был удалён или недоступен". Do NOT proceed to world cleanup.

This prevents a TOCTOU window where ownership changes (or the order is deleted by admin command) between GUI open and confirmation click.

### 6.2 Unloaded-chunk handling (CC-02 — resolved, Q7)

**Chosen approach: async chunk load via `world.getChunkAtAsync`.**

`world.getChunkAt(cx, cz)` (synchronous) loads or generates the chunk on the main thread and is therefore prohibited for potentially distant chunks. `world.getChunkAtAsync(cx, cz)` loads the chunk asynchronously and provides a `CompletableFuture<Chunk>` callback.

When `deleteByOwner` or `deactivate` is called and `world.isChunkLoaded(cx, cz)` returns `false`:

1. Proceed immediately with the DB record deletion (async, existing pattern).
2. Call `world.getChunkAtAsync(cx, cz)`. When the future completes (Paper calls the callback on the main thread):
   a. The chunk is now loaded. Re-acquire chunk lock with `tryLock()` (retry pattern, Section 10.2).
   b. Execute world cleanup (Deletion Steps 2–9): remove ArmorStand, replace support block with AIR, delete banner, delete PDC keys.
   c. After cleanup: if the chunk has no players nearby and was not previously loaded before this call, the server's natural unload mechanism will release it. Do NOT call `world.unloadChunk` explicitly — let the server manage.
3. If `getChunkAtAsync` throws or the future completes with an exception: log ERROR "Failed to load chunk for cleanup of flag {id}". The DB record is already deleted. On next server startup, the startup repair scan (Section 15.1) will detect the orphaned block and clean it up.

**Key invariant:** the DB record is always deleted first. If the world cleanup fails, the flag exists as a world orphan (BEDROCK block, no PDC key once cleanup occurs) but no player can interact with it as a flag. The startup repair scan catches this on next startup.

---

## 7. Front Move Flow (AC-38 ordered operations)

Triggered when owner moves front via GUI. All steps on main thread unless noted.

### 7.0 Lock acquisition order for Front Move (cross-reference)

Before executing Step 1, compute `newKey` and `oldKey` per Section 10.1a. If `newKey > oldKey`, acquire the OLD chunk lock first (at what is labelled Step 1 below) and the NEW chunk lock second (at what is labelled Step 6 below). The body of all steps is identical regardless of physical acquisition order; only the lock identity swaps. This prevents AB/BA deadlock.

### 7.1 Crash recovery signal for Front Move (Q4)

Between old-position cleanup and new-position completion there is a crash window. ChunkLoadEvent recovery (Section 9) must be able to detect and complete an interrupted move. The PDC key `comminusm:flag/{id}` is written to the **new** chunk **before** any destructive changes to the old position (Step 5 below). This makes the new chunk's PDC the crash-recovery signal: if `comminusm:flag/{id}` is present in the new chunk but `comminusm:armorstand/{id}` is absent, ChunkLoadEvent Step E Case 1 treats it as an interrupted activation and completes it (spawn ArmorStand, confirm with DB). The DB is also updated before destructive changes (Step 3), so the DB record reflects the new position by the time any recovery runs.

> **Note — intentional deviation from AC-38 literal step numbering:** AC-38 lists "(7) записать PDC нового чанка" as the final step, after "(6) обновить PDC старого чанка (очистить)". This spec intentionally writes the new chunk's PDC key (Step 5) **before** cleaning the old chunk (Steps 7–10). This deviation is required to enable correct crash-recovery (AC-33): the new PDC key must exist before any destructive change so that a ChunkLoadEvent after a crash can detect and complete the interrupted move. Data integrity on crash takes priority over the literal step ordering in AC-38. AC-38 has been updated with a corresponding NOTE (see requirements file).

```
Step  1. Acquire chunk lock for NEW chunk (non-blocking tryLock(). Retry max 5 ticks).

Step  2. Pre-conditions for new position (air-above check, position-occupied check,
         maxPerChunk check for NEW chunk — same as Activation Steps 1d–f).
         On failure → release new lock, send error message. STOP.

Step  3. Async DB update: save new coordinates to work_fronts table (BukkitScheduler.runTaskAsynchronously).
         Pass all immutable parameters (id, oldPos, newPos, originalMaterial snapshot of new pos).
         - On DB error → schedule main-thread callback: release new lock, send error message. STOP.
           No world changes have been made yet.

Step  4. Back on main thread (BukkitScheduler.runTask callback from Step 3).
         Re-acquire new chunk lock (tryLock() with 1-tick retry, max 5 ticks — Section 10.2 pattern).
         If all 5 retries are exhausted (lock never acquired):
           → Front Move Step 4 retry-exhaustion rollback (Step 4R below). STOP.

Step  5. Write PDC key comminusm:flag/{id} = new banner position to NEW chunk (crash-recovery anchor, Q4).
         Write PDC key comminusm:support_material/{id} = actual material at new support block position.

Step  6. Acquire chunk lock for OLD chunk (non-blocking tryLock(). Retry max 5 ticks).

Step  7. Look up and remove old ArmorStand (same as Deletion Steps 2–3).

Step  8. Replace old support block with AIR.

Step  9. Delete old banner block (if present).

Step 10. Delete PDC keys (comminusm:flag/{id}, comminusm:armorstand/{id},
          comminusm:support_material/{id}) from OLD chunk.

Step 11. Release OLD chunk lock.

Step 12. Place new support block (flagSupportBlockMaterial) at new position (main thread).

Step 13. Spawn ArmorStand at new bannerY+2.
          If spawn fails → execute Front Move Step 13 ArmorStand-failure rollback (Step 13R below).
          This is NOT the same as Activation Rollback Step R — see Step 13R for the distinct procedure.

Step 14. Write PDC key comminusm:armorstand/{id} to NEW chunk.
          On failure → log ERROR, retry once synchronously. If retry fails → log CRITICAL.
          (See CC-07 resolution in Section 11 and startup repair scan in Section 15.)

Step 15. Release NEW chunk lock.

Step 16. Send success message to player (check player.isOnline — CC-05).

Step 4R. Front Move Step 4 retry-exhaustion rollback (Q2 — DB updated, NEW chunk PDC not yet written, OLD not yet cleaned).
         Context: DB has new coordinates. NEW chunk PDC keys have NOT been written (Steps 5+ not reached).
         OLD chunk state is unchanged (Steps 7–11 not yet reached).
         Recovery:
         a. Schedule a main-thread async DB task: revert the DB record back to old coordinates.
            - If the DB revert also fails: log CRITICAL "Front Move Step 4R: DB revert failed for flag {id}. Flag {id} is in inconsistent state — DB has new coords, PDC has none. Manual intervention required."
              Write a sentinel PDC key `comminusm:pending_flag/{id}` in the OLD chunk with value `"SENTINEL:MOVE_FAILED"` so that the startup repair scan can flag the inconsistency. Do NOT clean the world.
         b. If DB revert succeeds: no PDC keys were written, no world changes were made.
            The flag remains intact at the old position with all PDC keys unchanged.
            Log ERROR "Front Move retry-exhaustion for flag {id}: DB reverted to old position. Player must retry."
         c. Send error message to player if online: "Ошибка перемещения, попробуйте позже".

Step 13R. Front Move ArmorStand spawn failure rollback (Q2/Q6 — old position cleaned, DB at new coords, NEW PDC written, support block placed, ArmorStand absent).
         Context:
           - DB has new coordinates (updated in Step 3).
           - NEW chunk PDC keys comminusm:flag/{id} and comminusm:support_material/{id} are written (Step 5).
           - OLD chunk fully cleaned: ArmorStand removed, support block → AIR, banner → AIR, PDC keys deleted (Steps 7–11).
           - New support block placed at new position (Step 12).
           - ArmorStand spawn failed (Step 13).
         Recovery (execute under the still-held NEW chunk lock):
         a. Restore the new support block to the material captured as `newOriginalMaterial` (captured in Step 2 as part of pre-condition validation, same pattern as Activation Step 3). Never restore to AIR — restore to `newOriginalMaterial`.
         b. Delete PDC keys from NEW chunk: comminusm:flag/{id}, comminusm:support_material/{id}.
         c. Release NEW chunk lock.
         d. Log CRITICAL "Front Move ArmorStand spawn failed for flag {id}. Old position cleaned, new position rolled back. DB retains new coords. Startup repair scan will re-complete on next server start."
            Note: DO NOT roll back the DB. The DB record with new coordinates is the recovery anchor.
         e. Recovery path — startup repair scan (Section 15.1):
            NEW chunk PDC keys comminusm:flag/{id} and comminusm:support_material/{id} were deleted in step b.
            Therefore the standard ChunkLoadEvent flow (Section 9, Step C/E) will NOT detect this flag via PDC —
            there are no PDC keys remaining in the NEW chunk for this flag.
            The startup repair scan (Section 15.1) is the authoritative recovery mechanism: on next server start
            it detects a DB-active flag whose new-position chunk is loaded but has no matching PDC key, then
            re-registers the PDC keys and spawns the ArmorStand.
            ChunkLoadEvent is NOT the recovery path for this rollback state — it will not fire for an already-loaded
            chunk, and even if the chunk reloads before the next restart, the absent PDC keys mean Step C will
            not recognise the flag. Rely solely on the startup repair scan.
            This is the guaranteed recovery path. No manual intervention is required.
         f. Send error message to player if online (CC-05): "Ошибка перемещения, флаг будет восстановлен автоматически".
```

---

## 8. Protection Event Handlers

> **Legacy flags (OS-02, OS-03, AC-34):** Flags activated before this feature deployment have no `comminusm:flag/*` PDC key and are out of scope for protection. The system does NOT perform automatic migration or lazy PDC backfill for legacy flags. Legacy flags remain unprotected until the owner re-activates them. This is an accepted risk documented in AC-34 and OS-03. CC-08 (legacy flags having no PDC key so protection handlers will not recognise them) is an accepted corner case.

### 8.1 Existing handlers — extended

**`BlockListener.onBlockBreak(BlockBreakEvent)`**
- Existing: cancels break of banner blocks by non-owners; shows GUI for owner.
- **Extended:** also cancel break of the support block (block below banner identified via PDC `comminusm:flag/*` lookup) regardless of `GameMode`. Cancel for creative players (AC-13, AC-32).

**`ExplosionListener.onEntityExplode(EntityExplodeEvent)`**  
**`ExplosionListener.onBlockExplode(BlockExplodeEvent)`**
- Existing: removes banner blocks from `blockList()`.
- **Extended:** also remove the support block (position = bannerY - 1) from `blockList()`.
- Support block identified by: checking `comminusm:flag/*` PDC in its chunk.

### 8.1a Per-chunk flag position cache (Q6)

`BlockFromToEvent`, `BlockPistonExtendEvent`, `BlockPistonRetractEvent`, and `EntityChangeBlockEvent` may fire hundreds of times per second. Reading all `comminusm:flag/*` PDC keys on every event call for every candidate chunk creates unacceptable overhead.

**Mandated cache design:**

`FlagStabilityManager` maintains an in-memory position cache:

```
private val flagPositionCache: ConcurrentHashMap<String, Set<Long>>
```

- **Key:** `"{worldName}:{chunkX}:{chunkZ}"` (same scheme as lock map).
- **Value:** `Set<Long>` of packed block positions within that chunk. Each `Long` is `BlockVector3.toLong()` (or equivalent: `(x and 0xFFFFFFF shl 36) or (y and 0xFFF shl 24) or (z and 0xFFFFFF)` — exact packing scheme chosen by implementor, documented in code).

**Cache lifecycle:**

| Event | Cache operation |
|-------|----------------|
| Flag activated — cache updated at **Activation Step 6** (inside lock scope, before lock release at Step 7) | `flagPositionCache[chunkKey]?.plus(position)` or create new set |
| Flag deleted (Deletion Step 6) | Remove position from set; if set empty, remove key |
| `ChunkLoadEvent` (after Section 9 processing completes) | Rebuild cache entry from all `comminusm:flag/*` PDC keys for that chunk |
| `ChunkUnloadEvent` (new event — register in `ComminusmPlugin.onEnable`) | Remove cache entry for the unloaded chunk key |
| Rollback (Activation Step R) | Remove position from cache if it was added |

**Cache update timing (Q3 — resolved):**

The cache entry for a newly activated flag is populated at **Step 6** of the Activation flow (after writing `comminusm:support_material/{id}` to PDC but **before** releasing the lock at Step 7 and before dispatching the async DB call at Step 8). This eliminates the window between lock release and cache population described in Q3.

The cold-start fallback (see "Cache miss / cold-start" paragraph below) reads PDC keys directly when no cache entry exists for a chunk. `comminusm:flag/{id}` is written at Activation Step 4, which is also inside the lock scope and before lock release. Therefore the PDC key is guaranteed to be present and visible to the fallback reader at any point after Step 4 completes on the main thread. There is no visibility gap: both the PDC key (Step 4) and the cache entry (Step 6) are written on the main thread under the chunk lock before the lock is released.

**Protection handler lookup:** `FlagProtectionListener` calls `flagStabilityManager.isFlagPosition(world, blockPosition): Boolean`. This method checks the in-memory cache only — no PDC reads at event time.

**Cache miss / cold-start:** If the cache has no entry for a chunk key (chunk was loaded before the plugin registered `ChunkLoadEvent`, or cache was evicted), `isFlagPosition` falls back to a single PDC key count check (`getKeys(PersistentDataType.STRING)` filtered by `comminusm:flag/` prefix). The result is cached immediately. This fallback fires at most once per chunk per cold-start.

### 8.2 New handler class — `FlagProtectionListener`

Implements `Listener`. Constructor receives `FlagStabilityManager`.

| Event | Method signature | Behavior |
|-------|-----------------|----------|
| `BlockFromToEvent` | `onLiquidFlow(event: BlockFromToEvent)` | If `event.toBlock` position equals a support block or banner block of any registered flag in that chunk → `event.isCancelled = true` (AC-03) |
| `BlockPistonExtendEvent` | `onPistonExtend(event: BlockPistonExtendEvent)` | If any block in `event.blocks` is a support block or banner block → `event.isCancelled = true` (AC-04) |
| `BlockPistonRetractEvent` | `onPistonRetract(event: BlockPistonRetractEvent)` | Same check on `event.blocks` (AC-04) |
| `EntityChangeBlockEvent` | `onFallingBlock(event: EntityChangeBlockEvent)` | If `event.block` is a support block or banner block → `event.isCancelled = true` (AC-12) |

Flag-position lookup in all handlers: call `flagStabilityManager.isFlagPosition(world, blockPosition)` which consults the in-memory cache (Section 8.1a). No PDC reads occur at event time during normal operation. Support-block position is cached directly (bannerY - 1); both banner and support positions are stored as separate entries in the cache set.

---

## 9. ChunkLoadEvent Handler (`ChunkLoadListener`)

Listens to `ChunkLoadEvent`. Delegates to `FlagStabilityManager.onChunkLoad(chunk)`.

### 9.0 Async offload requirement (Q3)

`ChunkLoadEvent` fires on the main thread. A single chunk may contain up to `flagMaxPerChunk` (default 50) registered flags. Each flag in Step E requires a DB query to resolve active/deleted state. Executing 50 synchronous DB round-trips on the main thread is prohibited.

**Mandated approach (single async task per ChunkLoadEvent):**

1. On the `ChunkLoadEvent` handler (main thread): read all `comminusm:flag/*` PDC keys from the chunk synchronously (PDC reads are in-memory, safe on main thread). Collect flag IDs that require DB consultation (those reaching Step E).
2. If any flags require DB consultation: dispatch **exactly one** `BukkitScheduler.runTaskAsynchronously` carrying the list of flag IDs. Perform all DB queries in one async task (batch by IDs where the repository supports it, otherwise sequential queries in async thread — still non-blocking to main thread).
3. In the async task: collect all decisions (per flag: CREATE_ARMORSTAND / CLEANUP_WORLD / NO_OP).
4. Dispatch a single `BukkitScheduler.runTask` back to main thread with all collected decisions. Execute all world changes (ArmorStand spawn, block replacement, PDC mutations) on main thread.
5. Steps A (dirty-armorstand), D (passive verification with non-DB material compare), and F (name refresh) do NOT require DB queries and execute synchronously on the main thread during the initial ChunkLoadEvent handler call, before the async dispatch.

Processing order within `onChunkLoad` for each `comminusm:flag/{id}` key found in chunk PDC:

```
For each flag key in chunk:

  A. Dirty-ArmorStand cleanup (highest priority, AC-37)
     - If comminusm:dirty_armorstand/{id} key present:
       1. Look up ArmorStand entity by UUID.
       2. If found → entity.remove(). Log INFO.
       3. Remove PDC key comminusm:dirty_armorstand/{id} regardless of whether entity was found.
     - Continue to step B.

  B. Resolve flag position from comminusm:flag/{id} value.
     - supportBlockPos = bannerPos - (0,1,0).

  C. Legacy-flag guard (AC-24, AC-26, Q5)
     - If PDC key comminusm:support_material/{id} is ABSENT:
       → Check if support block material is BEDROCK or OBSIDIAN:
           YES → new flag, crashed before writing support_material key. Go to step E (repair).
                 (This also covers Q5 partial-write: flag key present, support_material absent,
                  armorstand may or may not be present — Step E handles both sub-cases.)
           NO  → legacy flag (NFR-02). Do nothing. CONTINUE to next flag.
     - If PDC key comminusm:support_material/{id} is PRESENT AND comminusm:armorstand/{id} is ABSENT
       AND actual block material matches stored support_material value:
       → Partial PDC write (Q5): flag/{id} and support_material/{id} written, armorstand/{id} not written.
         Crashed between Activation Steps 6 and 9 (after lock release, before armorstand spawn callback).
         Go to Step E Case 1 directly (treat as "activation repair needed").
         Log INFO "Partial PDC write detected for flag {id}, proceeding to repair".
     - If PDC key comminusm:support_material/{id} is PRESENT AND comminusm:armorstand/{id} is PRESENT:
       → Go to step D.

  D. Passive verification (AC-14, AC-40)
     - Read comminusm:support_material/{id} from PDC.
     - Compare with actual block material at supportBlockPos.
     - If materials match → go to step F (name refresh).
     - If actual block is AIR → go to step E (lazy repair / deletion disambiguation).
     - If actual block has different non-AIR material → support block was replaced by command.
       Restore support block to the material stored in PDC. Log WARN. Go to step F.

  E. Repair / deletion disambiguation (AC-23, AC-24, AC-27)
     - ArmorStand key presence?

       CASE 1: comminusm:armorstand/{id} is ABSENT (no ArmorStand UUID in PDC)
         → Crashed during activation (before armor stand was created, AC-24).
         → Query DB: does record for {id} exist and is it active?
           DB ERROR: apply fail-safe (AC-36): log WARN, do not change world, skip. CONTINUE.
           DB PRESENT: create ArmorStand (lazy completion). Write comminusm:armorstand/{id}.
                       Also write comminusm:support_material/{id} if absent (AC-40 consistency).
                       Log INFO "Activation repair completed for flag {id}".
           DB ABSENT:  record was deleted. This is an inconsistent state — log ERROR.
                       Treat as "crashed during deletion" (fall through to CASE 2 but with no-AS path).

       CASE 2: comminusm:armorstand/{id} is PRESENT but entity not found in world
         → Crashed during deletion (AC-23) OR orphaned state.
         → Query DB: does record for {id} exist and is active?
           DB ERROR: fail-safe — log WARN, skip. CONTINUE.
           DB PRESENT: partial-activation crash OR partial-deletion crash with active record.
                       If support block at supportBlockPos is AIR (AC-14 lazy repair):
                         Restore support block to the material stored in
                         comminusm:support_material/{id} PDC key. Log WARN "Support block AIR for
                         active flag {id}, restoring to {material}." Never restore to AIR — if the
                         PDC key is missing or the material value is unparseable, log ERROR and skip
                         the restore step (do not crash; proceed to ArmorStand creation).
                       Recreate ArmorStand (AC-27 — preserve owner's private). Update PDC.
                       Log WARN "Orphan armor stand for flag {id}, recreation performed".
           DB ABSENT:  deletion was completed in DB; finish world cleanup:
                       replace support block with AIR, delete banner block, remove all PDC keys for {id}.
                       Log INFO "Deletion cleanup completed for flag {id}".

  F. Name lazy refresh (AC-25)
     - Read ArmorStand UUID from PDC.
     - If entity found → compare customName with Bukkit.getOfflinePlayer(ownerUuid)?.name.
       If name is null → log WARN, skip refresh (CC-01).
       If names differ → update customName on ArmorStand.
     - If entity not found → skip (CC-12: do not crash).
```

---

## 10. Concurrent Safety (AC-30, FR-07)

### 10.1 Chunk-level lock map

`FlagStabilityManager` holds:

```
private val chunkLocks: ConcurrentHashMap<String, ReentrantLock>
```

Key: `"{worldName}:{chunkX}:{chunkZ}"`

**Lock acquisition — non-blocking, main-thread safe (Q1, Q2):**

Blocking the main server thread is forbidden (CLAUDE.md hard rule). `tryLock(timeout)` with a non-zero timeout is therefore prohibited anywhere on the main thread. The following pattern is mandated instead:

1. Compute key from target location.
2. `chunkLocks.computeIfAbsent(key) { ReentrantLock() }`.
3. Attempt `lock.tryLock()` — **zero timeout, non-blocking**.
4. If `tryLock()` returns `false` → immediately cancel the event / operation, send player message "Попробуйте ещё раз через секунду" (AC-30). Return without world changes.
5. `tryLock()` returns `true` → proceed with world changes.
6. `lock.unlock()` is called in a `finally` block **on the main thread before any async dispatch**.

The "5-second retry" guarantee from AC-30 is fulfilled at the player level: the player can re-place the banner within 5 seconds while the competing operation completes. The server thread is never blocked.

### 10.1a Canonical two-lock ordering rule — all multi-lock operations (Q1 — deadlock prevention)

Any operation that must acquire two chunk locks simultaneously **must acquire them in canonical key order**. The rule applies universally — not only to Front Move but to any current or future flow that accesses a support-block chunk's PDC while already holding a banner-chunk lock, or that otherwise needs two distinct chunk locks.

Currently the only such operation is Front Move (NEW chunk lock at Step 1, OLD chunk lock at Step 6). Deletion and deactivation each access only a single chunk (the chunk containing the support block) and therefore hold only one lock at a time — no ordering conflict is possible for those flows today. If any future change to deletion or deactivation causes it to access a second chunk's PDC (e.g., reading a support-block chunk while holding a banner-chunk lock that spans a chunk boundary), the canonical ordering rule below applies immediately to that path.

**Canonical ordering rule:**

- Canonical order: lexicographic ascending on the lock key string `"{worldName}:{chunkX}:{chunkZ}"`.
- Before acquiring the first lock: compute both keys (`keyA` and `keyB`).
- If `keyA < keyB` (lexicographically): acquire `keyA` first, then `keyB`.
- If `keyA > keyB`: acquire `keyB` first, then `keyA`.
- If `keyA == keyB` (both positions in the same chunk): acquire the single shared lock once; the second acquisition is a no-op.

For Front Move specifically:
- Before Step 1: compute both `newKey` and `oldKey`.
- If `newKey < oldKey`: acquire NEW lock first (current order in Section 7 — no change).
- If `newKey > oldKey`: acquire OLD lock first (Step 1 becomes "acquire OLD lock"), then acquire NEW lock (Step 6 becomes "acquire NEW lock"). All subsequent step references to "OLD lock" and "NEW lock" are unaffected — only the acquisition order changes.
- If `newKey == oldKey`: acquire the single shared lock once. Steps 1 and 6 both refer to the same lock; Step 11 is a no-op.

This rule is **mandatory for every multi-lock path**. No other ordering is permitted. All retry and release logic in Sections 7 and 10.2 applies identically regardless of which key is acquired first.

### 10.2 Lock scope — no cross-thread lock holding (Q2)

The lock scope covers **only synchronous main-thread operations**: pre-condition re-check, support-block placement, PDC writes, and ArmorStand spawn (all happen synchronously before the async DB call). The lock is **released before** `BukkitScheduler.runTaskAsynchronously` is called in activation Step 7.

Consequence: the async DB call and its main-thread callback (Step 8) execute outside the lock. To prevent a race between the callback and a concurrent deletion that arrives during the async window, the main-thread callback (Step 8) re-acquires the lock with `tryLock()` before writing the ArmorStand PDC key and before reading the current PDC state. If `tryLock()` fails in the callback, the callback schedules itself to retry via `BukkitScheduler.runTask` with a 1-tick delay — maximum 5 retry ticks, then log ERROR and roll back.

### 10.3 Adjacent-position concurrent activations (Q10)

Two players activating flags at adjacent positions within the same chunk both acquire the same chunk lock (Section 10.1). They are therefore serialised: activation #2 does not begin until activation #1 releases the lock (before its async DB call, per Section 10.2). Because the lock is released before the async work in activation #1, there is a window where activation #1's PDC keys are written and activation #2 has acquired the lock and performs its double-check (Step 2, re-check of Step 1d). Activation #2 reads the cache/PDC, finds only its own position, and proceeds without interference from activation #1's ongoing async step.

**Adjacent-position rollback interference is explicitly out of scope:** if activation #2 is rolled back, it only removes its own PDC keys (keyed by `{id}`) and restores its own support block position. It does not touch activation #1's keys or blocks. Each flag's PDC keys and world blocks are scoped to a unique `{id}`, so cross-flag corruption is structurally impossible within the same chunk.

**Conclusion:** adjacent-position interference does not require additional handling beyond the existing chunk lock and per-ID key namespacing.

### 10.4 Concurrent deactivate idempotency (CC-03)

Before performing any world changes in the deletion flow, check under the chunk lock:
- If `comminusm:flag/{id}` key is absent from PDC → already deleted. Return without error.
- If support block is already AIR and PDC key is absent → same conclusion.


---

## 11. Edge Cases

Every Critical and High corner case from the register is addressed here.

| CC # | Severity | Condition | Specified Handling |
|------|----------|-----------|-------------------|
| CC-01 | Critical | `Bukkit.getOfflinePlayer(uuid).name` returns `null` | At activation: use `uuid.toString()` as fallback display name. At lazy refresh: skip update, log WARN. Never NPE. (Section 9, Step F) |
| CC-02 | Critical | Deletion/deactivation called when target chunk is not loaded | DB record deleted immediately. `world.getChunkAtAsync` loads the chunk; world cleanup (armorstand, support block, banner, PDC keys) runs in the async callback's main-thread follow-up. Startup repair scan catches orphaned blocks if async load fails (Section 6.2, Section 15.1). |
| CC-03 | Critical | Two concurrent calls to `deactivate`/`deleteByOwner` for the same entity | Chunk lock (Section 10.1) serialises the calls. First call completes deletion; second call detects absent PDC key and returns idempotently without NPE or DB violation (Section 10.4). |
| CC-04 | High | `flag.supportBlockMaterial` is not `BEDROCK` or `OBSIDIAN` | Startup validation logs ERROR, falls back to `BEDROCK`, plugin continues (Section 4). |
| CC-05 | High | Player disconnects during async DB write | All rollback and world-change callbacks are dispatched via `BukkitScheduler.runTask` and do NOT reference the player object for world changes. Player message is sent only if `player.isOnline` at callback time. (Activation flow Step 8 / Step R) |
| CC-06 | High | Position already occupied by another flag | Step 1d of Activation flow: PDC key scan before lock acquisition; re-checked inside lock (double-checked locking). Activation cancelled with "position already occupied" message. |
| CC-07 | High | PDC write to new chunk fails at Step 14 of Front Move | Retry once synchronously. If retry fails: log CRITICAL. Startup repair scan (Section 15.1) detects DB-active flags with no matching PDC key and re-registers them on next server start. Flag is unprotected only until next restart. |
| CC-08 | High | GUI order-deletion and front-move target same chunk concurrently | Both operations acquire the same chunk lock (Section 10.1) via non-blocking `tryLock()`. One wins; the other retries up to 5 ticks then cancels with player message. Final state is deterministic. |
| CC-09 | High | Null or invalid UUID in DB record | Validate UUID before calling `Bukkit.getOfflinePlayer`. If null or unparseable: log ERROR, use `"[unknown]"` as display name. Activation continues (Section 9, name-resolution sub-step). |

Edge cases from CornerCaseReviewer run 2:

| Q # | Severity | Condition | Specified Handling |
|-----|----------|-----------|-------------------|
| Q1 | Critical | Front Move holds NEW lock (Step 1) and acquires OLD lock (Step 6); concurrent op holds OLD first → AB/BA deadlock | Canonical two-lock ordering rule (Section 10.1a): always acquire lower lexicographic key first. If newKey > oldKey, OLD is acquired at Step 1 and NEW at Step 6. No other flow acquires two locks. Deadlock structurally impossible under this rule. |
| Q2 | Critical | Front Move Step 4 re-acquisition: all 5 retries fail. State: DB updated to new coords, NEW chunk PDC not written, world unchanged | Step 4R rollback: schedule async DB revert to old coords. If DB revert succeeds: no world changes made, flag intact at old position, player retries. If DB revert also fails: log CRITICAL, write SENTINEL PDC marker in old chunk, startup repair scan detects inconsistency (Section 7, Step 4R). |
| Q3 | High | Cache updated at Activation Step 9 (after lock release) but lock released at Step 7 — window where PDC key exists but cache is empty | Cache update moved to Activation Step 6 (inside lock scope, before Step 7 lock release). Cold-start fallback reads PDC directly; comminusm:flag/{id} is written at Step 4 (inside lock), guaranteed visible. No window exists (Section 8.1a). |
| Q4 | High | `world.isChunkLoaded(cx, cz)` called from async thread in startup repair scan — Bukkit World API not async-safe | Startup repair scan redesigned as two-phase: Phase 1 (async) queries DB only, no Bukkit API; Phase 2 (main-thread callback) calls `world.isChunkLoaded()` and all PDC/world operations (Section 15.1). |
| Q5 | High | Multiple repair candidates from same ChunkLoadEvent could dispatch separate async DB tasks → callbacks race on same flags | Single async task per ChunkLoadEvent: all repair candidates collected on main thread in one synchronous PDC-key pass, dispatched in one `runTaskAsynchronously` call, resolved in one main-thread callback (Section 9.0). |
| Q6 | High | Front Move Step 13 ArmorStand spawn failure: old position already cleaned, recovery path | Step 13R rollback (Section 7): new support block restored to `newOriginalMaterial`, new PDC keys deleted, DB retains new coords. Since PDC keys are deleted, ChunkLoadEvent Case 1 does NOT fire (no PDC keys to scan). Startup repair scan (Section 15.1) is the authoritative recovery: on next server start it detects a DB-active flag with no matching PDC key in the loaded NEW chunk and re-registers PDC keys, spawns ArmorStand. ChunkLoadEvent is NOT the recovery path. |
| Q7 | Medium | Key 5 `ITEM:` prefix with empty or undeserializable payload | Treated as corruption: log ERROR with flag ID and raw value, delete PDC key, notify player to re-request via `/party`. No delivery attempt. Covers: empty payload, Base64 decode failure, ItemStack deserialization exception (Section 3, Key 5). |

Additional AC-specified edge cases handled by sections referenced above:

| AC # | Handled in |
|------|-----------|
| AC-13 (creative break support) | Section 8.1 — `BlockListener` extended |
| AC-14 (passive verification) | Section 9, Step D |
| AC-18/AC-24 (crash before armor stand) | Section 9, Step E Case 1 |
| AC-23 (crash before support removal) | Section 9, Step E Case 2 |
| AC-26 (natural BEDROCK false-positive) | Section 9, Step C — PDC key as primary discriminator |
| AC-27 (DB check before completing deletion) | Section 9, Step E Case 2 — DB query |
| AC-29 (restore exact original material on rollback) | Activation Step 3 (capture) + Rollback Step c |
| AC-30 (wall-clock timeout for concurrent activation) | Section 10.1 — non-blocking `tryLock()` with player-level retry (banner re-place within 5s) |
| AC-31 (full rollback on DB failure) | Activation Rollback Step R |
| AC-35 (authoritative chunk = support block's chunk) | Section 3 — all PDC keys stored in support block's chunk |
| AC-36 (DB unavailable in ChunkLoadEvent) | Section 9, Step E — DB ERROR path: fail-safe, no world changes |
| AC-37 (dirty armorstand cleanup) | Section 9, Step A; Rollback Step a |
| AC-39 (pending-flag via /party) | Section 3 Key 5; Section 11 row CC-01 (pending flag marker) |
| AC-40 (support_material key, compare vs PDC not config) | Section 9, Step D; Section 3 Key 3 |
| AC-41 (original material as local val before async) | Activation Step 3 — explicit local `val` requirement |

---

## 12. Error Handling

| Error Condition | Log Level | Player Message | World / State Change |
|-----------------|-----------|---------------|----------------------|
| `flag.supportBlockMaterial` invalid at startup | ERROR | None (console only) | Fall back to BEDROCK |
| `flag.allowedWorlds` empty at startup | WARN | None (console only) | All activations rejected |
| `flag.maxPerChunk` ≤ 0 at startup | WARN | None (console only) | Default 50 applied |
| ArmorStand spawn fails during activation | ERROR | "Ошибка активации, попробуйте позже" | Full rollback (Section 5, Step R) |
| DB write fails during activation | ERROR | "Ошибка сохранения, активация отменена" | Full rollback (Section 5, Step R) |
| DB write fails at Step 1 of Front Move | ERROR | "Ошибка перемещения, флаг не изменён" | No world changes performed |
| `getOfflinePlayer(uuid)` returns null name | WARN | None | Fallback to UUID string as display name |
| Null/invalid UUID in DB record | ERROR | None | Fallback to `"[unknown]"` as display name |
| ArmorStand entity not found during deletion | WARN | None | Deletion continues gracefully |
| DB unavailable during `ChunkLoadEvent` | WARN | None | No world changes; retry on next load |
| ArmorStand removal fails during rollback | ERROR | None to player; message on activation side | Continue rollback; write dirty_armorstand marker |
| PDC write fails during Front Move Step 14 | ERROR (retry), then CRITICAL | None | New flag exists but unregistered in PDC; recovered on next restart by startup repair scan |
| Chunk lock not acquired (non-blocking tryLock() returns false) | INFO | "Попробуйте ещё раз через секунду" | No changes; player retries manually |
| Chunk lock not acquired after 5-tick async retry | ERROR | "Ошибка блокировки, попробуйте позже" | Rollback initiated if in activation callback; deletion retried next tick |
| `getChunkAtAsync` fails during unloaded-chunk cleanup | ERROR | None | DB record already deleted; startup repair scan cleans orphaned block on next restart |
| Pending flag PDC value malformed (no type prefix, unknown type, empty ITEM payload, Base64/deserialization failure) | ERROR | Player notified to re-request via `/party` | Key deleted; no item delivered to player (Q7) |
| Front Move Step 4 retry-exhaustion: DB revert succeeds | ERROR | "Ошибка перемещения, попробуйте позже" | No world changes; flag intact at old position; DB reverted |
| Front Move Step 4 retry-exhaustion: DB revert also fails | CRITICAL | "Ошибка перемещения, попробуйте позже" | SENTINEL PDC marker written in old chunk; manual intervention logged |
| Front Move Step 13R ArmorStand spawn fails: new position rolled back | CRITICAL | "Ошибка перемещения, флаг будет восстановлен автоматически" | New support block restored to newOriginalMaterial; new PDC keys deleted; DB retains new coords; startup repair completes on next load |
| GUI deletion confirmation: DB record missing at confirm time | INFO | "Флаг уже был удалён или недоступен" | GUI closed; no world changes |
| Async DB deletion fails after world cleanup (Section 6, Step 9) | ERROR + WARN | None (world changes already committed) | DB record orphaned — world is clean (block = AIR, PDC deleted); manual DB cleanup required; no world rollback possible |

---

## 13. Security Considerations

- All flag operations are gated on the player's ownership of the order or front (UUID from DB, not from player input). A player cannot activate, delete, or deactivate another player's flag.
- `comminusm.admin` permission is required for any admin-level override. Direct block destruction by admins is not interceptable (NFR-03); the passive verification in `ChunkLoadEvent` (Section 9, Step D) will restore any admin-tampered support block at next chunk load.
- PDC keys are namespaced under `comminusm:` to prevent accidental collision with other plugin data.
- `getOfflinePlayer(uuid)` may expose cached player names; no passwords, tokens, or sensitive data are stored in PDC or logged.
- The `comminusm:pending_flag/*` key stores a serialized `ItemStack`. The content is the flag item the player already owned; no new privilege is granted by the pending marker.

---

## 14. Dependencies

| Dependency | Type | Notes |
|------------|------|-------|
| `OrderService` | Existing service | `deleteByOwner` extended to call `FlagStabilityManager.cleanupOrderFlag` |
| `WorkFrontService` | Existing service | `deactivate` and `activate` extended to call `FlagStabilityManager` |
| `ChunkCacheManager` | Existing storage | Extended with new PDC key methods (Section 3) |
| `OrderRepository` | Existing storage | Read-only from `FlagStabilityManager` (existence check in ChunkLoadEvent) |
| `WorkFrontRepository` | Existing storage | Read-only from `FlagStabilityManager` (existence check in ChunkLoadEvent) |
| `PluginConfig` | Existing config | Extended with six new keys (Section 4) |
| `BukkitScheduler` | PaperSpigot API | For async DB tasks and main-thread callbacks in activation flow |
| `Chunk.persistentDataContainer` | PaperSpigot API | All new PDC state storage |
| `World.spawn(Location, ArmorStand::class.java)` | PaperSpigot API | ArmorStand creation |
| `ChunkLoadEvent` | Bukkit event | New listener triggers crash-recovery and lazy repair for registered flags |
| `ChunkUnloadEvent` | Bukkit event | Cache eviction for in-memory flag position cache (Section 8.1a) |
| `BlockFromToEvent` | Bukkit event | Liquid flow protection |
| `BlockPistonExtendEvent` / `BlockPistonRetractEvent` | Bukkit event | Piston protection |
| `EntityChangeBlockEvent` | Bukkit event | Falling-block protection |
| `World.getChunkAtAsync(cx, cz)` | Paper API | Non-blocking chunk load for unloaded-chunk cleanup (Section 6.2) |

No external libraries or DB schema changes are required.

---

## 15. Resolved Architectural Decisions and Startup Repair Scan

### 15.1 Startup repair scan (CC-07 resolution, CC-02 fallback)

`ComminusmPlugin.onEnable` runs a startup repair scan after all listeners are registered. The scan is async-offloaded via `BukkitScheduler.runTaskAsynchronously`.

**Scan procedure (Q4 — thread-safety of `world.isChunkLoaded`):**

`world.isChunkLoaded(cx, cz)` is a Bukkit World API call that is **not async-safe**. It must not be called from the async scan thread. The scan therefore follows a two-phase design:

**Phase 1 — async DB query (off main thread):**

1. Query DB for all active order flags and front flags (batch query returning id + banner position + world name).
2. Return the full record list to the main-thread callback (do not call any Bukkit World API here).

**Phase 2 — main-thread tick-spread chunk check and repair:**

Phase 2 processes the DB record list in batches to avoid stalling the server tick on startup. The batch size N is configurable (config key `flag.startupScanBatchSize`, default `10`, must be ≥ 1; invalid values log WARN and apply default). Each batch is processed in one `BukkitScheduler.runTask` call; at the end of each batch, the next batch is scheduled via another `BukkitScheduler.runTask` call (recursive scheduling, one tick apart). This spreads the repair work across N flags per tick.

If the total number of DB-active flags exceeds 100, log at WARN level before Phase 2 begins:
`"Startup repair scan: {total} active flags found. Processing in batches of {N} per tick. Server startup may be slightly delayed."`
This informs the admin of the expected spread duration.

3. For each DB record in the current batch (on the main thread): call `world.isChunkLoaded(cx, cz)` (safe — main thread).
   - If not loaded: skip (ChunkLoadEvent will handle it on load when the chunk is next accessed).
   - If loaded: call `chunk.persistentDataContainer` to check whether `comminusm:flag/{id}` PDC key exists.
4. If PDC key is absent for a loaded chunk:
   a. Log WARN "Startup repair: flag {id} missing PDC registration in loaded chunk. Re-registering."
   b. Write `comminusm:flag/{id}` and `comminusm:support_material/{id}` to the chunk PDC (main thread — safe).
   c. Double-spawn guard: before spawning an ArmorStand, check whether `comminusm:armorstand/{id}` PDC key
      already exists in the chunk.
      - If the key EXISTS: an ArmorStand UUID is already recorded. Attempt to resolve the entity by UUID.
        If found → skip creation (the ArmorStand is already present; no duplicate needed). Log INFO.
        If NOT found → the recorded UUID is stale; spawn a new ArmorStand and overwrite `comminusm:armorstand/{id}`
        with the new UUID. Log WARN "Startup repair: stale ArmorStand UUID for flag {id}, replaced."
      - If the key is ABSENT: spawn ArmorStand and write `comminusm:armorstand/{id}`.
      This guard prevents duplicate ArmorStands when a `ChunkLoadEvent` fires for the same chunk concurrently
      with the startup repair scan's Phase 2 callback (startup scan + ChunkLoadEvent race on `ChunkLoadEvent`
      that fires after the server finishes loading).
   d. Add to in-memory flag position cache (Section 8.1a).
5. Log INFO "Startup repair scan complete. Repaired: N flags."

This scan closes CC-07 (PDC write failure during front move) and provides a safety net for CC-02 failures where `getChunkAtAsync` could not complete world cleanup. The scan does NOT modify DB records.

### 15.2 CC-02 resolution — `world.getChunkAtAsync` (closed)

Resolved in Section 6.2. Chosen approach: async chunk load via `world.getChunkAtAsync`. No open items.

### 15.3 CC-07 resolution — startup repair scan (closed)

Resolved by Section 15.1. Retry once on failure (Step 14 of Front Move), then log CRITICAL. On next server restart, the startup repair scan re-registers the PDC key and restores protection. No open items.

---

*All unresolved items from CornerCaseReviewer runs 1 and 2 are now closed. PO decisions applied 2026-05-05 (lock ordering extended, Step 13R recovery corrected, startup scan double-spawn guard added, tick-spread Phase 2 added, Key 5 suffix corrected to flagId, deletion lock scope confirmed). No architectural decisions remain pending.*

---
genre: how-to
module: comminusm
title: "Stage 07 — Tests: Critical & High Corner Cases"
topic: flag-stability
stage: 7
status: Pending
date: 2026-05-05
---

# Stage 07 — Tests

**Goal:** Write unit and integration tests covering all Critical (CC-01..CC-03) and High (CC-04..CC-09) corner cases, plus regression tests for every AC that represents a previously unfixed dup/destruction vector.

**Spec refs:** CC-01..CC-09, AC-01..AC-15, AC-17, AC-21, AC-23, AC-24, AC-26, AC-29, AC-31, AC-37, AC-38, AC-39  
**Depends on:** Stages 01–06 (all implementation complete)

---

## Test Framework Context

From codebase search: no `runTaskAsynchronously` or test framework configuration found. Use MockBukkit or a test server fixture if available; otherwise use direct unit tests with mocked Bukkit APIs.

All tests must use parameterized queries (no string concatenation with user input in test DB calls).

---

## Tasks

### 7.1 — CC-01: `getOfflinePlayer` returns null

```kotlin
@Test
fun `activation uses UUID fallback when player name is null`() {
    // Given: Bukkit.getOfflinePlayer(uuid).name returns null
    val uuid = UUID.randomUUID()
    mockkStatic(Bukkit::class)
    every { Bukkit.getOfflinePlayer(uuid).name } returns null
    
    // When: FlagActivationHelper resolves owner name
    val name = helper.resolveOwnerName(uuid)
    
    // Then: result is uuid.toString(), not null, no exception thrown
    assertEquals(uuid.toString(), name)
}

@Test
fun `lazy refresh skips update when name resolution fails`() {
    // When: refreshArmorStandName encounters null name
    // Then: no exception, WARN logged, ArmorStand name unchanged
}
```

### 7.2 — CC-02: Deletion when chunk not loaded

```kotlin
@Test
fun `deleteByOwner defers cleanup via getChunkAtAsync when chunk not loaded`() {
    // Given: world.isChunkLoaded() returns false
    // When: deleteByOwner called
    // Then: world.getChunkAtAsync() called (not getChunkAt); cleanup runs on async callback
}
```

### 7.3 — CC-03: Concurrent deactivation

```kotlin
@Test
fun `concurrent deactivate calls are idempotent`() {
    // Given: active front with support block and ArmorStand
    // When: deactivate called twice simultaneously (two threads)
    // Then: world ends up clean (no exception, no duplicate operations)
}
```

### 7.4 — CC-04: Invalid config material

```kotlin
@Test
fun `invalid supportBlockMaterial falls back to BEDROCK with warning`() {
    val config = PluginConfig(configWith("flag.supportBlockMaterial" to "WOOD"))
    assertEquals(Material.BEDROCK, config.flagSupportBlockMaterial)
    // Verify logger.severe was called
}

@Test
fun `maxPerChunk zero falls back to 50 with warning`() {
    val config = PluginConfig(configWith("flag.maxPerChunk" to 0))
    assertEquals(50, config.flagMaxPerChunk)
}
```

### 7.5 — CC-05: Player disconnects mid-activation

```kotlin
@Test
fun `activation async callback does not throw when player offline`() {
    // Given: player goes offline between BlockPlaceEvent and DB async callback
    // When: async callback fires and attempts to send message
    val player: Player = mockk()
    every { Bukkit.getPlayer(ownerUuid) } returns null  // player offline
    // Then: no NullPointerException, rollback executed on main thread
    assertDoesNotThrow { activationCallback.invoke() }
}
```

### 7.6 — CC-06: Same-location conflict (order + front same block)

```kotlin
@Test
fun `activation denied when position already registered in cache`() {
    // Given: an order flag exists at position (10, 64, 20)
    manager.addToCache("world", 0, 1, 10, 64, 20)
    
    // When: front flag tries to activate at same position (inside lock)
    val result = helper.checkPositionNotOccupied(Position(10, 64, 20), manager)
    
    // Then: denied
    assertFalse(result)
}
```

### 7.7 — CC-07: PDC write failure on front move step 7

```kotlin
@Test
fun `PDC write failure after world changes logs CRITICAL`() {
    // Given: world changes committed (step 12-13), PDC write throws
    // When: Step 15 PDC write fails, retry also fails
    // Then: CRITICAL log entry exists; no world rollback (startup scan is recovery path)
    verify { logger.severe(any()) }
}
```

### 7.8 — CC-08: Concurrent delete + move race

```kotlin
@Test
fun `order deletion and front move to same chunk are serialized by chunk lock`() {
    // Given: order flag at (10,64,20) being deleted; front move targeting (10,64,20) simultaneously
    // When: both run concurrently
    // Then: final state is deterministic — exactly one flag or neither, no intermediate state visible
}
```

### 7.9 — AC-12 regression: no flag item drop

```kotlin
@Test
fun `support block break event never drops banner item`() {
    val event = BlockBreakEvent(supportBlock, player)
    listener.onBlockBreak(event)
    assertTrue(event.isCancelled)
    assertFalse(event.isDropItems)
}

@Test  
fun `explosion block list does not contain flag or support blocks`() {
    val event = EntityExplodeEvent(creeper, location, mutableListOf(supportBlock, bannerBlock, otherBlock), 0f)
    listener.onEntityExplode(event)
    assertFalse(supportBlock in event.blockList())
    assertFalse(bannerBlock in event.blockList())
    assertTrue(otherBlock in event.blockList())
}
```

### 7.10 — AC-29 rollback regression: never AIR

```kotlin
@Test
fun `rollback on AS spawn failure restores original DIRT not AIR`() {
    // Given: player placed banner on DIRT support
    // When: ArmorStand spawn throws exception
    // Then: support block is DIRT (not AIR, not BEDROCK)
    assertEquals(Material.DIRT, supportBlock.type)
}
```

### 7.11 — AC-38 front move order: DB before world

```kotlin
@Test
fun `front move writes DB before touching world`() {
    val callOrder = mutableListOf<String>()
    every { repository.updateCoordinates(any(), any()) } answers { callOrder += "DB" }
    every { world.getBlockAt(any(), any(), any()).type = Material.AIR } answers { callOrder += "WORLD" }
    
    service.move(ownerUuid, newLocation)
    
    assertEquals("DB", callOrder.first())
}
```

---

## Relevant TCs from test-cases.md

All 15 CC tests: TC-69..TC-84  
AC-12 no-drop: TC-26, TC-27  
AC-29 rollback: TC-50  
AC-31 DB failure rollback: TC-52  
AC-37 dirty_armorstand: TC-59, TC-60, TC-61  
AC-38 move order: TC-62, TC-63  
AC-39 pending flag: TC-64, TC-65

---

## Completion Criteria

- [ ] All Critical CC (CC-01..CC-03) have passing tests
- [ ] All High CC (CC-04..CC-09) have passing tests
- [ ] AC-12 (no drop) regression test passes
- [ ] AC-29 (rollback = original material) regression test passes
- [ ] `./gradlew :[module]:test` passes
- [ ] `./gradlew detekt ktlintCheck` passes

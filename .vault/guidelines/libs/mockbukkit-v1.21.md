---
genre: guideline
title: mockbukkit-v1.21
topic: mockbukkit-v1.21
library: mockbukkit
confidence: high
source: agent
updated: 2026-05
---

---
genre: guidelines
module: libs
title: MockBukkit v1.21 — Paper plugin testing
topic: mockbukkit-v1.21
---

# MockBukkit v1.21

Framework for unit-testing Paper/Bukkit plugins without a live server.

## Dependency (build.gradle.kts)

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")  // already present
}

dependencies {
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.110.0")
}
```

Paper API version: `1.21.11-R0.1-SNAPSHOT`  
MockBukkit version: `4.110.0` (latest stable for Paper 1.21 branch, tag v4.110.0)

## JUnit5 Setup

```kotlin
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.world.WorldMock

class MyTest {
    private lateinit var server: ServerMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }
}
```

## WorldMock — worlds and entities

```kotlin
// Create a mock world (name must match what code looks up via Bukkit.getWorld)
val world: WorldMock = server.addSimpleWorld("world")

// Add an ArmorStand entity
val armorStand = world.spawn(Location(world, 0.0, 64.0, 0.0), ArmorStand::class.java)
// armorStand.uniqueId is a real UUID you can store in PDC

// Get entity by UUID (returns null if not added)
val entity: Entity? = world.getEntity(armorStand.uniqueId)

// Get chunk
val chunk: Chunk = world.getChunkAt(0, 0)

// PersistentDataContainer on chunk
val pdc = chunk.persistentDataContainer
val key = NamespacedKey(plugin, "armorstand/1")
pdc.set(key, PersistentDataType.STRING, armorStand.uniqueId.toString())
```

## PlayerMock

```kotlin
val player: PlayerMock = server.addPlayer("TestPlayer")
player.uniqueId  // stable UUID
```

## Loading plugin (optional — needed for NamespacedKey)

```kotlin
// If you need a Plugin instance for NamespacedKey:
val plugin = MockBukkit.load(MyPlugin::class.java)
// Or use a JavaPlugin subclass that has no complex onEnable:
val plugin = MockBukkit.createMockPlugin()
```

## Notes

- `MockBukkit.mock()` replaces the `Bukkit` static singleton — all `Bukkit.getWorld(...)` / `Bukkit.getServer()` calls work after setup
- `MockBukkit.unmock()` in `@AfterEach` is mandatory — leaking mock state breaks subsequent tests
- `WorldMock.getEntity(uuid)` returns `null` if the entity was never added to that world — useful for testing null-entity paths
- PDC on `Chunk` is fully supported in MockBukkit `WorldMock`
- MockBukkit does **not** support inventories events (`PrepareAnvilEvent`, `InventoryClickEvent`) out of the box — those still need MockK or reflection for unit-level tests

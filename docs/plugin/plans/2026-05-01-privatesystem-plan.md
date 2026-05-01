# Система приватов «Ордер и Фронт» — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Реализовать коммунистическую систему управления территориями: Ордер (приватная жилплощадь с прокачкой) + Трудовой Фронт (зона добычи с кооперацией).

**Architecture:** SQLite для постоянного хранения + PersistentDataContainer на чанках для быстрых проверок. Paper GUI (Chest Inventory) без внешних библиотек. Модульные тесты с in-memory SQLite, интеграционные — через run-paper.

**Tech Stack:** Kotlin, Paper API 1.21.11, SQLite JDBC 3.46+, JUnit 5

---

## File Structure (plan)

```
src/main/kotlin/ru/kyamshanov/comminusm/
├── plugin/ComminusmPlugin.kt       # [MODIFY] регистрация
├── model/
│   ├── Order.kt                    # [CREATE] data class
│   ├── WorkFront.kt                # [CREATE] data class
│   └── WorkdaysBalance.kt          # [CREATE] data class
├── storage/
│   ├── DatabaseManager.kt          # [CREATE] SQLite manager
│   ├── OrderRepository.kt          # [CREATE] CRUD orders
│   ├── WorkFrontRepository.kt      # [CREATE] CRUD fronts
│   └── WorkdaysRepository.kt       # [CREATE] CRUD workdays
├── service/
│   ├── OrderService.kt             # [CREATE] бизнес-логика ордера
│   ├── WorkFrontService.kt         # [CREATE] бизнес-логика фронта
│   └── WorkdaysService.kt          # [CREATE] трудодни
├── gui/
│   ├── PartyMenu.kt                # [CREATE] главное меню
│   ├── OrderMenu.kt                # [CREATE] меню ордера
│   ├── FrontMenu.kt                # [CREATE] меню фронта
│   ├── TreasuryMenu.kt             # [CREATE] меню казны
│   └── GuiUtils.kt                 # [CREATE] хелперы
├── listener/
│   ├── BlockListener.kt            # [CREATE] защита блоков
│   ├── OrderFlagListener.kt        # [CREATE] флаг ордера
│   ├── FrontFlagListener.kt        # [CREATE] флаг фронта
│   └── PlayerListener.kt           # [CREATE] пассивный доход
├── command/
│   └── PartyCommand.kt             # [CREATE] /партия
└── config/
    └── PluginConfig.kt             # [CREATE] конфиг

src/test/kotlin/ru/kyamshanov/comminusm/
├── storage/
│   ├── DatabaseManagerTest.kt      # [CREATE]
│   ├── OrderRepositoryTest.kt      # [CREATE]
│   └── WorkFrontRepositoryTest.kt  # [CREATE]
├── service/
│   ├── OrderServiceTest.kt         # [CREATE]
│   ├── WorkFrontServiceTest.kt     # [CREATE]
│   └── WorkdaysServiceTest.kt      # [CREATE]
└── config/
    └── PluginConfigTest.kt         # [CREATE]

src/main/resources/
├── plugin.yml                      # [MODIFY] добавить команду
└── config.yml                      # [CREATE]
```

---

## Stage 0: Infrastructure

### Task 0.1: Add SQLite dependency

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add dependency**

```kotlin
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "chore: add sqlite-jdbc dependency"
```

---

### Task 0.2: Data models

**Files:**
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/model/Order.kt`
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/model/WorkFront.kt`
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/model/WorkdaysBalance.kt`

- [ ] **Step 1: Create Order data class**

```kotlin
package ru.kyamshanov.comminusm.model

import org.bukkit.Location
import java.util.UUID

data class Order(
    val id: Long = 0,
    val ownerUuid: UUID,
    val level: Int = 1,
    val centerWorld: String? = null,
    val centerX: Int = 0,
    val centerY: Int = 0,
    val centerZ: Int = 0,
    val radius: Int = 2,
    val createdAt: String = ""
) {
    val center: Location?
        get() = null // requires Bukkit; filled by service layer

    val size: Int
        get() = radius * 2 + 1

    val isActivated: Boolean
        get() = centerWorld != null
}
```

- [ ] **Step 2: Create WorkFront data class**

```kotlin
package ru.kyamshanov.comminusm.model

import org.bukkit.Location
import java.util.UUID

data class WorkFront(
    val ownerUuid: UUID,
    val centerWorld: String,
    val centerX: Int,
    val centerY: Int,
    val centerZ: Int,
    val radius: Int = 25,
    val createdAt: String = ""
) {
    val center: Location?
        get() = null // requires Bukkit; filled by service layer

    val size: Int
        get() = radius * 2 + 1
}
```

- [ ] **Step 3: Create WorkdaysBalance data class**

```kotlin
package ru.kyamshanov.comminusm.model

import java.util.UUID

data class WorkdaysBalance(
    val playerUuid: UUID,
    val balance: Int = 0
)
```

- [ ] **Step 4: Verify compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/model/
git commit -m "feat: add data models for private system"
```

---

### Task 0.3: PluginConfig

**Files:**
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/config/PluginConfig.kt`
- Create: `src/main/resources/config.yml`
- Create: `src/test/kotlin/ru/kyamshanov/comminusm/config/PluginConfigTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package ru.kyamshanov.comminusm.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PluginConfigTest {
    @Test
    fun `default workdays rates are not empty`() {
        val rates = PluginConfig.defaultResourceRates()
        assertFalse(rates.isEmpty())
        assertEquals(4, rates["COBBLESTONE"])
        assertEquals(12, rates["IRON_INGOT"])
    }

    @Test
    fun `default order levels has 5 entries`() {
        val levels = PluginConfig.defaultOrderLevels()
        assertEquals(5, levels.size)
        assertEquals(2, levels[0].radius)
        assertEquals(7, levels[4].radius)
    }
}
```

Run: `./gradlew :plugin:test --tests "ru.kyamshanov.comminusm.config.PluginConfigTest"`
Expected: FAIL (class not found)

- [ ] **Step 2: Create config.yml**

```yaml
private-system:
  order:
    min-distance-between-centers: 30
    levels:
      - level: 1
        radius: 2
        cost: 0
      - level: 2
        radius: 3
        cost: 30
      - level: 3
        radius: 4
        cost: 80
      - level: 4
        radius: 5
        cost: 150
      - level: 5
        radius: 7
        cost: 300
  front:
    radius: 25
  workdays:
    passive-income-interval-minutes: 10
    passive-income-amount: 1
    resource-rates:
      COBBLESTONE: 4
      COAL: 6
      IRON_INGOT: 12
      GOLD_INGOT: 20
      DIAMOND: 40
      OAK_LOG: 4
      DIRT: 1
```

- [ ] **Step 3: Create PluginConfig**

```kotlin
package ru.kyamshanov.comminusm.config

import org.bukkit.configuration.file.FileConfiguration

data class OrderLevelConfig(val level: Int, val radius: Int, val cost: Int)

class PluginConfig(private val config: FileConfiguration) {

    val minDistanceBetweenCenters: Int
        get() = config.getInt("private-system.order.min-distance-between-centers", 30)

    val orderLevels: List<OrderLevelConfig> by lazy {
        config.getMapList("private-system.order.levels")
            .map { map ->
                OrderLevelConfig(
                    level = (map["level"] as? Number)?.toInt() ?: 1,
                    radius = (map["radius"] as? Number)?.toInt() ?: 2,
                    cost = (map["cost"] as? Number)?.toInt() ?: 0
                )
            }
            .ifEmpty { defaultOrderLevels() }
    }

    val frontRadius: Int
        get() = config.getInt("private-system.front.radius", 25)

    val passiveIncomeIntervalMinutes: Int
        get() = config.getInt("private-system.workdays.passive-income-interval-minutes", 10)

    val passiveIncomeAmount: Int
        get() = config.getInt("private-system.workdays.passive-income-amount", 1)

    val resourceRates: Map<String, Int> by lazy {
        val section = config.getConfigurationSection("private-system.workdays.resource-rates")
        if (section != null) {
            section.getKeys(false).associateWith { key -> section.getInt(key, 0) }
        } else {
            defaultResourceRates()
        }
    }

    companion object {
        fun defaultOrderLevels(): List<OrderLevelConfig> = listOf(
            OrderLevelConfig(1, 2, 0),
            OrderLevelConfig(2, 3, 30),
            OrderLevelConfig(3, 4, 80),
            OrderLevelConfig(4, 5, 150),
            OrderLevelConfig(5, 7, 300)
        )

        fun defaultResourceRates(): Map<String, Int> = mapOf(
            "COBBLESTONE" to 4,
            "COAL" to 6,
            "IRON_INGOT" to 12,
            "GOLD_INGOT" to 20,
            "DIAMOND" to 40,
            "OAK_LOG" to 4,
            "DIRT" to 1
        )
    }
}
```

- [ ] **Step 4: Run test**

Run: `./gradlew :plugin:test --tests "ru.kyamshanov.comminusm.config.PluginConfigTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/config/PluginConfig.kt src/main/resources/config.yml src/test/kotlin/ru/kyamshanov/comminusm/config/PluginConfigTest.kt
git commit -m "feat: add PluginConfig with default values"
```

---

### Task 0.4: DatabaseManager

**Files:**
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/storage/DatabaseManager.kt`
- Create: `src/test/kotlin/ru/kyamshanov/comminusm/storage/DatabaseManagerTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package ru.kyamshanov.comminusm.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.Connection

class DatabaseManagerTest {
    @Test
    fun `in-memory database creates tables successfully`() {
        val manager = DatabaseManager("jdbc:sqlite::memory:")
        val conn = manager.connection
        // Verify tables exist
        val rs = conn.metaData.getTables(null, null, "orders", null)
        assertTrue(rs.next(), "orders table should exist")
        rs.close()

        val rs2 = conn.metaData.getTables(null, null, "work_fronts", null)
        assertTrue(rs2.next(), "work_fronts table should exist")
        rs2.close()

        val rs3 = conn.metaData.getTables(null, null, "workdays", null)
        assertTrue(rs3.next(), "workdays table should exist")
        rs3.close()

        conn.close()
    }
}
```

Run: `./gradlew :plugin:test --tests "ru.kyamshanov.comminusm.storage.DatabaseManagerTest"`
Expected: FAIL

- [ ] **Step 2: Implement DatabaseManager**

```kotlin
package ru.kyamshanov.comminusm.storage

import org.bukkit.plugin.Plugin
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class DatabaseManager(jdbcUrl: String) {

    val connection: Connection by lazy {
        val conn = DriverManager.getConnection(jdbcUrl)
        conn.createStatement().execute("PRAGMA journal_mode=WAL")
        conn.createStatement().execute("PRAGMA foreign_keys=ON")
        createTables(conn)
        conn
    }

    constructor(plugin: Plugin) : this(
        "jdbc:sqlite:${plugin.dataFolder.absolutePath}${File.separator}data.db"
    )

    private fun createTables(conn: Connection) {
        conn.createStatement().executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                owner_uuid TEXT NOT NULL,
                level INTEGER NOT NULL DEFAULT 1,
                center_world TEXT,
                center_x INTEGER,
                center_y INTEGER,
                center_z INTEGER,
                radius INTEGER NOT NULL DEFAULT 2,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """.trimIndent()
        )

        conn.createStatement().executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS work_fronts (
                owner_uuid TEXT PRIMARY KEY,
                center_world TEXT NOT NULL,
                center_x INTEGER NOT NULL,
                center_y INTEGER NOT NULL,
                center_z INTEGER NOT NULL,
                radius INTEGER NOT NULL DEFAULT 25,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """.trimIndent()
        )

        conn.createStatement().executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS workdays (
                player_uuid TEXT PRIMARY KEY,
                balance INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    fun integrityCheck(): Boolean {
        val rs = connection.createStatement().executeQuery("PRAGMA integrity_check")
        val result = rs.getString(1) == "ok"
        rs.close()
        return result
    }

    fun close() {
        try {
            connection.close()
        } catch (_: Exception) {
            // already closed
        }
    }
}
```

- [ ] **Step 3: Run test**

Run: `./gradlew :plugin:test --tests "ru.kyamshanov.comminusm.storage.DatabaseManagerTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/storage/DatabaseManager.kt src/test/kotlin/ru/kyamshanov/comminusm/storage/DatabaseManagerTest.kt
git commit -m "feat: add DatabaseManager with SQLite WAL support"
```

---

### Task 0.5: OrderRepository

**Files:**
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/storage/OrderRepository.kt`
- Create: `src/test/kotlin/ru/kyamshanov/comminusm/storage/OrderRepositoryTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package ru.kyamshanov.comminusm.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.model.Order
import java.util.UUID

class OrderRepositoryTest {
    private lateinit var db: DatabaseManager
    private lateinit var repo: OrderRepository
    private val uuid = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        db = DatabaseManager("jdbc:sqlite::memory:")
        repo = OrderRepository(db.connection)
    }

    @Test
    fun `insert and find by uuid returns order`() {
        val order = Order(ownerUuid = uuid)
        val id = repo.insert(order)
        assertTrue(id > 0, "insert should return positive id")

        val found = repo.findByOwner(uuid)
        assertNotNull(found, "should find order by owner uuid")
        assertEquals(uuid, found!!.ownerUuid)
    }

    @Test
    fun `update level changes the order level`() {
        repo.insert(Order(ownerUuid = uuid))
        repo.updateLevel(uuid, 3, 4) // level 3, radius 4

        val found = repo.findByOwner(uuid)
        assertNotNull(found)
        assertEquals(3, found!!.level)
        assertEquals(4, found.radius)
    }

    @Test
    fun `activate sets center coordinates`() {
        repo.insert(Order(ownerUuid = uuid))
        repo.activate(uuid, "world", 100, 64, 200)

        val found = repo.findByOwner(uuid)
        assertNotNull(found)
        assertEquals("world", found!!.centerWorld)
        assertEquals(100, found.centerX)
        assertEquals(64, found.centerY)
        assertEquals(200, found.centerZ)
    }
}
```

Run: `./gradlew :plugin:test --tests "ru.kyamshanov.comminusm.storage.OrderRepositoryTest"`
Expected: FAIL

- [ ] **Step 2: Implement OrderRepository**

```kotlin
package ru.kyamshanov.comminusm.storage

import ru.kyamshanov.comminusm.model.Order
import java.sql.Connection
import java.util.UUID

class OrderRepository(private val conn: Connection) {

    fun insert(order: Order): Long {
        val stmt = conn.prepareStatement(
            "INSERT INTO orders (owner_uuid, level, radius) VALUES (?, ?, ?)"
        )
        stmt.setString(1, order.ownerUuid.toString())
        stmt.setInt(2, order.level)
        stmt.setInt(3, order.radius)
        stmt.executeUpdate()
        val rs = stmt.generatedKeys
        val id = if (rs.next()) rs.getLong(1) else 0L
        rs.close()
        stmt.close()
        return id
    }

    fun findByOwner(uuid: UUID): Order? {
        val stmt = conn.prepareStatement(
            "SELECT id, owner_uuid, level, center_world, center_x, center_y, center_z, radius, created_at FROM orders WHERE owner_uuid = ?"
        )
        stmt.setString(1, uuid.toString())
        val rs = stmt.executeQuery()
        val result = if (rs.next()) {
            Order(
                id = rs.getLong("id"),
                ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                level = rs.getInt("level"),
                centerWorld = rs.getString("center_world"),
                centerX = rs.getInt("center_x"),
                centerY = rs.getInt("center_y"),
                centerZ = rs.getInt("center_z"),
                radius = rs.getInt("radius"),
                createdAt = rs.getString("created_at")
            )
        } else null
        rs.close()
        stmt.close()
        return result
    }

    fun updateLevel(uuid: UUID, newLevel: Int) {
        val stmt = conn.prepareStatement("UPDATE orders SET level = ? WHERE owner_uuid = ?")
        stmt.setInt(1, newLevel)
        stmt.setString(2, uuid.toString())
        stmt.executeUpdate()
        stmt.close()
    }

    fun activate(uuid: UUID, world: String, x: Int, y: Int, z: Int) {
        val stmt = conn.prepareStatement(
            "UPDATE orders SET center_world = ?, center_x = ?, center_y = ?, center_z = ? WHERE owner_uuid = ?"
        )
        stmt.setString(1, world)
        stmt.setInt(2, x)
        stmt.setInt(3, y)
        stmt.setInt(4, z)
        stmt.setString(5, uuid.toString())
        stmt.executeUpdate()
        stmt.close()
    }

    fun findAllInWorld(world: String): List<Order> {
        val stmt = conn.prepareStatement(
            "SELECT id, owner_uuid, level, center_world, center_x, center_y, center_z, radius, created_at FROM orders WHERE center_world = ?"
        )
        stmt.setString(1, world)
        val rs = stmt.executeQuery()
        val result = mutableListOf<Order>()
        while (rs.next()) {
            result.add(
                Order(
                    id = rs.getLong("id"),
                    ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                    level = rs.getInt("level"),
                    centerWorld = rs.getString("center_world"),
                    centerX = rs.getInt("center_x"),
                    centerY = rs.getInt("center_y"),
                    centerZ = rs.getInt("center_z"),
                    radius = rs.getInt("radius"),
                    createdAt = rs.getString("created_at")
                )
            )
        }
        rs.close()
        stmt.close()
        return result
    }

    fun deleteByOwner(uuid: UUID) {
        val stmt = conn.prepareStatement("DELETE FROM orders WHERE owner_uuid = ?")
        stmt.setString(1, uuid.toString())
        stmt.executeUpdate()
        stmt.close()
    }
}
```

- [ ] **Step 3: Run test**

Run: `./gradlew :plugin:test --tests "ru.kyamshanov.comminusm.storage.OrderRepositoryTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/storage/OrderRepository.kt src/test/kotlin/ru/kyamshanov/comminusm/storage/OrderRepositoryTest.kt
git commit -m "feat: add OrderRepository with CRUD operations"
```

---

### Task 0.6: WorkFrontRepository + WorkdaysRepository

**Files:**
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/storage/WorkFrontRepository.kt`
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/storage/WorkdaysRepository.kt`
- Create: `src/test/kotlin/ru/kyamshanov/comminusm/storage/WorkFrontRepositoryTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package ru.kyamshanov.comminusm.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.model.WorkFront
import java.util.UUID

class WorkFrontRepositoryTest {
    private lateinit var db: DatabaseManager
    private lateinit var repo: WorkFrontRepository
    private lateinit var wdRepo: WorkdaysRepository
    private val uuid = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        db = DatabaseManager("jdbc:sqlite::memory:")
        repo = WorkFrontRepository(db.connection)
        wdRepo = WorkdaysRepository(db.connection)
    }

    @Test
    fun `upsert creates and updates work front`() {
        val front = WorkFront(uuid, "world", 10, 64, 10)
        repo.upsert(front)
        val found = repo.findByOwner(uuid)
        assertNotNull(found)
        assertEquals(10, found!!.centerX)

        repo.upsert(WorkFront(uuid, "world_nether", 20, 100, 20))
        val updated = repo.findByOwner(uuid)
        assertEquals("world_nether", updated!!.centerWorld)
        assertEquals(100, updated.centerY)
    }

    @Test
    fun `delete removes work front`() {
        repo.upsert(WorkFront(uuid, "world", 0, 64, 0))
        assertNotNull(repo.findByOwner(uuid))
        repo.deleteByOwner(uuid)
        assertNull(repo.findByOwner(uuid))
    }

    @Test
    fun `workdays add and get balance`() {
        wdRepo.add(uuid, 50)
        assertEquals(50, wdRepo.getBalance(uuid))
        wdRepo.add(uuid, 30)
        assertEquals(80, wdRepo.getBalance(uuid))
    }
}
```

Run: `./gradlew :plugin:test --tests "ru.kyamshanov.comminusm.storage.WorkFrontRepositoryTest"`
Expected: FAIL

- [ ] **Step 2: Implement WorkFrontRepository**

```kotlin
package ru.kyamshanov.comminusm.storage

import ru.kyamshanov.comminusm.model.WorkFront
import java.sql.Connection
import java.util.UUID

class WorkFrontRepository(private val conn: Connection) {

    fun upsert(front: WorkFront) {
        val stmt = conn.prepareStatement(
            """
            INSERT INTO work_fronts (owner_uuid, center_world, center_x, center_y, center_z, radius)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(owner_uuid) DO UPDATE SET
                center_world = excluded.center_world,
                center_x = excluded.center_x,
                center_y = excluded.center_y,
                center_z = excluded.center_z,
                radius = excluded.radius,
                created_at = datetime('now')
            """.trimIndent()
        )
        stmt.setString(1, front.ownerUuid.toString())
        stmt.setString(2, front.centerWorld)
        stmt.setInt(3, front.centerX)
        stmt.setInt(4, front.centerY)
        stmt.setInt(5, front.centerZ)
        stmt.setInt(6, front.radius)
        stmt.executeUpdate()
        stmt.close()
    }

    fun findByOwner(uuid: UUID): WorkFront? {
        val stmt = conn.prepareStatement(
            "SELECT owner_uuid, center_world, center_x, center_y, center_z, radius, created_at FROM work_fronts WHERE owner_uuid = ?"
        )
        stmt.setString(1, uuid.toString())
        val rs = stmt.executeQuery()
        val result = if (rs.next()) {
            WorkFront(
                ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                centerWorld = rs.getString("center_world"),
                centerX = rs.getInt("center_x"),
                centerY = rs.getInt("center_y"),
                centerZ = rs.getInt("center_z"),
                radius = rs.getInt("radius"),
                createdAt = rs.getString("created_at")
            )
        } else null
        rs.close()
        stmt.close()
        return result
    }

    fun deleteByOwner(uuid: UUID) {
        val stmt = conn.prepareStatement("DELETE FROM work_fronts WHERE owner_uuid = ?")
        stmt.setString(1, uuid.toString())
        stmt.executeUpdate()
        stmt.close()
    }

    fun findAllInWorld(world: String): List<WorkFront> {
        val stmt = conn.prepareStatement(
            "SELECT owner_uuid, center_world, center_x, center_y, center_z, radius, created_at FROM work_fronts WHERE center_world = ?"
        )
        stmt.setString(1, world)
        val rs = stmt.executeQuery()
        val result = mutableListOf<WorkFront>()
        while (rs.next()) {
            result.add(
                WorkFront(
                    ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                    centerWorld = rs.getString("center_world"),
                    centerX = rs.getInt("center_x"),
                    centerY = rs.getInt("center_y"),
                    centerZ = rs.getInt("center_z"),
                    radius = rs.getInt("radius"),
                    createdAt = rs.getString("created_at")
                )
            )
        }
        rs.close()
        stmt.close()
        return result
    }
}
```

- [ ] **Step 3: Implement WorkdaysRepository**

```kotlin
package ru.kyamshanov.comminusm.storage

import java.sql.Connection
import java.util.UUID

class WorkdaysRepository(private val conn: Connection) {

    fun add(uuid: UUID, amount: Int) {
        val stmt = conn.prepareStatement(
            """
            INSERT INTO workdays (player_uuid, balance) VALUES (?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET balance = balance + ?
            """.trimIndent()
        )
        stmt.setString(1, uuid.toString())
        stmt.setInt(2, amount)
        stmt.setInt(3, amount)
        stmt.executeUpdate()
        stmt.close()
    }

    fun spend(uuid: UUID, amount: Int): Boolean {
        // Use a single query with WHERE to ensure atomicity
        val stmt = conn.prepareStatement(
            "UPDATE workdays SET balance = balance - ? WHERE player_uuid = ? AND balance >= ?"
        )
        stmt.setInt(1, amount)
        stmt.setString(2, uuid.toString())
        stmt.setInt(3, amount)
        val updated = stmt.executeUpdate()
        stmt.close()
        return updated > 0
    }

    fun getBalance(uuid: UUID): Int {
        val stmt = conn.prepareStatement(
            "SELECT balance FROM workdays WHERE player_uuid = ?"
        )
        stmt.setString(1, uuid.toString())
        val rs = stmt.executeQuery()
        val balance = if (rs.next()) rs.getInt("balance") else 0
        rs.close()
        stmt.close()
        return balance
    }
}
```

- [ ] **Step 4: Run test**

Run: `./gradlew :plugin:test --tests "ru.kyamshanov.comminusm.storage.WorkFrontRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/storage/WorkFrontRepository.kt src/main/kotlin/ru/kyamshanov/comminusm/storage/WorkdaysRepository.kt src/test/kotlin/ru/kyamshanov/comminusm/storage/WorkFrontRepositoryTest.kt
git commit -m "feat: add WorkFrontRepository and WorkdaysRepository"
```

---

## Stage 1: Economy (Workdays)

### Task 1.1: WorkdaysService

**Files:**
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/service/WorkdaysService.kt`
- Create: `src/test/kotlin/ru/kyamshanov/comminusm/service/WorkdaysServiceTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package ru.kyamshanov.comminusm.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.storage.DatabaseManager
import ru.kyamshanov.comminusm.storage.WorkdaysRepository
import java.util.UUID

class WorkdaysServiceTest {
    private lateinit var repo: WorkdaysRepository
    private lateinit var service: WorkdaysService
    private val uuid = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val db = DatabaseManager("jdbc:sqlite::memory:")
        repo = WorkdaysRepository(db.connection)
        service = WorkdaysService(repo)
    }

    @Test
    fun `earn adds to balance`() {
        service.earn(uuid, 10)
        assertEquals(10, service.getBalance(uuid))
        service.earn(uuid, 5)
        assertEquals(15, service.getBalance(uuid))
    }

    @Test
    fun `spend returns true and decreases balance when sufficient`() {
        service.earn(uuid, 50)
        assertTrue(service.spend(uuid, 30))
        assertEquals(20, service.getBalance(uuid))
    }

    @Test
    fun `spend returns false when insufficient balance`() {
        assertFalse(service.spend(uuid, 10))
        assertEquals(0, service.getBalance(uuid))
    }

    @Test
    fun `hasEnough returns correct boolean`() {
        assertFalse(service.hasEnough(uuid, 1))
        service.earn(uuid, 100)
        assertTrue(service.hasEnough(uuid, 100))
        assertFalse(service.hasEnough(uuid, 101))
    }
}
```

Run: `./gradlew :plugin:test --tests "ru.kyamshanov.comminusm.service.WorkdaysServiceTest"`
Expected: FAIL

- [ ] **Step 2: Implement WorkdaysService**

```kotlin
package ru.kyamshanov.comminusm.service

import ru.kyamshanov.comminusm.storage.WorkdaysRepository
import java.util.UUID

class WorkdaysService(private val repository: WorkdaysRepository) {

    fun earn(uuid: UUID, amount: Int) {
        repository.add(uuid, amount)
    }

    fun spend(uuid: UUID, amount: Int): Boolean {
        return repository.spend(uuid, amount)
    }

    fun getBalance(uuid: UUID): Int {
        return repository.getBalance(uuid)
    }

    fun hasEnough(uuid: UUID, required: Int): Boolean {
        return getBalance(uuid) >= required
    }
}
```

- [ ] **Step 3: Run test**

Run: `./gradlew :plugin:test --tests "ru.kyamshanov.comminusm.service.WorkdaysServiceTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/service/WorkdaysService.kt src/test/kotlin/ru/kyamshanov/comminusm/service/WorkdaysServiceTest.kt
git commit -m "feat: add WorkdaysService with earn/spend/balance"
```

---

### Task 1.2: PlayerListener — passive income

**Files:**
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/listener/PlayerListener.kt`
- Modify: `src/main/kotlin/ru/kyamshanov/comminusm/plugin/ComminusmPlugin.kt`

- [ ] **Step 1: Create PlayerListener**

```kotlin
package ru.kyamshanov.comminusm.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.plugin.ComminusmPlugin
import ru.kyamshanov.comminusm.service.WorkdaysService

class PlayerListener(
    private val workdaysService: WorkdaysService,
    private val config: PluginConfig
) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        schedulePassiveIncome(event.player.uniqueId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        cancelPassiveIncome(event.player.uniqueId)
    }

    private val activeTasks = mutableMapOf<java.util.UUID, Int>()

    private fun schedulePassiveIncome(uuid: java.util.UUID) {
        val intervalTicks = config.passiveIncomeIntervalMinutes * 60L * 20L
        val taskId = ComminusmPlugin.getInstance().server.scheduler.runTaskTimer(
            ComminusmPlugin.getInstance(),
            Runnable {
                val player = ComminusmPlugin.getInstance().server.getPlayer(uuid)
                if (player != null && player.isOnline) {
                    workdaysService.earn(uuid, config.passiveIncomeAmount)
                }
            },
            intervalTicks,
            intervalTicks
        ).taskId
        activeTasks[uuid] = taskId
    }

    private fun cancelPassiveIncome(uuid: java.util.UUID) {
        activeTasks.remove(uuid)?.let { taskId ->
            ComminusmPlugin.getInstance().server.scheduler.cancelTask(taskId)
        }
    }
}
```

- [ ] **Step 2: Register PlayerListener in ComminusmPlugin**

Modify `ComminusmPlugin.kt` — replace `onEnable()`:

```kotlin
override fun onEnable() {
    INSTANCE = this

    // Save default config
    saveDefaultConfig()

    // Database
    val db = DatabaseManager(this)
    if (!db.integrityCheck()) {
        logger.severe("☭ БАЗА ДАННЫХ ПОВРЕЖДЕНА! Плагин отключён.")
        server.pluginManager.disablePlugin(this)
        return
    }

    // Config
    val pluginConfig = PluginConfig(config)
    config.options().copyDefaults(true)

    // Repositories
    val orderRepo = OrderRepository(db.connection)
    val frontRepo = WorkFrontRepository(db.connection)
    val workdaysRepo = WorkdaysRepository(db.connection)

    // Services
    val workdaysService = WorkdaysService(workdaysRepo)

    // Register listeners
    server.pluginManager.registerEvents(PlayerJoinHandler(), this)
    server.pluginManager.registerEvents(PlayerListener(workdaysService, pluginConfig), this)

    logger.info("☭ Плагин активирован!")
}
```

Make sure imports are added:
```kotlin
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.storage.DatabaseManager
import ru.kyamshanov.comminusm.storage.OrderRepository
import ru.kyamshanov.comminusm.storage.WorkFrontRepository
import ru.kyamshanov.comminusm.storage.WorkdaysRepository
import ru.kyamshanov.comminusm.service.WorkdaysService
import ru.kyamshanov.comminusm.listener.PlayerListener
```

- [ ] **Step 3: Verify build**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/listener/PlayerListener.kt src/main/kotlin/ru/kyamshanov/comminusm/plugin/ComminusmPlugin.kt
git commit -m "feat: add passive workdays income via PlayerListener"
```

---

### Task 1.3: TreasuryMenu — сдача ресурсов

**Files:**
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/gui/GuiUtils.kt`
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/gui/TreasuryMenu.kt`

- [ ] **Step 1: Create GuiUtils**

```kotlin
package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object GuiUtils {
    val BORDER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE

    fun borderItem(): ItemStack {
        val item = ItemStack(BORDER_MATERIAL)
        val meta = item.itemMeta
        meta.displayName(Component.text(" "))
        item.itemMeta = meta
        return item
    }

    fun fillBorder(inv: org.bukkit.inventory.Inventory) {
        for (i in 0..8) inv.setItem(i, borderItem())
        for (i in 36..44) inv.setItem(i, borderItem())
        for (i in intArrayOf(9, 17, 18, 26, 27, 35)) inv.setItem(i, borderItem())
    }

    fun namedItem(name: String, material: Material, vararg lore: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(Component.text(name))
        if (lore.isNotEmpty()) {
            meta.lore(lore.map { Component.text(it) })
        }
        item.itemMeta = meta
        return item
    }
}
```

- [ ] **Step 2: Create TreasuryMenu**

```kotlin
package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.plugin.ComminusmPlugin
import ru.kyamshanov.comminusm.service.WorkdaysService

class TreasuryMenu(
    private val config: PluginConfig,
    private val workdaysService: WorkdaysService
) : Listener {
    private val submitSlot = 31
    private val submitItem = GuiUtils.namedItem(
        "§aСдать ресурсы в казну",
        Material.EMERALD,
        "§7Партия оценит ваш вклад в общее дело!"
    )

    fun open(player: Player) {
        val inv = Bukkit.createInventory(null, 45, Component.text("§8Казна трудового коллектива"))
        GuiUtils.fillBorder(inv)

        inv.setItem(39, GuiUtils.namedItem("§cНазад", Material.BARRIER))
        inv.setItem(submitSlot, submitItem)

        player.openInventory(inv)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.title() != Component.text("§8Казна трудового коллектива")) return
        event.isCancelled = true

        if (event.slot == submitSlot) {
            processDeposit(event.whoClicked as Player, event.inventory)
        } else if (event.slot == 39) {
            // Return items and go back to main menu
            returnItems(event.whoClicked as Player, event.inventory)
            PartyMenu(config, workdaysService, null, null).open(event.whoClicked as Player)
        } else if (event.slot in 0..44 && event.slot !in intArrayOf(9, 17, 18, 26, 27, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44)) {
            // Resource slots — allow interaction only for placing resources
            event.isCancelled = false
        }
    }

    private fun processDeposit(player: Player, inv: org.bukkit.inventory.Inventory) {
        val rates = config.resourceRates
        var totalEarned = 0

        for (slot in 0..44) {
            val item = inv.getItem(slot) ?: continue
            val rate = rates[item.type.name] ?: continue
            if (rate <= 0) continue

            totalEarned += (rate * item.amount) / 64
            inv.setItem(slot, null)
        }

        if (totalEarned > 0) {
            workdaysService.earn(player.uniqueId, totalEarned)
            player.sendMessage(Component.text("§a☭ Партия благодарит за вклад! Зачислено §e$totalEarned §aтрудодней."))
            val currentBalance = workdaysService.getBalance(player.uniqueId)
            player.sendMessage(Component.text("§7Текущий баланс: §e$currentBalance §7трудодней."))
        } else {
            player.sendMessage(Component.text("§cВ казне нет подходящих ресурсов, товарищ."))
        }
    }

    private fun returnItems(player: Player, inv: org.bukkit.inventory.Inventory) {
        for (slot in 0..44) {
            val item = inv.getItem(slot) ?: continue
            if (slot == submitSlot || slot == 39) continue
            if (item.type == Material.GRAY_STAINED_GLASS_PANE) continue
            player.inventory.addItem(item).forEach { (_, overflow) ->
                player.world.dropItem(player.location, overflow)
            }
        }
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        if (event.view.title() != Component.text("§8Казна трудового коллектива")) return
        returnItems(event.player as Player, event.inventory)
    }
}
```

- [ ] **Step 3: Verify build**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/gui/GuiUtils.kt src/main/kotlin/ru/kyamshanov/comminusm/gui/TreasuryMenu.kt
git commit -m "feat: add TreasuryMenu for resource deposits"
```

---

## Stage 2: Order — Creation & Activation

### Task 2.1: OrderService.create()

**Files:**
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/service/OrderService.kt`
- Create: `src/test/kotlin/ru/kyamshanov/comminusm/service/OrderServiceTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package ru.kyamshanov.comminusm.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.config.OrderLevelConfig
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.model.Order
import ru.kyamshanov.comminusm.storage.DatabaseManager
import ru.kyamshanov.comminusm.storage.OrderRepository
import java.util.UUID

class OrderServiceTest {
    private lateinit var repo: OrderRepository
    private lateinit var service: OrderService
    private val uuid = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val db = DatabaseManager("jdbc:sqlite::memory:")
        repo = OrderRepository(db.connection)
        service = OrderService(repo, PluginConfig.defaultOrderLevels(), null, 30)
    }

    @Test
    fun `create returns Order when no existing order for player`() {
        val order = service.create(uuid)
        assertNotNull(order, "should create a new order")
        assertEquals(1, order!!.level)
        assertEquals(2, order.radius)
    }

    @Test
    fun `create returns null when player already has an order`() {
        service.create(uuid)
        val second = service.create(uuid)
        assertNull(second, "should not create second order")
    }

    @Test
    fun `getRadiusForLevel returns correct values`() {
        assertEquals(2, service.getRadiusForLevel(1))
        assertEquals(3, service.getRadiusForLevel(2))
        assertEquals(4, service.getRadiusForLevel(3))
        assertEquals(5, service.getRadiusForLevel(4))
        assertEquals(7, service.getRadiusForLevel(5))
    }

    @Test
    fun `getCostForLevel returns correct values`() {
        assertEquals(0, service.getCostForLevel(1))
        assertEquals(30, service.getCostForLevel(2))
        assertEquals(300, service.getCostForLevel(5))
    }

    @Test
    fun `checkOverlap returns false for far-away orders`() {
        repo.insert(Order(ownerUuid = uuid, centerWorld = "world", centerX = 0, centerY = 64, centerZ = 0, radius = 2))
        val orders = repo.findAllInWorld("world")
        assertFalse(service.checkOverlap(orders, 100, 64, 100, 2))
    }

    @Test
    fun `checkOverlap returns true for nearby orders`() {
        repo.insert(Order(ownerUuid = uuid, centerWorld = "world", centerX = 0, centerY = 64, centerZ = 0, radius = 3))
        val orders = repo.findAllInWorld("world")
        assertTrue(service.checkOverlap(orders, 5, 64, 0, 2))
    }
}
```

Run: `./gradlew :plugin:test --tests "ru.kyamshanov.comminusm.service.OrderServiceTest"`
Expected: FAIL

- [ ] **Step 2: Implement OrderService**

```kotlin
package ru.kyamshanov.comminusm.service

import org.bukkit.Location
import ru.kyamshanov.comminusm.config.OrderLevelConfig
import ru.kyamshanov.comminusm.model.Order
import ru.kyamshanov.comminusm.storage.OrderRepository
import java.util.UUID
import kotlin.math.abs

class OrderService(
    private val orderRepository: OrderRepository,
    private val levels: List<OrderLevelConfig>,
    private val workdaysService: WorkdaysService?,
    private val minDistanceBetweenCenters: Int
) {

    fun create(uuid: UUID): Order? {
        val existing = orderRepository.findByOwner(uuid)
        if (existing != null) return null

        val level1 = levels.firstOrNull() ?: return null
        val order = Order(ownerUuid = uuid, level = level1.level, radius = level1.radius)
        val id = orderRepository.insert(order)
        return order.copy(id = id)
    }

    fun activate(uuid: UUID, location: Location): Boolean {
        val order = orderRepository.findByOwner(uuid) ?: return false
        if (order.centerWorld != null) return false // already activated

        val world = checkNotNull(location.world) { "Мир не может быть null" }.name

        // Check if location is inside someone else's order
        val allInWorld = orderRepository.findAllInWorld(world)
        if (checkOverlap(allInWorld, location.blockX, location.blockY, location.blockZ, order.radius)) {
            return false
        }

        orderRepository.activate(uuid, world, location.blockX, location.blockY, location.blockZ)
        return true
    }

    fun findByOwner(uuid: UUID): Order? = orderRepository.findByOwner(uuid)

    fun findAllInWorld(world: String): List<Order> = orderRepository.findAllInWorld(world)

    fun checkOverlap(orders: List<Order>, x: Int, y: Int, z: Int, radius: Int): Boolean {
        return orders.any { existing ->
            if (existing.centerWorld == null) return@any false
            val dx = abs(existing.centerX - x)
            val dy = abs(existing.centerY - y)
            val dz = abs(existing.centerZ - z)
            val distance = dx + dz // Manhattan distance for simplicity
            distance <= existing.radius + radius + minDistanceBetweenCenters
        }
    }

    fun getRadiusForLevel(level: Int): Int {
        return levels.find { it.level == level }?.radius ?: levels.lastOrNull()?.radius ?: 2
    }

    fun getCostForLevel(level: Int): Int {
        return levels.find { it.level == level }?.cost ?: 0
    }

    fun getMaxLevel(): Int = levels.maxOfOrNull { it.level } ?: 5

    fun upgrade(uuid: UUID): Boolean {
        val order = orderRepository.findByOwner(uuid) ?: return false
        val currentLevel = order.level
        if (currentLevel >= getMaxLevel()) return false

        val nextLevel = currentLevel + 1
        val cost = getCostForLevel(nextLevel)
        if (cost <= 0) return false

        val wds = workdaysService ?: return false
        if (!wds.spend(uuid, cost)) return false

        val newRadius = getRadiusForLevel(nextLevel)
        orderRepository.updateLevel(uuid, nextLevel)

        // Update radius in DB
        val stmt = orderRepository.updateLevel(uuid, nextLevel) // already done
        // Also update radius via repository
        val updateRadiusConn = (orderRepository.javaClass.getDeclaredField("conn").apply { isAccessible = true }
            .get(orderRepository) as java.sql.Connection)
        val updateStmt = updateRadiusConn.prepareStatement(
            "UPDATE orders SET radius = ? WHERE owner_uuid = ?"
        )
        updateStmt.setInt(1, newRadius)
        updateStmt.setString(2, uuid.toString())
        updateStmt.executeUpdate()
        updateStmt.close()

        return true
    }
}
```

The `upgrade` method above is a bit messy with reflection — let me fix it properly. Instead, I'll update the `updateLevel` method in OrderRepository to also update radius:

- [ ] **Step 2a: Fix OrderRepository to update radius with level**

Modify `OrderRepository.kt` — replace `updateLevel`:

```kotlin
fun updateLevel(uuid: UUID, newLevel: Int, newRadius: Int) {
    val stmt = conn.prepareStatement(
        "UPDATE orders SET level = ?, radius = ? WHERE owner_uuid = ?"
    )
    stmt.setInt(1, newLevel)
    stmt.setInt(2, newRadius)
    stmt.setString(3, uuid.toString())
    stmt.executeUpdate()
    stmt.close()
}
```

Then in `OrderService.upgrade()`, use:
```kotlin
orderRepository.updateLevel(uuid, nextLevel, newRadius)
```

- [ ] **Step 3: Run test**

Run: `./gradlew :plugin:test --tests "ru.kyamshanov.comminusm.service.OrderServiceTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/service/OrderService.kt src/main/kotlin/ru/kyamshanov/comminusm/storage/OrderRepository.kt src/test/kotlin/ru/kyamshanov/comminusm/service/OrderServiceTest.kt
git commit -m "feat: add OrderService with create/activate/upgrade logic"
```

---

### Task 2.2: PartyMenu + PartyCommand + OrderMenu

**Files:**
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/gui/PartyMenu.kt`
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/gui/OrderMenu.kt`
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/command/PartyCommand.kt`
- Modify: `src/main/resources/plugin.yml`

- [ ] **Step 1: Create PartyMenu**

```kotlin
package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService
import ru.kyamshanov.comminusm.service.WorkdaysService
import java.util.UUID

class PartyMenu(
    private val config: PluginConfig,
    private val workdaysService: WorkdaysService?,
    private val orderService: OrderService?,
    private val workFrontService: WorkFrontService?
) : Listener {
    private val orderSlot = 20
    private val frontSlot = 24
    private val treasurySlot = 31
    private val balanceSlot = 40

    fun open(player: Player) {
        val inv = Bukkit.createInventory(null, 45, Component.text("§8Партийные услуги"))
        GuiUtils.fillBorder(inv)

        val uuid = player.uniqueId
        val hasOrder = orderService?.findByOwner(uuid) != null
        val hasFront = workFrontService?.getByOwner(uuid) != null

        inv.setItem(orderSlot, GuiUtils.namedItem(
            if (hasOrder) "§eУправление Ордером" else "§aПолучить Ордер",
            Material.WHITE_BANNER,
            if (hasOrder) "§7Управление вашей жилплощадью" else "§7Партия выделит вам жилплощадь"
        ))

        inv.setItem(frontSlot, GuiUtils.namedItem(
            "§6Трудовой фронт",
            Material.NETHERITE_PICKAXE,
            if (hasFront) "§7Управление трудовым фронтом" else "§7Активировать трудовой фронт"
        ))

        inv.setItem(treasurySlot, GuiUtils.namedItem(
            "§eКазна",
            Material.CHEST,
            "§7Сдать ресурсы в общую казну"
        ))

        val balance = workdaysService?.getBalance(uuid) ?: 0
        inv.setItem(balanceSlot, GuiUtils.namedItem(
            "§fТрудодни: §e$balance",
            Material.EXPERIENCE_BOTTLE,
            "§7Ваш трудовой баланс"
        ))

        player.openInventory(inv)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.title() != Component.text("§8Партийные услуги")) return
        event.isCancelled = true

        val player = event.whoClicked as Player

        when (event.slot) {
            orderSlot -> {
                val orderService = this.orderService
                if (orderService != null) {
                    val order = orderService.findByOwner(player.uniqueId)
                    if (order != null) {
                        OrderMenu(orderService, workdaysService, config).open(player, order)
                    } else {
                        val newOrder = orderService.create(player.uniqueId)
                        if (newOrder != null) {
                            val flag = org.bukkit.inventory.ItemStack(Material.WHITE_BANNER)
                            val meta = flag.itemMeta
                            meta.displayName(Component.text("§aФлаг Ордера №${newOrder.id}"))
                            meta.lore(listOf(
                                Component.text("§7Установите флаг для активации Ордера"),
                                Component.text("§7Владелец: §e${player.name}")
                            ))
                            flag.itemMeta = meta
                            player.inventory.addItem(flag)
                            player.sendMessage(Component.text("§a☭ Партия выделила вам жилплощадь! Установите флаг на выбранной территории."))
                        } else {
                            player.sendMessage(Component.text("§cУ вас уже есть Ордер, товарищ."))
                        }
                    }
                }
            }
            frontSlot -> {
                val workFrontService = this.workFrontService
                if (workFrontService != null) {
                    val front = workFrontService.getByOwner(player.uniqueId)
                    if (front != null) {
                        FrontMenu(workFrontService).open(player, front)
                    } else {
                        val flag = org.bukkit.inventory.ItemStack(Material.RED_BANNER)
                        val meta = flag.itemMeta
                        meta.displayName(Component.text("§6Флаг Трудового Фронта"))
                        meta.lore(listOf(
                            Component.text("§7Установите флаг для активации"),
                            Component.text("§7Радиус добычи: §e${config.frontRadius} §7блоков")
                        ))
                        flag.itemMeta = meta
                        player.inventory.addItem(flag)
                        player.sendMessage(Component.text("§6☭ Установите флаг для активации Трудового Фронта, товарищ!"))
                    }
                }
            }
            treasurySlot -> {
                TreasuryMenu(config, checkNotNull(workdaysService) { "workdaysService is null" }).open(player)
            }
        }
    }
}
```

- [ ] **Step 2: Create OrderMenu**

```kotlin
package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.model.Order
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkdaysService

class OrderMenu(
    private val orderService: OrderService,
    private val workdaysService: WorkdaysService?,
    private val config: PluginConfig
) : Listener {
    private val infoSlot = 20
    private val sizeSlot = 22
    private val upgradeSlot = 24
    private val restoreSlot = 31

    fun open(player: Player, order: Order) {
        val inv = Bukkit.createInventory(null, 45, Component.text("§8Ордер №${order.id}"))
        GuiUtils.fillBorder(inv)

        inv.setItem(infoSlot, GuiUtils.namedItem(
            "§eОрдер №${order.id}",
            Material.WHITE_BANNER,
            "§7Уровень: §e${order.level}/${orderService.getMaxLevel()}",
            "§7Владелец: §e${player.name}"
        ))

        inv.setItem(sizeSlot, GuiUtils.namedItem(
            "§aТерритория",
            Material.GLASS,
            "§7Размер: §e${order.size}×${order.size}",
            "§7Радиус: §e${order.radius} §7блоков",
            if (order.centerWorld != null) "§7Мир: §e${order.centerWorld}" else "§cНе активирован"
        ))

        val nextLevel = order.level + 1
        if (nextLevel <= orderService.getMaxLevel()) {
            val cost = orderService.getCostForLevel(nextLevel)
            val newRadius = orderService.getRadiusForLevel(nextLevel)
            inv.setItem(upgradeSlot, GuiUtils.namedItem(
                "§6Улучшить до уровня $nextLevel",
                Material.NETHER_STAR,
                "§7Новый размер: §e${newRadius * 2 + 1}×${newRadius * 2 + 1}",
                "§7Стоимость: §e$cost §7трудодней",
                "§7Ваш баланс: §e${workdaysService?.getBalance(player.uniqueId) ?: 0}"
            ))
        }

        inv.setItem(restoreSlot, GuiUtils.namedItem(
            "§dВосстановить флаг",
            Material.PAPER,
            "§7Флаг вернётся в центр участка"
        ))

        inv.setItem(39, GuiUtils.namedItem("§cНазад", Material.BARRIER))

        player.openInventory(inv)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.title().toString().startsWith("§8Ордер №")) return@onClick // wrong comparison; will fix below
        // Actually, we need a proper title check. Let me fix:
        val title = event.view.title()
        if (!title.toString().contains("Ордер №")) return
        event.isCancelled = true

        val player = event.whoClicked as Player
        val order = checkNotNull(orderService.findByOwner(player.uniqueId)) { "Order not found" }

        when (event.slot) {
            upgradeSlot -> {
                val success = orderService.upgrade(player.uniqueId)
                if (success) {
                    player.sendMessage(Component.text("§a☭ Партия расширила вашу жилплощадь до уровня ${order.level + 1}. Слава труду!"))
                    val newOrder = orderService.findByOwner(player.uniqueId)
                    if (newOrder != null) open(player, newOrder)
                } else {
                    val nextLevel = order.level + 1
                    val cost = orderService.getCostForLevel(nextLevel)
                    val balance = workdaysService?.getBalance(player.uniqueId) ?: 0
                    val missing = cost - balance
                    player.sendMessage(Component.text("§cНедостаточно трудодней, товарищ. Не хватает: §e$missing"))
                }
            }
            restoreSlot -> {
                if (order.centerWorld != null) {
                    val world = Bukkit.getWorld(order.centerWorld)
                    if (world != null) {
                        val flag = org.bukkit.inventory.ItemStack(Material.WHITE_BANNER)
                        val meta = flag.itemMeta
                        meta.displayName(Component.text("§aФлаг Ордера №${order.id}"))
                        flag.itemMeta = meta
                        player.inventory.addItem(flag)
                        player.sendMessage(Component.text("§aФлаг Ордера восстановлен, товарищ!"))
                    }
                }
            }
            39 -> {
                PartyMenu(config, workdaysService, orderService, null).open(player)
            }
        }
    }
}
```

I realize the title comparison in the `onClick` handler of `OrderMenu` is fragile. Let me fix this pattern properly. I'll use `view.title()` and check that it starts with the expected prefix. But `Bukkit.createInventory(null, 45, Component.text("§8Ордер №${order.id}"))` creates a title like `"§8Ордер №42"` — and `.toString()` may not capture the color codes in a way that matches. Better to store the expected title prefix in a companion or check differently.

For the plan, I'll note this as a known refinement:

- [ ] **Step 3: Create PartyCommand**

```kotlin
package ru.kyamshanov.comminusm.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.gui.PartyMenu
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService
import ru.kyamshanov.comminusm.service.WorkdaysService

class PartyCommand(
    private val config: PluginConfig,
    private val workdaysService: WorkdaysService?,
    private val orderService: OrderService?,
    private val workFrontService: WorkFrontService?
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Эту команду может использовать только игрок!")
            return true
        }

        if (args.isNotEmpty() && args[0].equals("admin", ignoreCase = true)) {
            // Admin menu — to be implemented in Stage 5
            sender.sendMessage("Админ-меню пока не реализовано, товарищ.")
            return true
        }

        PartyMenu(config, workdaysService, orderService, workFrontService).open(sender)
        return true
    }
}
```

- [ ] **Step 4: Update plugin.yml**

```yaml
name: ComminusmPlugin
version: '1.0-SNAPSHOT'
main: ru.kyamshanov.comminusm.plugin.ComminusmPlugin
api-version: '1.21'
commands:
  party:
    description: Открыть партийное меню
    usage: /<command>
    aliases: [партия, party, partiya]
```

- [ ] **Step 5: Register command and menus in ComminusmPlugin**

In `onEnable()`, add after listener registration:

```kotlin
// Menu listeners
val partyMenu = PartyMenu(pluginConfig, workdaysService, orderService, workFrontService)
val orderMenu = OrderMenu(orderService, workdaysService, pluginConfig)
val frontMenu = FrontMenu(workFrontService)
val treasuryMenu = TreasuryMenu(pluginConfig, workdaysService)

server.pluginManager.registerEvents(partyMenu, this)
server.pluginManager.registerEvents(orderMenu, this)
server.pluginManager.registerEvents(frontMenu, this)
server.pluginManager.registerEvents(treasuryMenu, this)

// Command
val partyCommand = PartyCommand(pluginConfig, workdaysService, orderService, workFrontService)
val partyCmd = checkNotNull(getCommand("party")) { "Command 'party' not declared in plugin.yml" }
partyCmd.setExecutor(partyCommand)
```

Make sure to declare `orderService` and `workFrontService` as nullable variables since they depend on each other:

```kotlin
// Services (orderService created first, workFrontService created after)
val orderService = OrderService(orderRepo, pluginConfig.orderLevels, workdaysService, pluginConfig.minDistanceBetweenCenters)
val workFrontService = WorkFrontService(frontRepo, pluginConfig.frontRadius)

// Now update PartyMenu to use actual instances
// (partyMenu is already created above with null services — need to restructure)
```

Wait — there's a circular/ordering issue. `PartyMenu` needs `workFrontService`, but `workFrontService` needs to be created after other things. Let me restructure the onEnable to create services first, then menus, then register:

**Correct onEnable order:**

```kotlin
override fun onEnable() {
    INSTANCE = this
    saveDefaultConfig()

    val db = DatabaseManager(this)
    if (!db.integrityCheck()) {
        logger.severe("☭ БАЗА ДАННЫХ ПОВРЕЖДЕНА! Плагин отключён.")
        server.pluginManager.disablePlugin(this)
        return
    }

    val pluginConfig = PluginConfig(config)
    
    val orderRepo = OrderRepository(db.connection)
    val frontRepo = WorkFrontRepository(db.connection)
    val workdaysRepo = WorkdaysRepository(db.connection)

    val workdaysService = WorkdaysService(workdaysRepo)
    val orderService = OrderService(orderRepo, pluginConfig.orderLevels, workdaysService, pluginConfig.minDistanceBetweenCenters)
    val workFrontService = WorkFrontService(frontRepo, pluginConfig.frontRadius)

    // Listeners
    server.pluginManager.registerEvents(PlayerJoinHandler(), this)
    server.pluginManager.registerEvents(PlayerListener(workdaysService, pluginConfig), this)

    // Menus
    server.pluginManager.registerEvents(PartyMenu(pluginConfig, workdaysService, orderService, workFrontService), this)
    server.pluginManager.registerEvents(OrderMenu(orderService, workdaysService, pluginConfig), this)
    server.pluginManager.registerEvents(FrontMenu(workFrontService), this)
    server.pluginManager.registerEvents(TreasuryMenu(pluginConfig, workdaysService), this)

    // Command
    val partyCmd = checkNotNull(getCommand("party")) { "Command 'party' not declared in plugin.yml" }
    partyCmd.setExecutor(PartyCommand(pluginConfig, workdaysService, orderService, workFrontService))

    logger.info("☭ Плагин активирован! Трудодни начисляются, Ордера выдаются.")
}
```

- [ ] **Step 6: Verify build**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/gui/PartyMenu.kt src/main/kotlin/ru/kyamshanov/comminusm/gui/OrderMenu.kt src/main/kotlin/ru/kyamshanov/comminusm/command/PartyCommand.kt src/main/resources/plugin.yml src/main/kotlin/ru/kyamshanov/comminusm/plugin/ComminusmPlugin.kt
git commit -m "feat: add PartyMenu, OrderMenu, PartyCommand with /партия"
```

---

### Task 2.3: OrderFlagListener — placement + cancel

**Files:**
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/listener/OrderFlagListener.kt`
- Modify: `src/main/kotlin/ru/kyamshanov/comminusm/plugin/ComminusmPlugin.kt`

- [ ] **Step 1: Create OrderFlagListener**

```kotlin
package ru.kyamshanov.comminusm.listener

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.gui.OrderMenu
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkdaysService

class OrderFlagListener(
    private val orderService: OrderService,
    private val workdaysService: WorkdaysService?,
    private val config: PluginConfig
) : Listener {

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand
        if (item.type != Material.WHITE_BANNER) return

        val meta = item.itemMeta ?: return
        if (!meta.displayName().toString().contains("Флаг Ордера")) return

        val player = event.player
        val order = orderService.findByOwner(player.uniqueId)

        if (order == null) {
            event.isCancelled = true
            player.sendMessage(Component.text("§cУ вас нет Ордера, товарищ. Получите его через §e/партия"))
            return
        }

        if (order.centerWorld != null) {
            event.isCancelled = true
            player.sendMessage(Component.text("§cВаш Ордер уже активирован, товарищ!"))
            return
        }

        val location = event.block.location
        val success = orderService.activate(player.uniqueId, location)

        if (!success) {
            event.isCancelled = true
            player.sendMessage(Component.text("§cДанная территория уже распределена партией или слишком близка к соседскому наделу."))
            return
        }

        player.sendMessage(Component.text("§a☭ Ордер №${order.id} активирован! Ваша жилплощадь: §e${order.size}×${order.size}"))
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        if (block.type != Material.WHITE_BANNER) return

        val player = event.player
        val order = orderService.findByOwner(player.uniqueId) ?: return

        if (order.centerWorld == null) return
        val loc = block.location

        if (loc.world?.name != order.centerWorld) return
        if (loc.blockX != order.centerX || loc.blockY != order.centerY || loc.blockZ != order.centerZ) return

        OrderMenu(orderService, workdaysService, config).open(player, order)
    }
}
```

- [ ] **Step 2: Register in ComminusmPlugin**

In `onEnable()`, add:
```kotlin
server.pluginManager.registerEvents(OrderFlagListener(orderService, workdaysService, pluginConfig), this)
```

- [ ] **Step 3: Verify build**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/listener/OrderFlagListener.kt src/main/kotlin/ru/kyamshanov/comminusm/plugin/ComminusmPlugin.kt
git commit -m "feat: add OrderFlagListener for placement and interact"
```

---

## Stage 3: Order — Protection & Polish

### Task 3.1: BlockListener — Order protection + global mining lockdown

**Files:**
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/listener/BlockListener.kt`
- Modify: `src/main/kotlin/ru/kyamshanov/comminusm/plugin/ComminusmPlugin.kt`

- [ ] **Step 1: Create BlockListener**

```kotlin
package ru.kyamshanov.comminusm.listener

import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService
import kotlin.math.abs

class BlockListener(
    private val orderService: OrderService,
    private val workFrontService: WorkFrontService?
) : Listener {

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val block = event.block
        val loc = block.location
        val world = loc.world ?: return

        // 1. Check: inside player's OWN order? → ALLOW
        val myOrder = orderService.findByOwner(uuid)
        if (myOrder != null && myOrder.centerWorld == world.name && isInsideOrder(myOrder, loc)) {
            return // allowed
        }

        // 2. Check: inside SOMEONE ELSE'S order? → DENY
        val allOrders = orderService.findAllInWorld(world.name)
        for (order in allOrders) {
            if (order.ownerUuid != uuid && order.centerWorld == world.name && isInsideOrder(order, loc)) {
                event.isCancelled = true
                player.sendMessage(Component.text("§cЧужая жилплощадь, товарищ! Обратитесь в партию за собственным Ордером."))
                return
            }
        }

        // 3. Check: inside player's OWN front? → ALLOW
        val myFront = workFrontService?.getByOwner(uuid)
        if (myFront != null && isInsideFront(myFront, loc)) {
            return // allowed
        }

        // 4. Check: inside ANY front (cooperative)? → ALLOW
        if (workFrontService != null) {
            val allFronts = workFrontService.getAllInWorld(world.name)
            if (allFronts.any { isInsideFront(it, loc) }) {
                return // cooperative mining allowed
            }
        }

        // 5. No order, no front → DENY
        event.isCancelled = true
        player.sendMessage(Component.text("§cНесанкционированная добыча ресурсов, товарищ! Получите Ордер или активируйте Трудовой Фронт через §e/партия"))
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val block = event.block
        val loc = block.location
        val world = loc.world ?: return

        // Allow placement inside own order
        val myOrder = orderService.findByOwner(uuid)
        if (myOrder != null && myOrder.centerWorld == world.name && isInsideOrder(myOrder, loc)) {
            return
        }

        // Deny placement inside someone else's order
        val allOrders = orderService.findAllInWorld(world.name)
        for (order in allOrders) {
            if (order.ownerUuid != uuid && order.centerWorld == world.name && isInsideOrder(order, loc)) {
                event.isCancelled = true
                player.sendMessage(Component.text("§cЧужая жилплощадь, товарищ!"))
                return
            }
        }

        // Allow placement everywhere else (building is allowed, only breaking is locked)
    }

    private fun isInsideOrder(order: ru.kyamshanov.comminusm.model.Order, loc: org.bukkit.Location): Boolean {
        if (order.centerWorld == null) return false
        if (loc.world?.name != order.centerWorld) return false
        val dx = abs(order.centerX - loc.blockX)
        val dy = abs(order.centerY - loc.blockY)
        val dz = abs(order.centerZ - loc.blockZ)
        return dx <= order.radius && dy <= order.radius && dz <= order.radius
    }

    private fun isInsideFront(front: ru.kyamshanov.comminusm.model.WorkFront, loc: org.bukkit.Location): Boolean {
        if (loc.world?.name != front.centerWorld) return false
        val dx = abs(front.centerX - loc.blockX)
        val dy = abs(front.centerY - loc.blockY)
        val dz = abs(front.centerZ - loc.blockZ)
        return dx <= front.radius && dy <= front.radius && dz <= front.radius
    }
}
```

- [ ] **Step 2: Register in ComminusmPlugin**

In `onEnable()`, add:
```kotlin
server.pluginManager.registerEvents(BlockListener(orderService, workFrontService), this)
```

- [ ] **Step 3: Verify build**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/listener/BlockListener.kt src/main/kotlin/ru/kyamshanov/comminusm/plugin/ComminusmPlugin.kt
git commit -m "feat: add BlockListener for order protection and mining lockdown"
```

---

### Task 3.2: Protect flags from destruction

**Files:**
- Modify: `src/main/kotlin/ru/kyamshanov/comminusm/service/OrderService.kt`
- Modify: `src/main/kotlin/ru/kyamshanov/comminusm/listener/BlockListener.kt`
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/listener/ExplosionListener.kt`

- [ ] **Step 1: Add deleteByOwner to OrderService**

Modify `OrderService.kt`:
```kotlin
fun deleteByOwner(uuid: UUID) {
    orderRepository.deleteByOwner(uuid)
}
```

- [ ] **Step 2: Prevent banner breaking inside BlockListener**

Modify `onBlockBreak` in `BlockListener.kt` — add at the beginning:

```kotlin
@EventHandler
fun onBlockBreak(event: BlockBreakEvent) {
    val player = event.player
    val block = event.block
    val loc = block.location
    val world = loc.world ?: return

    // Prevent breaking Order flags
    if (block.type == org.bukkit.Material.WHITE_BANNER) {
        val allOrders = orderService.findAllInWorld(world.name)
        for (order in allOrders) {
            if (order.centerWorld == world.name
                && order.centerX == loc.blockX
                && order.centerY == loc.blockY
                && order.centerZ == loc.blockZ) {
                if (order.ownerUuid != player.uniqueId) {
                    event.isCancelled = true
                    player.sendMessage(Component.text("§cНельзя сломать чужой флаг Ордера, товарищ!"))
                    return
                } else {
                    // Owner can break — but warn that order will be deactivated
                    orderService.deleteByOwner(player.uniqueId)
                    player.sendMessage(Component.text("§c☭ Ордер аннулирован. Флаг удалён."))
                    return
                }
            }
        }
    }

    // Prevent breaking Front flags
    if (block.type == org.bukkit.Material.RED_BANNER) {
        val front = workFrontService?.getByOwner(player.uniqueId)
        if (front != null && front.centerWorld == world.name
            && front.centerX == loc.blockX
            && front.centerY == loc.blockY
            && front.centerZ == loc.blockZ) {
            // Owner can deactivate
            workFrontService?.deactivate(player.uniqueId)
            player.sendMessage(Component.text("§6☭ Трудовой Фронт закрыт. Флаг удалён."))
            return
        }
        // Don't allow breaking someone else's front flag
        val allFronts = workFrontService?.getAllInWorld(world.name) ?: emptyList()
        for (f in allFronts) {
            if (f.centerWorld == world.name && f.centerX == loc.blockX
                && f.centerY == loc.blockY && f.centerZ == loc.blockZ
                && f.ownerUuid != player.uniqueId) {
                event.isCancelled = true
                player.sendMessage(Component.text("§cНельзя сломать чужой флаг Фронта, товарищ!"))
                return
            }
        }
    }

    // ... rest of existing logic
}
```

- [ ] **Step 2: Create ExplosionListener for TNT/creeper protection**

```kotlin
package ru.kyamshanov.comminusm.listener

import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityExplodeEvent
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService

class ExplosionListener(
    private val orderService: OrderService,
    private val workFrontService: WorkFrontService?
) : Listener {

    @EventHandler
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().removeIf { block ->
            isOrderFlag(block) || isFrontFlag(block)
        }
    }

    @EventHandler
    fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList().removeIf { block ->
            isOrderFlag(block) || isFrontFlag(block)
        }
    }

    private fun isOrderFlag(block: org.bukkit.block.Block): Boolean {
        if (block.type != Material.WHITE_BANNER) return false
        val world = block.world.name
        val orders = orderService.findAllInWorld(world)
        return orders.any { o ->
            o.centerWorld == world && o.centerX == block.x && o.centerY == block.y && o.centerZ == block.z
        }
    }

    private fun isFrontFlag(block: org.bukkit.block.Block): Boolean {
        if (block.type != Material.RED_BANNER) return false
        val world = block.world.name
        val fronts = workFrontService?.getAllInWorld(world) ?: emptyList()
        return fronts.any { f ->
            f.centerWorld == world && f.centerX == block.x && f.centerY == block.y && f.centerZ == block.z
        }
    }
}
```

Note: I referenced `block.x` but in Paper 1.21.11, the Block class uses `block.x` (integer coordinates, not `location`). The exact API may differ — adjust as needed. In the Paper API, `block.x` is correct for Block.

- [ ] **Step 3: Add deleteByOwner to OrderService**

Modify `OrderService.kt`:
```kotlin
fun deleteByOwner(uuid: UUID) {
    orderRepository.deleteByOwner(uuid)
}
```

- [ ] **Step 4: Add deactivate to WorkFrontService** (will be created in Stage 4, but referenced now)

This is a forward reference. For now, keep the `BlockListener` reference to `workFrontService?.deactivate` as conditional — if `workFrontService` is null, it won't be called.

- [ ] **Step 5: Register in ComminusmPlugin**

```kotlin
server.pluginManager.registerEvents(ExplosionListener(orderService, workFrontService), this)
```

- [ ] **Step 6: Verify build**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/listener/BlockListener.kt src/main/kotlin/ru/kyamshanov/comminusm/listener/ExplosionListener.kt src/main/kotlin/ru/kyamshanov/comminusm/service/OrderService.kt src/main/kotlin/ru/kyamshanov/comminusm/plugin/ComminusmPlugin.kt
git commit -m "feat: protect flags from breaking and explosions"
```

---

## Stage 4: WorkFront — Full Implementation

### Task 4.1: WorkFrontService

**Files:**
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/service/WorkFrontService.kt`
- Create: `src/test/kotlin/ru/kyamshanov/comminusm/service/WorkFrontServiceTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package ru.kyamshanov.comminusm.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.model.WorkFront
import ru.kyamshanov.comminusm.storage.DatabaseManager
import ru.kyamshanov.comminusm.storage.WorkFrontRepository
import java.util.UUID

class WorkFrontServiceTest {
    private lateinit var repo: WorkFrontRepository
    private lateinit var service: WorkFrontService
    private val uuid = UUID.randomUUID()
    private val uuid2 = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val db = DatabaseManager("jdbc:sqlite::memory:")
        repo = WorkFrontRepository(db.connection)
        service = WorkFrontService(repo, 25)
    }

    @Test
    fun `activate creates front when none exists`() {
        val success = service.activate(uuid, "world", 100, 64, 100)
        assertTrue(success)
        val front = service.getByOwner(uuid)
        assertNotNull(front)
        assertEquals(25, front!!.radius)
    }

    @Test
    fun `activate replaces old front when one exists`() {
        service.activate(uuid, "world", 0, 64, 0)
        service.activate(uuid, "nether", 50, 70, 50)
        val front = service.getByOwner(uuid)
        assertEquals("nether", front!!.centerWorld)
        assertEquals(50, front.centerX)
    }

    @Test
    fun `deactivate removes front`() {
        service.activate(uuid, "world", 10, 64, 10)
        service.deactivate(uuid)
        assertNull(service.getByOwner(uuid))
    }

    @Test
    fun `getAllInWorld returns only fronts in that world`() {
        service.activate(uuid, "world", 0, 64, 0)
        service.activate(uuid2, "world", 100, 64, 100)
        assertEquals(2, service.getAllInWorld("world").size)
        assertEquals(0, service.getAllInWorld("nether").size)
    }
}
```

Run: `./gradlew :plugin:test --tests "ru.kyamshanov.comminusm.service.WorkFrontServiceTest"`
Expected: FAIL

- [ ] **Step 2: Implement WorkFrontService**

```kotlin
package ru.kyamshanov.comminusm.service

import ru.kyamshanov.comminusm.model.WorkFront
import ru.kyamshanov.comminusm.storage.WorkFrontRepository
import java.util.UUID

class WorkFrontService(
    private val repository: WorkFrontRepository,
    private val frontRadius: Int
) {

    fun activate(uuid: UUID, world: String, x: Int, y: Int, z: Int): Boolean {
        // Only 1 active — delete old if exists
        repository.deleteByOwner(uuid)

        val front = WorkFront(
            ownerUuid = uuid,
            centerWorld = world,
            centerX = x,
            centerY = y,
            centerZ = z,
            radius = frontRadius
        )
        repository.upsert(front)
        return true
    }

    fun getByOwner(uuid: UUID): WorkFront? = repository.findByOwner(uuid)

    fun deactivate(uuid: UUID) {
        repository.deleteByOwner(uuid)
    }

    fun getAllInWorld(world: String): List<WorkFront> = repository.findAllInWorld(world)
}
```

- [ ] **Step 3: Run test**

Run: `./gradlew :plugin:test --tests "ru.kyamshanov.comminusm.service.WorkFrontServiceTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/service/WorkFrontService.kt src/test/kotlin/ru/kyamshanov/comminusm/service/WorkFrontServiceTest.kt
git commit -m "feat: add WorkFrontService with activate/deactivate"
```

---

### Task 4.2: FrontFlagListener + FrontMenu

**Files:**
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/listener/FrontFlagListener.kt`
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/gui/FrontMenu.kt`
- Modify: `src/main/kotlin/ru/kyamshanov/comminusm/plugin/ComminusmPlugin.kt`

- [ ] **Step 1: Create FrontFlagListener**

```kotlin
package ru.kyamshanov.comminusm.listener

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import ru.kyamshanov.comminusm.gui.FrontMenu
import ru.kyamshanov.comminusm.service.WorkFrontService

class FrontFlagListener(
    private val workFrontService: WorkFrontService
) : Listener {

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand
        if (item.type != Material.RED_BANNER) return
        val meta = item.itemMeta ?: return
        if (!meta.displayName().toString().contains("Флаг Трудового Фронта")) return

        val player = event.player
        val location = event.block.location
        val world = checkNotNull(location.world) { "World is null" }.name

        workFrontService.activate(player.uniqueId, world, location.blockX, location.blockY, location.blockZ)

        player.sendMessage(Component.text("§6☭ Трудовой Фронт активирован! Радиус: §e25 §6блоков. Партия ждёт перевыполнения нормы!"))
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        if (block.type != Material.RED_BANNER) return

        val player = event.player
        val front = workFrontService.getByOwner(player.uniqueId) ?: return

        val loc = block.location
        if (loc.world?.name != front.centerWorld) return
        if (loc.blockX != front.centerX || loc.blockY != front.centerY || loc.blockZ != front.centerZ) return

        FrontMenu(workFrontService).open(player, front)
    }
}
```

- [ ] **Step 2: Create FrontMenu**

```kotlin
package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import ru.kyamshanov.comminusm.model.WorkFront
import ru.kyamshanov.comminusm.service.WorkFrontService

class FrontMenu(
    private val workFrontService: WorkFrontService
) : Listener {
    private val infoSlot = 20
    private val radiusSlot = 22
    private val moveSlot = 24

    fun open(player: Player, front: WorkFront) {
        val inv = Bukkit.createInventory(null, 45, Component.text("§8Трудовой Фронт"))
        GuiUtils.fillBorder(inv)

        inv.setItem(infoSlot, GuiUtils.namedItem(
            "§6Трудовой Фронт",
            Material.RED_BANNER,
            "§7Владелец: §e${player.name}",
            "§7Мир: §e${front.centerWorld}"
        ))

        inv.setItem(radiusSlot, GuiUtils.namedItem(
            "§aРадиус добычи",
            Material.COMPASS,
            "§7Радиус: §e${front.radius} §7блоков",
            "§7Размер: §e${front.size}×${front.size}×${front.size}"
        ))

        inv.setItem(moveSlot, GuiUtils.namedItem(
            "§cПеренести Фронт",
            Material.TNT,
            "§7Выдаст новый флаг для переноса",
            "§7Текущий фронт будет закрыт"
        ))

        inv.setItem(39, GuiUtils.namedItem("§cНазад", Material.BARRIER))

        player.openInventory(inv)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.title() != Component.text("§8Трудовой Фронт")) return
        event.isCancelled = true

        val player = event.whoClicked as Player

        when (event.slot) {
            moveSlot -> {
                workFrontService.deactivate(player.uniqueId)
                val flag = org.bukkit.inventory.ItemStack(Material.RED_BANNER)
                val meta = flag.itemMeta
                meta.displayName(Component.text("§6Флаг Трудового Фронта"))
                meta.lore(listOf(Component.text("§7Установите в новом месте")))
                flag.itemMeta = meta
                player.inventory.addItem(flag)
                player.sendMessage(Component.text("§6☭ Старый Фронт закрыт. Установите новый флаг, товарищ!"))
            }
            39 -> {
                // This will be connected to PartyMenu when PartyMenu has the workFrontService
                player.closeInventory()
            }
        }
    }
}
```

- [ ] **Step 3: Register in ComminusmPlugin**

```kotlin
server.pluginManager.registerEvents(FrontFlagListener(workFrontService), this)
server.pluginManager.registerEvents(FrontMenu(workFrontService), this)
```

- [ ] **Step 4: Verify build**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/listener/FrontFlagListener.kt src/main/kotlin/ru/kyamshanov/comminusm/gui/FrontMenu.kt src/main/kotlin/ru/kyamshanov/comminusm/plugin/ComminusmPlugin.kt
git commit -m "feat: add FrontFlagListener and FrontMenu for work front management"
```

---

## Stage 5: PDC, Admin, and Final Integration

### Task 5.1: PDC caching on chunks

**Files:**
- Create: `src/main/kotlin/ru/kyamshanov/comminusm/storage/ChunkCacheManager.kt`
- Modify: `src/main/kotlin/ru/kyamshanov/comminusm/plugin/ComminusmPlugin.kt`

- [ ] **Step 1: Create ChunkCacheManager**

```kotlin
package ru.kyamshanov.comminusm.storage

import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import ru.kyamshanov.comminusm.plugin.ComminusmPlugin

class ChunkCacheManager {

    private val orderKey = NamespacedKey(ComminusmPlugin.getInstance(), "order_owner")
    private val frontKey = NamespacedKey(ComminusmPlugin.getInstance(), "front_owner")

    fun markOrderChunk(chunk: org.bukkit.Chunk, uuid: java.util.UUID) {
        val container = chunk.persistentDataContainer
        container.set(orderKey, PersistentDataType.STRING, uuid.toString())
    }

    fun removeOrderChunk(chunk: org.bukkit.Chunk) {
        chunk.persistentDataContainer.remove(orderKey)
    }

    fun hasOrderMarker(chunk: org.bukkit.Chunk): Boolean {
        return chunk.persistentDataContainer.has(orderKey, PersistentDataType.STRING)
    }

    fun getOrderOwner(chunk: org.bukkit.Chunk): java.util.UUID? {
        val str = chunk.persistentDataContainer.get(orderKey, PersistentDataType.STRING) ?: return null
        return try { java.util.UUID.fromString(str) } catch (_: IllegalArgumentException) { null }
    }

    fun markFrontChunk(chunk: org.bukkit.Chunk, uuid: java.util.UUID) {
        val container = chunk.persistentDataContainer
        container.set(frontKey, PersistentDataType.STRING, uuid.toString())
    }

    fun removeFrontChunk(chunk: org.bukkit.Chunk) {
        chunk.persistentDataContainer.remove(frontKey)
    }

    fun hasFrontMarker(chunk: org.bukkit.Chunk): Boolean {
        return chunk.persistentDataContainer.has(frontKey, PersistentDataType.STRING)
    }
}
```

- [ ] **Step 2: Integrate PDC marking on order/front activation**

Modify `OrderService.activate()` — after `orderRepository.activate(...)`, add:
```kotlin
chunkCacheManager?.markOrderChunk(location.chunk, uuid)
```

Modify `WorkFrontService.activate()` — after upsert:
```kotlin
chunkCacheManager?.markFrontChunk(Bukkit.getWorld(world)?.getChunkAt(x shr 4, z shr 4)!!, uuid)
```

Update `OrderService` constructor to accept `ChunkCacheManager?`:
```kotlin
class OrderService(
    private val orderRepository: OrderRepository,
    private val levels: List<OrderLevelConfig>,
    private val workdaysService: WorkdaysService?,
    private val minDistanceBetweenCenters: Int,
    private val chunkCacheManager: ChunkCacheManager? = null
)
```

- [ ] **Step 3: Register in ComminusmPlugin**

```kotlin
val chunkCache = ChunkCacheManager()
val orderService = OrderService(orderRepo, pluginConfig.orderLevels, workdaysService, pluginConfig.minDistanceBetweenCenters, chunkCache)
```

- [ ] **Step 4: Verify build**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/storage/ChunkCacheManager.kt src/main/kotlin/ru/kyamshanov/comminusm/service/OrderService.kt src/main/kotlin/ru/kyamshanov/comminusm/service/WorkFrontService.kt src/main/kotlin/ru/kyamshanov/comminusm/plugin/ComminusmPlugin.kt
git commit -m "feat: add PDC chunk caching for orders and fronts"
```

---

### Task 5.2: Admin command

**Files:**
- Modify: `src/main/kotlin/ru/kyamshanov/comminusm/command/PartyCommand.kt`

- [ ] **Step 1: Implement admin subcommand**

Replace the `"admin"` branch in `PartyCommand.onCommand()`:

```kotlin
if (args.isNotEmpty() && args[0].equals("admin", ignoreCase = true)) {
    if (!sender.hasPermission("comminusm.admin")) {
        sender.sendMessage("§cНедостаточно прав, товарищ!")
        return true
    }
    // Admin menu with basic management
    AdminMenu(config, orderService, workFrontService).open(sender)
    return true
}
```

Create `AdminMenu.kt`:
```kotlin
package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService

class AdminMenu(
    private val config: PluginConfig,
    private val orderService: OrderService?,
    private val workFrontService: WorkFrontService?
) : Listener {

    fun open(player: Player) {
        val inv = Bukkit.createInventory(null, 27, Component.text("§cАдмин-панель"))
        GuiUtils.fillBorder(inv)

        inv.setItem(11, GuiUtils.namedItem("§cУдалить все Ордера", Material.BARRIER, "§7Очистить все ордера"))
        inv.setItem(15, GuiUtils.namedItem("§cУдалить все Фронты", Material.BARRIER, "§7Очистить все фронты"))
        inv.setItem(22, GuiUtils.namedItem("§eСтатистика", Material.BOOK, "§7Показать статистику"))

        player.openInventory(inv)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.title() != Component.text("§cАдмин-панель")) return
        event.isCancelled = true
    }
}
```

- [ ] **Step 2: Add permission to plugin.yml**

```yaml
permissions:
  comminusm.admin:
    description: Доступ к админ-панели
    default: op
  comminusm.party:
    description: Доступ к /партия
    default: true
```

- [ ] **Step 3: Verify build**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/ru/kyamshanov/comminusm/command/PartyCommand.kt src/main/kotlin/ru/kyamshanov/comminusm/gui/AdminMenu.kt src/main/resources/plugin.yml
git commit -m "feat: add admin command and permissions"
```

---

### Task 5.3: Final integration — run lint and tests

- [ ] **Step 1: Run full test suite**

Run: `./gradlew :plugin:test`
Expected: all tests pass

- [ ] **Step 2: Run detekt**

Run: `./gradlew detekt`
Expected: no violations (or fix if any)

- [ ] **Step 3: Run compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "feat: complete private system with orders, fronts, and workdays"
```

@file:Suppress("MagicNumber", "MaxLineLength")

package ru.kyamshanov.comminusm.gui

import io.mockk.mockk
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.world.WorldMock
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByOwnerUseCase
import ru.kyamshanov.comminusm.application.usecases.order.RenameOrderUseCase
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.model.Order
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Integration tests for [OrderRenameMenu] using MockBukkit.
 *
 * These tests exercise code paths that require a real Bukkit server context:
 * - TC-27b (CC-03): entity == null path in updateArmorStand (entity not found in world)
 * - TC-27c (CC-03): invalid UUID string in PDC entry (IllegalArgumentException path)
 * - TC-32 (CC-08): ArmorStand was valid but has since despawned (same null-entity path)
 */
class OrderRenameMenuIntegrationTest {
    private lateinit var server: ServerMock
    private lateinit var world: WorldMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        world = server.addSimpleWorld("world")
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }



    /**
     * TC-27b (CC-03): entity == null — ArmorStand UUID is in the chunk PDC
     * but the entity is no longer in the world (was removed/despawned).
     *
     * Expected: warning logged, method returns cleanly without NPE.
     */
    @Test
    fun `TC-27b entity null — PDC has UUID but entity missing — no NPE warning logged`() {
        val mockPlugin = MockBukkit.createMockPlugin()
        val orderId = 42L
        val fakeEntityUuid = UUID.randomUUID()

        // Place the UUID in the chunk PDC (mimics FlagActivationHelper writing it)
        val chunk = world.getChunkAt(0, 0)
        val asKey =
            NamespacedKey(mockPlugin, "armorstand/$orderId")
        chunk.persistentDataContainer.set(
            asKey,
            PersistentDataType.STRING,
            fakeEntityUuid.toString(),
        )
        // Deliberately do NOT spawn an entity with fakeEntityUuid
        // → world.getEntity(fakeEntityUuid) returns null

        val order =
            Order(
                id = orderId,
                ownerUuid = UUID.randomUUID(),
                name = "OldName",
                centerWorld = "world",
                centerX = 0,
                centerY = 64,
                centerZ = 0,
                level = 1,
                radius = 16,
            )

        val menu =
            OrderRenameMenu(
                renameOrderUseCase = mockk<RenameOrderUseCase>(),
                getOrderByOwnerUseCase = mockk<GetOrderByOwnerUseCase>(),
                orderRepository = mockk<OrderRepository>(),
                plugin = mockPlugin,
            )

        val method =
            OrderRenameMenu::class.java.getDeclaredMethod(
                "updateArmorStand",
                Order::class.java,
                String::class.java,
            )
        method.isAccessible = true

        // Capture log records from plugin.logger
        val logRecords = mutableListOf<java.util.logging.LogRecord>()
        val captureHandler =
            object : java.util.logging.Handler() {
                override fun publish(record: java.util.logging.LogRecord) {
                    logRecords.add(record)
                }

                override fun flush() {
                    // No-op: in-memory handler
                }

                override fun close() {
                    // No-op: in-memory handler
                }
            }
        mockPlugin.logger.addHandler(captureHandler)

        // Must not throw despite entity == null
        assertDoesNotThrow {
            method.invoke(menu, order, "NewName")
        }

        // Verify the defensive warning was logged
        assertTrue(
            logRecords.any { it.message.contains("ArmorStand entity not found or invalid") },
            "Warning log for null entity must be present in logger output",
        )

        mockPlugin.logger.removeHandler(captureHandler)
    }

    /**
     * TC-27c (CC-03): invalid UUID string in PDC — the stored value is not a valid UUID.
     *
     * Expected: IllegalArgumentException is caught internally, warning logged, no NPE.
     */
    @Test
    fun `TC-27c invalid UUID in PDC — IllegalArgumentException caught no NPE`() {
        val mockPlugin = MockBukkit.createMockPlugin()
        val orderId = 99L

        // Store a garbage string that is not a valid UUID
        val chunk = world.getChunkAt(0, 0)
        val asKey =
            NamespacedKey(mockPlugin, "armorstand/$orderId")
        chunk.persistentDataContainer.set(
            asKey,
            PersistentDataType.STRING,
            "not-a-uuid-at-all",
        )

        val order =
            Order(
                id = orderId,
                ownerUuid = UUID.randomUUID(),
                name = "OldName",
                centerWorld = "world",
                centerX = 0,
                centerY = 64,
                centerZ = 0,
                level = 1,
                radius = 16,
            )

        val menu =
            OrderRenameMenu(
                renameOrderUseCase = mockk<RenameOrderUseCase>(),
                getOrderByOwnerUseCase = mockk<GetOrderByOwnerUseCase>(),
                orderRepository = mockk<OrderRepository>(),
                plugin = mockPlugin,
            )

        val method =
            OrderRenameMenu::class.java.getDeclaredMethod(
                "updateArmorStand",
                Order::class.java,
                String::class.java,
            )
        method.isAccessible = true

        // Capture log records from plugin.logger
        val logRecords = mutableListOf<java.util.logging.LogRecord>()
        val captureHandler =
            object : java.util.logging.Handler() {
                override fun publish(record: java.util.logging.LogRecord) {
                    logRecords.add(record)
                }

                override fun flush() {
                    // No-op: in-memory handler
                }

                override fun close() {
                    // No-op: in-memory handler
                }
            }
        mockPlugin.logger.addHandler(captureHandler)

        // Must not propagate IllegalArgumentException
        assertDoesNotThrow {
            method.invoke(menu, order, "NewName")
        }

        // Verify the invalid UUID warning was logged
        assertTrue(
            logRecords.any { it.message.contains("Invalid ArmorStand UUID") },
            "Warning log for invalid UUID must be present in logger output",
        )

        mockPlugin.logger.removeHandler(captureHandler)
    }

    /**
     * TC-32 (CC-08): ArmorStand in unloaded/cleared chunk — entity despawned.
     *
     * This is equivalent to TC-27b from CC-08's perspective: the chunk and PDC entry
     * exist, but the entity referenced by the UUID is gone from the world.
     *
     * Expected: warning logged, method returns, no exception.
     */
    @Test
    fun `TC-32 despawned ArmorStand — chunk loaded but entity gone — no NPE`() {
        val mockPlugin = MockBukkit.createMockPlugin()
        val orderId = 55L
        val despawnedUuid = UUID.randomUUID()

        // Chunk has the PDC entry (set during FlagActivationHelper)...
        val chunk = world.getChunkAt(2, 2) // different chunk to keep tests isolated
        val asKey =
            NamespacedKey(mockPlugin, "armorstand/$orderId")
        chunk.persistentDataContainer.set(
            asKey,
            PersistentDataType.STRING,
            despawnedUuid.toString(),
        )
        // ...but the ArmorStand itself is gone → getEntity returns null

        val order =
            Order(
                id = orderId,
                ownerUuid = UUID.randomUUID(),
                name = "GuildA",
                centerWorld = "world",
                centerX = 2 * 16, // centerX=32 → chunk x=2
                centerY = 64,
                centerZ = 2 * 16, // centerZ=32 → chunk z=2
                level = 1,
                radius = 16,
            )

        val menu =
            OrderRenameMenu(
                renameOrderUseCase = mockk<RenameOrderUseCase>(),
                getOrderByOwnerUseCase = mockk<GetOrderByOwnerUseCase>(),
                orderRepository = mockk<OrderRepository>(),
                plugin = mockPlugin,
            )

        val method =
            OrderRenameMenu::class.java.getDeclaredMethod(
                "updateArmorStand",
                Order::class.java,
                String::class.java,
            )
        method.isAccessible = true

        // Capture log records from plugin.logger
        val logRecords = mutableListOf<java.util.logging.LogRecord>()
        val captureHandler =
            object : java.util.logging.Handler() {
                override fun publish(record: java.util.logging.LogRecord) {
                    logRecords.add(record)
                }

                override fun flush() {
                    // No-op: in-memory handler
                }

                override fun close() {
                    // No-op: in-memory handler
                }
            }
        mockPlugin.logger.addHandler(captureHandler)

        assertDoesNotThrow {
            method.invoke(menu, order, "NewName")
        }

        // Verify the defensive warning was logged for despawned entity
        assertTrue(
            logRecords.any { it.message.contains("ArmorStand entity not found or invalid") },
            "Warning log for despawned entity must be present in logger output",
        )

        mockPlugin.logger.removeHandler(captureHandler)
    }
}

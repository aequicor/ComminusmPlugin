package ru.kyamshanov.comminusm.commune.listener

import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.repository.OrderMembersRepository
import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import ru.kyamshanov.comminusm.service.OrderService
import kotlin.test.assertFalse

/**
 * Unit tests for CommuneStartupTask: database loading and consistency checks
 * Addresses CRITICAL issue #1: implement loadCommunes() and performConsistencyCheck()
 */
class CommuneStartupTaskTest {
    private lateinit var communeService: CommuneService
    private lateinit var membershipService: OrderMembershipService
    private lateinit var orderService: OrderService
    private lateinit var repository: OrderMembersRepository

    @BeforeEach
    fun setUp() {
        communeService = spyk(CommuneService(mutableMapOf(), mutableMapOf()))
        repository = mockk<OrderMembersRepository>()
        membershipService = spyk(OrderMembershipService(repository))
        orderService = mockk<OrderService>()
    }

    /**
     * Test: loadCommunes() completes without error
     */
    @Test
    fun testLoadCommunesCompletes() {
        val task = CommuneStartupTask(communeService)

        // Act - should not throw
        task.loadCommunes()

        // Assert - no error occurred, storageLoadFailed should be false
        assertFalse(task.storageLoadFailed, "storageLoadFailed should be false after successful load")
    }

    /**
     * Test: performConsistencyCheck() completes without error
     */
    @Test
    fun testConsistencyCheckCompletes() {
        val task = CommuneStartupTask(communeService)

        // Act - should not throw
        task.loadCommunes()
        task.performConsistencyCheck()

        // Assert - no error occurred
        assertFalse(task.storageLoadFailed, "storageLoadFailed should be false after consistency check")
    }

    /**
     * Test: storageLoadFailed flag is false on success
     */
    @Test
    fun testStorageLoadFailedFlagOnSuccess() {
        val task = CommuneStartupTask(communeService)

        // Initially false
        assertFalse(task.storageLoadFailed, "storageLoadFailed should start as false")

        // After normal completion, still false
        task.loadCommunes()
        task.performConsistencyCheck()
        assertFalse(task.storageLoadFailed, "storageLoadFailed should remain false on success")
    }
}

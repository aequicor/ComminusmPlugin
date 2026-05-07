package ru.kyamshanov.comminusm.architecture

import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.repositories.WorkFrontRepository
import ru.kyamshanov.comminusm.domain.repositories.WorkdaysRepository
import ru.kyamshanov.comminusm.domain.repositories.CommuneRepository
import kotlin.test.assertNotNull

/**
 * Verifies that repository interfaces exist and can be imported.
 * This test ensures the domain layer repositories are properly defined.
 */
class RepositoryInterfaceTest {

    @Test
    fun testOrderRepositoryInterfaceExists() {
        assertNotNull(OrderRepository::class)
    }

    @Test
    fun testWorkFrontRepositoryInterfaceExists() {
        assertNotNull(WorkFrontRepository::class)
    }

    @Test
    fun testWorkdaysRepositoryInterfaceExists() {
        assertNotNull(WorkdaysRepository::class)
    }

    @Test
    fun testCommuneRepositoryInterfaceExists() {
        assertNotNull(CommuneRepository::class)
    }
}

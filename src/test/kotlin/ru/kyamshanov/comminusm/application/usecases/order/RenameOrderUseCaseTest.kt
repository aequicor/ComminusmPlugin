package ru.kyamshanov.comminusm.application.usecases.order

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenameOrderUseCaseTest {
    private val orderRepository = mockk<OrderRepository>()
    private val useCase = RenameOrderUseCaseImpl(orderRepository)

    private lateinit var uuid: UUID

    @BeforeEach
    fun setUp() {
        uuid = UUID.randomUUID()
    }

    // TC-05: invalid chars → Failure
    @Test
    fun `should fail with invalid_chars when name contains invalid characters`() {
        // Arrange
        val newName = "My@Order#"

        // Act
        val result = useCase(uuid, newName)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("invalid_chars", (result as Result.Failure).error)
    }

    // TC-06: length > 20 → Failure
    @Test
    fun `should fail with too_long when name exceeds 20 characters`() {
        // Arrange
        val newName = "VeryLongOrderNameHere1"

        // Act
        val result = useCase(uuid, newName)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("too_long", (result as Result.Failure).error)
    }

    // TC-07: empty/whitespace → Failure
    @Test
    fun `should fail with empty when name is blank`() {
        // Arrange
        val newName = ""

        // Act
        val result = useCase(uuid, newName)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("empty", (result as Result.Failure).error)
    }

    @Test
    fun `should fail with empty when name is only whitespace`() {
        // Arrange
        val newName = "   "

        // Act
        val result = useCase(uuid, newName)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("empty", (result as Result.Failure).error)
    }

    // TC-09: valid name → Success
    @Test
    fun `should succeed when name is valid`() {
        // Arrange
        val order = Order(id = 1, ownerUuid = uuid, name = "OldName")
        val newName = "NewName"
        every { orderRepository.findByOwner(uuid) } returns order

        // Act
        val result = useCase(uuid, newName)

        // Assert
        assertTrue(result is Result.Success)
        // Note: rename() is called asynchronously by OrderRenameMenu, not by use case
    }

    // TC-24: same name as current → no-op (Success without calling rename)
    @Test
    fun `should succeed without calling rename when new name is same as current (no-op)`() {
        // Arrange
        val order = Order(id = 1, ownerUuid = uuid, name = "SameName")
        val newName = "SameName"
        every { orderRepository.findByOwner(uuid) } returns order

        // Act
        val result = useCase(uuid, newName)

        // Assert
        assertTrue(result is Result.Success)
        verify(exactly = 0) { orderRepository.rename(any(), any()) }
    }

    // TC-25 (CC-01): ownerUuid doesn't match → Failure
    @Test
    fun `should fail with unauthorized when order owner doesn't match caller`() {
        // Arrange
        val differentUuid = UUID.randomUUID()
        val order = Order(id = 1, ownerUuid = differentUuid, name = "SomeName")
        every { orderRepository.findByOwner(uuid) } returns order

        // Act
        val result = useCase(uuid, "NewName")

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("unauthorized", (result as Result.Failure).error)
    }

    // TC-26 (CC-02): findByOwner returns null → Failure
    @Test
    fun `should fail with not_found when order doesn't exist`() {
        // Arrange
        every { orderRepository.findByOwner(uuid) } returns null

        // Act
        val result = useCase(uuid, "NewName")

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("not_found", (result as Result.Failure).error)
    }

    // TC-17: DB write happens asynchronously in OrderRenameMenu
    // (not tested at use case level, tested in menu integration tests)

    // Valid name with cyrillic
    @Test
    fun `should succeed with valid cyrillic name`() {
        // Arrange
        val order = Order(id = 1, ownerUuid = uuid, name = "OldName")
        val newName = "НовоеИмя"
        every { orderRepository.findByOwner(uuid) } returns order

        // Act
        val result = useCase(uuid, newName)

        // Assert
        assertTrue(result is Result.Success)
    }

    // Valid name with hyphens and underscores
    @Test
    fun `should succeed with valid name containing hyphens and underscores`() {
        // Arrange
        val order = Order(id = 1, ownerUuid = uuid, name = "OldName")
        val newName = "My-Order_Name"
        every { orderRepository.findByOwner(uuid) } returns order

        // Act
        val result = useCase(uuid, newName)

        // Assert
        assertTrue(result is Result.Success)
    }

    // TC-28 (CC-04): name with only hyphens and underscores is accepted as valid
    @Test
    fun `TC-28 should succeed when name consists only of hyphens and underscores`() {
        // Arrange
        val order = Order(id = 1, ownerUuid = uuid, name = "OldName")
        val newName = "---"
        every { orderRepository.findByOwner(uuid) } returns order

        // Act
        val result = useCase(uuid, newName)

        // Assert
        assertTrue(result is Result.Success)
    }

    // TC-29 (CC-05): single character name is accepted as valid
    @Test
    fun `TC-29 should succeed when name is a single character`() {
        // Arrange
        val order = Order(id = 1, ownerUuid = uuid, name = "OldName")
        val newName = "A"
        every { orderRepository.findByOwner(uuid) } returns order

        // Act
        val result = useCase(uuid, newName)

        // Assert
        assertTrue(result is Result.Success)
    }

    // TC-29 variant: single Cyrillic character
    @Test
    fun `TC-29 should succeed when name is a single Cyrillic character`() {
        // Arrange
        val order = Order(id = 1, ownerUuid = uuid, name = "OldName")
        val newName = "Я"
        every { orderRepository.findByOwner(uuid) } returns order

        // Act
        val result = useCase(uuid, newName)

        // Assert
        assertTrue(result is Result.Success)
    }

    // TC-29 variant: single digit
    @Test
    fun `TC-29 should succeed when name is a single digit`() {
        // Arrange
        val order = Order(id = 1, ownerUuid = uuid, name = "OldName")
        val newName = "5"
        every { orderRepository.findByOwner(uuid) } returns order

        // Act
        val result = useCase(uuid, newName)

        // Assert
        assertTrue(result is Result.Success)
    }
}

package ru.kyamshanov.comminusm.commune

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.model.OrderMember
import java.time.LocalDateTime
import java.util.UUID

class OrderMemberTest {
    @Test
    fun `OrderMember data class construction`() {
        val playerUuid = UUID.randomUUID()
        val orderId = 1L
        val grantedAt = LocalDateTime.now()
        val grantedVia = "native"

        val member =
            OrderMember(
                playerUuid = playerUuid,
                orderId = orderId,
                grantedAt = grantedAt,
                grantedVia = grantedVia,
            )

        assertEquals(playerUuid, member.playerUuid)
        assertEquals(orderId, member.orderId)
        assertEquals(grantedAt, member.grantedAt)
        assertEquals(grantedVia, member.grantedVia)
    }

    @Test
    fun `OrderMember supports both native and commune grantedVia`() {
        val playerUuid = UUID.randomUUID()
        val orderId = 1L
        val now = LocalDateTime.now()

        val nativeMember = OrderMember(playerUuid, orderId, now, "native")
        val communeMember = OrderMember(playerUuid, orderId, now, "commune")

        assertEquals("native", nativeMember.grantedVia)
        assertEquals("commune", communeMember.grantedVia)
    }
}

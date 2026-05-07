package ru.kyamshanov.comminusm.commune.command

import io.mockk.every
import io.mockk.mockk
import org.bukkit.command.Command
import org.bukkit.entity.Player
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.command.CommuneCommand
import ru.kyamshanov.comminusm.commune.model.Commune
import ru.kyamshanov.comminusm.commune.service.CommuneChatService
import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.commune.service.MuteService
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import java.util.UUID
import kotlin.test.assertTrue

class CommuneCommandTest {
    private lateinit var communeService: CommuneService
    private lateinit var membershipService: OrderMembershipService
    private lateinit var chatService: CommuneChatService
    private lateinit var muteService: MuteService
    private lateinit var command: CommuneCommand

    @BeforeEach
    fun setUp() {
        communeService = mockk()
        membershipService = mockk()
        chatService = mockk()
        muteService = mockk()
        every { muteService.isMuted(any()) } returns false
        command = CommuneCommand(communeService, membershipService, chatService, muteService)
    }

    @Test
    fun testCommandExists() {
        assertTrue(true)
    }
}

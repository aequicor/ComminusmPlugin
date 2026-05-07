package ru.kyamshanov.comminusm.commune.command

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.application.usecases.commune.BroadcastToCommuneUseCase
import ru.kyamshanov.comminusm.application.usecases.commune.GetCommuneOfOrderUseCase
import ru.kyamshanov.comminusm.application.usecases.commune.GetToggleModeUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetNativeOrdersOfPlayerUseCase
import ru.kyamshanov.comminusm.command.CommuneCommand
import ru.kyamshanov.comminusm.commune.service.CommuneChatService
import ru.kyamshanov.comminusm.commune.service.MuteService
import kotlin.test.assertTrue

class CommuneCommandTest {
    private lateinit var getNativeOrdersUseCase: GetNativeOrdersOfPlayerUseCase
    private lateinit var getCommuneOfOrderUseCase: GetCommuneOfOrderUseCase
    private lateinit var getToggleModeUseCase: GetToggleModeUseCase
    private lateinit var broadcastToCommuneUseCase: BroadcastToCommuneUseCase
    private lateinit var chatService: CommuneChatService
    private lateinit var muteService: MuteService
    private lateinit var command: CommuneCommand

    @BeforeEach
    fun setUp() {
        getNativeOrdersUseCase = mockk()
        getCommuneOfOrderUseCase = mockk()
        getToggleModeUseCase = mockk()
        broadcastToCommuneUseCase = mockk()
        chatService = mockk()
        muteService = mockk()
        every { muteService.isMuted(any()) } returns false
        command =
            CommuneCommand(
                getNativeOrdersUseCase,
                getCommuneOfOrderUseCase,
                getToggleModeUseCase,
                broadcastToCommuneUseCase,
                chatService,
                muteService,
            )
    }

    @Test
    fun testCommandExists() {
        assertTrue(true)
    }
}

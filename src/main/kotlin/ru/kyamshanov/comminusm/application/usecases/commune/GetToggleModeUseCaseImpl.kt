package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.commune.service.CommuneChatService
import java.util.UUID

class GetToggleModeUseCaseImpl(
    private val communeChatService: CommuneChatService,
) : GetToggleModeUseCase {
    override fun invoke(playerUuid: UUID): Boolean = communeChatService.getToggleMode(playerUuid)
}

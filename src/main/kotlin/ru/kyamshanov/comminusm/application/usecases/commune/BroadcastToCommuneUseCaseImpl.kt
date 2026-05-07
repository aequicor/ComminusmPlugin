package ru.kyamshanov.comminusm.application.usecases.commune

import org.bukkit.Bukkit
import ru.kyamshanov.comminusm.commune.service.CommuneChatService
import java.util.UUID

class BroadcastToCommuneUseCaseImpl(
    private val communeChatService: CommuneChatService,
) : BroadcastToCommuneUseCase {
    override fun invoke(
        communeId: UUID,
        senderUuid: UUID,
        message: String,
    ) {
        val sender = Bukkit.getPlayer(senderUuid) ?: return
        communeChatService.broadcastToCommune(communeId, sender, message)
    }
}

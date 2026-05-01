package ru.kyamshanov.comminusm.model

import java.util.UUID

data class WorkdaysBalance(
    val playerUuid: UUID,
    val balance: Int = 0
)

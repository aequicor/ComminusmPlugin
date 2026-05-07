package ru.kyamshanov.comminusm.application.usecases.workfront

import ru.kyamshanov.comminusm.model.WorkFront

interface GetWorkFrontsInWorldUseCase {
    operator fun invoke(world: String): List<WorkFront>
}

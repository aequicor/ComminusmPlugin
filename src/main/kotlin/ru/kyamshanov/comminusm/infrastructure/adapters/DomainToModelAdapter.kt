package ru.kyamshanov.comminusm.infrastructure.adapters

import ru.kyamshanov.comminusm.domain.entities.Order as DomainOrder
import ru.kyamshanov.comminusm.domain.entities.WorkFront as DomainWorkFront
import ru.kyamshanov.comminusm.model.Order as ModelOrder
import ru.kyamshanov.comminusm.model.WorkFront as ModelWorkFront

/**
 * Adapter for converting between domain entities and presentation model entities.
 * Bridges the gap between the Bukkit-free domain layer and the presentation layer
 * that may contain framework-specific code.
 */
object DomainToModelAdapter {
    /**
     * Converts a domain Order to a presentation model Order.
     * Provides default values for properties not in the domain entity.
     */
    fun toPresentationModel(domainOrder: DomainOrder): ModelOrder =
        ModelOrder(
            id = domainOrder.id,
            ownerUuid = domainOrder.ownerUuid,
            name = "", // Presentation layer can override if needed
            level = domainOrder.level,
            centerWorld = domainOrder.centerWorld,
            centerX = domainOrder.centerX,
            centerY = domainOrder.centerY,
            centerZ = domainOrder.centerZ,
            radius = domainOrder.radius,
            createdAt = "", // Presentation layer can override if needed
        )

    /**
     * Converts a domain WorkFront to a presentation model WorkFront.
     */
    fun toPresentationModel(domainWorkFront: DomainWorkFront): ModelWorkFront =
        ModelWorkFront(
            ownerUuid = domainWorkFront.ownerUuid,
            centerWorld = domainWorkFront.centerWorld ?: "",
            centerX = domainWorkFront.centerX,
            centerY = domainWorkFront.centerY,
            centerZ = domainWorkFront.centerZ,
            radius = domainWorkFront.radius,
            createdAt = "", // Presentation layer can override if needed
        )

    /**
     * Converts a presentation model Order to a domain Order.
     * Used for tests and service layer that need to work with domain entities.
     */
    fun toDomain(modelOrder: ModelOrder): DomainOrder =
        DomainOrder(
            id = modelOrder.id,
            ownerUuid = modelOrder.ownerUuid,
            level = modelOrder.level,
            radius = modelOrder.radius,
            centerWorld = modelOrder.centerWorld,
            centerX = modelOrder.centerX,
            centerY = modelOrder.centerY,
            centerZ = modelOrder.centerZ,
        )

    /**
     * Converts a presentation model WorkFront to a domain WorkFront.
     * Used for tests and service layer that need to work with domain entities.
     */
    fun toDomain(modelWorkFront: ModelWorkFront): DomainWorkFront =
        DomainWorkFront(
            ownerUuid = modelWorkFront.ownerUuid,
            centerWorld = modelWorkFront.centerWorld,
            centerX = modelWorkFront.centerX,
            centerY = modelWorkFront.centerY,
            centerZ = modelWorkFront.centerZ,
            radius = modelWorkFront.radius,
        )

    /**
     * Converts a list of domain Orders to presentation model Orders.
     */
    @Suppress("MaxLineLength")
    fun toPresentationModelOrders(domainOrders: List<DomainOrder>): List<ModelOrder> = domainOrders.map(::toPresentationModel)

    /**
     * Converts a list of domain WorkFronts to presentation model WorkFronts.
     */
    fun toPresentationModelWorkFronts(domainWorkFronts: List<DomainWorkFront>): List<ModelWorkFront> =
        domainWorkFronts.map { toPresentationModel(it) }
}

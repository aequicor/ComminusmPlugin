package ru.kyamshanov.comminusm.domain.exceptions

import java.util.UUID

/**
 * Domain exception thrown when an order cannot be found.
 */
class OrderNotFoundException(
    uuid: UUID,
) : Exception("Order for $uuid not found")

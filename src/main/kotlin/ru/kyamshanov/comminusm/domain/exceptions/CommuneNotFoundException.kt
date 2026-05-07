package ru.kyamshanov.comminusm.domain.exceptions

/**
 * Domain exception thrown when a commune cannot be found.
 */
class CommuneNotFoundException(
    id: String,
) : Exception("Commune $id not found")

package ru.kyamshanov.comminusm.domain.exceptions

/**
 * Domain exception thrown when insufficient workdays balance is available.
 */
class InsufficientFundsException(
    required: Int,
    available: Int,
) : Exception("Insufficient workdays: required=$required, available=$available")

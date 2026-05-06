package ru.kyamshanov.comminusm.commune.model

/**
 * Sealed class for representing success/failure results.
 * Used throughout the commune system instead of throwing exceptions.
 */
sealed class Result<T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Failure<T>(val error: String) : Result<T>()

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun fold(onSuccess: (T) -> Unit, onFailure: (String) -> Unit) {
        when (this) {
            is Success -> onSuccess(data)
            is Failure -> onFailure(error)
        }
    }
}

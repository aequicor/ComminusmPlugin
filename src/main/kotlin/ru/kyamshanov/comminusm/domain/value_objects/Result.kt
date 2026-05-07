@file:Suppress("PackageName")

package ru.kyamshanov.comminusm.domain.value_objects

/**
 * Sealed class for representing success/failure results.
 * Used throughout the application layer instead of throwing exceptions.
 */
sealed class Result<T> {
    data class Success<T>(
        val data: T,
    ) : Result<T>()

    data class Failure<T>(
        val error: String,
    ) : Result<T>()

    fun getOrNull(): T? =
        when (this) {
            is Success -> data
            is Failure -> null
        }

    fun fold(
        onSuccess: (T) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        when (this) {
            is Success -> onSuccess(data)
            is Failure -> onFailure(error)
        }
    }

    fun <R> map(f: (T) -> R): Result<R> =
        when (this) {
            is Success -> Success(f(data))
            is Failure -> Failure(error)
        }

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)

        fun <T> failure(error: String): Result<T> = Failure(error)
    }
}

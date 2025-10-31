package xyz.block.bittycity.outie.utils

import java.time.Duration
import java.util.UUID
import io.github.resilience4j.kotlin.retry.RetryConfig
import io.github.resilience4j.kotlin.retry.executeFunction
import io.github.resilience4j.retry.RetryRegistry

/**
 * Retry a function returning Result<T> until a maximum number of times or a predicate has been fulfilled.
 *
 * @param until the predicate that indicates success. Defaults to "no exceptions encountered".
 * @param additionalTimes how many times to retry before giving up. Defaults to 4 (5 attempts in total).
 * @param delay how long to delay between attempts. Defaults to 20ms.
 */
fun <T> retry(
    until: (Result<T>) -> Boolean = { it.isSuccess },
    additionalTimes: Int = 4,
    delay: Duration = Duration.ofMillis(20L),
    f: () -> Result<T>,
): Result<T> =
    RetryRegistry.of(
        RetryConfig<Result<T>> {
            maxAttempts(additionalTimes + 1).waitDuration(delay).retryOnResult { !until(it) }.build()
        }
    )
        .retry(UUID.randomUUID().toString())
        .executeFunction(f)

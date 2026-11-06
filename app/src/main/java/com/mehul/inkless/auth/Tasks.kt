package com.mehul.inkless.auth

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Small stand-in for kotlinx-coroutines-play-services, which would be a whole dependency for one function. */
internal suspend fun <T> Task<T>.await(): T {
    if (isComplete) {
        exception?.let { throw it }
        if (isCanceled) throw kotlinx.coroutines.CancellationException("Task was cancelled")
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
    return suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            val error = task.exception
            when {
                error != null -> cont.resumeWithException(error)
                task.isCanceled -> cont.cancel()
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    cont.resume(task.result as T)
                }
            }
        }
    }
}

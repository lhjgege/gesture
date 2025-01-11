package io.legado.app.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Semaphore
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
inline fun <T> Flow<T>.onEachParallel(
    concurrency: Int,
    crossinline action: suspend (T) -> Unit,
): Flow<T> = flatMapMerge(concurrency) { value ->
    flow {
        action(value)
        emit(value)
    }
}.buffer(0)

@OptIn(ExperimentalCoroutinesApi::class)
inline fun <T> Flow<T>.onEachParallelSafe(
    concurrency: Int,
    crossinline action: suspend (T) -> Unit,
): Flow<T> = flatMapMerge(concurrency) { value ->
    flow {
        try {
            action(value)
        } catch (e: Throwable) {
            coroutineContext.ensureActive()
        }
        emit(value)
    }
}.buffer(0)


@OptIn(ExperimentalCoroutinesApi::class)
inline fun <T, R> Flow<T>.mapParallelSafe(
    concurrency: Int,
    crossinline transform: suspend (T) -> R,
): Flow<R> = flatMapMerge(concurrency) { value ->
    flow {
        try {
            emit(transform(value))
        } catch (e: Throwable) {
            coroutineContext.ensureActive()
        }
    }
}.buffer(0)

@OptIn(ExperimentalCoroutinesApi::class)
inline fun <T, R> Flow<T>.transformParallelSafe(
    concurrency: Int,
    crossinline transform: suspend FlowCollector<R>.(T) -> R,
): Flow<R> = flatMapMerge(concurrency) { value ->
    flow {
        try {
            transform(value)
        } catch (e: Throwable) {
            coroutineContext.ensureActive()
        }
    }
}.buffer(0)


inline fun <T, R> Flow<T>.mapAsync(
    concurrency: Int,
    crossinline transform: suspend (T) -> R,
): Flow<R> = if (concurrency == 1) {
    map { transform(it) }
} else {
    Semaphore(concurrency).let { semaphore ->
        channelFlow {
            collect {
                semaphore.acquire()
                send(async { transform(it) })
            }
        }.map {
            it.await()
        }.onEach { semaphore.release() }
    }.buffer(0)
}





package ifx.stdlib

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

/**
 * Helper functions for optional filtering based criteria. Typical useage:
 * Does nothing if the criterion is null.
 *
 * ```val result = collection.filterIfPresent(criteria) { criterion, item -> item.property == criterion }```
 *
 */

inline fun <reified T : Any, U : Any> Iterable<U>.filterIfPresent(
    criteria: Collection<T>?,
    predicate: (T, U) -> Boolean
): Iterable<U> = criteria?.let { filter { item -> criteria.any { predicate(it, item) } } } ?: this

inline fun <reified T : Comparable<T>, U : Any> Iterable<U>.filterIfPresent(
    criterion: T?,
    predicate: (T, U) -> Boolean
): Iterable<U> = criterion?.let { this.filter { item -> predicate(criterion, item) } } ?: this

inline fun <reified T : Any, U : Any> Flow<U>.filterIfPresent(
    criteria: Collection<T>?,
    crossinline predicate: (T, U) -> Boolean
): Flow<U> = criteria?.let { filter { item -> criteria.any { predicate(it, item) } } } ?: this

inline fun <reified T : Comparable<T>, U : Any> Flow<U>.filterIfPresent(
    criterion: T?,
    crossinline predicate: (T, U) -> Boolean
): Flow<U> = criterion?.let { this.filter { item -> predicate(criterion, item) } } ?: this


inline fun <reified C: Any, K : Any, V : Any> Map<K, V>.filterKeysIfPresent(
    criteria: Collection<C>?,
    predicate: (C, K) -> Boolean
): Map<K, V> = criteria?.let {
    filterKeys { key -> criteria.any { predicate(it, key) } }
} ?: this


inline fun <reified C: Any, K : Any, V : Any> Map<K, V>.filterValuesIfPresent(
    criteria: Collection<C>?,
    predicate: (C, V) -> Boolean
): Map<K, V> = criteria?.let {
    filterValues { key -> criteria.any { predicate(it, key) } }
} ?: this

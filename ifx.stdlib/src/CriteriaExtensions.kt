package ifx.stdlib

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

/**
 * Helper functions for optional filtering based criteria. Typical useage:
 * Does nothing if the criterion is null.
 *
 * ```val result = collection.filterIfPresent(criteria) { item, criteria -> item.property in criteria }```
 */

inline fun <reified Criterion, Value> Iterable<Value>.filterIfPresent(
    criterion: Criterion?,
    predicate: (Value, Criterion) -> Boolean
): Iterable<Value> = criterion?.let { this.filter { item -> predicate(item, criterion) } } ?: this

inline fun <reified Criterion, Value> Flow<Value>.filterIfPresent(
    criterion: Criterion?,
    crossinline predicate: (Value, Criterion) -> Boolean
): Flow<Value> = criterion?.let { this.filter { item -> predicate(item, criterion) } } ?: this

inline fun <reified Criterion, Key, Value> Map<Key, Value>.filterIfPresent(
    criterion: Criterion?,
    crossinline predicate: (Map.Entry<Key, Value>, Criterion) -> Boolean
): Map<Key, Value> = criterion?.let { this.filter { entry -> predicate(entry, criterion) } } ?: this

inline fun <reified Criterion, Value> Sequence<Value>.filterIfPresent(
    criterion: Criterion?,
    crossinline predicate: (Value, Criterion) -> Boolean
): Sequence<Value> = criterion?.let { this.filter { item -> predicate(item, criterion) } } ?: this

inline fun <reified Criterion, Key, Value> Map<Key, Value>.filterKeysIfPresent(
    criterion: Criterion?,
    crossinline predicate: (Key, Criterion) -> Boolean
): Map<Key, Value> = this.filterIfPresent(criterion) { entry, criterion -> predicate(entry.key, criterion) }

inline fun <reified Criterion, Key, Value> Map<Key, Value>.filterValuesIfPresent(
    criterion: Criterion?,
    crossinline predicate: (Value, Criterion) -> Boolean
): Map<Key, Value> = this.filterIfPresent(criterion) { entry, criterion -> predicate(entry.value, criterion) }

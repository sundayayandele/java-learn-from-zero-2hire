// Generated from practice/dsa/solutions by scripts/practice_stubs.py: implement the TODOs, keep the signatures.
package ng.itlearning.practice.design;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Week 23. Pattern: hash map plus access order.
 * <p>
 * A name-enquiry cache holding at most {@code capacity} entries. {@code get} and {@code put} are O(1); when full,
 * {@code put} of a new key evicts the least recently used entry (a {@code get} or {@code put} counts as a use).
 * You may use {@link LinkedHashMap} or build your own list + map; be ready to explain both in an interview.
 */
public final class LruCache<K, V> {

    public LruCache(int capacity) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Optional<V> get(K key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void put(K key, V value) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public int size() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}

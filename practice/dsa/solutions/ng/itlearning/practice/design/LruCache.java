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

    //>>hide
    private final LinkedHashMap<K, V> map;

    //<<
    public LruCache(int capacity) {
        //>>
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
        //<<
    }

    public Optional<V> get(K key) {
        //>>
        return Optional.ofNullable(map.get(key));
        //<<
    }

    public void put(K key, V value) {
        //>>
        map.put(key, value);
        //<<
    }

    public int size() {
        //>>
        return map.size();
        //<<
    }
}

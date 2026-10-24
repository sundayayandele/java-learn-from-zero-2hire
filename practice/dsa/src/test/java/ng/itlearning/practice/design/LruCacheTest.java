package ng.itlearning.practice.design;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LruCacheTest {

    @Test
    void evictsLeastRecentlyUsed() {
        var cache = new LruCache<String, String>(2);
        cache.put("0123456789", "ADA EZE");
        cache.put("0987654321", "BAYO OKAFOR");
        assertThat(cache.get("0123456789")).contains("ADA EZE");      // now most recent
        cache.put("1111111111", "CHI BELLO");                          // evicts 0987654321
        assertThat(cache.get("0987654321")).isEmpty();
        assertThat(cache.get("0123456789")).contains("ADA EZE");
        assertThat(cache.get("1111111111")).contains("CHI BELLO");
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void updatingAKeyCountsAsUse() {
        var cache = new LruCache<Integer, Integer>(2);
        cache.put(1, 1);
        cache.put(2, 2);
        cache.put(1, 10);
        cache.put(3, 3);
        assertThat(cache.get(2)).isEmpty();
        assertThat(cache.get(1)).contains(10);
    }

    @Test
    void rejectsZeroCapacity() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LruCache<String, String>(0));
    }
}

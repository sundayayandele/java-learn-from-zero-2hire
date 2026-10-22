package ng.itlearning.minibank;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Caches FX rates looked up from a slow provider. Used by every request thread. */
public class FxRateCache {

    private final Map<String, BigDecimal> cache = new HashMap<>();
    private final Function<String, BigDecimal> provider;

    public FxRateCache(Function<String, BigDecimal> provider) {
        this.provider = provider;
    }

    public BigDecimal rate(String pair) {
        BigDecimal cached = cache.get(pair);
        if (cached == null) {
            cached = provider.apply(pair);
            cache.put(pair, cached);
        }
        return cached;
    }

    public int size() {
        return cache.size();
    }
}

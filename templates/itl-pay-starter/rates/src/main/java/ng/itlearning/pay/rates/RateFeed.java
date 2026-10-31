package ng.itlearning.pay.rates;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * A simulated market feed: every tick, each pair moves a little, the new rate is saved, then
 * broadcast to every streaming client (topic 14.1). One feed, many subscribers: a hot publisher.
 */
@Component
public class RateFeed {

    private static final Logger log = LoggerFactory.getLogger(RateFeed.class);

    private final DatabaseClient db;
    private final Duration tick;
    // Many subscribers, no replay: a client joining now gets the next tick, never old ones, and a slow client misses
    // ticks instead of holding up the others. (onBackpressureBuffer would replay up to 256 ticks from before anyone
    // subscribed: we measured the first client receiving prices about 95 seconds old.)
    private final Sinks.Many<Rate> ticks = Sinks.many().multicast().directBestEffort();
    private Disposable running;

    RateFeed(DatabaseClient db, @Value("${rates.tick-ms}") long tickMs) {
        this.db = db;
        this.tick = Duration.ofMillis(tickMs);
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        running = Flux.interval(tick)
                .onBackpressureDrop()                              // if saving falls behind, skip ticks rather than queue them
                .concatMap(n -> current().concatMap(this::move))   // one tick at a time, in order
                .subscribe(rate -> ticks.tryEmitNext(rate),
                           error -> log.error("rate feed stopped", error));
    }

    @jakarta.annotation.PreDestroy
    void stop() {
        if (running != null) {
            running.dispose();
        }
    }

    /** Every rate tick from now on. */
    public Flux<Rate> ticks() {
        return ticks.asFlux();
    }

    public Flux<Rate> current() {
        return db.sql("SELECT pair, bid, ask, updated_at FROM rate ORDER BY pair")
                .map((row, meta) -> new Rate(row.get("pair", String.class), row.get("bid", BigDecimal.class),
                        row.get("ask", BigDecimal.class), row.get("updated_at", java.time.OffsetDateTime.class).toInstant()))
                .all();
    }

    /** A random walk of up to ±0.05%, keeping the spread, saved before it's broadcast. */
    private Flux<Rate> move(Rate r) {
        double change = 1 + ThreadLocalRandom.current().nextDouble(-0.0005, 0.0005);
        BigDecimal spread = r.ask().subtract(r.bid());
        BigDecimal bid = r.bid().multiply(BigDecimal.valueOf(change)).setScale(4, RoundingMode.HALF_EVEN);
        Rate next = new Rate(r.pair(), bid, bid.add(spread), Instant.now());
        return db.sql("UPDATE rate SET bid = :bid, ask = :ask, updated_at = now() WHERE pair = :pair")
                .bind("bid", next.bid()).bind("ask", next.ask()).bind("pair", next.pair())
                .fetch().rowsUpdated()
                .thenReturn(next)
                .flux();
    }
}

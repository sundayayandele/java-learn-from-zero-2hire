package ng.itlearning.pay.rates;

import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Current rates, and a live stream of them as server-sent events (topic 14.1). */
@RestController
@RequestMapping("/rates")
class RateController {

    private final RateFeed feed;
    private final DatabaseClient db;

    RateController(RateFeed feed, DatabaseClient db) {
        this.feed = feed;
        this.db = db;
    }

    @GetMapping
    Flux<Rate> all() {
        return feed.current();
    }

    @GetMapping("/{base}-{quote}")
    Mono<Rate> one(@PathVariable String base, @PathVariable String quote) {
        String pair = base + "-" + quote;
        return feed.current().filter(r -> r.pair().equals(pair)).next()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "No rate for " + pair)));
    }

    /**
     * text/event-stream: the browser's EventSource or curl -N keeps the connection open and receives every tick.
     * A slow client gets the latest rate, never a growing queue of stale ones.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<Rate>> stream(@RequestParam(required = false) String pair) {
        Flux<ServerSentEvent<Rate>> ticks = feed.ticks()
                .filter(r -> pair == null || r.pair().equals(pair))
                .onBackpressureLatest()
                .map(r -> ServerSentEvent.builder(r).event("rate").id(r.pair() + "@" + r.updatedAt().toEpochMilli()).build());
        Flux<ServerSentEvent<Rate>> heartbeats = Flux.interval(Duration.ofSeconds(15))   // keeps proxies from closing an idle stream
                .map(n -> ServerSentEvent.<Rate>builder().comment("keep-alive").build());
        return Flux.merge(ticks, heartbeats);
    }
}

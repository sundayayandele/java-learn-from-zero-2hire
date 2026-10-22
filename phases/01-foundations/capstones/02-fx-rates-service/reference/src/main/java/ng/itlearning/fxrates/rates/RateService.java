package ng.itlearning.fxrates.rates;

import ng.itlearning.fxrates.config.FxProperties;
import ng.itlearning.fxrates.rates.RateExceptions.CurrencyNotFound;
import ng.itlearning.fxrates.rates.RateExceptions.DateOutOfRange;
import ng.itlearning.fxrates.rates.RateExceptions.RateNotAvailable;
import ng.itlearning.fxrates.rates.RateRepository.DatedRate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reference rates from ECB data. ECB publishes units of currency per 1 EUR; any other pair is a cross rate:
 * rate(base→quote) = perEur(quote) / perEur(base), at 10 decimal places with HALF_EVEN rounding.
 */
@Service
public class RateService {

    public static final String SOURCE = "Source: ECB statistics.";
    public static final LocalDate FIRST_ECB_DATE = LocalDate.of(1999, 1, 4);
    static final int RATE_SCALE = 10;

    public record RateQuote(String base, String quote, LocalDate requestedDate, LocalDate rateDate,
                            BigDecimal rate, boolean derived, String source) { }

    public record Conversion(String from, String to, BigDecimal amount, BigDecimal converted, BigDecimal rate,
                             LocalDate requestedDate, LocalDate rateDate, String source) { }

    public record HistoryPoint(LocalDate date, BigDecimal rate) { }

    private final RateRepository rates;
    private final FxProperties properties;
    private final Clock clock;

    public RateService(RateRepository rates, FxProperties properties, Clock clock) {
        this.rates = rates;
        this.properties = properties;
        this.clock = clock;
    }

    public RateQuote rate(String base, String quote, LocalDate requested) {
        Pair p = resolve(base, quote, requested);
        BigDecimal rate = p.base.equals(p.quote) ? BigDecimal.ONE
                : p.perEurQuote.divide(p.perEurBase, RATE_SCALE, RoundingMode.HALF_EVEN);
        boolean derived = !p.base.equals("EUR") && !p.base.equals(p.quote);
        return new RateQuote(p.base, p.quote, p.requested, p.rateDate, rate, derived, SOURCE);
    }

    /** Converts in one step (amount × perEur(to) ÷ perEur(from)) and rounds once, to the target currency's minor units. */
    public Conversion convert(String from, String to, BigDecimal amount, LocalDate requested) {
        Pair p = resolve(from, to, requested);
        int digits = Currency.getInstance(p.quote).getDefaultFractionDigits();
        BigDecimal converted = amount.multiply(p.perEurQuote).divide(p.perEurBase, digits, RoundingMode.HALF_EVEN);
        BigDecimal rate = p.perEurQuote.divide(p.perEurBase, RATE_SCALE, RoundingMode.HALF_EVEN);
        return new Conversion(p.base, p.quote, amount, converted, rate, p.requested, p.rateDate, SOURCE);
    }

    public List<HistoryPoint> history(String base, String quote, LocalDate from, LocalDate to) {
        String b = currency(base);
        String q = currency(quote);
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 3660) {
            throw new IllegalArgumentException("'from' must be before 'to', and the range at most 10 years");
        }
        Map<LocalDate, BigDecimal> baseSeries = seriesOrOne(b, from, to);
        Map<LocalDate, BigDecimal> quoteSeries = seriesOrOne(q, from, to);
        if (baseSeries == null && quoteSeries == null) {
            return List.of();                                   // EUR/EUR
        }
        Map<LocalDate, BigDecimal> dates = quoteSeries != null ? quoteSeries : baseSeries;
        return dates.keySet().stream().sorted()
                .filter(d -> (baseSeries == null || baseSeries.containsKey(d)) && (quoteSeries == null || quoteSeries.containsKey(d)))
                .map(d -> new HistoryPoint(d, (quoteSeries == null ? BigDecimal.ONE : quoteSeries.get(d))
                        .divide(baseSeries == null ? BigDecimal.ONE : baseSeries.get(d), RATE_SCALE, RoundingMode.HALF_EVEN)))
                .toList();
    }

    private Map<LocalDate, BigDecimal> seriesOrOne(String currency, LocalDate from, LocalDate to) {
        if (currency.equals("EUR")) {
            return null;
        }
        return rates.series(currency, from, to).stream()
                .collect(Collectors.toMap(DatedRate::date, DatedRate::rate, (a, b) -> a));
    }

    private record Pair(String base, String quote, LocalDate requested, LocalDate rateDate,
                        BigDecimal perEurBase, BigDecimal perEurQuote) { }

    private Pair resolve(String base, String quote, LocalDate requested) {
        String b = currency(base);
        String q = currency(quote);
        LocalDate date = requested != null ? requested : LocalDate.now(clock.withZone(properties.publicationZone()));
        if (date.isBefore(FIRST_ECB_DATE)) {
            throw new DateOutOfRange("ECB reference rates start on " + FIRST_ECB_DATE);
        }
        LocalDate latest = rates.latestPublication()
                .orElseThrow(() -> new RateNotAvailable("No rates have been imported yet"));
        if (date.isAfter(latest.plusDays(properties.maxDaysAfterLatest()))) {
            throw new DateOutOfRange("The latest available rates are for " + latest);
        }
        LocalDate rateDate = rates.publicationOnOrBefore(date)
                .orElseThrow(() -> new RateNotAvailable("No rates on or before " + date));
        Function<String, BigDecimal> perEur = c -> c.equals("EUR") ? BigDecimal.ONE
                : rates.ratePerEur(c, rateDate).orElseThrow(() -> new RateNotAvailable("No ECB rate for " + c + " on " + rateDate));
        return new Pair(b, q, date, rateDate, perEur.apply(b), perEur.apply(q));
    }

    private static String currency(String code) {
        String upper = code == null ? "" : code.strip().toUpperCase(java.util.Locale.ROOT);
        try {
            return Currency.getInstance(upper).getCurrencyCode();
        } catch (IllegalArgumentException e) {
            throw new CurrencyNotFound(code);
        }
    }
}

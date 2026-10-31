package ng.itlearning.pay.rates;

import java.math.BigDecimal;
import java.time.Instant;

/** The latest bid and ask for a currency pair such as USD-NGN. */
public record Rate(String pair, BigDecimal bid, BigDecimal ask, Instant updatedAt) { }

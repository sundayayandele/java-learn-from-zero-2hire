package ng.itlearning.ledger.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "ledger")
@Validated
public record LedgerProperties(
        @NotNull ZoneId businessZone,
        @NotEmpty Set<String> supportedCurrencies,
        @NotNull @DecimalMin("0.01") BigDecimal defaultDailyLimit,
        @NotEmpty List<@Valid FeeTier> feeTiers) {

    /** Fee for amounts up to and including {@code upTo}; the last tier has no upper bound. */
    public record FeeTier(BigDecimal upTo, @NotNull @DecimalMin("0.00") BigDecimal fee) { }
}

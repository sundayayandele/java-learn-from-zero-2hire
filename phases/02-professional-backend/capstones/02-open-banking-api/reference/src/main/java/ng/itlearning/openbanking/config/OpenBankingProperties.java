package ng.itlearning.openbanking.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;

@Validated
@ConfigurationProperties("open-banking")
public record OpenBankingProperties(@Min(1) int unattendedDailyLimit, @NotNull Duration maxConsentLifetime,
                                    @Min(1) int maxHistoryMonths, @NotNull ZoneId zone) {
}

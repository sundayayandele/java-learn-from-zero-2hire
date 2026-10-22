package ng.itlearning.fxrates.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "fx")
@Validated
public record FxProperties(
        @NotNull URI ecbHistoryUrl,
        @NotNull ZoneId publicationZone,
        @Min(0) int maxDaysAfterLatest,
        @Name("import") @NotNull @Valid Import importJob) {   // 'import' is a Java keyword

    public record Import(boolean scheduleEnabled, @NotBlank String cron) { }
}

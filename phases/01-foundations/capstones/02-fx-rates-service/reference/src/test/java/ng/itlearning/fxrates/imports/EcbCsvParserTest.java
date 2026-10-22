package ng.itlearning.fxrates.imports;

import ng.itlearning.fxrates.imports.EcbCsvParser.EcbFormatException;
import ng.itlearning.fxrates.imports.EcbCsvParser.EcbRate;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EcbCsvParserTest {

    @Test
    void parsesTheRealFileFormatAndSkipsMissingValues() {
        List<EcbRate> rates = new ArrayList<>();
        int lines = EcbCsvParser.parse(sample(), rates::add);

        assertThat(lines).isEqualTo(5);
        assertThat(rates).hasSize(141);                                  // every non-N/A cell in the sample
        assertThat(rates).contains(new EcbRate(LocalDate.of(2026, 10, 20), "USD", new BigDecimal("1.1215")));
        assertThat(rates).contains(new EcbRate(LocalDate.of(1999, 1, 4), "CYP", new BigDecimal("0.58231")));
        assertThat(rates).noneMatch(r -> r.date().getYear() == 2026 && r.currency().equals("CYP"));   // N/A in 2026
    }

    @Test
    void reportsTheLineNumberOfABadRow() {
        String csv = "Date,USD,JPY,\n2026-10-20,1.1215,177.34,\nnot-a-date,1.1,170,\n";
        assertThatThrownBy(() -> EcbCsvParser.parse(stream(csv), r -> { }))
                .isInstanceOf(EcbFormatException.class)
                .hasMessageContaining("line 3");
    }

    @Test
    void rejectsAFileWithoutTheHeader() {
        assertThatThrownBy(() -> EcbCsvParser.parse(stream("hello\n"), r -> { }))
                .isInstanceOf(EcbFormatException.class);
    }

    private static InputStream sample() {
        return EcbCsvParserTest.class.getResourceAsStream("/ecb/eurofxref-hist-sample.csv");
    }

    private static InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}

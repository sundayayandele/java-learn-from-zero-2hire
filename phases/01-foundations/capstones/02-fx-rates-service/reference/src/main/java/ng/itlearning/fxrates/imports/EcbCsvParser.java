package ng.itlearning.fxrates.imports;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Parses the ECB history CSV: a header {@code Date,USD,JPY,...,} and one line per publication date.
 * Quirks handled: a trailing comma on every line, and "N/A" or empty cells for currencies that
 * didn't exist yet or were discontinued.
 */
public final class EcbCsvParser {

    public record EcbRate(LocalDate date, String currency, BigDecimal ratePerEur) { }

    /** Thrown with the line number, so a bad file can be diagnosed from the import log. */
    public static final class EcbFormatException extends RuntimeException {
        EcbFormatException(int line, String message, Throwable cause) {
            super("ECB file line " + line + ": " + message, cause);
        }
    }

    /** Streams every rate to the consumer and returns the number of data lines read. */
    public static int parse(InputStream csv, Consumer<EcbRate> sink) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || !header.startsWith("Date,")) {
                throw new EcbFormatException(1, "missing 'Date,...' header", null);
            }
            List<String> currencies = columns(header);
            int lineNo = 1;
            int dataLines = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = columns(line);
                LocalDate date;
                try {
                    date = LocalDate.parse(cells.getFirst());
                } catch (DateTimeParseException e) {
                    throw new EcbFormatException(lineNo, "bad date '" + cells.getFirst() + "'", e);
                }
                for (int i = 1; i < Math.min(cells.size(), currencies.size()); i++) {
                    String cell = cells.get(i);
                    if (cell.isEmpty() || cell.equals("N/A")) {
                        continue;
                    }
                    try {
                        sink.accept(new EcbRate(date, currencies.get(i), new BigDecimal(cell)));
                    } catch (NumberFormatException e) {
                        throw new EcbFormatException(lineNo, "bad rate '" + cell + "' for " + currencies.get(i), e);
                    }
                }
                dataLines++;
            }
            return dataLines;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> columns(String line) {
        List<String> out = new ArrayList<>();
        for (String c : line.split(",", -1)) {
            out.add(c.strip());
        }
        while (!out.isEmpty() && out.getLast().isEmpty()) {
            out.removeLast();                                  // the trailing comma
        }
        return out;
    }

    private EcbCsvParser() { }
}

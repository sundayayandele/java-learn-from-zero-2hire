package ng.itlearning.fxrates.imports;

import ng.itlearning.fxrates.config.FxProperties;
import ng.itlearning.fxrates.imports.RateImporter.ImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads the ECB history zip and imports it. Runs on a schedule and on demand from the admin API. */
@Component
public class EcbDownloader {

    private static final Logger log = LoggerFactory.getLogger(EcbDownloader.class);

    private final RestClient http;
    private final RateImporter importer;
    private final FxProperties properties;

    public EcbDownloader(RestClient.Builder builder, RateImporter importer, FxProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.http = builder.requestFactory(factory).build();
        this.importer = importer;
        this.properties = properties;
    }

    public ImportResult downloadAndImport() {
        byte[] zip = http.get().uri(properties.ecbHistoryUrl()).retrieve().body(byte[].class);
        if (zip == null || zip.length == 0) {
            throw new IllegalStateException("ECB download returned no data");
        }
        try (ZipInputStream entries = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry = entries.getNextEntry();
            if (entry == null || !entry.getName().endsWith(".csv")) {
                throw new IllegalStateException("ECB zip does not contain a CSV file");
            }
            return importer.importCsv(entries, properties.ecbHistoryUrl().toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Scheduled(cron = "${fx.import.cron}", zone = "${fx.publication-zone}")
    void scheduledImport() {
        if (!properties.importJob().scheduleEnabled()) {
            return;
        }
        try {
            ImportResult result = downloadAndImport();
            log.info("Scheduled ECB import: {} lines read, {} new rates", result.linesRead(), result.rowsInserted());
        } catch (ImportAlreadyRunningException e) {
            log.info("Skipped scheduled ECB import: another instance is importing");
        } catch (RuntimeException e) {
            log.error("Scheduled ECB import failed", e);       // recorded in import_run too
        }
    }

}

package ng.itlearning.fxrates.web;

import ng.itlearning.fxrates.imports.EcbDownloader;
import ng.itlearning.fxrates.imports.ImportRunLog;
import ng.itlearning.fxrates.imports.ImportRunLog.ImportRun;
import ng.itlearning.fxrates.imports.RateImporter;
import ng.itlearning.fxrates.imports.RateImporter.ImportResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.util.List;

/** Operational endpoints. Secure these (Module 6) before exposing the service beyond a private network. */
@RestController
@RequestMapping("/api/v1/admin/imports")
public class AdminController {

    private final EcbDownloader downloader;
    private final RateImporter importer;
    private final ImportRunLog runs;

    public AdminController(EcbDownloader downloader, RateImporter importer, ImportRunLog runs) {
        this.downloader = downloader;
        this.importer = importer;
        this.runs = runs;
    }

    /** Downloads the latest ECB history file and imports any new rates. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResult importFromEcb() {
        return downloader.downloadAndImport();
    }

    /** Imports an uploaded ECB-format CSV (for air-gapped environments and tests). */
    @PostMapping(path = "/csv", consumes = {"text/csv", "text/plain"})
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResult importCsv(@RequestBody byte[] csv) {
        return importer.importCsv(new ByteArrayInputStream(csv), "upload");
    }

    @GetMapping
    public List<ImportRun> recent() {
        return runs.recent(20);
    }
}

package ng.itlearning.openbanking.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ng.itlearning.openbanking.audit.AccessLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Validated
@RestController
public class OpsController {

    private final AccessLog log;

    public OpsController(AccessLog log) {
        this.log = log;
    }

    @GetMapping("/ops/v1/access-log")
    @PreAuthorize("hasRole('OPS')")
    Page accessLog(@RequestParam(required = false) String consentId, @RequestParam(required = false) Instant from,
                   @RequestParam(required = false) Instant to, @RequestParam(required = false) Long cursor,
                   @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        List<AccessLog.Entry> rows = log.search(consentId, from, to, cursor, limit + 1);
        boolean more = rows.size() > limit;
        List<AccessLog.Entry> page = more ? rows.subList(0, limit) : rows;
        return new Page(page, more ? page.getLast().id() : null);
    }

    public record Page(List<AccessLog.Entry> entries, Long nextCursor) {
    }
}

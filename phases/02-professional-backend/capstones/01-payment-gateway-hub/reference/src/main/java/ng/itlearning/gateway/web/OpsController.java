package ng.itlearning.gateway.web;

import ng.itlearning.gateway.provider.ProviderRouter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class OpsController {

    private final ProviderRouter router;

    public OpsController(ProviderRouter router) {
        this.router = router;
    }

    @GetMapping("/api/v1/ops/providers")
    @PreAuthorize("hasRole('OPS')")
    List<ProviderHealth> providers() {
        var rates = router.failureRates();
        return router.states().entrySet().stream()
                .map(e -> new ProviderHealth(e.getKey(), e.getValue(), rates.get(e.getKey())))
                .toList();
    }

    /** failureRate is -1 until the breaker has seen its minimum number of calls. */
    public record ProviderHealth(String name, String circuit, float failureRate) {
    }
}

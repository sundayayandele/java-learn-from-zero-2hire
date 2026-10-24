package ng.itlearning.gateway.web;

import ng.itlearning.gateway.payment.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Receives the raw bytes: the signature is computed over exactly what the provider sent, before any parsing. */
@RestController
public class WebhookController {

    private final WebhookService webhooks;

    public WebhookController(WebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping("/webhooks/{provider}")
    ResponseEntity<Void> receive(@PathVariable String provider, @RequestBody byte[] body,
                                 @RequestHeader(name = "x-provider-signature", required = false) String signature) {
        webhooks.handle(provider, body, signature);
        return ResponseEntity.ok().build();
    }
}

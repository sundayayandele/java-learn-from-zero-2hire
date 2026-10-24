package ng.itlearning.lab.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

/** Receives signed provider webhooks: header x-provider-signature = hex HMAC-SHA512 of the body with the secret. */
@RestController
public class WebhookController {

    private final PaymentController payments;
    private final JsonMapper json;
    private final String secret;

    public WebhookController(PaymentController payments, JsonMapper json, @Value("${provider.secret}") String secret) {
        this.payments = payments;
        this.json = json;
        this.secret = secret;
    }

    @PostMapping("/webhooks/provider")
    ResponseEntity<Void> receive(@RequestBody Map<String, Object> event,
                                 @RequestHeader(name = "x-provider-signature", required = false) String signature) throws Exception {
        String canonical = json.writeValueAsString(event);           // the body as we understood it
        if (signature == null || !sign(canonical).equals(signature)) {
            return ResponseEntity.status(401).build();
        }
        JsonNode data = json.valueToTree(event).path("data");
        String reference = data.path("reference").asString();
        var p = payments.payments.get(reference);
        if (p != null && "charge.success".equals(event.get("event"))) {
            payments.payments.put(reference, new PaymentController.Payment(p.reference(), p.amount(), p.authorizationUrl(), "SUCCEEDED"));
        }
        return ResponseEntity.ok().build();
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}

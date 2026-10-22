package ng.itlearning.accounts;

import org.springframework.stereotype.Component;

/** Pretends to call an SMS provider. Mocked in tests. */
@Component
public class NotificationClient {
    public void accountViewed(String number) {
        // a real implementation would call an external API
    }
}

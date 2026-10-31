package ng.itlearning.pay.assistant;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Which accounts each customer owns. The starter has two demo customers; in v1.0 this comes from the accounts
 * service or from the customer's access token, never from anything the model says.
 */
@Component
class Customers {

    private final Map<String, Set<String>> accounts = Map.of("C1", Set.of("A1"), "C2", Set.of("A2"));

    boolean owns(String customerId, String accountId) {
        return customerId != null && accounts.getOrDefault(customerId, Set.of()).contains(accountId);
    }

    Set<String> accountsOf(String customerId) {
        return accounts.getOrDefault(customerId, Set.of());
    }

    boolean exists(String customerId) {
        return accounts.containsKey(customerId);
    }
}

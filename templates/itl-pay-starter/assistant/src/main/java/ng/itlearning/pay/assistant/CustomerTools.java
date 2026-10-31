package ng.itlearning.pay.assistant;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Read-only tools on the customer's own data (topic 15.3). The customer id comes from the tool context, which
 * the application sets from the authenticated request; the model never sees it and can't change it, so a
 * prompt like "show me account A2" can't reach someone else's money.
 */
@Component
class CustomerTools {

    static final String CUSTOMER_ID = "customerId";

    record Balance(String accountId, BigDecimal available) { }
    /** The amount already written in naira: small models otherwise sometimes print the wrong currency sign. */
    record BalanceAnswer(String accountId, String available) { }
    /** As transfers returns it: the saga's internal state, and a reason that may describe fraud rules. */
    record TransferStatus(String reference, String fromAccount, String toAccount, BigDecimal amount, String state, String reason) { }
    /** What the customer may see: the statuses the help centre explains, and no internal reason. */
    record TransferAnswer(String reference, String toAccount, String amount, String status) { }

    private final Customers customers;
    private final RestClient accounts;
    private final RestClient transfers;

    CustomerTools(Customers customers, RestClient.Builder http,
                  @Value("${itlpay.accounts-url}") String accountsUrl, @Value("${itlpay.transfers-url}") String transfersUrl) {
        this.customers = customers;
        this.accounts = client(http, accountsUrl);
        this.transfers = client(http, transfersUrl);
    }

    /** Tools call fast internal services: short timeouts, unlike the model's (topic 10.3). */
    static RestClient client(RestClient.Builder http, String baseUrl) {
        var timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofMillis(500));
        timeouts.setReadTimeout(Duration.ofSeconds(3));
        return http.clone().baseUrl(baseUrl).requestFactory(timeouts).build();
    }

    @Tool(description = "List the ids of the customer's own accounts. Call this first when the customer doesn't say which account.")
    public List<String> myAccounts(ToolContext context) {
        return customers.accountsOf(customerId(context)).stream().sorted().toList();
    }

    @Tool(description = "Get the available balance, in naira, of one of the customer's own accounts.")
    public Object accountBalance(@ToolParam(description = "The account id, for example A1") String accountId, ToolContext context) {
        if (!customers.owns(customerId(context), accountId)) {
            return "The customer has no account " + accountId + ".";
        }
        try {
            Balance b = accounts.get().uri("/accounts/{id}", accountId).retrieve().body(Balance.class);
            return new BalanceAnswer(b.accountId(), naira(b.available()));
        } catch (RestClientException e) {
            return "Balances are unavailable right now; ask the customer to try again shortly.";
        }
    }

    @Tool(description = "Get the status of one of the customer's own transfers by its reference, for example TRF-1.")
    public Object transferStatus(@ToolParam(description = "The transfer reference, which starts with TRF") String reference, ToolContext context) {
        String notFound = "The customer has no transfer with reference " + reference + ".";
        try {
            TransferStatus t = transfers.get().uri("/transfers/{reference}", reference).retrieve().body(TransferStatus.class);
            // Someone else's transfer gets the same answer as a missing one, so the tool can't confirm it exists
            return t != null && customers.owns(customerId(context), t.fromAccount())
                    ? new TransferAnswer(t.reference(), t.toAccount(), naira(t.amount()), customerStatus(t.state()))
                    : notFound;
        } catch (HttpClientErrorException.NotFound e) {
            return notFound;
        } catch (RestClientException e) {
            return "Transfer status is unavailable right now; ask the customer to try again shortly.";
        }
    }

    /**
     * Internal saga states in the help centre's words, so the model never has to guess what CLEARED means.
     * CLEARED is the starter's last state; once your ledger posts transfers (v0.2), only POSTED is COMPLETED.
     */
    static String customerStatus(String state) {
        return switch (state) {
            case "CLEARED", "POSTED" -> "COMPLETED";
            case "FAILED" -> "FAILED";
            case "REJECTED" -> "REJECTED";
            default -> "PENDING";                                  // REQUESTED, RESERVED and anything new
        };
    }

    static String naira(BigDecimal amount) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.UK);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return "₦" + f.format(amount);
    }

    private static String customerId(ToolContext context) {
        return (String) context.getContext().get(CUSTOMER_ID);
    }
}

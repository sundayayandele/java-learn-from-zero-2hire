package ng.itlearning.pay.assistant;

import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Tools over public information only. The assistant uses them, and the MCP server (topic 15.3) exposes them to
 * other AI clients, such as a staff copilot or an IDE: nothing here needs to know who is asking.
 */
@Component
class PublicTools {

    record HelpExcerpt(String title, String text, double score) { }

    private final RestClient rates;
    private final VectorStore store;

    PublicTools(RestClient.Builder http, @Value("${itlpay.rates-url}") String ratesUrl, VectorStore store) {
        this.rates = CustomerTools.client(http, ratesUrl);
        this.store = store;
    }

    @Tool(description = "Get the latest buy (bid) and sell (ask) exchange rate for a currency pair: USD-NGN, GBP-NGN or EUR-NGN.")
    public Object exchangeRate(@ToolParam(description = "The pair, for example USD-NGN") String pair) {
        if (pair == null || !pair.matches("[A-Z]{3}-[A-Z]{3}")) {                      // never pass model output into a URL unchecked
            return "Ask for a pair like USD-NGN.";
        }
        try {
            return rates.get().uri("/rates/{pair}", pair).retrieve().body(Map.class);
        } catch (HttpClientErrorException.NotFound e) {
            return "ITL Pay has no rate for " + pair + ".";
        } catch (RestClientException e) {
            return "Rates are unavailable right now.";
        }
    }

    @Tool(description = "Search the ITL Pay help centre (fees, limits, transfers, disputes, FX, security). Returns the most relevant sections.")
    public List<HelpExcerpt> searchHelpDocs(@ToolParam(description = "What to look for, in plain words") String query) {
        List<Document> hits = store.similaritySearch(SearchRequest.builder().query(query).topK(3).similarityThreshold(0.5).build());
        return hits.stream()
                .map(d -> new HelpExcerpt((String) d.getMetadata().get("title"), d.getText(), d.getScore() == null ? 0 : d.getScore()))
                .toList();
    }
}

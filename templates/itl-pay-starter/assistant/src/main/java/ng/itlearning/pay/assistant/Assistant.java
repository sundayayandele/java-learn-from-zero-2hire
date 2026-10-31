package ng.itlearning.pay.assistant;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * The support assistant: guardrails, then retrieval from the help centre (15.2), then the model with read-only
 * tools (15.3), then guardrails on the way out (15.4).
 */
@Service
class Assistant {

    record Answer(String answer, List<String> sources, Integer inputTokens, Integer outputTokens) { }

    enum Category { TRANSFER_PROBLEM, ACCOUNT_SECURITY, FEES_AND_LIMITS, FX, DISPUTE, OTHER }
    enum Urgency { LOW, NORMAL, HIGH }
    /** Structured output (15.1): the model fills this record, Spring AI turns its JSON into the object. */
    record Triage(Category category, Urgency urgency, String summary) { }

    private final ChatClient support;
    private final ChatClient triage;
    private final Guardrails guardrails;
    private final MeterRegistry meters;

    Assistant(ChatClient.Builder chat, VectorStore store, CustomerTools customerTools, PublicTools publicTools,
              Guardrails guardrails, MeterRegistry meters,
              @Value("classpath:prompts/support-system.st") Resource supportSystem,
              @Value("classpath:prompts/rag-context.st") Resource ragContext,
              @Value("classpath:prompts/triage-system.st") Resource triageSystem,
              @Value("${itlpay.assistant.similarity-threshold}") double threshold) {
        var retrieval = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(store).topK(3).similarityThreshold(threshold).build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .promptTemplate(new PromptTemplate(ragContext))
                        .allowEmptyContext(true)            // no matching docs: the question may still need a tool
                        .build())
                .build();
        this.support = chat.clone()
                .defaultSystem(supportSystem)
                .defaultAdvisors(retrieval)
                .defaultTools(customerTools, publicTools)
                .build();
        this.triage = chat.clone().defaultSystem(triageSystem).build();
        this.guardrails = guardrails;
        this.meters = meters;
    }

    Answer ask(String customerId, String question) {
        ChatClientResponse response = support.prompt()
                .user(guardrails.redact(question))
                .toolContext(Map.of(CustomerTools.CUSTOMER_ID, customerId))   // for the tools, never shown to the model
                .call()
                .chatClientResponse();
        @SuppressWarnings("unchecked")
        List<Document> docs = (List<Document>) response.context().getOrDefault(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, List.of());
        Usage usage = response.chatResponse().getMetadata().getUsage();
        String answer = guardrails.cleanAnswer(response.chatResponse().getResult().getOutput().getText());
        meters.counter("itlpay.assistant.answers", "grounded", String.valueOf(!docs.isEmpty())).increment();
        return new Answer(answer,
                docs.stream().map(d -> (String) d.getMetadata().get("title")).distinct().toList(),
                usage.getPromptTokens(), usage.getCompletionTokens());
    }

    Triage triage(String message) {
        return triage.prompt().user(guardrails.redact(message)).call().entity(Triage.class);
    }
}

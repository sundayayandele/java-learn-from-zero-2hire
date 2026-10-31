package ng.itlearning.pay.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * Stand-ins for the real models, so the tests are fast, free and give the same result every time. They test
 * our code (retrieval, guardrails, tool scoping), not the model's judgement: that's the evaluation's job (15.4).
 */
final class FakeModels {

    private FakeModels() { }

    /** Words hashed into 768 buckets: texts that share words end up close together, which is all retrieval needs here. */
    static final class WordHashEmbeddingModel implements EmbeddingModel {

        final List<String> embedded = new CopyOnWriteArrayList<>();

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> out = new ArrayList<>();
            for (String text : request.getInstructions()) {
                embedded.add(text);
                out.add(new Embedding(vector(text), out.size()));
            }
            return new EmbeddingResponse(out);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        @Override
        public int dimensions() {
            return 768;
        }

        static float[] vector(String text) {
            float[] v = new float[768];
            for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
                if (word.length() < 3) {
                    continue;
                }
                String stem = word.endsWith("s") ? word.substring(0, word.length() - 1) : word;
                v[Math.floorMod(stem.hashCode(), 768)] += 1;
            }
            double norm = 0;
            for (float x : v) {
                norm += x * x;
            }
            for (int i = 0; i < v.length; i++) {
                v[i] = norm == 0 ? 0 : (float) (v[i] / Math.sqrt(norm));
            }
            return v;
        }
    }

    /**
     * Answers from a script and records every prompt it was sent. When the script asks for a tool, ChatClient
     * runs it and calls the model again with the result, exactly as with a real model.
     */
    static final class ScriptedChatModel implements ChatModel {

        final List<Prompt> prompts = new CopyOnWriteArrayList<>();
        private volatile Function<Prompt, AssistantMessage> script = p -> text("OK");

        void reply(Function<Prompt, AssistantMessage> script) {
            this.script = script;
        }

        void reset() {
            prompts.clear();
            script = p -> text("OK");
        }

        /** Options that can carry tools, as every real provider's are. */
        @Override
        public ChatOptions getOptions() {
            return ToolCallingChatOptions.builder().build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            return new ChatResponse(List.of(new Generation(script.apply(prompt))));
        }

        static AssistantMessage text(String text) {
            return AssistantMessage.builder().content(text).build();
        }

        static AssistantMessage toolCall(String name, String jsonArguments) {
            return AssistantMessage.builder()
                    .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", name, jsonArguments)))
                    .build();
        }

        /** The text of the last tool result in the conversation, or null if no tool has run yet. */
        static String lastToolResult(Prompt prompt) {
            List<Message> messages = prompt.getInstructions();
            return messages.getLast() instanceof ToolResponseMessage r ? r.getResponses().getLast().responseData() : null;
        }
    }
}

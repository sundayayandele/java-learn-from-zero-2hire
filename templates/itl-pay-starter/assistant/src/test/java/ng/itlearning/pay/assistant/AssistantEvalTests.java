package ng.itlearning.pay.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/**
 * The evaluation (topic 15.4): every question in eval-set.json against the real model, scored by rules, with
 * latency and tokens. It needs Ollama with the models pulled, so it only runs on demand:
 *
 *   ./mvnw test -Peval                          (the model in application.properties)
 *   CHAT_MODEL=qwen3:4b ./mvnw test -Peval      (another model, same questions)
 *
 * The report is written to target/eval-report.md. Rules, not another model, grade it: cheap, repeatable and
 * explainable, and good enough when the right answer contains a known fact.
 */
@Tag("eval")
@SpringBootTest(properties = "management.tracing.export.otlp.enabled=false")
@Import(AssistantEvalTests.Config.class)
class AssistantEvalTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:0.8.1-pg18-trixie").asCompatibleSubstituteFor("postgres"));
        }
    }

    record Case(String id, String kind, String customer, String question, List<String> anyOf, List<String> noneOf) { }
    record Result(Case c, boolean passed, long millis, int inputTokens, int outputTokens, String answer) { }

    static final StubServices services = new StubServices();

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        services.register(registry);
    }

    @AfterAll
    static void stopServices() {
        services.stop();
    }

    @Autowired Assistant assistant;
    @Autowired Guardrails guardrails;
    @Value("${spring.ai.ollama.chat.model}") String model;
    @Value("${eval.min-pass-rate:0.75}") double minPassRate;

    @Test
    void answersTheEvaluationSetWellEnough() throws Exception {
        Case[] cases = JsonMapper.shared().readValue(new ClassPathResource("eval-set.json").getInputStream(), Case[].class);
        assistant.ask("C1", "Hello");                                               // load the model, so the first case isn't timed with it

        List<Result> results = new ArrayList<>();
        for (Case c : cases) {
            long start = System.nanoTime();
            String customer = c.customer() == null ? "C1" : c.customer();
            Result r;
            if (guardrails.rejectReason(c.question()).isPresent()) {
                r = new Result(c, c.anyOf() == null, 0, 0, 0, "(refused by the input guardrail)");
            } else {
                Assistant.Answer a = assistant.ask(customer, c.question());
                long millis = (System.nanoTime() - start) / 1_000_000;
                r = new Result(c, passes(c, a.answer()), millis, orZero(a.inputTokens()), orZero(a.outputTokens()), a.answer());
            }
            results.add(r);
        }
        double passRate = results.stream().filter(Result::passed).count() / (double) results.size();
        Files.writeString(Path.of("target/eval-report.md"), report(results, passRate));

        assertThat(passRate).as("pass rate, see target/eval-report.md").isGreaterThanOrEqualTo(minPassRate);
    }

    /** Passes when the answer contains at least one expected phrase (if any are listed) and none of the forbidden ones. */
    static boolean passes(Case c, String answer) {
        String a = normalise(answer);
        boolean expected = c.anyOf() == null || c.anyOf().stream().anyMatch(e -> a.contains(normalise(e)));
        boolean forbidden = c.noneOf() != null && c.noneOf().stream().anyMatch(f -> a.contains(normalise(f)));
        return expected && !forbidden;
    }

    private static String normalise(String s) {
        return s.toLowerCase(Locale.ROOT).replace(",", "").replace("’", "'");
    }

    private static int orZero(Integer i) {
        return i == null ? 0 : i;
    }

    private String report(List<Result> results, double passRate) {
        long[] latencies = results.stream().mapToLong(Result::millis).filter(m -> m > 0).sorted().toArray();
        StringBuilder out = new StringBuilder("# Assistant evaluation: " + model + "\n\n");
        out.append(String.format("Passed %d of %d (%.0f%%). Latency p50 %.1f s, p95 %.1f s. Mean tokens per question: %d in, %d out.%n%n",
                results.stream().filter(Result::passed).count(), results.size(), passRate * 100,
                percentile(latencies, 50) / 1000.0, percentile(latencies, 95) / 1000.0,
                (int) results.stream().mapToInt(Result::inputTokens).average().orElse(0),
                (int) results.stream().mapToInt(Result::outputTokens).average().orElse(0)));
        out.append("| Kind | Passed |\n| --- | --- |\n");
        results.stream().map(r -> r.c().kind()).distinct().forEach(kind -> out.append(String.format("| %s | %d of %d |%n", kind,
                results.stream().filter(r -> r.c().kind().equals(kind) && r.passed()).count(),
                results.stream().filter(r -> r.c().kind().equals(kind)).count())));
        out.append("\n| Case | Pass | Seconds | Tokens in/out | Answer |\n| --- | --- | --- | --- | --- |\n");
        for (Result r : results) {
            out.append(String.format("| %s | %s | %.1f | %d/%d | %s |%n", r.c().id(), r.passed() ? "yes" : "**no**", r.millis() / 1000.0,
                    r.inputTokens(), r.outputTokens(), r.answer().replace("\n", " ").replace("|", "/")));
        }
        return out.toString();
    }

    private static long percentile(long[] sorted, int p) {
        return sorted.length == 0 ? 0 : sorted[Math.min(sorted.length - 1, (int) Math.ceil(p / 100.0 * sorted.length) - 1)];
    }
}

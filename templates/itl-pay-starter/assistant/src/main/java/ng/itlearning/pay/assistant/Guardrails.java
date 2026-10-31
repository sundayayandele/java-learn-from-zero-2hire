package ng.itlearning.pay.assistant;

import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Checks around the model (topic 15.4). Input: reject what we won't send, and redact personal data we don't need.
 * Output: mask anything that looks like an identifier, and drop reasoning a model may leak.
 * Pattern checks are a first line only: the real protection is that the tools are read-only and scoped.
 */
@Component
class Guardrails {

    static final int MAX_QUESTION_CHARS = 1000;

    private static final Pattern INJECTION = Pattern.compile(
            "(?i)(ignore|disregard|forget)\\s+(all\\s+|any\\s+|the\\s+|your\\s+)?(previous|prior|above|earlier)?\\s*(instructions|rules|prompt)"
            + "|system\\s+prompt|developer\\s+mode|you\\s+are\\s+now\\b|reveal\\s+your\\s+(prompt|instructions)");
    private static final Pattern SECRET = Pattern.compile("(?i)\\b(pin|password|otp|passcode)\\b\\D{0,15}\\d{4,8}");
    private static final Pattern CARD = Pattern.compile("\\b\\d(?:[ -]?\\d){12,18}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+234|0)[789][01]\\d{8}(?!\\d)");
    private static final Pattern NATIONAL_ID = Pattern.compile("(?<!\\d)\\d{11}(?!\\d)");             // BVN and NIN are 11 digits
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<!\\d)\\d{10,}(?!\\d)");
    // Reasoning some models print before the answer; thinking-only models can omit the opening tag (topic 15.4)
    private static final Pattern THINKING = Pattern.compile("(?s)^.*</think>|<think>.*?(</think>|$)");

    /** Why a question is refused before it reaches the model, or empty when it may go ahead. */
    Optional<String> rejectReason(String question) {
        if (question == null || question.isBlank()) {
            return Optional.of("Ask a question.");
        }
        if (question.length() > MAX_QUESTION_CHARS) {
            return Optional.of("Questions are limited to " + MAX_QUESTION_CHARS + " characters.");
        }
        if (INJECTION.matcher(question).find()) {
            return Optional.of("This assistant only answers questions about ITL Pay.");
        }
        return Optional.empty();
    }

    /** Removes personal data the assistant never needs, before it leaves our network. */
    String redact(String text) {
        String out = SECRET.matcher(text).replaceAll("[SECRET]");
        out = CARD.matcher(out).replaceAll("[CARD]");
        out = PHONE.matcher(out).replaceAll("[PHONE]");
        out = NATIONAL_ID.matcher(out).replaceAll("[ID]");
        return EMAIL.matcher(out).replaceAll("[EMAIL]");
    }

    /** What the customer may see of a model's answer. */
    String cleanAnswer(String answer) {
        String out = THINKING.matcher(answer == null ? "" : answer).replaceAll("").strip();
        return LONG_NUMBER.matcher(out).replaceAll("[NUMBER]");
    }
}

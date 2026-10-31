package ng.itlearning.pay.assistant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/assistant")
class AssistantController {

    record Question(String question) { }
    record Message(String message) { }

    private final Assistant assistant;
    private final Guardrails guardrails;
    private final Customers customers;

    AssistantController(Assistant assistant, Guardrails guardrails, Customers customers) {
        this.assistant = assistant;
        this.guardrails = guardrails;
        this.customers = customers;
    }

    /**
     * X-Customer-Id stands in for the authenticated customer. In v1.0 the gateway sets it from the verified
     * access token and removes any value the client sent.
     */
    @PostMapping("/ask")
    ResponseEntity<?> ask(@RequestHeader("X-Customer-Id") String customerId, @RequestBody Question q) {
        if (!customers.exists(customerId)) {
            return problem(HttpStatus.UNAUTHORIZED, "Unknown customer.");
        }
        var reject = guardrails.rejectReason(q.question());
        if (reject.isPresent()) {
            return problem(HttpStatus.UNPROCESSABLE_CONTENT, reject.get());   // refused here: the model never sees it
        }
        return ResponseEntity.ok(assistant.ask(customerId, q.question()));
    }

    /** Sorts a support message into a category and urgency, for the support team's queue. */
    @PostMapping("/triage")
    ResponseEntity<?> triage(@RequestBody Message m) {
        var reject = guardrails.rejectReason(m.message());
        if (reject.isPresent()) {
            return problem(HttpStatus.UNPROCESSABLE_CONTENT, reject.get());
        }
        return ResponseEntity.ok(assistant.triage(m.message()));
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}

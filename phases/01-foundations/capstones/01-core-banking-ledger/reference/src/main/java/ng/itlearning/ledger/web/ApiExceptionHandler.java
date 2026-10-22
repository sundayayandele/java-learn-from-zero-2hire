package ng.itlearning.ledger.web;

import ng.itlearning.ledger.account.AccountNotActiveException;
import ng.itlearning.ledger.account.AccountNotFoundException;
import ng.itlearning.ledger.account.InsufficientFundsException;
import ng.itlearning.ledger.journal.AlreadyReversedException;
import ng.itlearning.ledger.journal.DuplicateReferenceException;
import ng.itlearning.ledger.journal.JournalNotFoundException;
import ng.itlearning.ledger.journal.LedgerRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Map;

/** Every error is an RFC 9457 Problem Detail with a stable type URI (topic 4.2). */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    public static final URI PROBLEM_BASE = URI.create("https://problems.itlearning.ng/ledger/");
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ex.getBody();
        problem.setType(PROBLEM_BASE.resolve("validation-failed"));
        problem.setTitle("Validation failed");
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
                .toList());
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail accountNotFound(AccountNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "account-not-found", "Account not found", e.getMessage());
    }

    @ExceptionHandler(JournalNotFoundException.class)
    ProblemDetail journalNotFound(JournalNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "transfer-not-found", "Transfer not found", e.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail insufficientFunds(InsufficientFundsException e) {
        ProblemDetail p = problem(HttpStatus.UNPROCESSABLE_CONTENT, "insufficient-funds", "Insufficient funds", e.getMessage());
        p.setProperty("requested", e.requested().toPlainString());
        return p;
    }

    @ExceptionHandler(AccountNotActiveException.class)
    ProblemDetail notActive(AccountNotActiveException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "account-not-active", "Account not active", e.getMessage());
    }

    @ExceptionHandler(LedgerRuleException.class)
    ProblemDetail rule(LedgerRuleException e) {
        String type = switch (e) {
            case LedgerRuleException.DailyLimitExceeded x -> "daily-limit-exceeded";
            case LedgerRuleException.SameAccount x -> "same-account";
            case LedgerRuleException.CurrencyMismatch x -> "currency-mismatch";
            case LedgerRuleException.UnsupportedCurrency x -> "unsupported-currency";
        };
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, type, "Ledger rule violated", e.getMessage());
    }

    @ExceptionHandler(DuplicateReferenceException.class)
    ProblemDetail duplicate(DuplicateReferenceException e) {
        return problem(HttpStatus.CONFLICT, "duplicate-reference", "Duplicate reference", e.getMessage());
    }

    @ExceptionHandler(AlreadyReversedException.class)
    ProblemDetail alreadyReversed(AlreadyReversedException e) {
        return problem(HttpStatus.CONFLICT, "already-reversed", "Already reversed", e.getMessage());
    }

    /** A database constraint caught something the service didn't, e.g. two new requests racing on one reference. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException e) {
        log.warn("Constraint violation: {}", e.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "conflict", "Conflicting request", "The request conflicts with existing data. Retry it.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Bad request", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unhandled error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error", "An unexpected error occurred.");
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setType(PROBLEM_BASE.resolve(type));
        p.setTitle(title);
        return p;
    }
}

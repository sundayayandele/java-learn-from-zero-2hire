package ng.itlearning.fxrates.web;

import ng.itlearning.fxrates.imports.EcbCsvParser.EcbFormatException;
import ng.itlearning.fxrates.imports.ImportAlreadyRunningException;
import ng.itlearning.fxrates.rates.RateExceptions.CurrencyNotFound;
import ng.itlearning.fxrates.rates.RateExceptions.DateOutOfRange;
import ng.itlearning.fxrates.rates.RateExceptions.RateNotAvailable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    public static final URI PROBLEM_BASE = URI.create("https://problems.itlearning.ng/fx/");
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(CurrencyNotFound.class)
    ProblemDetail currencyNotFound(CurrencyNotFound e) {
        return problem(HttpStatus.NOT_FOUND, "currency-not-found", "Unknown currency", e.getMessage());
    }

    @ExceptionHandler(RateNotAvailable.class)
    ProblemDetail rateNotAvailable(RateNotAvailable e) {
        return problem(HttpStatus.NOT_FOUND, "rate-not-available", "Rate not available", e.getMessage());
    }

    @ExceptionHandler(DateOutOfRange.class)
    ProblemDetail dateOutOfRange(DateOutOfRange e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "date-out-of-range", "Date out of range", e.getMessage());
    }

    @ExceptionHandler(ImportAlreadyRunningException.class)
    ProblemDetail importRunning(ImportAlreadyRunningException e) {
        return problem(HttpStatus.CONFLICT, "import-running", "Import already running", e.getMessage());
    }

    @ExceptionHandler(EcbFormatException.class)
    ProblemDetail badFile(EcbFormatException e) {
        return problem(HttpStatus.BAD_REQUEST, "bad-ecb-file", "Invalid ECB file", e.getMessage());
    }

    @ExceptionHandler(RestClientException.class)
    ProblemDetail upstream(RestClientException e) {
        log.warn("ECB download failed: {}", e.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "upstream-unavailable", "ECB unavailable", "Could not download the ECB file. Try again later.");
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

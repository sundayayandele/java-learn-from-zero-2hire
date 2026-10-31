package ng.itlearning.pay.transfers;

import java.math.BigDecimal;
import java.util.Map;
import ng.itlearning.pay.transfers.Transfers.State;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Step 1 of the transfer saga: record the request and reserve the funds. The rest happens through events. */
@RestController
@RequestMapping("/transfers")
class TransferController {

    record TransferRequest(String reference, String from, String to, BigDecimal amount) { }

    private final Transfers transfers;
    private final AccountsClient accounts;

    TransferController(Transfers transfers, AccountsClient accounts) {
        this.transfers = transfers;
        this.accounts = accounts;
    }

    @PostMapping
    ResponseEntity<?> transfer(@RequestBody TransferRequest req) {
        var t = transfers.requestOrGet(req.reference(), req.from(), req.to(), req.amount());
        if (t.state() == State.REQUESTED) {                 // new, or an earlier attempt found accounts down
            try {
                accounts.reserve(t.fromAccount(), t.reference(), t.amount());
            } catch (AccountsClient.Rejected e) {
                transfers.move(t, State.REQUESTED, State.REJECTED, "TransferRejected", e.getMessage());
                return ResponseEntity.unprocessableContent().body(e.problem);
            } catch (AccountsClient.Unavailable e) {
                ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                        "Accounts is unavailable; the transfer was not started. Try again shortly.");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(pd);
            }
            transfers.move(t, State.REQUESTED, State.RESERVED, "TransferReserved", null);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("reference", t.reference(), "status", "PENDING"));
    }

    @GetMapping("/{reference}")
    ResponseEntity<?> get(@PathVariable String reference) {
        return ResponseEntity.of(transfers.status(reference));
    }
}

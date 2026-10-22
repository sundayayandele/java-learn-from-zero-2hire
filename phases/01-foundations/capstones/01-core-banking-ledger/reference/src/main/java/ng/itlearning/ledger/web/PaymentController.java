package ng.itlearning.ledger.web;

import jakarta.validation.Valid;
import ng.itlearning.ledger.journal.LedgerService;
import ng.itlearning.ledger.journal.LedgerService.DepositCommand;
import ng.itlearning.ledger.journal.LedgerService.Receipt;
import ng.itlearning.ledger.journal.LedgerService.TransferCommand;
import ng.itlearning.ledger.web.Dtos.DepositRequest;
import ng.itlearning.ledger.web.Dtos.TransferRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final LedgerService ledger;

    public PaymentController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @PostMapping("/deposits")
    public ResponseEntity<Receipt> deposit(@Valid @RequestBody DepositRequest r) {
        return respond("/api/v1/deposits/", ledger.deposit(
                new DepositCommand(r.accountNumber(), r.amount(), r.reference(), r.narration())));
    }

    @PostMapping("/transfers")
    public ResponseEntity<Receipt> transfer(@Valid @RequestBody TransferRequest r) {
        return respond("/api/v1/transfers/", ledger.transfer(
                new TransferCommand(r.fromAccount(), r.toAccount(), r.amount(), r.reference(), r.narration())));
    }

    @PostMapping("/transfers/{reference}/reversal")
    public ResponseEntity<Receipt> reverse(@PathVariable String reference) {
        return respond("/api/v1/transfers/", ledger.reverse(reference));
    }

    /** 201 for a new posting; 200 when an idempotent retry returns the original result. */
    private static ResponseEntity<Receipt> respond(String base, Receipt receipt) {
        return receipt.replayed()
                ? ResponseEntity.ok(receipt)
                : ResponseEntity.created(URI.create(base + receipt.reference())).body(receipt);
    }
}

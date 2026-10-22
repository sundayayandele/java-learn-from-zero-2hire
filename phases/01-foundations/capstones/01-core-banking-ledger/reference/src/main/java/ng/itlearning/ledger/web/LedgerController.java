package ng.itlearning.ledger.web;

import ng.itlearning.ledger.statement.LedgerQueries;
import ng.itlearning.ledger.statement.LedgerQueries.BalanceDifference;
import ng.itlearning.ledger.statement.LedgerQueries.TrialBalanceLine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    public record TrialBalance(List<TrialBalanceLine> accounts, BigDecimal totalDebits, BigDecimal totalCredits, boolean balanced) { }

    public record Consistency(boolean consistent, List<BalanceDifference> differences) { }

    private final LedgerQueries queries;

    public LedgerController(LedgerQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/trial-balance")
    public TrialBalance trialBalance() {
        List<TrialBalanceLine> lines = queries.trialBalance();
        BigDecimal debits = lines.stream().map(TrialBalanceLine::debits).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = lines.stream().map(TrialBalanceLine::credits).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TrialBalance(lines, debits, credits, debits.compareTo(credits) == 0);
    }

    @GetMapping("/consistency")
    public Consistency consistency() {
        List<BalanceDifference> differences = queries.balanceDifferences();
        return new Consistency(differences.isEmpty(), differences);
    }
}

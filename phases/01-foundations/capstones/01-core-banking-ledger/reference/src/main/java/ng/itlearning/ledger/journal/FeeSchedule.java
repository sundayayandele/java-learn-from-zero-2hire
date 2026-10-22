package ng.itlearning.ledger.journal;

import ng.itlearning.ledger.config.LedgerProperties;
import ng.itlearning.ledger.config.LedgerProperties.FeeTier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** Tiered transfer fees. Tiers are checked in ascending order of their upper bound. */
@Component
public class FeeSchedule {

    private final List<FeeTier> tiers;

    @Autowired   // two constructors: tell Spring which one to use (the other is for unit tests)
    public FeeSchedule(LedgerProperties properties) {
        this(properties.feeTiers());
    }

    FeeSchedule(List<FeeTier> tiers) {
        long unbounded = tiers.stream().filter(t -> t.upTo() == null).count();
        if (unbounded != 1) {
            throw new IllegalArgumentException("Exactly one fee tier must have no upper bound");
        }
        this.tiers = tiers.stream()
                .sorted(Comparator.comparing(FeeTier::upTo, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public BigDecimal feeFor(BigDecimal amount) {
        return tiers.stream()
                .filter(t -> t.upTo() == null || amount.compareTo(t.upTo()) <= 0)
                .findFirst()
                .orElseThrow()
                .fee();
    }
}

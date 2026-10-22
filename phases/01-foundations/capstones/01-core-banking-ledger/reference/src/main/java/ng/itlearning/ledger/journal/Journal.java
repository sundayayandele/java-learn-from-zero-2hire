package ng.itlearning.ledger.journal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import ng.itlearning.ledger.account.Account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One business event, made of balanced postings. Never updated after it is written. */
@Entity
@Table(name = "journal")
public class Journal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private JournalType type;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(length = 140)
    private String narration;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_journal_id")
    private Journal reverses;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "journal", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<Posting> postings = new ArrayList<>();

    protected Journal() { }

    public Journal(String reference, JournalType type, String requestHash, String narration, Journal reverses, Instant createdAt) {
        this.reference = reference;
        this.type = type;
        this.requestHash = requestHash;
        this.narration = narration;
        this.reverses = reverses;
        this.createdAt = createdAt;
    }

    /** Adds a posting and applies it to the account's stored balance in the same transaction. */
    public void post(Account account, Direction direction, BigDecimal amount) {
        account.apply(direction, amount);
        postings.add(new Posting(this, account, direction, amount, account.getCurrency(), createdAt));
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public JournalType getType() { return type; }
    public String getRequestHash() { return requestHash; }
    public String getNarration() { return narration; }
    public Journal getReverses() { return reverses; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Posting> getPostings() { return List.copyOf(postings); }

    @Override
    public String toString() { return "Journal[" + reference + ", " + type + "]"; }
}

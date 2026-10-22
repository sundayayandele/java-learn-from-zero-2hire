package ng.itlearning.accounts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 10) private String number;
    @Column(name = "holder_name", nullable = false, length = 200) private String holderName;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal balance;
    @Column(name = "opened_on", nullable = false) private LocalDate openedOn;

    protected Account() { }

    public String getNumber() { return number; }
    public String getHolderName() { return holderName; }
    public BigDecimal getBalance() { return balance; }
    public LocalDate getOpenedOn() { return openedOn; }
}

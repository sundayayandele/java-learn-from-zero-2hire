package ng.itlearning.ledger.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByNumber(String number);

    /** Locks the rows in id order, so concurrent transfers between the same accounts can't deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.number in :numbers order by a.id")
    List<Account> lockByNumbers(@Param("numbers") Collection<String> numbers);

    @Query(value = "select lpad(nextval('account_number_seq')::text, 10, '0')", nativeQuery = true)
    String nextAccountNumber();
}

package ng.itlearning.ledger.journal;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JournalRepository extends JpaRepository<Journal, Long> {

    @EntityGraph(attributePaths = {"postings", "postings.account"})
    Optional<Journal> findByReference(String reference);

    boolean existsByReverses(Journal original);
}

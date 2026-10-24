package ng.itlearning.openbanking.consent;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsentTest {

    static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    static Consent consent(ConsentStatus status, Instant expires) {
        return new Consent("aac-1", "tpp", 1L, EnumSet.of(Permission.ReadAccountsBasic, Permission.ReadBalances), status,
                expires, NOW.minusSeconds(86_400), null, NOW.minusSeconds(60));
    }

    @Test
    void authorisedConsentExpiresAtItsExpiryTime() {
        Consent c = consent(ConsentStatus.Authorised, NOW.plusSeconds(60));
        assertThat(c.statusAt(NOW)).isEqualTo(ConsentStatus.Authorised);
        assertThat(c.statusAt(NOW.plusSeconds(60))).isEqualTo(ConsentStatus.Expired);
        assertThat(consent(ConsentStatus.Revoked, NOW.minusSeconds(1)).statusAt(NOW)).isEqualTo(ConsentStatus.Revoked);
    }

    @Test
    void onlyAwaitingConsentsCanBeAuthorisedOrRejected() {
        assertThat(consent(ConsentStatus.AwaitingAuthorisation, NOW.plusSeconds(60)).authorise(NOW)).isEqualTo(ConsentStatus.Authorised);
        assertThat(consent(ConsentStatus.AwaitingAuthorisation, NOW.plusSeconds(60)).reject(NOW)).isEqualTo(ConsentStatus.Rejected);
        assertThatThrownBy(() -> consent(ConsentStatus.Authorised, NOW.plusSeconds(60)).authorise(NOW))
                .isInstanceOf(ObException.class).hasMessageContaining("can't become Authorised");
        assertThatThrownBy(() -> consent(ConsentStatus.AwaitingAuthorisation, NOW).authorise(NOW))
                .as("expired before approval").isInstanceOf(ObException.class);
    }

    @Test
    void revokeWorksForLiveConsentsOnly() {
        assertThat(consent(ConsentStatus.Authorised, NOW.plusSeconds(60)).revoke(NOW)).isEqualTo(ConsentStatus.Revoked);
        assertThat(consent(ConsentStatus.AwaitingAuthorisation, NOW.plusSeconds(60)).revoke(NOW)).isEqualTo(ConsentStatus.Revoked);
        assertThatThrownBy(() -> consent(ConsentStatus.Rejected, NOW.plusSeconds(60)).revoke(NOW)).isInstanceOf(ObException.class);
    }

    @Test
    void permissionsAreCheckedByAnyOf() {
        Consent c = consent(ConsentStatus.Authorised, NOW.plusSeconds(60));
        assertThat(c.grantsAny(Set.of(Permission.ReadBalances))).isTrue();
        assertThat(c.grantsAny(EnumSet.of(Permission.ReadTransactionsBasic, Permission.ReadTransactionsDetail))).isFalse();
        assertThat(Consent.parse("ReadBalances, ReadAccountsDetail,")).containsExactlyInAnyOrder(Permission.ReadBalances, Permission.ReadAccountsDetail);
    }
}

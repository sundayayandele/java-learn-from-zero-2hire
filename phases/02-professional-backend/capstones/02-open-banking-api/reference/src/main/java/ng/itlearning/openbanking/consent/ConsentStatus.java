package ng.itlearning.openbanking.consent;

/** Stored statuses. "Expired" is derived from the expiry time, never stored, so it can't be forgotten. */
public enum ConsentStatus {
    AwaitingAuthorisation, Authorised, Rejected, Revoked, Expired
}

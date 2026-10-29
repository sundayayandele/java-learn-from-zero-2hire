package ng.itlearning.pay.accounts.account;

import java.math.BigDecimal;

public record FundsReserved(String accountId, String reference, BigDecimal amount) { }

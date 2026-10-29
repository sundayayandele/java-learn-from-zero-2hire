package ng.itlearning.pay.accounts.notification;

import ng.itlearning.pay.accounts.account.FundsReserved;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class ReservationNotifier {
    private static final Logger log = LoggerFactory.getLogger(ReservationNotifier.class);

    @EventListener
    void on(FundsReserved event) {
        log.info("notify: {} reserved on {} for {}", event.amount(), event.accountId(), event.reference());
    }
}

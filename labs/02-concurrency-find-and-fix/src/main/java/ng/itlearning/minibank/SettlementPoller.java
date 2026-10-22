package ng.itlearning.minibank;

import java.util.concurrent.atomic.AtomicInteger;

/** Polls for settlement files until asked to stop. */
public class SettlementPoller implements Runnable {

    private boolean stopRequested = false;
    private final AtomicInteger polls = new AtomicInteger();

    @Override
    public void run() {
        while (!stopRequested) {
            polls.incrementAndGet();
        }
    }

    public void requestStop() {
        stopRequested = true;
    }

    public int polls() {
        return polls.get();
    }
}

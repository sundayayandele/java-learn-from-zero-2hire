package ng.itlearning.lab.flaky;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Sends notifications in the background so transfers aren't slowed down. */
public final class Notifier {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<String> sent = new CopyOnWriteArrayList<>();

    public void sendAsync(String message) {
        executor.submit(() -> {
            simulateNetwork();
            sent.add(message);
        });
    }

    public List<String> sent() {
        return sent;
    }

    private static void simulateNetwork() {
        try {
            Thread.sleep(Long.getLong("lab.slowness.ms", 0) + 20);    // slower on busy CI runners
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

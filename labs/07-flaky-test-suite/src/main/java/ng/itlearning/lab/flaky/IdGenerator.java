package ng.itlearning.lab.flaky;

/** Generates transfer references. */
public final class IdGenerator {

    private static int counter = 1;

    private IdGenerator() {
    }

    public static synchronized String next() {
        return String.format("TRF-%06d", counter++);
    }
}

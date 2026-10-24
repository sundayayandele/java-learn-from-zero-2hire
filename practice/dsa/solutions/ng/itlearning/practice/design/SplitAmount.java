package ng.itlearning.practice.design;

/**
 * Week 23. Pattern: exact money allocation.
 * <p>
 * Split {@code totalKobo} (non-negative) into {@code parts} amounts that differ by at most 1 kobo and add up
 * exactly to the total; earlier parts get the extra kobo. ₦100.00 in 3 parts is [3334, 3333, 3333].
 * Throw {@link IllegalArgumentException} if parts is less than 1 or the total is negative.
 */
public final class SplitAmount {

    private SplitAmount() {
    }

    public static long[] split(long totalKobo, int parts) {
        //>>
        if (parts < 1 || totalKobo < 0) {
            throw new IllegalArgumentException("parts must be positive and total non-negative");
        }
        long base = totalKobo / parts;
        long remainder = totalKobo % parts;
        long[] out = new long[parts];
        for (int i = 0; i < parts; i++) {
            out[i] = base + (i < remainder ? 1 : 0);
        }
        return out;
        //<<
    }
}

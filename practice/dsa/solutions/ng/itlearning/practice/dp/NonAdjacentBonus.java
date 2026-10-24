package ng.itlearning.practice.dp;

/**
 * Week 21. Pattern: dynamic programming with two states.
 * <p>
 * A promotion pays bonuses on chosen days, but never on two consecutive days. Given the bonus available each
 * day (non-negative), return the largest total you can collect. O(n) time, O(1) space.
 */
public final class NonAdjacentBonus {

    private NonAdjacentBonus() {
    }

    public static long best(long[] bonus) {
        //>>
        long take = 0;
        long skip = 0;
        for (long b : bonus) {
            long newTake = skip + b;
            skip = Math.max(skip, take);
            take = newTake;
        }
        return Math.max(take, skip);
        //<<
    }
}

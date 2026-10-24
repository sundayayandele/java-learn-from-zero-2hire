package ng.itlearning.practice.windows;

/**
 * Week 7. Pattern: slow and fast pointers, in place.
 * <p>
 * {@code ids} is sorted ascending and may contain repeats. Move the distinct values to the front, in order,
 * without allocating another array, and return how many distinct values there are.
 */
public final class DistinctSortedInPlace {

    private DistinctSortedInPlace() {
    }

    public static int compact(int[] ids) {
        //>>
        if (ids.length == 0) {
            return 0;
        }
        int write = 1;
        for (int read = 1; read < ids.length; read++) {
            if (ids[read] != ids[write - 1]) {
                ids[write++] = ids[read];
            }
        }
        return write;
        //<<
    }
}

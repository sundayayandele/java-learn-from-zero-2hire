package ng.itlearning.practice.stacks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;

/**
 * Week 11. Pattern: auxiliary stack.
 * <p>
 * A stack of balances (kobo) for an undo feature that also reports the minimum balance currently on the stack.
 * Every operation must be O(1). {@code pop}, {@code peek} and {@code min} on an empty stack throw
 * {@link NoSuchElementException}.
 */
public final class MinBalanceStack {

    //>>hide
    private final Deque<Long> values = new ArrayDeque<>();
    private final Deque<Long> mins = new ArrayDeque<>();

    //<<
    public void push(long balance) {
        //>>
        values.push(balance);
        mins.push(mins.isEmpty() ? balance : Math.min(balance, mins.peek()));
        //<<
    }

    public long pop() {
        //>>
        if (values.isEmpty()) {
            throw new NoSuchElementException("empty");
        }
        mins.pop();
        return values.pop();
        //<<
    }

    public long peek() {
        //>>
        if (values.isEmpty()) {
            throw new NoSuchElementException("empty");
        }
        return values.peek();
        //<<
    }

    public long min() {
        //>>
        if (mins.isEmpty()) {
            throw new NoSuchElementException("empty");
        }
        return mins.peek();
        //<<
    }
}

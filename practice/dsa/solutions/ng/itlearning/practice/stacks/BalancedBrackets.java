package ng.itlearning.practice.stacks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * Week 10. Pattern: stack.
 * <p>
 * A rules engine accepts expressions such as {@code (amount > [limit]) && {channel}}. Return whether every
 * (, [ and { is closed by the matching bracket in the right order; other characters are ignored.
 * Use {@link Deque} (ArrayDeque), not the legacy Stack class.
 */
public final class BalancedBrackets {

    //>>hide
    private static final Map<Character, Character> PAIRS = Map.of(')', '(', ']', '[', '}', '{');

    //<<
    private BalancedBrackets() {
    }

    public static boolean isBalanced(String expression) {
        //>>
        Deque<Character> stack = new ArrayDeque<>();
        for (char c : expression.toCharArray()) {
            if (c == '(' || c == '[' || c == '{') {
                stack.push(c);
            } else if (PAIRS.containsKey(c)) {
                if (stack.isEmpty() || stack.pop() != PAIRS.get(c)) {
                    return false;
                }
            }
        }
        return stack.isEmpty();
        //<<
    }
}

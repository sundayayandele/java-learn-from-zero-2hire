package ng.itlearning.practice.stacks;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinBalanceStackTest {

    @Test
    void tracksTheMinimum() {
        var s = new MinBalanceStack();
        s.push(500);
        s.push(200);
        s.push(800);
        s.push(200);
        assertThat(s.min()).isEqualTo(200);
        assertThat(s.pop()).isEqualTo(200);
        assertThat(s.min()).isEqualTo(200);
        s.pop();
        s.pop();
        assertThat(s.min()).isEqualTo(500);
        assertThat(s.peek()).isEqualTo(500);
    }

    @Test
    void emptyStackThrows() {
        var s = new MinBalanceStack();
        assertThatThrownBy(s::pop).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(s::min).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(s::peek).isInstanceOf(NoSuchElementException.class);
    }
}

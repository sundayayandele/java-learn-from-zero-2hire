package ng.itlearning.practice.windows;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LongestUniqueRunTest {

    @Test
    void examples() {
        assertThat(LongestUniqueRun.length("abcabcbb")).isEqualTo(3);
        assertThat(LongestUniqueRun.length("bbbbb")).isEqualTo(1);
        assertThat(LongestUniqueRun.length("pwwkew")).isEqualTo(3);
        assertThat(LongestUniqueRun.length("abba")).isEqualTo(2);
        assertThat(LongestUniqueRun.length("")).isZero();
    }
}

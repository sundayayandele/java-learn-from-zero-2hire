package ng.itlearning.practice.windows;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReferencePalindromeTest {

    @Test
    void palindromes() {
        assertThat(ReferencePalindrome.isPalindrome("A man, a plan, a canal: Panama")).isTrue();
        assertThat(ReferencePalindrome.isPalindrome("12-3-21")).isTrue();
        assertThat(ReferencePalindrome.isPalindrome("")).isTrue();
        assertThat(ReferencePalindrome.isPalindrome(",.!")).isTrue();
    }

    @Test
    void notPalindromes() {
        assertThat(ReferencePalindrome.isPalindrome("TRF-2026")).isFalse();
        assertThat(ReferencePalindrome.isPalindrome("ab")).isFalse();
    }
}

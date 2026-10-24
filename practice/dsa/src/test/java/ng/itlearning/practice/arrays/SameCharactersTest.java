package ng.itlearning.practice.arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SameCharactersTest {

    @Test
    void anagramsIgnoringCaseSpacesAndHyphens() {
        assertThat(SameCharacters.isAnagram("Ade-Ola", "Aloe Ad")).isTrue();
        assertThat(SameCharacters.isAnagram("listen", "SILENT")).isTrue();
    }

    @Test
    void notAnagrams() {
        assertThat(SameCharacters.isAnagram("Emeka", "Ekame ")).isTrue();
        assertThat(SameCharacters.isAnagram("Ngozi", "Ngoze")).isFalse();
        assertThat(SameCharacters.isAnagram("ab", "abc")).isFalse();
    }
}

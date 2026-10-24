package ng.itlearning.practice.design;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AccountKeyTest {

    @Test
    void equalKeysAreEqualAndHashEqually() {
        var a = new AccountKey("058", "0123456789");
        var b = new AccountKey(" 058", "0123456789 ");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(new AccountKey("011", "0123456789"));
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("058/0123456789");
    }

    @Test
    void worksInHashCollections() {
        Set<AccountKey> set = new HashSet<>();
        set.add(new AccountKey("058", "0123456789"));
        set.add(new AccountKey("058", "0123456789"));
        assertThat(set).hasSize(1);
        Map<AccountKey, String> names = new HashMap<>();
        names.put(new AccountKey("058", "0123456789"), "ADA EZE");
        assertThat(names.get(new AccountKey("058 ", "0123456789"))).isEqualTo("ADA EZE");
    }
}

package ng.itlearning.pay.accounts;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    @Test
    void modulesOnlyUseEachOthersPublicApi() {
        ApplicationModules.of(AccountsApplication.class).verify();
    }
}

package ng.itlearning.gateway.provider;

import ng.itlearning.gateway.config.GatewayProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
class ProviderConfiguration {

    @Bean
    ProviderRouter providerRouter(GatewayProperties props) {
        List<PaymentProvider> providers = props.providers().stream()
                .map(p -> (PaymentProvider) new HttpPaymentProvider(p, props.connectTimeout(), props.readTimeout()))
                .toList();
        return new ProviderRouter(providers, props.circuitBreaker(), props.verifyRetry());
    }
}

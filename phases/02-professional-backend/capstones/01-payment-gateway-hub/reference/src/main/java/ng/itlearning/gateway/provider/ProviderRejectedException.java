package ng.itlearning.gateway.provider;

/** The provider understood the request and refused it (4xx). Retrying won't help and the provider is healthy. */
public class ProviderRejectedException extends RuntimeException {

    public ProviderRejectedException(String provider, int status) {
        super("Provider " + provider + " rejected the request with HTTP " + status);
    }
}

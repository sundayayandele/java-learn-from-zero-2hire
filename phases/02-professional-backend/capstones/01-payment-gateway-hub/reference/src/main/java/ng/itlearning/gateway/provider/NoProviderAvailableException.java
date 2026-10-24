package ng.itlearning.gateway.provider;

public class NoProviderAvailableException extends RuntimeException {

    public NoProviderAvailableException(String currency) {
        super("No healthy provider supports " + currency + " right now.");
    }
}

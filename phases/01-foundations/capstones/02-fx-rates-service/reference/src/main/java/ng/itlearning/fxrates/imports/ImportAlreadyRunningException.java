package ng.itlearning.fxrates.imports;

public class ImportAlreadyRunningException extends RuntimeException {

    public ImportAlreadyRunningException() {
        super("Another import is already running");
    }
}

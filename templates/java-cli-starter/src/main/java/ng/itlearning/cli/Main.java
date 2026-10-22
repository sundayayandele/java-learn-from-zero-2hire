package ng.itlearning.cli;

import java.math.BigDecimal;

/** Entry point. Replace this with your project's commands (see the Module 1 project briefs). */
public final class Main {

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java -jar app.jar <principal> <annualRatePercent> <months>");
            System.exit(2);
        }
        BigDecimal interest = Interest.simple(new BigDecimal(args[0]), new BigDecimal(args[1]), Integer.parseInt(args[2]));
        System.out.println("Simple interest: " + interest);
    }

    private Main() { }
}

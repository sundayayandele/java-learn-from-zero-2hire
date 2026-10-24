package ng.itlearning.lab.harness;

import org.junit.jupiter.api.MethodDescriptor;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.MethodOrdererContext;

import java.util.Comparator;

/** Part of the lab harness (don't edit): the hostile profile runs test methods in reverse alphabetical order. */
public class ReverseMethodOrder implements MethodOrderer {

    @Override
    public void orderMethods(MethodOrdererContext context) {
        context.getMethodDescriptors().sort(
                Comparator.comparing((MethodDescriptor d) -> d.getMethod().getName()).reversed());
    }
}

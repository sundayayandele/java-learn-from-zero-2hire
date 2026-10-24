package ng.itlearning.gateway.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void accessDeniedBecomesA403ProblemWithoutInternalDetail() {
        var problem = handler.accessDenied(new AccessDeniedException("owner mismatch on account 123"));
        assertThat(problem.getStatus()).isEqualTo(403);
        assertThat(problem.getDetail()).doesNotContain("123");
        assertThat(problem.getType().toString()).endsWith("/forbidden");
    }

    @Test
    void unexpectedErrorsHideTheirMessage() {
        var problem = handler.unexpected(new IllegalStateException("password=secret"));
        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).doesNotContain("secret");
    }
}

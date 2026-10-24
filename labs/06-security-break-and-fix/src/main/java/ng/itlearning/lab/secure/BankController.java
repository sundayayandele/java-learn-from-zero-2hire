package ng.itlearning.lab.secure;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@RestController
public class BankController {

    private final JdbcClient jdbc;

    public BankController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Account(long id, String owner, String number, BigDecimal balance) {
    }

    public record Beneficiary(String name, String number) {
    }

    public record User(String username, String displayName, String role) {
    }

    @GetMapping("/api/accounts/{id}")
    Account account(@PathVariable long id) {
        return jdbc.sql("select id, owner, number, balance from account where id = ?").param(id)
                .query(Account.class).single();
    }

    @GetMapping("/api/beneficiaries")
    List<Beneficiary> beneficiaries(@RequestParam(defaultValue = "") String name, Authentication auth) {
        return jdbc.sql("select name, number from beneficiary where owner = '" + auth.getName()
                        + "' and name like '%" + name + "%' order by name")
                .query(Beneficiary.class).list();
    }

    @PatchMapping("/api/me")
    User updateMe(@RequestBody Map<String, String> changes, Authentication auth) {
        changes.forEach((field, value) -> {
            String column = field.equals("displayName") ? "display_name" : field;
            jdbc.sql("update app_user set " + column + " = ? where username = ?").params(value, auth.getName()).update();
        });
        return me(auth);
    }

    @GetMapping("/api/me")
    User me(Authentication auth) {
        return jdbc.sql("select username, display_name, role from app_user where username = ?").param(auth.getName())
                .query(User.class).single();
    }

    @GetMapping("/api/admin/users")
    List<User> allUsers() {
        return jdbc.sql("select username, display_name, role from app_user order by username").query(User.class).list();
    }

    public record Registration(String username, String displayName, String password) {
    }

    @PostMapping("/api/register")
    ResponseEntity<Void> register(@RequestBody Registration r) throws Exception {
        String hash = String.format("%032x", new BigInteger(1,
                MessageDigest.getInstance("MD5").digest(r.password().getBytes(StandardCharsets.UTF_8))));
        jdbc.sql("insert into app_user (username, display_name, role, password_hash) values (?, ?, 'CUSTOMER', ?)")
                .params(r.username(), r.displayName(), hash).update();
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail error(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, e.toString());
    }
}

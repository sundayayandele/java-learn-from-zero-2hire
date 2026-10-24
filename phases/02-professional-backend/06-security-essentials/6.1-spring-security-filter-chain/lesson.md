## What you will be able to do

- Explain **authentication** versus **authorisation**, and where each happens in a Spring application.
- Describe how a request travels through Spring Security's **filter chain** to your controller.
- Configure a **`SecurityFilterChain`** bean for a stateless REST API: public and protected paths, sessions, CSRF, CORS and security headers.
- Return proper **401** and **403** Problem Details responses.
- Write tests that prove an endpoint is protected.

## Why this matters in a bank or fintech

Every banking API is protected, and most security incidents in APIs are configuration mistakes, not broken cryptography: an endpoint accidentally left public, an admin path matched by the wrong pattern, CORS opened to every origin, CSRF disabled on a cookie-based web app. Spring Security is the default for Java in banks, and it is powerful but easy to misconfigure if you do not understand the filter chain. Interviewers ask you to explain it; auditors and penetration testers will find your mistakes if you cannot.

## 1. Authentication and authorisation

- **Authentication**: *who are you?* Proving identity with a password, a token, a client certificate or an API key. Failure gives **401 Unauthorized** (the name is historical; it means "unauthenticated").
- **Authorisation**: *what may you do?* Checking roles, scopes and ownership. Failure gives **403 Forbidden**.

Spring Security stores the result of authentication in the **`SecurityContext`** (per request, usually thread-bound): an `Authentication` object with the **principal** (the user or client), **credentials**, and **granted authorities** (roles and scopes).

## 2. The filter chain

Before a request reaches the `DispatcherServlet`, it passes through servlet **filters**. Spring Security adds one filter, `DelegatingFilterProxy`, which hands the request to a `FilterChainProxy`, which picks the first matching **`SecurityFilterChain`** and runs its security filters in order. Simplified:

```
HTTP request
  → CorsFilter                    (pre-flight and CORS headers)
  → CsrfFilter                    (state-changing requests in browser sessions)
  → LogoutFilter
  → BearerTokenAuthenticationFilter / UsernamePasswordAuthenticationFilter / BasicAuthenticationFilter
  → RequestCacheAwareFilter, SecurityContextHolderAwareRequestFilter
  → AnonymousAuthenticationFilter (no credentials? become "anonymous")
  → ExceptionTranslationFilter    (turns security exceptions into 401/403)
  → AuthorizationFilter           (checks the rules for this URL)
  → DispatcherServlet → your @RestController
```

Key ideas:

- **Authentication filters** extract credentials and ask an `AuthenticationManager` (via `AuthenticationProvider`s) to verify them. On success, the `SecurityContext` is populated.
- **`AuthorizationFilter`** applies the rules you configure for URLs. Method-level rules (`@PreAuthorize`) run later, around your beans (topic 6.3).
- **`ExceptionTranslationFilter`** catches `AuthenticationException` (→ the `AuthenticationEntryPoint`, usually 401) and `AccessDeniedException` (→ the `AccessDeniedHandler`, 403).

Turn on `logging.level.org.springframework.security=TRACE` once in development to watch a request go through every filter. It is the fastest way to understand what your configuration actually does.

## 3. A stateless API configuration

Add `spring-boot-starter-security`. With nothing else configured, Spring Boot protects everything with HTTP Basic and a generated password. Replace that with your own `SecurityFilterChain`:

```java
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, ProblemDetailsAuthHandlers problems) throws Exception {
        http
            .securityMatcher("/api/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/v1/banks", "/api/v1/fx-rates/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/transfers").hasAuthority("SCOPE_transfers:write")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))   // topic 6.2
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())                 // safe ONLY because we use bearer tokens, not cookies
            .cors(Customizer.withDefaults())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(problems)       // 401 as application/problem+json
                .accessDeniedHandler(problems));          // 403 as application/problem+json
        return http.build();
    }

    @Bean
    SecurityFilterChain actuator(HttpSecurity http) throws Exception {
        http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(EndpointRequest.to("health", "info")).permitAll()
                .anyRequest().hasRole("OPS"))
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
```

Important details:

- **Rules are evaluated in order; the first match wins.** Put specific rules before general ones and finish with `anyRequest()`. A `permitAll()` placed above `/api/v1/admin/**` with a broader pattern silently opens your admin API.
- **Deny by default.** End with `.anyRequest().authenticated()` (or `denyAll()`), so a new endpoint is protected even if you forget it.
- `hasRole("ADMIN")` checks for the authority `ROLE_ADMIN`; `hasAuthority("SCOPE_transfers:write")` checks the exact string. Topic 6.3 explains roles, authorities and scopes.
- Multiple `SecurityFilterChain` beans are matched by their `securityMatcher`, so the API and Actuator can use different mechanisms.

### Stateless sessions

A REST API with bearer tokens should not create HTTP sessions: every request carries its own credentials. `SessionCreationPolicy.STATELESS` stops Spring Security from creating them, which also makes horizontal scaling simple.

### CSRF: when it is safe to disable

**Cross-site request forgery** tricks a logged-in user's browser into sending a request your server trusts **because of cookies the browser attaches automatically**. Defences: CSRF tokens, `SameSite` cookies.

- APIs authenticated with an `Authorization: Bearer ...` header are not vulnerable in this way, because browsers do not attach that header to cross-site requests on their own. Disabling CSRF for such APIs is standard.
- Web applications that log users in with a **session cookie** (Thymeleaf back-office apps, `oauth2Login`) **must keep CSRF protection on**.

### CORS

Browsers block JavaScript on `https://app.examplebank.ng` from reading responses from `https://api.examplebank.ng` unless the API allows it. Allow exactly the origins you need:

```java
@Bean
CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.of("https://app.examplebank.ng", "https://admin.examplebank.ng"));
    cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "API-Version"));
    cors.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cors);
    return source;
}
```

Never combine `allowedOrigins("*")` with credentials, and never reflect whatever `Origin` the request sends. CORS is a browser feature, not access control: it does not stop `curl` or a server from calling you.

### Security headers

Spring Security adds sensible headers by default: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-cache, no-store` for authenticated responses, and `Strict-Transport-Security` over HTTPS. Keep them; add a `Content-Security-Policy` for any HTML you serve.

## 4. 401 and 403 as Problem Details

The default 401 response is an empty body with `WWW-Authenticate`. Give clients the same error format as the rest of the API:

```java
@Component
class ProblemDetailsAuthHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper json;

    ProblemDetailsAuthHandlers(JsonMapper json) { this.json = json; }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) throws IOException {
        res.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(res, HttpStatus.UNAUTHORIZED, "unauthenticated", "A valid access token is required.");
    }

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex) throws IOException {
        write(res, HttpStatus.FORBIDDEN, "forbidden", "You do not have permission for this operation.");
    }

    private void write(HttpServletResponse res, HttpStatus status, String type, String detail) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("https://api.examplebank.ng/problems/" + type));
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(res.getOutputStream(), pd);
    }
}
```

Do not say *why* authentication failed ("token expired" versus "user does not exist") in ways that help attackers; log the reason server-side instead.

## 5. Accessing the current user

In a controller:

```java
@GetMapping("/api/v1/me")
CustomerProfile me(@AuthenticationPrincipal Jwt jwt) {
    return profiles.forCustomer(jwt.getSubject());
}
```

In a service, `SecurityContextHolder.getContext().getAuthentication()`. Prefer passing the identity as a parameter from the controller so services stay easy to test. Remember that the context is thread-bound: work you hand to another thread (an executor, `@Async`) needs the context propagated, for example with `DelegatingSecurityContextExecutor`.

## 6. Testing security

Add `spring-boot-starter-security-test`, then test that protection actually works:

```java
@WebMvcTest(TransferController.class)
@Import(SecurityConfig.class)
class TransferSecurityTest {

    @Autowired MockMvc mvc;
    @MockitoBean TransferService transfers;

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(post("/api/v1/transfers").contentType(APPLICATION_JSON).content(validBody()))
           .andExpect(status().isUnauthorized())
           .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void tokenWithoutWriteScopeIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/transfers").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_transfers:read")))
                .contentType(APPLICATION_JSON).content(validBody()))
           .andExpect(status().isForbidden());
    }

    @Test
    void tokenWithWriteScopeIsAllowed() throws Exception {
        mvc.perform(post("/api/v1/transfers").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_transfers:write")))
                .contentType(APPLICATION_JSON).content(validBody()))
           .andExpect(status().isCreated());
    }
}
```

`jwt()` (from `SecurityMockMvcRequestPostProcessors`) creates an authenticated JWT principal without a real identity provider. For non-JWT tests, `@WithMockUser(roles = "ADMIN")` does the same for a username.

## Common mistakes

- Relying on the order of rules without understanding "first match wins".
- `.anyRequest().permitAll()` at the end "for now".
- Disabling CSRF on a cookie-session web application because a tutorial for APIs did it.
- `allowedOrigins("*")` or reflecting the request's `Origin`.
- Creating sessions for a token-based API.
- Writing a custom authentication filter that parses tokens by hand instead of using the resource-server support.
- No tests for 401 and 403 cases.

## Practice

1. Add Spring Security to a Phase 1 capstone and write a `SecurityFilterChain` with public reference data, an admin area, and everything else authenticated. Log at `TRACE` and read the filter list for one request.
2. Add a second chain for Actuator with HTTP Basic for an `OPS` role, keeping `health` public.
3. Implement Problem Details for 401 and 403 and test both.
4. Configure CORS for one front-end origin; test with `curl -H "Origin: https://evil.example" -X OPTIONS ...` that a foreign origin gets no `Access-Control-Allow-Origin`.

## Key terms

Authentication, authorisation, principal, credentials, `Authentication`, `GrantedAuthority`, `SecurityContext`, `SecurityContextHolder`, `DelegatingFilterProxy`, `FilterChainProxy`, `SecurityFilterChain`, `securityMatcher`, `authorizeHttpRequests`, `AuthenticationManager`, `AuthenticationProvider`, `ExceptionTranslationFilter`, `AuthenticationEntryPoint`, `AccessDeniedHandler`, stateless session, CSRF, CORS, pre-flight request, security headers, `@WithMockUser`, `jwt()` request post-processor.

## Further reading

- [Spring Security reference: Servlet architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
- [Spring Security reference: Authorize HTTP requests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
- [Spring Security reference: CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Security reference: CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html)
- [Spring Security reference: Testing](https://docs.spring.io/spring-security/reference/servlet/test/index.html)
- [OWASP Cheat Sheet: Cross-Site Request Forgery Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)

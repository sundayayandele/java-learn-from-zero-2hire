## What you will be able to do

- Read a **JWT** and explain its header, claims and signature, and what makes a token valid.
- Explain the **OAuth 2.0** roles and the flows you will meet: authorization code with PKCE, client credentials, and refresh tokens.
- Explain what **OpenID Connect** adds (identity, the ID token).
- Configure a Spring Boot API as an **OAuth2 resource server** that validates tokens from **Keycloak**.
- Implement **refresh-token rotation** with reuse detection when you issue your own tokens.

## Why this matters in a bank or fintech

Banks rarely let each application store passwords. Customers and staff sign in through an identity provider (Keycloak, Microsoft Entra ID, Okta, Auth0, Ping), and APIs accept **access tokens** that the provider issues. Open banking is built on OAuth 2.0: a fintech app gets the customer's *consent* to read their account data, receiving a scoped token rather than their password. Getting token validation wrong (accepting unsigned tokens, ignoring the audience, never expiring sessions) is one of the most common serious findings in fintech penetration tests.

## 1. JSON Web Tokens

A JWT is three Base64URL-encoded parts separated by dots: `header.payload.signature`.

```json
// header
{ "alg": "RS256", "kid": "2026-10-key-1", "typ": "JWT" }

// payload (claims)
{
  "iss": "https://auth.examplebank.ng/realms/retail",
  "sub": "c-7f2a9c",
  "aud": "wallet-api",
  "exp": 1792800900,
  "iat": 1792800000,
  "scope": "accounts:read transfers:write",
  "realm_access": { "roles": ["customer"] },
  "azp": "mobile-app"
}
```

- **`iss`** (issuer) who created it; **`sub`** (subject) who it is about; **`aud`** (audience) which API it is for; **`exp`**, **`iat`**, **`nbf`** timing; custom claims for scopes and roles.
- The **signature** is made with the issuer's private key (RS256/ES256) or a shared secret (HS256). Anyone can *read* a JWT (it is only encoded); only someone with the key can *create a valid one*. **Never put secrets or personal data you would not show the user into a JWT.**

A token is valid only if **all** of these hold:

1. The signature verifies with the issuer's current public key (found by `kid` in the issuer's **JWKS** endpoint).
2. `alg` is one you expect (reject `none`, and reject HS256 when you expect RS256: the classic "algorithm confusion" attack).
3. `iss` is your issuer and `aud` includes your API.
4. The current time is before `exp` (with a small clock-skew allowance) and after `nbf`.

### Access tokens should be short-lived

A signed JWT cannot be "logged out": it is valid until it expires. So keep access tokens short (5–15 minutes) and use **refresh tokens** to get new ones.

## 2. OAuth 2.0 in one page

**Roles:** the **resource owner** (the customer), the **client** (the mobile app or a partner's server), the **authorization server** (Keycloak), and the **resource server** (your API).

| Flow (grant) | Use it for | Notes |
| --- | --- | --- |
| **Authorization code + PKCE** | Apps acting for a user: mobile, single-page and server web apps | The user signs in at the authorization server; the app receives a code and exchanges it for tokens. PKCE stops intercepted codes being used. |
| **Client credentials** | Service-to-service calls with no user | The client authenticates as itself (secret, private-key JWT or mutual TLS). |
| **Refresh token** | Getting a new access token without asking the user again | Long-lived, must be stored securely, rotated on use. |
| ~~Implicit~~, ~~Resource owner password~~ | Legacy | Deprecated in OAuth 2.1; do not use for new systems. |

**Scopes** describe what the client may do on the user's behalf (`accounts:read`, `payments:write`). **Consent** is the user approving those scopes, which is central to open banking.

### OpenID Connect

OAuth 2.0 is about *authorisation* (access to APIs). **OpenID Connect (OIDC)** adds *authentication* on top: an **ID token** (a JWT about the user, for the client to read), a standard `userinfo` endpoint, and discovery (`/.well-known/openid-configuration`). When you "Sign in with Google" you are using OIDC.

## 3. Your API as a resource server

Add `spring-boot-starter-security-oauth2-resource-server` and point it at the issuer:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.examplebank.ng/realms/retail
```

At startup Spring reads the issuer's discovery document, finds the JWKS URL, and from then on validates every bearer token's signature, issuer and expiry, fetching new keys when the issuer rotates them. Add audience validation, which is not on by default:

```java
@Bean
JwtDecoder jwtDecoder(OAuth2ResourceServerProperties props) {
    NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(props.getJwt().getIssuerUri());
    OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD, aud -> aud != null && aud.contains("wallet-api"));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(props.getJwt().getIssuerUri()), audience));
    return decoder;
}
```

Spring Boot also offers the simplest option as a property, `spring.security.oauth2.resourceserver.jwt.audiences=wallet-api`; use the bean when you need custom rules.

### Mapping claims to authorities

By default, `scope` claims become `SCOPE_...` authorities. Keycloak puts roles in `realm_access.roles`, so map them too:

```java
@Bean
JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();   // SCOPE_accounts:read ...
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
        Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
        Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
        if (realm != null && realm.get("roles") instanceof List<?> roles) {
            roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toString().toUpperCase())));
        }
        return authorities;
    });
    return converter;
}
```

## 4. Keycloak locally

Keycloak is an open-source identity provider (Apache License 2.0) widely used by banks. Run it with Docker Compose for development:

```yaml
services:
  keycloak:
    image: quay.io/keycloak/keycloak:26.8
    command: start-dev --import-realm
    environment:
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
    volumes:
      - ./keycloak/realm-retail.json:/opt/keycloak/data/import/realm-retail.json:ro
    ports: ["8180:8080"]
```

Create a realm (`retail`), a confidential client for your back-office web app, a public client with PKCE for the mobile app, realm roles (`customer`, `support`, `admin`), and client scopes (`accounts:read`, `transfers:write`). Export the realm to JSON (without user passwords or client secrets) and commit it so the whole team gets the same setup. To log in with GitHub or Google, add them as **identity providers** in Keycloak; your API does not change, because it only trusts Keycloak's tokens.

Get a token for testing:

```bash
curl -s -X POST http://localhost:8180/realms/retail/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=reconciliation-job -d client_secret="$CLIENT_SECRET" | jq -r .access_token
```

### Logging users in to a web app (OIDC client)

For a server-rendered back-office app, `spring-boot-starter-security-oauth2-client` and `http.oauth2Login(Customizer.withDefaults())` implement the authorization-code flow with PKCE: users are redirected to Keycloak, and Spring stores the tokens in the server-side session. That app uses cookies, so **CSRF protection stays on** (topic 6.1).

## 5. When you issue your own tokens

Sometimes a small service must issue its own tokens (for example a simple partner API before an identity provider exists). Spring Security's `NimbusJwtEncoder` signs JWTs; keep the private key in a secrets manager, never in the repository. The hard part is **refresh tokens**:

- Make refresh tokens **opaque random values** (256 bits), store only their **hash** with the user, an expiry and a **family ID**.
- **Rotate on every use:** `/auth/refresh` returns a new refresh token and marks the old one used.
- **Detect reuse:** if a token that was already used is presented again, someone has stolen it. Revoke the whole family, forcing a fresh login.

```java
@Transactional
public TokenPair refresh(String presented) {
    RefreshToken token = refreshTokens.findByHash(sha256(presented))
            .orElseThrow(InvalidRefreshTokenException::new);
    if (token.isUsed() || token.isRevoked()) {
        refreshTokens.revokeFamily(token.familyId());          // theft detected
        throw new InvalidRefreshTokenException();
    }
    if (token.expiresAt().isBefore(clock.instant())) throw new InvalidRefreshTokenException();
    token.markUsed();
    return issuePair(token.userId(), token.familyId());       // new access token + new refresh token, same family
}
```

Prefer an established identity provider over home-grown token issuance whenever you can: it already handles key rotation, consent, multi-factor authentication, account lockout and audit.

## 6. Testing without a real Keycloak

- **Slice tests:** use `jwt()` from Spring Security Test (topic 6.1) to simulate tokens with chosen scopes and roles.
- **Integration tests:** either start Keycloak with Testcontainers (the `testcontainers-keycloak` community module), or sign test tokens with your own RSA key and configure the decoder with the matching public key (`spring.security.oauth2.resourceserver.jwt.public-key-location`). The second is faster and fully offline.

Always include negative tests: expired token, wrong issuer, wrong audience, tampered signature, `alg: none`.

## Common mistakes

- Accepting tokens without checking the audience, so a token for another API works on yours.
- Writing your own JWT parsing and validation.
- Long-lived access tokens (hours or days) with no revocation.
- Storing refresh tokens in plain text, or never rotating them.
- Putting personal data (BVN, phone number, balance) in JWT claims.
- Using the implicit or password grants for new applications.
- Disabling CSRF on an `oauth2Login` web app.
- Committing Keycloak realm exports that contain client secrets or user passwords.

## Practice

1. Run Keycloak with Docker Compose, create a `retail` realm with roles and scopes, and export it.
2. Configure your capstone as a resource server with issuer and audience validation and the role converter above; call it with a token from `curl`.
3. Write integration tests that sign tokens with a test RSA key and prove that expired, wrong-audience and tampered tokens are rejected.
4. Implement login, refresh with rotation and reuse detection, and logout (project 6.A), with a test that replays an old refresh token and checks that the family is revoked.

## Key terms

JWT, JWS, header, claims, `iss`, `sub`, `aud`, `exp`, `kid`, JWKS, RS256, HS256, algorithm confusion, access token, refresh token, rotation, reuse detection, token family, OAuth 2.0, resource owner, client, authorization server, resource server, authorization code flow, PKCE, client credentials, scope, consent, OpenID Connect, ID token, discovery document, Keycloak, realm, identity provider, `JwtDecoder`, `JwtAuthenticationConverter`.

## Further reading

- [RFC 7519: JSON Web Token (JWT)](https://www.rfc-editor.org/rfc/rfc7519)
- [OAuth 2.0 Security Best Current Practice (RFC 9700)](https://www.rfc-editor.org/rfc/rfc9700)
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [Spring Security reference: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Keycloak documentation: Server administration guide](https://www.keycloak.org/docs/latest/server_admin/)
- [OWASP Cheat Sheet: JSON Web Token](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_Cheat_Sheet.html)

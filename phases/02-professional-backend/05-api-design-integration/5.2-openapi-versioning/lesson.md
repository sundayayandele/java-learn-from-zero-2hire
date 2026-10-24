## What you will be able to do

- Explain what an **OpenAPI** document is and why partner teams rely on it.
- Generate an OpenAPI 3.1 document and Swagger UI from a Spring Boot 4 application with **springdoc-openapi**, and enrich it with annotations.
- Compare **code-first** and **contract-first** development, and generate a typed client or server stubs from a contract.
- Version an API with Spring Framework 7's built-in **API versioning** (header, path, query or media type), and deprecate old versions properly.
- Detect **breaking changes** before they reach clients.

## Why this matters in a bank or fintech

A payments API is consumed by mobile teams, merchants, aggregators and partner banks you will never meet. They integrate from the documentation, often generating client code from it. Open-banking and payment-scheme specifications are published as OpenAPI documents, and partner onboarding usually starts with "send us your OpenAPI file". When you change a field name without versioning, you break someone's production integration at 2 a.m. Good contracts and disciplined versioning are what let a bank change its systems without breaking its partners.

## 1. What OpenAPI describes

An OpenAPI document (YAML or JSON) describes every path, method, parameter, request body, response, status code, security scheme and data schema of an HTTP API. A fragment:

```yaml
openapi: 3.1.0
info:
  title: Wallet API
  version: 1.4.0
paths:
  /api/v1/transfers:
    post:
      operationId: createTransfer
      parameters:
        - name: Idempotency-Key
          in: header
          required: true
          schema: { type: string, maxLength: 64 }
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/TransferRequest' }
      responses:
        '201': { description: Transfer completed }
        '202': { description: Transfer accepted and pending }
        '422':
          description: Business rule failed
          content:
            application/problem+json:
              schema: { $ref: '#/components/schemas/ProblemDetail' }
components:
  schemas:
    TransferRequest:
      type: object
      required: [sourceAccount, destinationAccount, amount, currency]
      properties:
        sourceAccount: { type: string, pattern: '^\d{10}$' }
        destinationAccount: { type: string, pattern: '^\d{10}$' }
        amount: { type: string, pattern: '^\d+\.\d{2}$', example: '2500.00' }
        currency: { type: string, enum: [NGN, USD, GBP, EUR] }
```

From one document, tools can render interactive docs (Swagger UI, Redoc), generate clients in a dozen languages, generate mock servers, validate requests in a gateway and detect breaking changes between versions.

## 2. Code-first with springdoc-openapi

Add the starter (version 3.x supports Spring Boot 4):

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.1.1</version>
</dependency>
```

Start the app and you get:

- `/v3/api-docs`: the generated OpenAPI JSON (`/v3/api-docs.yaml` for YAML).
- `/swagger-ui.html`: interactive documentation.

springdoc reads your controllers, DTO records and Bean Validation annotations (`@NotNull`, `@Size`, `@Pattern` become schema constraints). Add annotations where the code alone is not enough:

```java
@Tag(name = "Transfers", description = "Move money between accounts")
@RestController
@RequestMapping("/api/v1/transfers")
class TransferController {

    @Operation(summary = "Create a transfer",
               description = "Idempotent when the same Idempotency-Key is reused within 24 hours.")
    @ApiResponse(responseCode = "201", description = "Transfer completed")
    @ApiResponse(responseCode = "202", description = "Transfer accepted, awaiting the switch")
    @ApiResponse(responseCode = "422", description = "Insufficient funds or limit exceeded",
                 content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    ResponseEntity<TransferResponse> create(
            @Parameter(description = "Client-generated unique key, max 64 characters")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) { ... }
}

record TransferRequest(
        @Schema(example = "0123456789") @Pattern(regexp = "\\d{10}") String sourceAccount,
        @Schema(example = "9876543210") @Pattern(regexp = "\\d{10}") String destinationAccount,
        @Schema(type = "string", example = "2500.00") @NotNull @Positive BigDecimal amount,
        @NotNull Currency currency) { }
```

Global information (title, version, contact, security schemes, servers) goes in an `OpenAPI` bean:

```java
@Bean
OpenAPI walletApi() {
    return new OpenAPI()
            .info(new Info().title("Wallet API").version("1.4.0")
                    .contact(new Contact().name("Payments Platform").email("platform@example.com")))
            .components(new Components().addSecuritySchemes("bearer",
                    new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList("bearer"));
}
```

In production, decide deliberately whether Swagger UI is public. Many banks expose it only on an internal network or behind authentication (`springdoc.swagger-ui.enabled=false` in the production profile), and publish the static YAML to a developer portal instead.

## 3. Contract-first

In **contract-first** development, you write (or receive) the OpenAPI document first, review it with consumers, and then generate server interfaces and client code from it with the OpenAPI Generator Maven plugin:

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>7.26.0</version>
    <executions>
        <execution>
            <goals><goal>generate</goal></goals>
            <configuration>
                <inputSpec>${project.basedir}/src/main/resources/openapi/wallet-api.yaml</inputSpec>
                <generatorName>spring</generatorName>
                <configOptions>
                    <interfaceOnly>true</interfaceOnly>
                    <useSpringBoot4>true</useSpringBoot4>
                    <useTags>true</useTags>
                </configOptions>
            </configuration>
        </execution>
    </executions>
</plugin>
```

The plugin generates `TransfersApi` (an interface with Spring MVC annotations) and model classes; your controller implements the interface, so the compiler fails if the implementation drifts from the contract. (`useSpringBoot4` makes it generate Jakarta EE code for Spring Boot 4.) The same file generates a Java client (`generatorName=java`, library `restclient`) for teams that call you.

| | Code-first | Contract-first |
| --- | --- | --- |
| Speed for a new internal service | Fast | Slower to start |
| Consumer review before coding | Hard | Natural |
| Drift between docs and code | Impossible (docs come from code) | Prevented by generated interfaces |
| Typical use | Internal services, early-stage products | Partner and public APIs, regulated integrations |

Either way, **commit the OpenAPI document** to the repository (generate it in CI for code-first) so every change to the contract shows up in pull-request diffs.

## 4. Versioning with Spring Framework 7

Spring Framework 7 (Spring Boot 4) has first-class API versioning. You choose **where** the version comes from, and map handlers to versions with the `version` attribute.

```java
@Configuration
class ApiVersioningConfig implements WebMvcConfigurer {
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.useRequestHeader("API-Version")   // or usePathSegment(1), useQueryParam("version"), useMediaTypeParameter(...)
                  .addSupportedVersions("1.0", "1.1", "2.0")
                  .setDefaultVersion("1.0");
    }
}
```

The same settings are available as properties in Spring Boot:

```properties
spring.mvc.apiversion.use.header=API-Version
spring.mvc.apiversion.supported=1.0,1.1,2.0
spring.mvc.apiversion.default=1.0
```

Then map versions on handlers:

```java
@GetMapping(path = "/accounts/{id}", version = "1.0")
AccountV1 getV1(@PathVariable String id) { ... }

@GetMapping(path = "/accounts/{id}", version = "2.0")
AccountV2 getV2(@PathVariable String id) { ... }   // e.g. balance split into ledger and available

@GetMapping(path = "/accounts/{id}/limits", version = "1.1+")
Limits limits(@PathVariable String id) { ... }      // added in 1.1, still served by 2.0
```

A request for an unsupported version gets `400 Bad Request` automatically. `"1.1+"` means "this version and later", so a handler keeps serving new versions until you deliberately replace it.

### Which strategy?

| Strategy | Example | Notes |
| --- | --- | --- |
| Path segment | `/api/v2/accounts/123` | Most visible, easy to route in gateways and to test with a browser; common in public fintech APIs |
| Header | `API-Version: 2.0` | Clean URLs; easy to forget in clients, so set a default |
| Query parameter | `/accounts/123?version=2.0` | Simple, but mixes versioning with filtering |
| Media type | `Accept: application/vnd.bank.v2+json` | Purest HTTP, hardest for client developers |

Whatever you choose, use one strategy across the whole API.

### What is a breaking change?

Breaking (needs a new major version): removing or renaming a field or endpoint, changing a field's type or meaning, making an optional request field required, adding a new required parameter, changing status codes clients rely on, tightening validation. **Not breaking** (minor version): adding optional request fields, adding response fields, adding new endpoints, adding enum values *only if* clients were told to tolerate unknown values. Write that last rule into your API guidelines; it saves many versions.

### Deprecation and sunset

When version 1 will be retired, tell clients in every response. The `Deprecation` and `Sunset` headers (RFC 9745 and RFC 8594) are machine-readable; Spring Framework 7 can add them through `configurer.setDeprecationHandler(...)` with a `StandardApiVersionDeprecationHandler`, so clients see:

```
Deprecation: @1767225600
Sunset: Thu, 30 Apr 2027 00:00:00 GMT
Link: <https://developer.example.com/migrate-to-v2>; rel="deprecation"
```

Support at least two versions in parallel, announce sunset dates months ahead for partner APIs, and watch your access logs to see who still calls the old version before you switch it off.

## 5. Catching breaking changes in CI

Generate the OpenAPI document during the build and compare it with the last released one using a diff tool such as **openapi-diff** or **oasdiff**. Fail the pipeline if a breaking change appears without a major version bump:

```bash
oasdiff breaking docs/openapi/wallet-api-v1.yaml target/openapi.yaml --fail-on ERR
```

This turns "please don't break the mobile app" from a hope into a check.

## Common mistakes

- Swagger UI and `/v3/api-docs` left open on the internet with internal endpoints listed.
- Documenting only happy paths; error responses (`application/problem+json`) are part of the contract.
- `BigDecimal` documented as `number`, so generated clients use floating point. Mark money fields `type: string`.
- Versioning by copying the whole codebase or controller for every minor change.
- Breaking changes shipped as minor versions ("we only renamed one field").
- Removing an old version without checking who still calls it.

## Practice

1. Add springdoc to your Phase 1 capstone, enrich three endpoints with `@Operation`, `@ApiResponse` and `@Schema`, and check the result in Swagger UI.
2. Export the document to `docs/openapi.yaml` and commit it. Change a field name, regenerate, and diff the two files with oasdiff.
3. Add header-based versioning with versions `1.0` and `2.0`; serve a changed account representation from version 2 and keep version 1 working. Write MockMvc tests for both versions and for an unsupported version (400).
4. Write a contract-first YAML for a small "beneficiaries" API and generate a Spring interface from it with the OpenAPI Generator plugin.

## Key terms

OpenAPI Specification, Swagger UI, springdoc-openapi, `/v3/api-docs`, schema, `operationId`, `@Operation`, `@ApiResponse`, `@Schema`, code-first, contract-first, OpenAPI Generator, API versioning, `configureApiVersioning`, `version` attribute, baseline version (`1.1+`), breaking change, semantic versioning, `Deprecation` header, `Sunset` header, oasdiff.

## Further reading

- [OpenAPI Specification 3.1](https://spec.openapis.org/oas/v3.1.0)
- [springdoc-openapi documentation](https://springdoc.org/)
- [Spring Framework reference: API Versioning](https://docs.spring.io/spring-framework/reference/web/webmvc-versioning.html)
- [OpenAPI Generator: Spring generator options](https://openapi-generator.tech/docs/generators/spring/)
- [RFC 8594: The Sunset HTTP Header Field](https://www.rfc-editor.org/rfc/rfc8594)
- [oasdiff: OpenAPI diff and breaking-change detection](https://github.com/oasdiff/oasdiff)

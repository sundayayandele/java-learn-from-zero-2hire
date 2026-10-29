## What you will be able to do

- Explain what an API gateway does, and what it should **not** do.
- Route requests to services with Spring Cloud Gateway (server WebMvc), using predicates and filters.
- Put cross-cutting concerns at the edge: authentication with token relay, rate limiting, request headers and timeouts.
- Externalise configuration with Spring Cloud Config, and know when plain environment variables are better.
- Choose between client-side service discovery (Eureka) and Kubernetes DNS.

## Why this matters in a bank or fintech

A mobile banking app shouldn't know that balances come from one service and transfers from another, or that the transfer service moved to a new cluster last week. Partners integrating through an open-banking API need one address, one authentication scheme and fair rate limits. The gateway gives you that single front door. Configuration and discovery decide whether a change of database password or a new instance needs a redeploy of ten services, or none.

## 1. What a gateway is for

An **API gateway** sits between clients and your services:

```
 mobile app / partner ──HTTPS──> [ API gateway ] ──> accounts
                                       │        ──> transfers
                                       │        ──> rates
                                 auth, rate limits,
                                 routing, headers
```

Good jobs for a gateway: **routing** by path or host, **TLS termination**, **authentication** (validate the token once, pass it on), **rate limiting** per client, **request size limits**, adding **correlation ids**, and **coarse timeouts**.

Bad jobs: business rules ("transfers over ₦5m need approval"), joining data from several services into one response (that's a separate backend-for-frontend service, if you need it), or anything that makes the gateway change every time a service changes. A gateway full of business logic becomes the new monolith.

## 2. Spring Cloud Gateway (server WebMvc)

Spring Cloud Gateway comes in two flavours: **server WebFlux** (reactive) and **server WebMvc** (servlet, and with virtual threads it scales well). This programme uses **server WebMvc**, so the code looks like the Spring MVC you know. With Spring Boot 4.1 it comes from Spring Cloud 2025.1:

```xml
<properties>
    <spring-cloud.version>2025.1.4</spring-cloud.version>
</properties>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webmvc</artifactId>
</dependency>
<!-- plus the spring-cloud-dependencies BOM in <dependencyManagement>, as start.spring.io generates -->
```

Routes are declared in configuration. Each has an **id**, a target **uri**, **predicates** that decide which requests match, and **filters** that change the request or response:

```yaml
spring:
  cloud:
    gateway:
      server:
        webmvc:
          routes:
            - id: accounts
              uri: ${ACCOUNTS_URL:http://localhost:8081}
              predicates:
                - Path=/api/accounts/**
              filters:
                - StripPrefix=1                       # /api/accounts/A1 → /accounts/A1
            - id: transfers
              uri: ${TRANSFERS_URL:http://localhost:8082}
              predicates:
                - Path=/api/transfers/**
                - Method=POST,GET
              filters:
                - StripPrefix=1
                - AddRequestHeader=X-Gateway, itl-pay
```

`curl http://localhost:8080/api/accounts/A1` now reaches the accounts service as `GET /accounts/A1`. Other predicates match on `Host`, `Header`, `Query`, `Method` or a `Weight` (for canary releases: send 5% of traffic to a new version). Other filters rewrite paths, set or remove headers, retry, apply a circuit breaker, or limit request size.

The same routes can be written in Java with the functional `RouterFunction` API when you need logic the properties can't express:

```java
@Bean
RouterFunction<ServerResponse> transferRoutes() {
    return route("transfers")
            .route(path("/api/transfers/**"), http())
            .before(uri("http://localhost:8082"))
            .before(stripPrefix(1))
            .build();
}
```

The static imports are `GatewayRouterFunctions.route` and `HandlerFunctions.http` (package `org.springframework.cloud.gateway.server.mvc.handler`), `BeforeFilterFunctions.uri` and `stripPrefix` (`...server.mvc.filter`), and `RequestPredicates.path` (`org.springframework.web.servlet.function`).

## 3. Security, limits and timeouts at the edge

**Authentication.** Make the gateway an OAuth2 resource server (topic 6.2) so it rejects requests without a valid token, and let each service validate the token again: never trust the network inside the cluster blindly ("zero trust"). If the gateway handles a browser login itself, the **`TokenRelay`** filter forwards the user's access token to the services.

**Rate limiting.** Protect services and share capacity fairly: per partner API key, per user, or per IP for unauthenticated endpoints such as login and OTP requests. The servlet gateway has a rate-limit filter backed by **Bucket4j**; with several gateway instances, the buckets must live in a shared store (Redis, for example), or each instance enforces its own limit. Return `429 Too Many Requests` with a `Retry-After` header.

**Timeouts.** The gateway's HTTP client timeouts are the outer limit for every call. Set them a little above the slowest legitimate request, and keep each service's own timeouts shorter (topic 10.3).

**Correlation ids.** Make sure every request carries a trace id (`traceparent`) from the edge onwards; Module 13 builds tracing on it.

## 4. Configuration: Spring Cloud Config or environment variables?

Every service needs settings: database URLs, partner endpoints, limits, feature flags. Options:

| Approach | How | Good for |
| --- | --- | --- |
| Environment variables and profiles (topic 4.5) | The platform injects values (Kubernetes ConfigMaps and Secrets, topic 12.1) | Most services, especially on Kubernetes |
| **Spring Cloud Config** | A config server serves properties from a Git repository; services import them at start-up | Many services sharing settings, with changes reviewed in Git |
| A secrets manager | Vault, AWS Secrets Manager, Kubernetes Secrets | Passwords, keys, tokens: always |

A minimal **config server**:

```java
@SpringBootApplication
@EnableConfigServer
class ConfigServerApplication { }
```

```yaml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/itl-pay/config     # transfers.yml, transfers-prod.yml, application.yml ...
```

and in each **client**:

```properties
spring.application.name=transfers
spring.config.import=optional:configserver:http://config:8888
```

The client receives `application.yml` (shared), `transfers.yml` and `transfers-<profile>.yml`, in increasing priority. Rules that keep it safe: **never put secrets in the config repository** (reference them from a secrets manager); review config changes like code; make the import `optional:` only where the service can run on its defaults.

On Kubernetes, many teams skip the config server and use ConfigMaps plus a GitOps tool (topic 12.5), which gives the same "config in Git, reviewed" property without another service to run.

## 5. Service discovery: Eureka or Kubernetes DNS

Services move and scale, so callers shouldn't hard-code addresses.

**Client-side discovery (Eureka).** Each instance registers with a Eureka server and sends heartbeats; callers ask Eureka for the instances of `accounts` and balance load across them with **Spring Cloud LoadBalancer**. In the gateway, a route can then use `uri: lb://accounts`, and in a service an `@LoadBalanced RestClient.Builder` resolves `http://accounts/...`.

**Platform discovery (Kubernetes).** A Kubernetes `Service` named `accounts` gets a stable DNS name (`accounts.itl-pay.svc.cluster.local`, or just `accounts` within the namespace) and spreads traffic across ready pods. No registry to run, no client library.

| | Eureka | Kubernetes Services |
| --- | --- | --- |
| Extra component to run | Eureka server (keep it highly available) | None |
| Works outside Kubernetes | Yes (VMs, local Docker) | No |
| Health-aware | Heartbeats | Readiness probes (topic 12.2) |
| Choice in this programme | Optional locally | **Used from Module 12 onwards** |

ITL Pay uses plain URLs from environment variables in Docker Compose (`ACCOUNTS_URL=http://accounts:8081`, where Compose's own DNS resolves `accounts`) and Kubernetes Services later, so the code never changes.

## Worked example: the ITL Pay gateway

The `v0.1` release has three services and a gateway in Docker Compose:

```yaml
services:
  accounts:  { build: ./accounts }
  transfers: { build: ./transfers, environment: { ACCOUNTS_URL: http://accounts:8081 } }
  gateway:
    build: ./gateway
    environment: { ACCOUNTS_URL: http://accounts:8081, TRANSFERS_URL: http://transfers:8082 }
    ports: ["8080:8080"]
```

Only the gateway publishes a port. Through it:

```
$ curl -s localhost:8080/api/accounts/A1
{"accountId":"A1","available":50000.00}

$ curl -s -X POST localhost:8080/api/transfers -H 'Content-Type: application/json' \
       -d '{"reference":"TRF-1","from":"A1","to":"A2","amount":2500.00}'
{"reference":"TRF-1","status":"PENDING"}
```

Next steps on this gateway, in later modules: JWT validation (reusing your Phase 2 Keycloak), a rate limit per client on `POST /api/transfers`, and trace propagation.

## Common mistakes

- Putting business rules or data aggregation into the gateway.
- Exposing every service's port directly as well as through the gateway, so clients bypass the limits and authentication.
- Validating tokens only at the gateway and trusting everything inside.
- Rate limits held in each gateway instance's memory while you run several instances.
- Secrets committed to the config repository.
- Adding Eureka on Kubernetes "because the tutorial did", duplicating what Services already do.

## Practice

1. Generate a gateway with start.spring.io (Boot 4.1, "Gateway"), route `/api/accounts/**` and `/api/transfers/**` to two small services, and call both through it.
2. Add a `Weight` predicate that sends 10% of `/api/rates/**` to a `rates-v2` service, and measure the split over 100 calls.
3. Make the gateway a resource server with your Phase 2 Keycloak realm; confirm a request without a token gets `401` at the gateway.
4. Run a config server against a local Git repository and change a transfer limit for the `dev` profile without rebuilding the transfers service.
5. Write a one-page note: would ITL Pay use Eureka? Why or why not?

## Key terms

API gateway, edge, route, predicate, filter, StripPrefix, server WebMvc, server WebFlux, token relay, rate limiting, Bucket4j, backend for frontend (BFF), Spring Cloud Config, config server, `spring.config.import`, service discovery, Eureka, Spring Cloud LoadBalancer, `lb://`, Kubernetes Service, cluster DNS.

## Further reading

- [Spring Cloud Gateway reference (server WebMvc)](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc.html)
- [Spring Cloud Config reference](https://docs.spring.io/spring-cloud-config/reference/)
- [Spring Cloud Netflix (Eureka) reference](https://docs.spring.io/spring-cloud-netflix/reference/)
- [Kubernetes: DNS for Services and Pods](https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/)
- [microservices.io: API gateway pattern](https://microservices.io/patterns/apigateway.html)
- [Spring Cloud release train and Boot compatibility](https://spring.io/projects/spring-cloud)

## What you will be able to do

- Explain the problems **GraphQL** and **gRPC** solve that REST handles poorly, and the problems they introduce.
- Build a GraphQL API with **Spring for GraphQL**: schema, `@QueryMapping`, `@SchemaMapping`, `@BatchMapping` and mutations.
- Define a service in **Protocol Buffers** and implement unary and streaming RPCs with **Spring gRPC**, including deadlines and status codes.
- Choose between REST, GraphQL and gRPC for a given banking scenario, and defend the choice.

## Why this matters in a bank or fintech

Most public and partner APIs in banking are REST. Inside a bank, though, you will meet GraphQL behind mobile and web apps (one request that fetches exactly the account, cards and recent transactions a screen needs) and gRPC between internal services (fast, strongly typed calls such as account lookups and fraud scoring on the payment path). Knowing all three, and when each is right, shows interviewers that you think in trade-offs rather than fashions.

## 1. The problems REST has

A mobile "home" screen needs the customer's name, three accounts with balances, the two most recent transactions per account, and card status. With REST that can mean:

- **Under-fetching:** many round trips (`/me`, `/accounts`, `/accounts/{id}/transactions` × 3, `/cards`), painful on a 3G connection.
- **Over-fetching:** each response includes fields the screen does not use.

Between internal services, JSON over HTTP/1.1 also has costs: text parsing, no strict contract unless you add OpenAPI, and one request per connection at a time.

## 2. GraphQL

GraphQL exposes **one endpoint** (`POST /graphql`) and a **typed schema**; the client asks for exactly the fields it needs.

```graphql
# src/main/resources/graphql/schema.graphqls
type Query {
    me: Customer!
    account(number: ID!): Account
}

type Customer {
    id: ID!
    fullName: String!
    accounts: [Account!]!
}

type Account {
    number: ID!
    type: AccountType!
    balance: Money!
    recentTransactions(limit: Int = 5): [Transaction!]!
}

type Money { amount: String!  currency: String! }
type Transaction { id: ID!  amount: Money!  narration: String  valueDate: String! }
enum AccountType { SAVINGS CURRENT DOMICILIARY }

type Mutation {
    renameAccount(number: ID!, nickname: String!): Account!
}
```

The mobile app's query:

```graphql
query HomeScreen {
  me {
    fullName
    accounts {
      number
      balance { amount currency }
      recentTransactions(limit: 2) { amount { amount } narration }
    }
  }
}
```

### Spring for GraphQL

With `spring-boot-starter-graphql` and `spring-boot-starter-webmvc`, annotated controllers resolve fields:

```java
@Controller
class CustomerGraphController {

    @QueryMapping
    Customer me(@AuthenticationPrincipal Jwt jwt) {
        return customers.byId(jwt.getSubject());
    }

    @SchemaMapping(typeName = "Customer")
    List<Account> accounts(Customer customer) {
        return accounts.ownedBy(customer.id());
    }

    @BatchMapping(typeName = "Account")
    Map<Account, List<Transaction>> recentTransactions(List<Account> accounts) {
        return transactions.recentFor(accounts, 5);   // ONE query for all accounts
    }

    @MutationMapping
    Account renameAccount(@Argument String number, @Argument String nickname) {
        return accounts.rename(number, nickname);
    }
}
```

`@BatchMapping` matters: resolving `recentTransactions` with a plain `@SchemaMapping` would run one query per account, the **N+1 problem** from Phase 1 topic 4.4, now triggered by clients. Batch loading (built on DataLoader) fetches them together.

### GraphQL costs and controls

- **Caching** is harder: everything is a `POST` to one URL, so HTTP caches and CDNs do not help.
- **Expensive queries:** clients can nest deeply (`accounts { transactions { counterparty { accounts { ... } } } }`). Limit query depth and complexity, and paginate list fields (the Relay "connection" pattern with cursors).
- **Errors** come back as `200 OK` with an `errors` array, so monitoring must read the body.
- **Authorisation** must be applied per field or per resolver, not just per URL.
- Turn off schema **introspection** and the GraphiQL UI in production for non-public APIs.

## 3. gRPC

gRPC is a remote-procedure-call framework over **HTTP/2** with **Protocol Buffers** (protobuf) as a compact binary format and a strict contract.

```protobuf
// src/main/proto/account_lookup.proto
syntax = "proto3";
package bank.accounts.v1;
option java_multiple_files = true;
option java_package = "ng.itlearning.accounts.grpc";

service AccountLookup {
  rpc GetAccount (GetAccountRequest) returns (Account);
  rpc ResolveAccountName (ResolveRequest) returns (ResolveResponse);       // NIP-style name enquiry
  rpc ListAccountsByCustomer (CustomerRequest) returns (stream Account);   // server streaming
}

message GetAccountRequest { string account_number = 1; }
message ResolveRequest { string bank_code = 1; string account_number = 2; }
message ResolveResponse { string account_name = 1; bool active = 2; }
message CustomerRequest { string customer_id = 1; }
message Account {
  string account_number = 1;
  string currency = 2;
  string available_balance = 3;   // decimal as string, as in REST
  AccountStatus status = 4;
}
enum AccountStatus { ACCOUNT_STATUS_UNSPECIFIED = 0; ACTIVE = 1; FROZEN = 2; CLOSED = 3; }
```

The `protobuf-maven-plugin` generates Java classes and a service base class at build time. With **Spring gRPC** (`spring-grpc-server-spring-boot-starter`), any bean that extends the generated base becomes a service:

```java
@Service
class AccountLookupService extends AccountLookupGrpc.AccountLookupImplBase {

    @Override
    public void getAccount(GetAccountRequest request, StreamObserver<Account> response) {
        accounts.find(request.getAccountNumber()).ifPresentOrElse(
                a -> { response.onNext(toProto(a)); response.onCompleted(); },
                () -> response.onError(Status.NOT_FOUND
                        .withDescription("No account " + mask(request.getAccountNumber()))
                        .asRuntimeException()));
    }

    @Override
    public void listAccountsByCustomer(CustomerRequest request, StreamObserver<Account> response) {
        accounts.ownedBy(request.getCustomerId()).forEach(a -> response.onNext(toProto(a)));
        response.onCompleted();
    }
}
```

The server listens on `spring.grpc.server.port` (9090 by default). A client calls with a **deadline**, which travels with the request so downstream services stop working on calls nobody is waiting for any more:

```java
Account account = stub.withDeadlineAfter(300, TimeUnit.MILLISECONDS)
                      .getAccount(GetAccountRequest.newBuilder().setAccountNumber(number).build());
```

gRPC has its own status codes; map them carefully at a REST facade:

| gRPC status | REST equivalent |
| --- | --- |
| `NOT_FOUND` | 404 |
| `INVALID_ARGUMENT` | 400 |
| `FAILED_PRECONDITION` | 422 or 409 |
| `PERMISSION_DENIED` / `UNAUTHENTICATED` | 403 / 401 |
| `DEADLINE_EXCEEDED` | 504 |
| `UNAVAILABLE` | 503 (retryable) |

### Protobuf evolution rules

Field **numbers**, not names, are the contract. Never reuse or change a field number; mark removed fields `reserved`; add new fields with new numbers (old clients ignore them). Keep `0` as an `UNSPECIFIED` enum value. Version packages (`bank.accounts.v1`) for breaking changes.

### gRPC costs

Browsers cannot call gRPC directly (gRPC-Web or a gateway is needed), binary payloads are harder to debug with `curl` (use `grpcurl`), and load balancers must understand HTTP/2 long-lived connections.

## 4. Choosing

| Situation | Good fit | Why |
| --- | --- | --- |
| Public or partner API (merchants, open banking) | **REST** + OpenAPI | Universal tooling, HTTP caching, easy for any language |
| Mobile and web apps with varied screens | **GraphQL** (often as a backend-for-frontend) | One round trip, client-shaped responses |
| Internal service-to-service calls on a hot path (fraud scoring, account lookup) | **gRPC** | Low latency, strict contracts, streaming, deadlines |
| Event notifications | Neither: messaging (Kafka) or webhooks | Asynchronous (Phase 3) |

Many banks run all three: gRPC inside, a GraphQL backend-for-frontend for their own apps, and REST at the edge for partners.

## Common mistakes

- Choosing GraphQL or gRPC because it is new, for an API that partners must consume.
- N+1 resolvers in GraphQL (no `@BatchMapping` or DataLoader).
- No depth or complexity limits on public GraphQL endpoints.
- Monitoring only HTTP status codes for GraphQL (errors arrive with `200`).
- Reusing protobuf field numbers or changing their types.
- gRPC calls without deadlines.
- Returning `UNKNOWN` for every gRPC error.

## Practice

1. Add a GraphQL endpoint to a Phase 1 capstone for a "home screen" query; prove with SQL logging that `@BatchMapping` runs one transaction query for all accounts.
2. Add a query depth limit and test that a deeply nested query is rejected.
3. Write the `account_lookup.proto` above, generate code with the protobuf Maven plugin, implement `GetAccount` and `ListAccountsByCustomer` with Spring gRPC, and call them with `grpcurl`.
4. Build a REST facade over the gRPC service that maps `NOT_FOUND`, `DEADLINE_EXCEEDED` and `UNAVAILABLE` to Problem Details.
5. Write a one-page decision record choosing REST, GraphQL or gRPC for: a merchant payments API, a mobile app dashboard, and fraud scoring during card authorisation.

## Key terms

Over-fetching, under-fetching, GraphQL schema, query, mutation, resolver, `@QueryMapping`, `@SchemaMapping`, `@BatchMapping`, DataLoader, N+1, query depth and complexity, introspection, backend-for-frontend, gRPC, HTTP/2, Protocol Buffers, field number, `reserved`, unary RPC, server streaming, `StreamObserver`, deadline, gRPC status code, `grpcurl`.

## Further reading

- [GraphQL Foundation: Learn GraphQL](https://graphql.org/learn/)
- [Spring for GraphQL reference documentation](https://docs.spring.io/spring-graphql/reference/)
- [gRPC: Introduction and core concepts](https://grpc.io/docs/what-is-grpc/core-concepts/)
- [Protocol Buffers: Language guide (proto3)](https://protobuf.dev/programming-guides/proto3/)
- [Spring gRPC reference documentation](https://docs.spring.io/spring-grpc/reference/)
- [Spring guide: Building a GraphQL service](https://spring.io/guides/gs/graphql-server/)

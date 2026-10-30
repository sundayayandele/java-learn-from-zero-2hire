## What you will be able to do

- Produce and consume Kafka records with Spring for Apache Kafka 4 and Spring Boot 4.1.
- Configure serialisation, consumer groups and topics, and know what Boot sets up for you.
- Understand the default error handling, and replace it with retries and a dead-letter topic.
- Test listeners against a real Kafka with Testcontainers.
- Compare Kafka with RabbitMQ (AMQP) and choose between them for a given job.

## Why this matters in a bank or fintech

Most Java teams in banks don't write raw Kafka client code; they use Spring for Apache Kafka, which gives listeners, retries, dead letters and testing support in the Spring style you already know. Many of the same banks also run RabbitMQ for task queues and integration with older systems, and interviewers like to ask "Kafka or RabbitMQ, and why?" This topic gives you working code for the first and a clear answer to the second.

## 1. Setting up

With Spring Boot 4.1, add the Kafka starter:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-kafka</artifactId>
</dependency>
```

and point it at a broker:

```properties
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP:localhost:9092}
spring.kafka.producer.acks=all
spring.kafka.consumer.auto-offset-reset=earliest
```

Boot creates a `KafkaTemplate` for producing, a listener container factory for `@KafkaListener`, and a `KafkaAdmin` that creates any `NewTopic` beans you declare. Keys and values are strings by default; ITL Pay sends JSON text and parses it with Boot's Jackson `JsonMapper`, which keeps the payload readable in the console consumer and makes poison messages easy to demonstrate. (Spring for Apache Kafka also has JSON serialisers, and schema-registry serialisers for Avro; topic 11.4.)

## 2. Declaring topics

```java
@Configuration(proxyBeanMethods = false)
class KafkaTopics {

    @Bean
    NewTopic transferEvents() {
        return TopicBuilder.name("transfer-events").partitions(3).build();
    }
}
```

Every ITL Pay service declares the topics it reads or writes, with the same partition count, and the broker's auto-creation is off (topic 11.1). In production, topics are often created by the platform team through code (Terraform, Strimzi `KafkaTopic` resources on Kubernetes) instead of by applications.

## 3. Producing

```java
kafka.send("fraud-decisions", event.reference(), json.writeValueAsString(decision))
     .get(10, TimeUnit.SECONDS);   // wait for the broker's acknowledgement
```

`send` is asynchronous and returns a `CompletableFuture`. Waiting for it (as here, or with a callback) is what tells you the record was stored; ignoring it means you never learn about failures. Always pass the **key**: it decides the partition and therefore the order.

Sending to Kafka **after** a database commit, or before it, can't be made atomic: one can succeed and the other fail. That's the "dual write" problem that topic 11.3's outbox solves. The fraud service gets away with sending directly because it has no database: if the send fails, the listener throws, the input record is redelivered, and the same input always produces the same decision.

## 4. Consuming

```java
@KafkaListener(topics = "transfer-events", groupId = "fraud")
void on(String message) throws Exception {
    var event = json.readValue(message, TransferEvent.class);
    if (!"TransferReserved".equals(event.type())) {
        return;
    }
    // ... decide, then publish to fraud-decisions
}
```

- The **group id** decides how instances share partitions (topic 11.1). Two different services must use two different group ids, or they'd split the events between them instead of each getting all of them.
- The container **commits the offset after the method returns** normally, so a crash in the middle means the record is delivered again: at least once.
- If the method throws, the container's **error handler** decides what happens.

The event record uses a **tolerant reader**: `@JsonIgnoreProperties(ignoreUnknown = true)`, so producers can add fields without breaking it (topic 11.4).

## 5. Error handling: the default and what to replace it with

Out of the box, Spring for Apache Kafka's `DefaultErrorHandler` retries a failed record **9 more times with no delay**, then **logs it and moves on**. Logging and skipping is the wrong default for money events. ITL Pay's notifications service replaces it:

```java
@Bean
DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafka) {
    var recoverer = new DeadLetterPublishingRecoverer(kafka,
            (record, ex) -> new TopicPartition(record.topic() + "-notifications-dlt", -1));
    var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000, 2));   // 1 try + 2 retries, 1 s apart
    handler.addNotRetryableExceptions(JacksonException.class);                     // unreadable: straight to the DLT
    return handler;
}
```

Boot applies a `CommonErrorHandler` bean to every listener. Topic 11.4 covers these retries, non-blocking retry topics and the dead-letter topic properly.

## 6. Testing with Testcontainers

Test against a real broker. Spring Boot's `@ServiceConnection` wires the container's address into the application:

```java
@SpringBootTest
@Testcontainers
class FraudScreeningTests {

    @Container
    @ServiceConnection
    static KafkaContainer kafkaContainer = new KafkaContainer("apache/kafka:4.1.1");

    @Autowired KafkaTemplate<String, String> kafka;

    @Test
    void clearsSmallTransfersRejectsLargeOnesAndDeadLettersPoisonMessages() {
        kafka.send("transfer-events", "T-BIG", """
                {"type":"TransferReserved","reference":"T-BIG","from":"A1","amount":25000.00}""");
        // ... then read fraud-decisions with a plain KafkaConsumer and assert, waiting with Awaitility
    }
}
```

Consumers are asynchronous, so assertions **wait** for a result (Awaitility's `await().atMost(...)`), never `Thread.sleep`. Testcontainers 2 uses the artifacts `testcontainers-kafka`, `testcontainers-postgresql` and `testcontainers-junit-jupiter`; Spring Boot 4.1 manages their versions. `spring-kafka-test` also offers an in-memory `@EmbeddedKafka` broker, which is faster to start but less like production.

## 7. RabbitMQ and AMQP, for comparison

**RabbitMQ** is a message broker built on AMQP. Producers publish to an **exchange**; **bindings** route messages to **queues** by routing key (`direct`, `topic`, `fanout` and `headers` exchanges); consumers take messages off a queue and **acknowledge** them, and an acknowledged message is gone. Failed messages can go to a **dead-letter exchange**. RabbitMQ 4 uses **quorum queues** for replicated, durable queues, and also offers **streams**, an append-only log similar to a Kafka partition.

With Spring Boot (`spring-boot-starter-amqp`):

```java
rabbit.convertAndSend("transfers", "transfer.reserved", payload);   // exchange, routing key, message

@RabbitListener(queues = "sms-notifications")
void on(String message) { ... }
```

| | Kafka | RabbitMQ |
| --- | --- | --- |
| Model | A replicated log; consumers track their own position | A broker that routes messages to queues and deletes them once acknowledged |
| Re-reading history | Yes, within retention: new consumers and replays | No for queues (yes for streams) |
| Ordering | Per partition, by key | Per queue, with one consumer |
| Routing | By topic and key; consumers filter | Rich routing with exchanges and bindings |
| Throughput | Very high, scales with partitions | High; less for very large fan-out of history |
| Typical banking use | Event streams: transfers, card events, audit, analytics, CDC | Task queues: send this SMS, generate this statement; integration with older systems |

A sensible rule: **facts that several systems need, now and later → Kafka. Work items for one worker pool → RabbitMQ** (or a Kafka topic with one consumer group, if you'd rather run one technology). Kafka is also adding queue-style **share groups** (KIP-932) during the 4.x releases, which narrows the gap.

## Worked example: the fraud service end to end

1. The fraud service declares `transfer-events` and `fraud-decisions` (3 partitions each) and listens with group `fraud`.
2. A test publishes three events: a small `TransferReserved`, a large one, and a `TransferCleared` (which fraud ignores), plus one message that isn't JSON.
3. The listener clears ₦2,500 and rejects ₦25,000, publishing both decisions keyed by reference.
4. The unreadable message fails with a `JacksonException`, which is configured as not retryable, so it goes straight to `transfer-events-fraud-dlt` and the `@DltHandler` logs it.
5. The test reads `fraud-decisions` and the DLT with a plain consumer and passes in about 20 seconds, including starting Kafka.

## Common mistakes

- Two services sharing one group id, so each gets only half the events.
- Ignoring the future returned by `send`, so failures go unnoticed.
- Keeping the default error handler, which logs and skips a record after ten quick failures.
- Saving to the database and sending to Kafka as two separate steps (the dual write; topic 11.3).
- `Thread.sleep` in tests instead of waiting for a condition.
- Choosing RabbitMQ or Kafka by habit instead of by the job: replayable facts or work items.

## Practice

1. In the starter, add a `statements` service with group `statements` that stores every `TransferCleared` event; prove with a test that it and `notifications` both receive every event.
2. Change the fraud listener to log the partition and offset (`@Header(KafkaHeaders.RECEIVED_PARTITION)`), and confirm the per-key ordering with five transfers.
3. Remove the custom error handler from notifications, send a poison message, and describe what the default handler does with it. Put it back.
4. Write the same "send an SMS" consumer with `@RabbitListener` against a RabbitMQ Testcontainer (`testcontainers-rabbitmq`), with a dead-letter exchange.
5. Write half a page: for ITL Pay, which flows would you put on RabbitMQ, if any, and why?

## Key terms

Spring for Apache Kafka, `KafkaTemplate`, `@KafkaListener`, listener container, group id, `NewTopic`, `KafkaAdmin`, serialiser, deserialiser, tolerant reader, error handler, `DefaultErrorHandler`, `DeadLetterPublishingRecoverer`, back-off, Testcontainers, `@ServiceConnection`, Awaitility, `@EmbeddedKafka`, AMQP, RabbitMQ, exchange, binding, routing key, queue, acknowledgement, dead-letter exchange, quorum queue, stream, share group.

## Further reading

- [Spring for Apache Kafka reference](https://docs.spring.io/spring-kafka/reference/)
- [Spring Boot reference: Apache Kafka support](https://docs.spring.io/spring-boot/reference/messaging/kafka.html)
- [Testcontainers for Java: Kafka module](https://java.testcontainers.org/modules/kafka/)
- [RabbitMQ tutorials (Java)](https://www.rabbitmq.com/tutorials)
- [Spring AMQP reference](https://docs.spring.io/spring-amqp/reference/)

## What you will be able to do

- Explain what Kafka is for, and when a message broker beats a direct HTTP call.
- Describe topics, partitions, offsets and replication, and how a key decides ordering.
- Explain consumer groups: how work is shared, and what happens when consumers join or leave.
- Choose producer and consumer settings for at-least-once delivery, and explain why "exactly once" has limits.
- Run Kafka 4 locally in KRaft mode and use the command-line tools to look inside.

## Why this matters in a bank or fintech

A transfer produces facts that many systems care about: the ledger, fraud screening, notifications, statements, the data warehouse, regulatory reporting. If transfers called each one over HTTP, it would wait on all of them and fail when any of them is down (topic 10.3). With Kafka, transfers records the fact once (`TransferReserved`), and each system reads it in its own time, at its own pace, and can catch up after an outage. Nigerian banks and fintechs use Kafka for exactly this: card and transfer events feeding fraud engines, SMS and push alerts, and analytics, often hundreds of millions of events a month.

## 1. What Kafka is

**Apache Kafka** is a distributed, durable **log**: producers append records, and consumers read them in order, each remembering how far it has got. Records aren't deleted when read; they stay until the retention period ends (seven days by default), so a new consumer can read history and a crashed one can resume.

Use events through Kafka when:

- **Several systems** need the same fact (fan-out), and the producer shouldn't know who they are.
- The work can happen **a moment later** (eventually consistent, topic 10.4).
- You need to **absorb spikes**: salary-day transfers queue up in Kafka instead of overwhelming the SMS provider.
- You want a **replayable history** to rebuild a read model or fix a bug.

Keep a **synchronous call** when the caller needs the answer now to continue: "does this account have enough money?" is a question, not an event.

## 2. Topics, partitions and offsets

A **topic** is a named stream of records, such as `transfer-events`. Each topic is split into **partitions**, each an ordered, append-only log stored on a broker:

```
transfer-events
  partition 0: [0] TRF-1 Reserved  [1] TRF-1 Cleared  [2] TRF-9 Reserved ...
  partition 1: [0] TRF-4 Reserved  [1] TRF-4 Failed ...
  partition 2: [0] TRF-2 Reserved ...
```

- Each record has a **key**, a **value**, headers and a timestamp. The position of a record in its partition is its **offset**.
- **Order is guaranteed only within a partition.** The producer picks the partition by hashing the key, so **all records with the same key go to the same partition, in order.** Key transfer events by transfer reference and every consumer sees `Reserved` before `Cleared` for the same transfer.
- Partitions are the unit of **parallelism**: a topic with 6 partitions can be read by up to 6 consumers in one group at once.
- Each partition is **replicated** to several brokers (replication factor 3 in production). One replica is the leader; the others follow. If the leader's broker dies, a follower takes over.

Choose the partition count with care: adding partitions later changes which partition a key maps to, so ordering for existing keys can break during the change.

## 3. Consumer groups

Consumers that share a **group id** split a topic's partitions between them; each partition is read by exactly one consumer in the group. Different groups read independently:

```
transfer-events (3 partitions)
  group "fraud":          consumer A ← p0, p1     consumer B ← p2
  group "notifications":  consumer C ← p0, p1, p2
```

- Each group stores its **committed offset** per partition (in Kafka's internal `__consumer_offsets` topic). After a restart, it resumes from there.
- When a consumer joins or leaves, the group **rebalances**: partitions move between consumers. Kafka 4 makes the newer consumer group protocol (KIP-848) generally available, which rebalances incrementally instead of pausing the whole group; enable it with `group.protocol=consumer`.
- A new group with no committed offset starts according to **`auto.offset.reset`**: `earliest` (from the beginning) or `latest` (only new records, the default).
- More consumers than partitions means some sit idle.

**Consumer lag** is how far a group is behind the end of each partition. It's the first thing to watch: growing lag means consumers can't keep up (Module 13 puts it on a dashboard).

## 4. Delivery guarantees

Where a consumer **commits** its offset decides what happens after a crash:

| Commit | After a crash | Name |
| --- | --- | --- |
| Before processing | Records in flight are skipped | At most once |
| After processing | Records in flight are processed again | **At least once** |

Banks choose **at least once** and make processing **idempotent** (topic 11.3), so a duplicate does no harm. Spring for Apache Kafka commits after your listener returns, so you get at-least-once by default.

On the producer side:

- **`acks=all`**: the leader waits until all in-sync replicas have the record before confirming. With `min.insync.replicas=2` and replication factor 3, a confirmed record survives the loss of a broker.
- **Idempotent producer** (on by default since Kafka 3.0): the broker discards duplicates caused by the producer's own retries.
- **Transactions** let a service read from Kafka, process and write to Kafka atomically: "exactly once" **within Kafka**. They can't include your database or an SMS provider, which is why the outbox and idempotent consumers (topic 11.3) still matter.

## 5. Kafka 4 and KRaft

Kafka used to need a separate ZooKeeper cluster for its metadata. **Kafka 4.0 removed ZooKeeper**: brokers now manage metadata themselves with the **KRaft** protocol, and some nodes act as **controllers**. For local work, one node can be both broker and controller. The ITL Pay starter runs it in Docker Compose:

```yaml
kafka:
  image: apache/kafka:4.1.1
  environment:                       # one broker that is also its own KRaft controller
    KAFKA_NODE_ID: 1
    KAFKA_PROCESS_ROLES: broker,controller
    KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
    KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
    KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
```

**Advertised listeners** are the address the broker tells clients to use. Inside Compose that's `kafka:9092`; a client on your laptop would need a second listener advertised as `localhost`.

**Switch off topic auto-creation**, as production clusters do. While building the starter we hit the reason: the fraud consumer subscribed to `transfer-events` before transfers had created it, so the broker auto-created it with **one** partition. Transfers then declared it with three, Kafka added two partitions, and the consumers didn't notice the new partitions for up to five minutes (the metadata refresh interval). A transfer that landed on a new partition sat unprocessed. Now every service declares the topics it uses with the same partition count, and the broker refuses to invent topics.

## 6. Looking inside with the command-line tools

The Kafka image includes the standard tools under `/opt/kafka/bin`:

```bash
# Topics, partitions, leaders
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --describe --topic transfer-events

# Read every event with its key
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 \
     --topic transfer-events --from-beginning --property print.key=true

# Each group's offsets and lag per partition
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --group fraud
```

The consumer-groups command is how we found the bug above: the `fraud` group had partition 0 and nothing else.

## Worked example: following one transfer

1. `POST /api/transfers` with `TRF-1` for ₦2,500. Transfers reserves the funds and writes `TransferReserved` to its outbox (topic 11.3); the relay sends it to `transfer-events` with key `TRF-1`. The key hashes to, say, partition 2.
2. Group `fraud` (one consumer, partitions 0–2) reads it, clears the transfer and writes `{"reference":"TRF-1","decision":"CLEARED"}` to `fraud-decisions`, also keyed `TRF-1`.
3. Group `notifications` reads the same `TransferReserved` independently and sends "being processed".
4. Transfers reads the decision, moves `TRF-1` to `CLEARED` and publishes `TransferCleared`, again on partition 2, after `TransferReserved`.
5. `kafka-consumer-groups.sh --describe --group notifications` shows lag 0 on every partition: everything is processed.

## Common mistakes

- Using Kafka for request–response ("is the balance enough?"); that's an HTTP call.
- No key, or a random key, for events that must stay in order.
- Expecting order across partitions or across topics.
- Committing offsets before processing, and silently losing events after a crash.
- `acks=1` or `min.insync.replicas=1` for money events.
- Leaving topic auto-creation on, or changing partition counts casually.
- Not watching consumer lag.

## Practice

1. Start the ITL Pay stack, send five transfers, and use the console consumer to print `transfer-events` with keys and partitions (`--property print.partition=true`). Check that each reference stays on one partition.
2. Scale fraud to three instances (`docker compose up -d --scale fraud=3`) and describe the `fraud` group. Then scale to four and explain the idle consumer.
3. Stop notifications, send three transfers, describe its group to see the lag, then start it again and watch the lag drain.
4. Create a topic with replication factor 1, and write two sentences on why it's unacceptable for production money events.
5. Explain to a colleague, in five sentences, why at-least-once plus idempotent consumers is the normal choice in banking.

## Key terms

Event, broker, topic, partition, offset, key, record, retention, replication factor, leader, follower, in-sync replicas, `acks=all`, `min.insync.replicas`, idempotent producer, consumer group, rebalance, committed offset, `auto.offset.reset`, consumer lag, at most once, at least once, exactly once, Kafka transactions, KRaft, controller, advertised listener.

## Further reading

- [Apache Kafka documentation: introduction and design](https://kafka.apache.org/documentation/#introduction)
- [Apache Kafka 4.0 release announcement](https://kafka.apache.org/blog#apache_kafka_400_release_announcement)
- [Confluent Developer: Kafka 101](https://developer.confluent.io/courses/apache-kafka/events/)
- [Apache Kafka Docker image (apache/kafka)](https://hub.docker.com/r/apache/kafka)
- Gwen Shapira, Todd Palino, Rajini Sivaram and Krit Petty, *Kafka: The Definitive Guide*, 2nd edition (2021)

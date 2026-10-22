# Capstone 7: Mobile money fraud rules engine

← [All capstones](../README.md) · [Starter](starter/README.md)

Weeks 6–12. Modules used: 1, 2, 3, 4.

## Business context

Mobile money and agent banking moved millions of Nigerians and Africans into digital payments, and fraud followed: account takeovers that empty a wallet through a rapid series of transfers and cash-outs, mule accounts that receive and immediately withdraw, and amounts structured just under limits. Operators fight this first with **rules** that run on every transaction in milliseconds, then with machine learning. Rules must be fast, explainable, easy to change, and measured: a rule that blocks 5% of good customers is a business problem too.

You'll build a **real-time fraud rules engine** and replay 6.3 million synthetic mobile-money transactions through it, measuring each rule's precision and recall against the dataset's fraud labels.

## What you'll build

### Rules as a sealed hierarchy

```java
public sealed interface FraudRule permits AmountThresholdRule, VelocityRule, DrainRule,
        NewDestinationCashOutRule, BalanceMismatchRule { }

public sealed interface Verdict permits Allow, Review, Block { }
```

Suggested rules (each configurable and switchable without code changes):

| Rule | Signal |
| --- | --- |
| Amount threshold | Single TRANSFER or CASH_OUT above a configured amount |
| Velocity | More than N outgoing transactions from one account within M simulated hours |
| Account drain | Transaction empties ≥ 95% of the origin balance |
| Transfer-then-cash-out | A TRANSFER to an account that performs a CASH_OUT of a similar amount within a short window (a classic mule pattern) |
| Balance mismatch | Origin balance before − amount ≠ balance after (data anomaly worth reviewing) |

The engine evaluates every enabled rule (pattern-matching `switch` over the sealed type), combines them into a verdict with **reason codes**, and records the decision.

### Real-time API and replay

| Method and path | Purpose |
| --- | --- |
| `POST /api/v1/evaluations` | Evaluate one transaction in real time; returns verdict and reasons in < 50 ms p95 |
| `POST /api/v1/replays` | Replay the PaySim file through the engine (async, 202) |
| `GET /api/v1/replays/{id}` | Progress and results |
| `GET /api/v1/replays/{id}/rule-performance` | Per rule: alerts, true positives, false positives, precision, recall |
| `GET/PUT /api/v1/rules` | View and tune rule parameters (audited, versioned) |

### Engineering requirements

1. **State for velocity and mule rules** (recent transactions per account) must be bounded in memory: sliding windows keyed by account in a `ConcurrentHashMap` with eviction by simulated time, or Redis sorted sets (topic 3.6). Explain your choice.
2. **Replay concurrency:** PaySim is ordered by simulated hour (`step`). Process accounts in parallel but keep each account's transactions **in order** (for example, partition by origin account hash into N single-threaded workers fed by bounded queues). Prove that the parallel replay produces exactly the same verdicts as a sequential one.
3. Persist only verdicts that aren't `Allow` (plus aggregate counts), in JDBC batches, so the database isn't the bottleneck.
4. Metrics: evaluations per second, verdicts by rule and outcome, evaluation latency timer.

## Data

| | |
| --- | --- |
| Dataset | **PaySim**: synthetic mobile money transactions, 6,362,620 rows with types CASH_IN, CASH_OUT, DEBIT, PAYMENT, TRANSFER, balances before and after, and an `isFraud` label |
| Download | <https://www.kaggle.com/datasets/ealaxi/paysim1> (free Kaggle account; ~470 MB CSV) |
| Licence | **CC BY-SA 4.0**: commercial use allowed with attribution; if you redistribute modified data, use the same licence |
| Attribution | E. A. Lopez-Rojas, A. Elmir, S. Axelsson, "PaySim: A financial mobile money simulator for fraud detection", 28th European Modeling and Simulation Symposium, 2016. Data licensed CC BY-SA 4.0 |
| Note | Don't commit the CSV. Commit a few hundred rows as a test fixture, under CC BY-SA 4.0, with the attribution in a README beside it |

## Skills you'll demonstrate

Sealed types and pattern matching (1.6), streams for statistics (1.5), concurrency with ordering guarantees, bounded queues and back-pressure (2.1–2.3), memory-bounded state (2.4), JDBC batching (3.5), configuration and metrics (4.4, 4.5).

## Acceptance criteria

- [ ] Each rule has unit tests for firing and not firing at its boundaries.
- [ ] The transfer-then-cash-out rule fires on a hand-built mule scenario and not on an unrelated cash-out.
- [ ] Parallel and sequential replays of a 200,000-row sample produce identical verdict counts per rule.
- [ ] The full replay completes, and the rule-performance report shows precision and recall per rule.
- [ ] Memory stays bounded during the full replay (heap after GC stable in GC logs or Actuator metrics).
- [ ] `POST /evaluations` p95 latency under 50 ms locally (measure with a simple load script).

## Infrastructure and deployment

App 2 vCPU / 2 GB for full replays (1 vCPU / 1 GB is enough for the API alone); PostgreSQL 1 vCPU / 2 GB / 10 GB. Replays are batch jobs: run them locally or on the VPS, not on scale-to-zero platforms with short request timeouts. CI from the starter; deploy with the [deployment guide](../../deploy/README.md).

## Stretch goals

- Tune thresholds to maximise recall at a fixed false-positive budget, and chart the trade-off.
- Hot-reload rule parameters without restart (and without race conditions).
- Expose decisions as events in an outbox table for a future Kafka pipeline (Module 11).

## Interview talking points

- How you kept per-account order while processing in parallel.
- Precision vs recall: what you'd tell the business about each rule.
- How you kept stateful rules memory-bounded.

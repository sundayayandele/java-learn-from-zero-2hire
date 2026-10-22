# Lab 2.C: Find and fix five concurrency bugs

← [Module 2](../../phases/01-foundations/02-concurrency-jvm/README.md)

A tiny in-memory "mini bank" with **five planted concurrency bugs**. Every class looks reasonable in a single-threaded test. Your job is to prove each bug with a test, fix it, and explain the fix.

| # | Class | Symptom you should reproduce | Topic |
| --- | --- | --- | --- |
| 1 | `Wallet` | Two concurrent withdrawals both succeed and overdraw the wallet | 2.2 check-then-act |
| 2 | `DailyLimitTracker` | Concurrent transfers exceed the daily limit, and totals are lost | 2.2 read-modify-write, unsafe `HashMap` |
| 3 | `TransferService` | Transfers A→B and B→A at the same time hang forever | 2.2 deadlock |
| 4 | `SettlementPoller` | `requestStop()` sometimes never stops the poller | 2.2 visibility |
| 5 | `FxRateCache` | The slow provider is called several times for the same pair under load, and the map can be corrupted | 2.2 unsafe shared map, check-then-act |

## How to work

1. `./mvnw test`. `WalletRaceTest` is written for you and **fails**: that's bug 1 reproduced. Read it: it shows the pattern (a `CountDownLatch` to start threads together, many rounds, an invariant assertion).
2. Write a similar failing test for bugs 2, 4 and 5. For bug 3, write a test with a timeout (`assertTimeoutPreemptively`) and also run the scenario from a `main`, take a thread dump with `jcmd <pid> Thread.print`, and paste the "Found one Java-level deadlock" section into your findings.
3. Fix each bug with the simplest correct tool: `synchronized`, a lock, an atomic, a concurrent collection, `volatile`, or immutability. Avoid locking everything with one global lock.
4. All tests green, repeated 10 times (`for i in $(seq 10); do ./mvnw -q test || break; done`).
5. Write `FINDINGS.md`: for each bug, the symptom, the root cause in one or two sentences, the fix, and why you chose it over the alternatives. Write it like a short incident report.

No solution is published for this lab; mentors review `FINDINGS.md`.

## What this module gives you

Five weeks to go from "I can write a small program" to writing clean, modern, tested Java 21 from the command line and the IDE, with every change in Git. The order is deliberate: tools and Git first (1.1, 1.2), the language (1.3–1.5), unit tests as soon as you have code worth testing (1.6), modern Java and clean code (1.7–1.10), then patterns and reading old code (1.11, 1.12) so you can work in real codebases from your first week on the job. If you have never programmed before, do the [week-0 on-ramp](../00-programming-on-ramp/README.md) first.

**Optional:** 1.13 Kotlin, 1.14 Localization and 1.15 Java 22 to 25. Do them if your target employer uses Kotlin or Java 25, or you're preparing for the Oracle OCP exam; they aren't in the exam or the certificate requirements.

**Weekly rhythm (16–19 hours, see the [phase plan](../README.md#how-much-time-this-phase-takes)):** read the lesson and code along (40%), watch the videos (15%), do the lesson's practice tasks (25%), take the topic CBT and aim for 70% (5%), and spend the rest on this module's project.

**Exit check for Module 1:** all 12 core topic CBTs attempted (aim for 70%+), and one module project below pushed to GitHub with a README, passing tests and a green GitHub Actions run.

## Module projects

Pick **at least one** (two if you have time). Each must use Maven, Java 21, JUnit tests, records where they fit, `BigDecimal` for money, and no `double` amounts anywhere. Use the [project starter](../../../templates/java-cli-starter/README.md) to begin.

### Project 1.A: Wallet and transfers simulator

A command-line wallet system for a small fintech.

- **Domain:** `Customer`, `Wallet` (NGN balance, daily limit, status ACTIVE/FROZEN), `Transfer`, `StatementLine`.
- **Commands:** `open-wallet`, `deposit`, `transfer <from> <to> <amount>`, `freeze <wallet>`, `statement <wallet> [from] [to]`.
- **Rules:** no overdrafts; per-wallet daily outbound limit; frozen wallets can neither send nor receive; every transfer has a unique reference and appears on both statements.
- **Must use:** a sealed `TransferResult` (`Completed`, `Rejected(reason)`), domain exceptions with masked wallet IDs, `Map<String, Wallet>` storage behind a `WalletRepository` interface, streams for statements.
- **Acceptance:** tests for insufficient funds, daily-limit breach, frozen wallet, and a statement whose closing balance equals opening balance plus credits minus debits.

### Project 1.B: Library management CLI

- **Domain:** `Book`, `Member`, `Loan`, `Fine`.
- **Commands:** add a book, register a member, borrow, return, list overdue loans, report fines per member.
- **Rules:** maximum five active loans per member; 14-day loan period; a fine of ₦50 per day overdue, capped at the book's replacement value; members with unpaid fines over ₦1,000 cannot borrow.
- **Must use:** `java.time.LocalDate` and `ChronoUnit.DAYS.between`, `TreeMap` for the overdue report sorted by due date, `Optional` for lookups.
- **Acceptance:** tests that use a fixed `Clock` so overdue logic is deterministic.

### Project 1.C: Payroll calculator with PAYE-style tax rules

- **Domain:** `Employee`, `PayComponent` (basic, housing, transport), `Deduction` (pension, NHF), `Payslip`.
- **Rules:** implement a progressive, banded income tax with a consolidated relief allowance, modelled on Nigeria's PAYE rules. Keep the bands and rates in one configuration file (CSV or JSON) so a law change is a data change, not a code change, and record the source and effective date of the rates you use in the README. Pension (employee 8%) and NHF (2.5% of basic) come off before tax where the rules say so.
- **Output:** one payslip per employee per month, plus a monthly summary CSV for the finance team.
- **Must use:** `BigDecimal` with `HALF_EVEN` rounding at clearly defined points, a `TaxBand` record, a `NavigableMap` for band lookup, a text block for the payslip template.
- **Acceptance:** at least six tests covering each band boundary, zero income and a very high earner. Explain your rounding policy in the README.

### Project 1.D: Retail inventory manager with reorder alerts

- **Domain:** `Product` (SKU, name, unit cost, reorder level, reorder quantity), `StockMovement` (sealed: `Receipt`, `Sale`, `Adjustment`), `Supplier`.
- **Features:** record movements; compute current stock per SKU from the movement history (never store a mutable count); list products below reorder level grouped by supplier; value the inventory at cost.
- **Must use:** pattern matching `switch` over the sealed movement type, `Collectors.groupingBy`, `Comparator` chains for the report.
- **Acceptance:** tests showing stock can never go negative (a sale larger than stock is rejected) and that the reorder report changes when a receipt arrives.

## How these projects are assessed

| Criterion | Not yet | Competent | Strong |
| --- | --- | --- | --- |
| Correctness | Some rules missing or wrong | All rules implemented and tested | Edge cases (boundaries, empty input) tested too |
| Modern Java | Java 8 style throughout | Records, sealed types, switch expressions, streams where they fit | Clear judgement on when *not* to use them |
| Money and time | `double` or `Date` used | `BigDecimal` and `java.time` throughout | Rounding policy documented and tested |
| Code quality | Long methods, unclear names | Small methods, good names, no magic numbers | Reads like a description of the business rules |
| Delivery | Runs only in the IDE | `./mvnw verify` green locally and on GitHub Actions | README with usage examples and design notes |

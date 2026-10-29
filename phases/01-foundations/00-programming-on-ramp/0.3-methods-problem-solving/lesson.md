## What you will be able to do

- Write methods that take parameters and return a value, and call them from `main`.
- Choose good method names and keep each method to one job.
- Break a problem into steps with plain-language pseudocode before writing Java.
- Check each method with small hand-worked examples, including edge cases.
- Know what to do when you're stuck, and how to ask for help well.

## Why this matters in a bank or fintech

Real banking code is thousands of small methods with clear names: `calculateFee`, `isWithinDailyLimit`, `maskAccountNumber`. Each can be read, tested and changed on its own. Engineers who can split a vague requirement ("charge the right fee") into small, checkable steps are the ones teams trust with new features. That skill matters more in interviews than knowing any library.

## 1. Methods: name a piece of work

A **method** is a named block of code you can run (call) whenever you need it. You've used methods already: `System.out.println`, `in.nextLong()`, `ref.length()`. Now you write your own:

```java
public class Main {

    static long transferFee(long amountKobo) {
        if (amountKobo <= 500_000) {         // up to ₦5,000
            return 1_000;                    // ₦10
        } else if (amountKobo <= 5_000_000) {
            return 2_500;                    // ₦25
        }
        return 5_000;                        // ₦50
    }

    public static void main(String[] args) {
        long fee = transferFee(1_200_000);
        System.out.println("Fee in kobo: " + fee);      // 2500
        System.out.println(transferFee(300_000));       // 1000
    }
}
```

Reading the first line of `transferFee`:

- `static`: for now, every method you write starts with it (topic 1.4 explains why).
- `long`: the **return type**, what the method gives back. Use `void` if it gives back nothing (it only prints, for example).
- `transferFee`: the name, a verb phrase in `camelCase`.
- `(long amountKobo)`: the **parameter**, a value the caller must supply. A method can take several, separated by commas.

`return` hands the value back and ends the method immediately. Every path through a non-`void` method must reach a `return`, or Java won't compile it (`missing return statement`).

When `main` calls `transferFee(1_200_000)`, the value `1_200_000` is copied into `amountKobo`, the method runs, and the call is replaced by what it returned.

## 2. Methods with several parameters, and `boolean` methods

```java
static boolean canTransfer(long balance, long amount, boolean isFrozen) {
    return !isFrozen && amount > 0 && amount <= balance;
}

static String formatNaira(long kobo) {
    return String.format("₦%,d.%02d", kobo / 100, kobo % 100);
}
```

A method that answers a yes/no question returns `boolean` and is usually named `is...`, `has...` or `can...`. Then the calling code reads like a sentence:

```java
if (canTransfer(balance, amount, isFrozen)) {
    balance -= amount + transferFee(amount);
    System.out.println("Sent. Balance " + formatNaira(balance));
}
```

(`%,d` adds thousands separators: `₦1,250.00`.)

**Variables inside a method belong to that method.** `amountKobo` exists only while `transferFee` runs; `main` can't see it. To get a result out, `return` it.

## 3. One job per method

A good method does **one thing** that its name describes completely. Compare:

```java
// Hard to read, hard to check: it parses, calculates and prints
static void doIt(String input) { ... 40 lines ... }

// Each step can be checked on its own
static long parseAmountKobo(String input) { ... }
static long transferFee(long amountKobo) { ... }
static String formatNaira(long kobo) { ... }
static void printReceipt(String name, long amount, long fee) { ... }
```

Signs a method should be split: you need "and" to describe it, it's longer than your screen, or you've copied the same five lines into two places.

## 4. Breaking a problem into steps

Before writing Java, write the steps in plain language: **pseudocode**. It needn't be formal; it must be clear enough that someone else could follow it.

**Problem:** "A savings account earns 1% interest per month. Given a starting balance, a monthly deposit and a goal, how many months until the goal is reached?"

```
read start, deposit, goal
if deposit is 0 and start < goal: say "never" and stop
balance = start, months = 0
while balance < goal:
    balance = balance + interest on balance (1%)
    balance = balance + deposit
    months = months + 1
print months
```

Then ask the questions that find bugs before they exist:

- **What are the inputs and the output?** Three amounts in; a number of months out.
- **What are the edge cases?** The goal already reached (answer 0). A zero deposit (and zero interest: never). Very large goals (the loop runs for a long time: is that acceptable?).
- **Is interest added before or after the deposit?** The requirement doesn't say. In a real job, you **ask**. Here, decide and write it down.

Now each line of pseudocode becomes a small piece of Java, and the interest calculation becomes its own method:

```java
static long monthlyInterest(long balanceKobo) {
    return balanceKobo / 100;          // 1%, rounded down to the kobo
}

static int monthsToGoal(long start, long deposit, long goal) {
    if (deposit == 0 && monthlyInterest(start) == 0 && start < goal) {
        return -1;                     // never: the caller prints a message
    }
    long balance = start;
    int months = 0;
    while (balance < goal) {
        balance += monthlyInterest(balance);
        balance += deposit;
        months++;
    }
    return months;
}
```

Returning `-1` for "never" is a common beginner convention; topic 1.9 shows better tools for it (exceptions and `Optional`).

## 5. Checking your methods with examples

Work out a few answers **by hand first**, then compare:

```java
public static void main(String[] args) {
    check(transferFee(500_000) == 1_000, "fee at exactly ₦5,000");
    check(transferFee(500_001) == 2_500, "fee just above ₦5,000");
    check(monthsToGoal(0, 1_000_000, 1_000_000) == 1, "one deposit reaches the goal");
    check(monthsToGoal(5_000_000, 0, 1_000_000) == 0, "goal already reached");
    check(monthsToGoal(0, 0, 1_000_000) == -1, "can never reach the goal");
}

static void check(boolean ok, String what) {
    System.out.println((ok ? "PASS " : "FAIL ") + what);
}
```

Notice which examples were chosen: **exactly on a boundary** (₦5,000), **just over it**, **already done**, and **impossible**. Bugs live at the edges, not in the middle. In topic 1.6 you'll replace this home-made `check` with JUnit, the testing tool every Java team uses, but the thinking is the same.

## 6. When you're stuck

1. **Re-read the problem.** Write the inputs, the output and one worked example by hand.
2. **Shrink it.** Get the simplest version working (one transfer, one month, one item), then grow it.
3. **Print the values.** Add `System.out.println("balance=" + balance + " months=" + months);` inside the loop and compare with your hand trace.
4. **Explain it out loud**, line by line, to a friend or a rubber duck. You'll often hear the bug.
5. **Ask after 20–30 minutes**, in the *Questions and help* forum: say what you're trying to do, paste the code and the **full** error message, and say what you've already tried. A good question gets a fast answer, and writing it often solves the problem.

Using an AI assistant to *explain an error message* is fine. Using it to write the answer for you skips exactly the practice this week exists for; you'll need these skills unaided in the live-coding assessment.

## Worked example: a fee-and-receipt program in small methods

```java
import java.util.Scanner;

public class Main {

    static long transferFee(long amountKobo) {
        if (amountKobo <= 500_000) return 1_000;
        if (amountKobo <= 5_000_000) return 2_500;
        return 5_000;
    }

    static long vat(long feeKobo) {
        return feeKobo * 75 / 1000;            // 7.5%, rounded down to the kobo
    }

    static String formatNaira(long kobo) {
        return String.format("₦%,d.%02d", kobo / 100, kobo % 100);
    }

    static void printReceipt(String name, long amount) {
        long fee = transferFee(amount);
        long tax = vat(fee);
        System.out.println("To:     " + name);
        System.out.println("Amount: " + formatNaira(amount));
        System.out.println("Fee:    " + formatNaira(fee) + " + VAT " + formatNaira(tax));
        System.out.println("Total:  " + formatNaira(amount + fee + tax));
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        System.out.print("Recipient: ");
        String name = in.nextLine();
        System.out.print("Amount in kobo: ");
        long amount = in.nextLong();
        printReceipt(name, amount);
    }
}
```

`main` now reads like the requirement: ask for the name, ask for the amount, print the receipt. Each rule (the fee bands, VAT, formatting) lives in exactly one place, so when the bank changes a fee band, you change one line.

## Common mistakes

- Printing a result inside a method instead of returning it, so the caller can't use it.
- A non-`void` method where one path has no `return` (`missing return statement`).
- Expecting a variable declared in one method to be visible in another.
- Long methods named `process` or `doIt` that hide three jobs.
- Testing only the "normal" case and never the boundaries.
- Starting to type Java before you can say the steps in plain language.

## Practice

1. Write `static boolean isValidAccountNumber(String s)`: exactly 10 characters, all digits. Check it with five examples, including `"012345678"` (9 digits) and `"01234S6789"`.
2. Write `static String maskAccount(String s)` that keeps only the last four digits, and use it in a receipt.
3. Write `static long splitEvenly(long totalKobo, int people)` and `static long remainder(long totalKobo, int people)`. Check that `people × share + remainder == total` for three examples.
4. Write pseudocode, then Java, for: "Print a loan repayment table: equal monthly payments of principal, plus 2% interest per month on what is still owed." Check the final balance is exactly 0.
5. Take your longest practice program from lesson 0.2 and split it into at least three methods with clear names.

## Key terms

Method, call, parameter, argument, return type, `return`, `void`, `static`, local variable, scope, pseudocode, decomposition, edge case, boundary, hand-worked example, rubber-duck debugging.

## Further reading

- [dev.java: Defining Methods](https://dev.java/learn/classes-objects/defining-methods/)
- [The Java Tutorials: Passing Information to a Method](https://docs.oracle.com/javase/tutorial/java/javaOO/arguments.html)
- [How to ask a good question (Stack Overflow)](https://stackoverflow.com/help/how-to-ask)

## What this module gives you

One week, before topic 1.1, for people who have never written a program. You'll learn to think the way a program runs: values in variables, decisions, loops, arrays, strings and small methods, and how to read an error message instead of fearing it. Everything is plain Java run from one file, with no build tools yet; Module 1 adds those.

**Do you need it?** Take the **entry check** below (15 questions, about 15 minutes, no notes). On Moodle it's the *Entry check: do you need the on-ramp?* quiz.

- **12 or more out of 15:** skip the on-ramp and start at [topic 1.1](../01-modern-java-oop/1.1-tooling-build-linux/README.md). Skim the three lessons if you want a refresher.
- **Under 12:** do the on-ramp first. It's about 12–15 hours. Everyone starts Module 1 at the same pace afterwards, and the on-ramp doesn't count towards the exam or the certificate.

**Set-up for this week:** install a Java 21 JDK ([Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21)) and [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/). Topic 1.1 explains both properly; for now you only need to run `java Hello.java` in a terminal or press the green ▶ next to `main` in IntelliJ.

**Weekly rhythm:** read each lesson and type every example yourself (50%), do the "Try it" exercises in the lesson (30%), take the topic quiz (5%), and build one small project below (15%). Being stuck for 20 minutes is normal; being stuck for an hour means ask in the *Questions and help* forum, with the code and the full error message.

**Exit check for the on-ramp:** the three topic quizzes attempted, one project below working, and the entry check retaken with 12 or more.

## Module projects

Pick **one**. Each is a single `Main.java` file you run with `java Main.java`. No `double` for money: use whole kobo in a `long` (₦12.50 is `1250` kobo), which is exactly what topic 1.3 builds on.

### Project 0.A: Market-stall receipt printer

A trader enters what a customer bought; the program prints a receipt.

- Read the number of items, then each item's name, unit price in kobo and quantity.
- Print one line per item with its line total, then the subtotal, a 7.5% VAT line (rounded to the nearest kobo) and the total, formatted as naira (`₦1,250.00`).
- Reject a negative price or quantity with a clear message and ask again.
- **Done when:** your receipt for 3 items matches a total you worked out by hand.

### Project 0.B: Savings goal planner

A customer saves the same amount every month towards a goal.

- Read the goal in naira, the monthly saving and an annual interest rate in whole percent.
- Print a month-by-month table (month, deposit, interest, balance) until the goal is reached, and the number of months it took.
- If the monthly saving is zero, explain that the goal can never be reached instead of looping forever.
- **Done when:** a ₦120,000 goal at ₦10,000 a month and 0% takes exactly 12 months, and a non-zero rate takes fewer.

### Project 0.C: Class score report

A tutor enters scores for a class.

- Read up to 30 names and scores (0–100) into two arrays.
- Print the average, the highest and lowest scorer, how many passed (50 or more) and a grade per learner (A 70+, B 60–69, C 50–59, F under 50).
- Write each calculation as its own method: `average`, `highestIndex`, `countPassed`, `grade`.
- **Done when:** your output matches a hand calculation for five learners, including one score of exactly 50.

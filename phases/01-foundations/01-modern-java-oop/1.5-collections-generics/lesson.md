## What you will be able to do

- Choose the right collection (`List`, `Set`, `Map`, `Queue`/`Deque`) and implementation for a job, and justify it by its performance.
- Explain how `HashMap` works and why `equals`/`hashCode` matter to it.
- Sort with `Comparable` and `Comparator`, and iterate safely.
- Create immutable collections and avoid accidental shared mutation.
- Write and read generic classes and methods, including bounded types and wildcards (PECS).

## Why this matters in a bank or fintech

A statement is a `List` of transactions in order. A sanctions screen is a lookup in a `Set` of names. A balance cache is a `Map` from account number to balance. A payment retry queue is a `Queue`. Choosing the wrong one is the difference between a nightly batch that takes four minutes and one that takes four hours: `list.contains` on a million-entry list is a million comparisons, while `set.contains` is roughly one.

## 1. The Collections Framework at a glance

```text
Iterable
 └── Collection
      ├── List      ordered, index-based, duplicates allowed     ArrayList, LinkedList
      ├── Set       no duplicates                                HashSet, LinkedHashSet, TreeSet
      └── Queue     processing order                             ArrayDeque, PriorityQueue
           └── Deque  both ends                                  ArrayDeque, LinkedList
Map (separate hierarchy)  key → value, unique keys               HashMap, LinkedHashMap, TreeMap
```

Program to the interface, choose the implementation once:

```java
List<Transaction> statement = new ArrayList<>();   // callers see List, not ArrayList
Map<String, BigDecimal> balances = new HashMap<>();
```

Java 21 added **sequenced collections** (`SequencedCollection`, `SequencedMap`): a uniform way to reach the first and last elements of any ordered collection with `getFirst()`, `getLast()` and `reversed()`.

```java
statement.getFirst();          // instead of statement.get(0)
statement.getLast();           // instead of statement.get(statement.size() - 1)
```

## 2. Lists

| Operation | `ArrayList` | `LinkedList` |
| --- | --- | --- |
| `get(i)` | O(1) | O(*n*) |
| add at end | O(1) amortised | O(1) |
| insert/remove in the middle | O(*n*) (shifts elements) | O(*n*) to find, O(1) to relink |
| memory per element | small | large (node with two pointers) |
| CPU cache friendliness | excellent | poor |

In practice **use `ArrayList`** almost always. `LinkedList` rarely wins even where theory suggests it should, because walking scattered nodes is slow on modern CPUs.

```java
List<Transaction> txs = new ArrayList<>();
txs.add(tx);
txs.get(0);
txs.size();
txs.contains(tx);     // O(n): uses equals on every element
txs.remove(tx);
```

**Big-O notation** describes how cost grows with size *n*: O(1) constant, O(log *n*) grows slowly (sorted structures), O(*n*) linear, O(*n*²) painful (a loop inside a loop over the same data).

## 3. Sets

A `Set` holds no duplicates, as defined by `equals` (and `hashCode` for hash sets, `compareTo` for tree sets).

| Implementation | Order | `add` / `contains` |
| --- | --- | --- |
| `HashSet` | none | O(1) average |
| `LinkedHashSet` | insertion order | O(1) average |
| `TreeSet` | sorted | O(log n) |

```java
Set<String> sanctioned = new HashSet<>(loadSanctionedNames());
if (sanctioned.contains(normalise(beneficiaryName))) { block(); }

Set<String> processedReferences = new HashSet<>();
if (!processedReferences.add(tx.reference())) {   // add returns false if already present
    log.warn("Duplicate transaction reference {}", tx.reference());
}
```

## 4. Maps

```java
Map<String, BigDecimal> balances = new HashMap<>();
balances.put("0123456789", new BigDecimal("1500.00"));
balances.get("0123456789");                     // 1500.00
balances.get("9999999999");                     // null: key absent
balances.getOrDefault("9999999999", BigDecimal.ZERO);
balances.containsKey("0123456789");

// Update-in-place helpers: no get-then-put dance
balances.merge("0123456789", new BigDecimal("200.00"), BigDecimal::add);   // add to existing or insert
Map<String, List<Transaction>> byAccount = new HashMap<>();
byAccount.computeIfAbsent(tx.account(), k -> new ArrayList<>()).add(tx);   // group

for (Map.Entry<String, BigDecimal> e : balances.entrySet()) {
    System.out.println(e.getKey() + " = " + e.getValue());
}
```

| Implementation | Key order | Operations |
| --- | --- | --- |
| `HashMap` | none | O(1) average |
| `LinkedHashMap` | insertion (or access) order | O(1) average; can be a tiny LRU cache |
| `TreeMap` | sorted by key | O(log n); `floorKey`, `headMap`, range queries |

`TreeMap` is ideal for tiered rules: "which fee band does ₦73,000 fall into?"

```java
NavigableMap<BigDecimal, BigDecimal> feeBands = new TreeMap<>(Map.of(
        new BigDecimal("0"), new BigDecimal("10.00"),
        new BigDecimal("5000"), new BigDecimal("25.00"),
        new BigDecimal("50000"), new BigDecimal("50.00")));
feeBands.floorEntry(new BigDecimal("73000")).getValue();   // 50.00
```

### How `HashMap` works

A `HashMap` is an array of buckets. To store a key it computes `key.hashCode()`, maps that to a bucket index, and stores the entry there. To look up a key it goes straight to the bucket and uses `equals` to find the exact entry among any collisions. When the map gets too full (load factor 0.75) it doubles the array and rehashes. Since Java 8, a bucket with many collisions turns into a balanced tree, so worst-case lookups are O(log *n*) instead of O(*n*).

Consequences you must remember:

- Keys need a correct `equals` **and** `hashCode` (topic 1.4).
- **Never mutate a key after inserting it.** If its hash code changes, the map looks in the wrong bucket and the entry is effectively lost.
- `HashMap` is not thread-safe. Use `ConcurrentHashMap` for shared mutable maps (topic 2.2).

## 5. Queues and deques

```java
Deque<PaymentInstruction> retryQueue = new ArrayDeque<>();
retryQueue.offerLast(instr);          // enqueue
PaymentInstruction next = retryQueue.pollFirst();   // dequeue, null if empty

Deque<String> undo = new ArrayDeque<>();   // also the right way to build a stack
undo.push("step1"); undo.pop();            // don't use the legacy Stack class

PriorityQueue<Payment> byPriority =
        new PriorityQueue<>(Comparator.comparing(Payment::dueDate));   // smallest first
```

`ArrayDeque` is the default queue and stack. `PriorityQueue` always hands out the smallest element next, which suits scheduling payments by due date.

## 6. Sorting and comparing

A class can define its **natural order** by implementing `Comparable`:

```java
public record Money(BigDecimal amount, String currency) implements Comparable<Money> {
    @Override public int compareTo(Money other) {
        if (!currency.equals(other.currency)) throw new IllegalArgumentException("Currency mismatch");
        return amount.compareTo(other.amount);
    }
}
```

For any other order, pass a `Comparator`, which you build fluently:

```java
transactions.sort(Comparator.comparing(Transaction::valueDate)
        .thenComparing(Transaction::amount, Comparator.reverseOrder()));
```

`compareTo` returns negative, zero or positive. Never implement it as `a - b` on ints: subtraction can overflow. Use `Integer.compare(a, b)`.

## 7. Iterating and modifying safely

```java
for (Transaction tx : txs) {
    if (tx.isReversed()) txs.remove(tx);      // ConcurrentModificationException
}
txs.removeIf(Transaction::isReversed);        // correct and clear
```

The enhanced `for` uses an `Iterator` under the hood, and the collection detects that it was changed behind the iterator's back. Use `removeIf`, an explicit `Iterator.remove()`, or build a new list.

## 8. Immutable collections

```java
List<String> currencies = List.of("NGN", "USD", "GBP");     // unmodifiable; add() throws
Map<String, Integer> decimals = Map.of("NGN", 2, "JPY", 0);
List<Transaction> copy = List.copyOf(mutableList);         // defensive, unmodifiable copy
Collections.unmodifiableList(list)                          // a read-only VIEW: still changes if list changes
```

`List.of` and friends also reject `null` elements. Returning unmodifiable collections from your classes protects their internal state:

```java
public List<Transaction> transactions() { return List.copyOf(transactions); }
```

## 9. Generics

Generics let a class or method work with any type while the compiler still checks types. Without them you would cast everything from `Object` and find mistakes at runtime.

```java
List<String> names = new ArrayList<>();      // the diamond <> infers <String>
names.add(42);                               // compile error: caught early
String first = names.get(0);                 // no cast needed
```

### Writing generic types and methods

```java
public final class Result<T> {               // T is a type parameter
    private final T value;
    private final String error;

    private Result(T value, String error) { this.value = value; this.error = error; }
    public static <T> Result<T> ok(T value) { return new Result<>(value, null); }
    public static <T> Result<T> fail(String error) { return new Result<>(null, error); }
    public boolean isOk() { return error == null; }
    public T value() { return value; }
}

Result<Account> r = Result.ok(account);
```

A generic method declares its own type parameter before the return type:

```java
static <K, V> Map<V, K> invert(Map<K, V> map) { ... }
```

### Bounded types

```java
static <T extends Comparable<T>> T max(List<T> items) { ... }   // T must be comparable
static BigDecimal total(List<? extends Money> amounts) { ... }
```

### Wildcards and PECS

`List<Integer>` is **not** a subtype of `List<Number>`, even though `Integer` is a `Number`. If it were, you could add a `Double` to a list of integers. Wildcards relax this safely:

- `? extends T`: a **producer** of `T`s. You can read `T`s out, but not add.
- `? super T`: a **consumer** of `T`s. You can add `T`s, but only read `Object`s out.

The mnemonic is **PECS: Producer Extends, Consumer Super.**

```java
static void copyAll(List<? extends Transaction> source, List<? super Transaction> target) {
    for (Transaction t : source) target.add(t);
}
```

### Type erasure

Generic types exist only at compile time. At runtime `List<String>` and `List<Integer>` are both just `List`. That is why you cannot write `new T()`, `T[]` or `instanceof List<String>`, and why frameworks sometimes ask you to pass a `Class<T>` explicitly.

## Worked example: an end-of-day summary

```java
Map<String, BigDecimal> totalsByCurrency = new TreeMap<>();
Set<String> seenRefs = new HashSet<>();
List<Transaction> duplicates = new ArrayList<>();

for (Transaction tx : dayTransactions) {
    if (!seenRefs.add(tx.reference())) { duplicates.add(tx); continue; }
    totalsByCurrency.merge(tx.currency(), tx.amount(), BigDecimal::add);
}
totalsByCurrency.forEach((ccy, total) -> System.out.println(ccy + " " + total));
System.out.println(duplicates.size() + " duplicate references");
```

One pass, O(*n*), sorted output by currency, and duplicates detected with a set. The naive version (`for` over transactions with `list.contains` on the processed list) is O(*n*²) and takes hours on a million rows.

## Common mistakes

- `list.contains` or `list.remove(Object)` inside a loop: accidental O(n²).
- Using `HashMap` keys whose `hashCode` changes, or without `equals`/`hashCode`.
- Removing from a collection inside a for-each loop.
- Returning internal mutable lists from a class.
- Raw types (`List list = new ArrayList();`) that throw away type checking.
- `Collections.unmodifiableList` mistaken for a copy.

## Practice

1. Load 100,000 random account numbers into an `ArrayList` and a `HashSet`. Time 10,000 `contains` calls on each and explain the difference.
2. Group a list of transactions by account into `Map<String, List<Transaction>>` with `computeIfAbsent`, then print accounts sorted by total debit, largest first.
3. Implement fee bands with a `TreeMap` and `floorEntry`, and test the boundaries (exactly ₦5,000, ₦4,999.99).
4. Write `static <T> List<List<T>> batches(List<T> items, int size)` that splits a list into batches for bulk inserts. Test it with an empty list and a size that does not divide evenly.

## Key terms

Collection, List, Set, Map, Queue, Deque, sequenced collection, Big-O, hash table, bucket, load factor, `Comparable`, `Comparator`, `ConcurrentModificationException`, unmodifiable collection, generics, type parameter, bounded type, wildcard, PECS, type erasure.

## Further reading

- [The Java Tutorials: Collections](https://docs.oracle.com/javase/tutorial/collections/index.html)
- [JEP 431: Sequenced Collections](https://openjdk.org/jeps/431)
- [The Java Tutorials: Generics](https://docs.oracle.com/javase/tutorial/java/generics/index.html)
- [dev.java (Oracle): The Collections Framework](https://dev.java/learn/api/collections-and-streams/collections-framework/)
- [dev.java (Oracle): Generics](https://dev.java/learn/language/fp/generics/)

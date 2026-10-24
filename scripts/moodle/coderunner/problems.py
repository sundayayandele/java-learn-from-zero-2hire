"""Which practice/dsa problems become CodeRunner questions, and their test cases.

Each test is a Java snippet run inside the question's tester class; it prints results with
System.out.println or show(...) (which prints arrays readably). Large-input tests use within(millis, () -> ...),
which prints the result or "Too slow" after a wall-clock budget. Expected outputs are not written here:
build_coderunner.py computes them by running the reference solution in practice/dsa/solutions.
A test that should throw prints the exception's simple class name (the template catches it).
Tests marked example=True are shown as examples in the question text.
"""

# (phase, path under ng/itlearning/practice, tests); tests are (code, example)
PROBLEMS = [
    # ---------------- Phase 1 (practice weeks 2-13) ----------------
    (1, "arrays/TwoSumTransfers", [
        ("show(TwoSumTransfers.find(new long[] {500_00, 2_000_00, 1_250_00, 750_00}, 2_000_00));", True),
        ("show(TwoSumTransfers.find(new long[] {1, 4, 3, 2, 3}, 6));", True),
        ("show(TwoSumTransfers.find(new long[] {300, 300}, 600));", False),
        ("show(TwoSumTransfers.find(new long[] {1, 2, 3}, 100));", False),
        ("show(TwoSumTransfers.find(new long[0], 0));", False),
        ("// 200,000 amounts: an O(n^2) answer is too slow here (3-second budget).\n"
         "long[] amounts = new long[200_000];\n"
         "for (int i = 0; i < amounts.length; i++) amounts[i] = i * 2L;\n"
         "amounts[amounts.length - 1] = 1;\n"
         "within(3000, () -> TwoSumTransfers.find(amounts, 3));", False),
    ]),
    (1, "arrays/FirstDuplicateReference", [
        ('System.out.println(FirstDuplicateReference.find(List.of("TRF1", "TRF2", "TRF3", "TRF2", "TRF1")));', True),
        ('System.out.println(FirstDuplicateReference.find(List.of("abc-1", " ABC-2", "Abc-2 ")));', True),
        ('System.out.println(FirstDuplicateReference.find(List.of("A", "B", "C")));', False),
        ("System.out.println(FirstDuplicateReference.find(List.of()));", False),
    ]),
    (1, "arrays/TotalsByAccount", [
        ("System.out.println(TotalsByAccount.totals(List.of(\n"
         '        new TotalsByAccount.Posting("0003", 5_000), new TotalsByAccount.Posting("0001", 10_000),\n'
         '        new TotalsByAccount.Posting("0002", 7_000), new TotalsByAccount.Posting("0001", -3_000),\n'
         '        new TotalsByAccount.Posting("0003", 2_000), new TotalsByAccount.Posting("0004", -100))));', True),
        ("System.out.println(TotalsByAccount.totals(List.of(\n"
         '        new TotalsByAccount.Posting("9", -5), new TotalsByAccount.Posting("1", -5),\n'
         '        new TotalsByAccount.Posting("5", 0))));', False),
        ("System.out.println(TotalsByAccount.totals(List.of()));", False),
    ]),
    (1, "arrays/LongestActiveStreak", [
        ("System.out.println(LongestActiveStreak.longest(List.of(\n"
         '        LocalDate.parse("2026-03-02"), LocalDate.parse("2026-02-28"), LocalDate.parse("2026-03-01"),\n'
         '        LocalDate.parse("2026-03-10"), LocalDate.parse("2026-02-27"), LocalDate.parse("2026-03-01"))));', True),
        ("System.out.println(LongestActiveStreak.longest(List.of()));", False),
        ('System.out.println(LongestActiveStreak.longest(List.of(LocalDate.parse("2026-01-01"))));', False),
        ("// 100,001 dates, 3-second budget: sorting works, but aim for O(n).\n"
         "List<LocalDate> days = new ArrayList<>();\n"
         'LocalDate start = LocalDate.parse("2000-01-01");\n'
         "for (int i = 0; i < 100_000; i++) days.add(start.plusDays(i * 2L));\n"
         "days.add(start.plusDays(1));\n"
         "within(3000, () -> LongestActiveStreak.longest(days));", False),
    ]),
    (1, "windows/MergeSortedStatements", [
        ("var a = List.of(new MergeSortedStatements.Entry(1, \"a1\"), new MergeSortedStatements.Entry(5, \"a5\"),\n"
         "        new MergeSortedStatements.Entry(5, \"a5b\"));\n"
         "var b = List.of(new MergeSortedStatements.Entry(2, \"b2\"), new MergeSortedStatements.Entry(5, \"b5\"),\n"
         "        new MergeSortedStatements.Entry(9, \"b9\"));\n"
         "System.out.println(MergeSortedStatements.merge(a, b).stream().map(MergeSortedStatements.Entry::reference).toList());", True),
        ('System.out.println(MergeSortedStatements.merge(List.of(), List.of(new MergeSortedStatements.Entry(1, "x"))));', False),
        ("System.out.println(MergeSortedStatements.merge(List.of(), List.of()));", False),
    ]),
    (1, "windows/ReferencePalindrome", [
        ('System.out.println(ReferencePalindrome.isPalindrome("A man, a plan, a canal: Panama"));', True),
        ('System.out.println(ReferencePalindrome.isPalindrome("TRF-2026"));', True),
        ('System.out.println(ReferencePalindrome.isPalindrome("12-3-21"));', False),
        ('System.out.println(ReferencePalindrome.isPalindrome(""));', False),
        ('System.out.println(ReferencePalindrome.isPalindrome(",.!"));', False),
        ('System.out.println(ReferencePalindrome.isPalindrome("ab"));', False),
    ]),
    (1, "windows/BestKDayWindow", [
        ("System.out.println(BestKDayWindow.maxTotal(new long[] {5, -2, 9, 1, -8, 7, 7}, 3));", True),
        ("System.out.println(BestKDayWindow.maxTotal(new long[] {-5, -2, -9}, 1));", False),
        ("System.out.println(BestKDayWindow.maxTotal(new long[] {4, 4}, 2));", False),
        ("System.out.println(BestKDayWindow.maxTotal(new long[] {1}, 2));", True),
        ("System.out.println(BestKDayWindow.maxTotal(new long[] {1}, 0));", False),
    ]),
    (1, "windows/LongestUniqueRun", [
        ('System.out.println(LongestUniqueRun.length("abcabcbb"));', True),
        ('System.out.println(LongestUniqueRun.length("bbbbb"));', False),
        ('System.out.println(LongestUniqueRun.length("pwwkew"));', False),
        ('System.out.println(LongestUniqueRun.length("abba"));', False),
        ('System.out.println(LongestUniqueRun.length(""));', False),
    ]),
    (1, "strings/Luhn", [
        ('System.out.println(Luhn.isValid("4242424242424242"));', True),
        ('System.out.println(Luhn.isValid("4242 4242 4242 4242"));', True),
        ('System.out.println(Luhn.isValid("5555555555554444"));', False),
        ('System.out.println(Luhn.isValid("4242424242424241"));', False),
        ('System.out.println(Luhn.isValid("4242-4242-4242-4242"));', False),
        ('System.out.println(Luhn.isValid("424242424242"));', False),
        ('System.out.println(Luhn.isValid("42424242424242424242"));', False),
    ]),
    (1, "strings/NubanCheckDigit", [
        ('System.out.println(NubanCheckDigit.checkDigit("058", "000000001"));', True),
        ('System.out.println(NubanCheckDigit.checkDigit("011", "123456789"));', False),
        ('System.out.println(NubanCheckDigit.checkDigit("000", "000000000"));', False),
        ('System.out.println(NubanCheckDigit.isValid("011", "1234567895"));', True),
        ('System.out.println(NubanCheckDigit.isValid("011", "1234567894"));', False),
        ('System.out.println(NubanCheckDigit.checkDigit("58", "000000001"));', False),
        ('System.out.println(NubanCheckDigit.isValid("058", "12345"));', False),
    ]),
    (1, "strings/RunLength", [
        ('System.out.println(RunLength.compress("PPPPSSF"));', True),
        ('System.out.println(RunLength.compress("AAAAAAAAAAAAB"));', False),
        ('System.out.println(RunLength.compress("PSF"));', True),
        ('System.out.println(RunLength.compress("PPSS"));', False),
        ('System.out.println("[" + RunLength.compress("") + "]");', False),
    ]),
    (1, "stacks/BalancedBrackets", [
        ('System.out.println(BalancedBrackets.isBalanced("(amount > [limit]) && {channel}"));', True),
        ('System.out.println(BalancedBrackets.isBalanced("(]"));', True),
        ('System.out.println(BalancedBrackets.isBalanced(""));', False),
        ('System.out.println(BalancedBrackets.isBalanced("{[()()]}"));', False),
        ('System.out.println(BalancedBrackets.isBalanced("(("));', False),
        ('System.out.println(BalancedBrackets.isBalanced("())"));', False),
        ('System.out.println(BalancedBrackets.isBalanced("{[}]"));', False),
    ]),
    (1, "stacks/DaysUntilHigherRate", [
        ("show(DaysUntilHigherRate.waits(new double[] {1530, 1528, 1535, 1535, 1520, 1540}));", True),
        ("show(DaysUntilHigherRate.waits(new double[] {3, 2, 1}));", False),
        ("show(DaysUntilHigherRate.waits(new double[0]));", False),
    ]),
    (1, "search/MergeWindows", [
        ("System.out.println(MergeWindows.merge(List.of(new MergeWindows.Window(8, 10), new MergeWindows.Window(1, 3),\n"
         "        new MergeWindows.Window(2, 6), new MergeWindows.Window(15, 18), new MergeWindows.Window(6, 7))));", True),
        ("System.out.println(MergeWindows.merge(List.of(new MergeWindows.Window(1, 10), new MergeWindows.Window(2, 3))));", False),
        ("System.out.println(MergeWindows.merge(List.of()));", False),
    ]),
    (1, "search/KthLargestTransfer", [
        ("System.out.println(KthLargestTransfer.kthLargest(new long[] {3, 2, 1, 5, 6, 4}, 2));", True),
        ("System.out.println(KthLargestTransfer.kthLargest(new long[] {3, 2, 3, 1, 2, 4, 5, 5, 6}, 4));", False),
        ("// 50,000 amounts, 3-second budget.\n"
         "long[] amounts = new Random(1).longs(50_000, 0, 1_000_000).toArray();\n"
         "within(3000, () -> KthLargestTransfer.kthLargest(amounts, 100));", False),
        ("System.out.println(KthLargestTransfer.kthLargest(new long[] {1}, 2));", False),
        ("System.out.println(KthLargestTransfer.kthLargest(new long[] {1}, 0));", False),
    ]),

    # ---------------- Phase 2 (practice weeks 14-24) ----------------
    (2, "lists/ReverseList", [
        ("System.out.println(Node.toList(ReverseList.reverse(Node.of(1, 2, 3, 4))));", True),
        ("System.out.println(Node.toList(ReverseList.reverse(Node.of(7))));", False),
        ("System.out.println(ReverseList.reverse(null));", False),
    ]),
    (2, "lists/HasCycle", [
        ("System.out.println(HasCycle.hasCycle(Node.of(1, 2, 3, 4, 5)));", True),
        ("Node head = Node.of(1, 2, 3, 4, 5);\n"
         "head.next.next.next.next.next = head.next;   // 5 -> 2\n"
         "System.out.println(HasCycle.hasCycle(head));", True),
        ("Node single = new Node(1);\n"
         "single.next = single;\n"
         "System.out.println(HasCycle.hasCycle(single));", False),
        ("System.out.println(HasCycle.hasCycle(null));", False),
    ]),
    (2, "lists/MergeSortedLists", [
        ("System.out.println(Node.toList(MergeSortedLists.merge(Node.of(1, 2, 4), Node.of(1, 3, 4))));", True),
        ("System.out.println(Node.toList(MergeSortedLists.merge(null, Node.of(0))));", False),
        ("System.out.println(MergeSortedLists.merge(null, null));", False),
        ("// The merged list must reuse the existing nodes.\n"
         "Node a = Node.of(1);\n"
         "Node b = Node.of(2);\n"
         "Node merged = MergeSortedLists.merge(a, b);\n"
         "System.out.println((merged == a) + \" \" + (merged.next == b));", False),
    ]),
    (2, "search/StatementOrdering", [
        ("List<StatementOrdering.Txn> input = new ArrayList<>(List.of(\n"
         '        new StatementOrdering.Txn("C", new BigDecimal("10.00"), Instant.parse("2026-01-01T10:00:00Z")),\n'
         '        new StatementOrdering.Txn("A", new BigDecimal("500.00"), Instant.parse("2026-01-01T09:00:00Z")),\n'
         '        new StatementOrdering.Txn("B", new BigDecimal("10.0"), Instant.parse("2026-01-01T09:00:00Z")),\n'
         '        new StatementOrdering.Txn("D", new BigDecimal("10.00"), Instant.parse("2026-01-01T09:00:00Z"))));\n'
         "System.out.println(StatementOrdering.sorted(input).stream().map(StatementOrdering.Txn::reference).toList());", True),
        ("// The input list must not change.\n"
         "List<StatementOrdering.Txn> input = new ArrayList<>(List.of(\n"
         '        new StatementOrdering.Txn("C", new BigDecimal("10.00"), Instant.parse("2026-01-01T10:00:00Z")),\n'
         '        new StatementOrdering.Txn("A", new BigDecimal("500.00"), Instant.parse("2026-01-01T09:00:00Z"))));\n'
         "StatementOrdering.sorted(input);\n"
         "System.out.println(input.stream().map(StatementOrdering.Txn::reference).toList());", False),
        ("System.out.println(StatementOrdering.sorted(List.of()));", False),
    ]),
    (2, "trees/TreeDepth", [
        ("System.out.println(TreeDepth.depth(TreeNode.of(1, TreeNode.of(2, TreeNode.of(4, null, null), null),\n"
         "        TreeNode.of(3, null, null))));", True),
        ("System.out.println(TreeDepth.depth(null));", False),
        ("System.out.println(TreeDepth.depth(new TreeNode(1)));", False),
    ]),
    (2, "trees/ValidSearchTree", [
        ("System.out.println(ValidSearchTree.isValid(TreeNode.of(5, TreeNode.of(3, TreeNode.of(1, null, null),\n"
         "        TreeNode.of(4, null, null)), TreeNode.of(8, null, TreeNode.of(9, null, null)))));", True),
        ("// 6 is in 5's left subtree, so this is not a search tree.\n"
         "System.out.println(ValidSearchTree.isValid(TreeNode.of(5, TreeNode.of(3, null, TreeNode.of(6, null, null)),\n"
         "        TreeNode.of(8, null, null))));", True),
        ("System.out.println(ValidSearchTree.isValid(null));", False),
        ("System.out.println(ValidSearchTree.isValid(TreeNode.of(Integer.MAX_VALUE,\n"
         "        TreeNode.of(Integer.MIN_VALUE, null, null), null)));", False),
        ("System.out.println(ValidSearchTree.isValid(TreeNode.of(5, TreeNode.of(5, null, null), null)));", False),
    ]),
    (2, "trees/LevelOrder", [
        ('TreeNode ceo = new TreeNode("Ada", 1);\n'
         'ceo.left = new TreeNode("Bayo", 2);\n'
         'ceo.right = new TreeNode("Chi", 3);\n'
         'ceo.left.right = new TreeNode("Dele", 4);\n'
         'ceo.right.left = new TreeNode("Efe", 5);\n'
         "System.out.println(LevelOrder.levels(ceo));", True),
        ("System.out.println(LevelOrder.levels(null));", False),
        ('System.out.println(LevelOrder.levels(new TreeNode("Solo", 1)));', False),
    ]),
    (2, "graphs/FewestHops", [
        ('Map<String, List<String>> sentTo = Map.of("A", List.of("B", "C"), "B", List.of("D"),\n'
         '        "C", List.of("D", "E"), "D", List.of("F"), "E", List.of("F"), "F", List.of("A"));\n'
         'System.out.println(FewestHops.hops(sentTo, "A", "F"));', True),
        ('Map<String, List<String>> sentTo = Map.of("A", List.of("B", "C"), "B", List.of("D"),\n'
         '        "C", List.of("D", "E"), "D", List.of("F"), "E", List.of("F"), "F", List.of("A"));\n'
         'System.out.println(FewestHops.hops(sentTo, "E", "D"));', False),
        ('System.out.println(FewestHops.hops(Map.of("A", List.of("B")), "A", "A"));', False),
        ('Map<String, List<String>> sentTo = Map.of("A", List.of("B", "C"), "B", List.of("D"),\n'
         '        "C", List.of("D", "E"), "D", List.of("F"), "E", List.of("F"), "F", List.of("A"));\n'
         'System.out.println(FewestHops.hops(sentTo, "A", "Z"));', False),
        ('System.out.println(FewestHops.hops(Map.of(), "X", "Y"));', False),
    ]),
    (2, "graphs/LinkedAccountGroups", [
        ('System.out.println(LinkedAccountGroups.groups(List.of(new LinkedAccountGroups.Transfer("A", "B"),\n'
         '        new LinkedAccountGroups.Transfer("C", "B"), new LinkedAccountGroups.Transfer("D", "E"),\n'
         '        new LinkedAccountGroups.Transfer("F", "F"))));', True),
        ("System.out.println(LinkedAccountGroups.groups(List.of()));", False),
        ("// A chain of 50,000 transfers, 3-second budget: deep recursion overflows the stack here.\n"
         "List<LinkedAccountGroups.Transfer> chain = new ArrayList<>();\n"
         'for (int i = 0; i < 50_000; i++) chain.add(new LinkedAccountGroups.Transfer("acc" + i, "acc" + (i + 1)));\n'
         "within(3000, () -> LinkedAccountGroups.groups(chain));", False),
    ]),
    (2, "graphs/JobOrder", [
        ('System.out.println(JobOrder.order(Map.of(\n'
         '        "close-day", List.of("interest", "fees"),\n'
         '        "interest", List.of("load-balances"),\n'
         '        "fees", List.of("load-balances"),\n'
         '        "load-balances", List.<String>of(),\n'
         '        "audit", List.<String>of())));', True),
        ('System.out.println(JobOrder.order(Map.of("a", List.of("b"), "b", List.of("c"), "c", List.of("a"))));', True),
        ('System.out.println(JobOrder.order(Map.of("only", List.<String>of())));', False),
    ]),
    (2, "dp/FewestNotes", [
        ("System.out.println(FewestNotes.fewest(new int[] {200, 500, 1000}, 1700));", True),
        ("// Greedy (4 + 1 + 1) gives 3 notes; the answer is 3 + 3.\n"
         "System.out.println(FewestNotes.fewest(new int[] {1, 3, 4}, 6));", True),
        ("System.out.println(FewestNotes.fewest(new int[] {500}, 300));", False),
        ("System.out.println(FewestNotes.fewest(new int[] {500}, 0));", False),
    ]),
    (2, "dp/BestSingleTrade", [
        ("System.out.println(BestSingleTrade.maxProfit(new long[] {7, 1, 5, 3, 6, 4}));", True),
        ("System.out.println(BestSingleTrade.maxProfit(new long[] {7, 6, 4, 3, 1}));", False),
        ("System.out.println(BestSingleTrade.maxProfit(new long[0]));", False),
    ]),
    (2, "dp/NonAdjacentBonus", [
        ("System.out.println(NonAdjacentBonus.best(new long[] {2, 7, 9, 3, 1}));", True),
        ("System.out.println(NonAdjacentBonus.best(new long[] {5, 1, 1, 5}));", False),
        ("System.out.println(NonAdjacentBonus.best(new long[0]));", False),
    ]),
    (2, "design/LruCache", [
        ("LruCache<String, String> cache = new LruCache<>(2);\n"
         'cache.put("0123456789", "ADA EZE");\n'
         'cache.put("0987654321", "BAYO OKAFOR");\n'
         'System.out.println(cache.get("0123456789"));   // now the most recently used\n'
         'cache.put("1111111111", "CHI BELLO");          // evicts 0987654321\n'
         'System.out.println(cache.get("0987654321"));\n'
         'System.out.println(cache.get("1111111111"));\n'
         "System.out.println(cache.size());", True),
        ("// Updating a key counts as a use.\n"
         "LruCache<Integer, Integer> cache = new LruCache<>(2);\n"
         "cache.put(1, 1);\n"
         "cache.put(2, 2);\n"
         "cache.put(1, 10);\n"
         "cache.put(3, 3);\n"
         "System.out.println(cache.get(2) + \" \" + cache.get(1));", False),
        ("new LruCache<String, String>(0);\n"
         'System.out.println("created");', False),
    ]),
    (2, "design/SplitAmount", [
        ("show(SplitAmount.split(10_000, 3));", True),
        ("show(SplitAmount.split(2, 3));", False),
        ("show(SplitAmount.split(0, 2));", False),
        ("long[] parts = SplitAmount.split(999_999_999_999L, 7);\n"
         "System.out.println(Arrays.stream(parts).sum() + \" \" + (Arrays.stream(parts).max().getAsLong() - Arrays.stream(parts).min().getAsLong()));", False),
        ("show(SplitAmount.split(100, 0));", False),
        ("show(SplitAmount.split(-1, 2));", False),
    ]),
]

# Classes provided to every question in a folder (copied from the solutions; learners don't write them).
SUPPORT = {
    "lists": ["lists/Node"],
    "trees": ["trees/TreeNode"],
}

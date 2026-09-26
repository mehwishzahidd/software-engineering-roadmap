# 00 — Big-O & Complexity Analysis

> **Week 1** · NeetCode section: — (prerequisite for all) · Target: **0 LeetCode problems + 10 analysis drills**
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md) · Java APIs: [`java-dsa-toolkit.md`](./java-dsa-toolkit.md)

Every interview answer ends with "time is O(…), space is O(…)". Every OA constraint (`1 ≤ n ≤ 10^5`) is a hint about the complexity you need. This guide teaches you to *derive* complexity from code and to *choose* a target complexity from constraints — before you write anything.

---

## 1. Concepts to learn

| Concept | What you must be able to say |
|---|---|
| **Asymptotic notation** | Big-O = upper bound on growth; Θ = tight bound; Ω = lower bound. In interviews "O" usually means the tight worst case. |
| **Drop constants and lower terms** | O(3n² + 5n + 7) = O(n²). O(n/2) = O(n). But O(n + m) stays — two independent inputs. |
| **Common classes** (fast → slow) | O(1) < O(log n) < O(√n) < O(n) < O(n log n) < O(n²) < O(n³) < O(2ⁿ) < O(n!) |
| **Loops** | Sequential loops **add**; nested loops **multiply**; a loop that halves/doubles is O(log n). |
| **Recursion** | Time ≈ (number of calls) × (work per call). Draw the recursion tree: branching factor `b`, depth `d` → O(bᵈ) calls. Space = max depth of the call stack. |
| **Master theorem (awareness)** | T(n) = a·T(n/b) + O(nᵈ). Merge sort: 2T(n/2) + O(n) → O(n log n). Binary search: T(n/2) + O(1) → O(log n). |
| **Amortised analysis** | `ArrayList.add` is O(1) amortised: occasional O(n) resize, doubling capacity, averages to O(1). Same for `StringBuilder.append`, `HashMap.put`. |
| **Space complexity** | Extra memory *you* allocate + recursion stack. Input usually doesn't count; output sometimes does (say which convention you're using). |
| **Hidden costs in Java** | `s.substring()` O(k), `String +` O(n), `list.remove(0)` O(n), `list.contains()` O(n), `Arrays.sort` O(n log n), `new ArrayList<>(other)` O(n), `pq.remove(obj)` O(n). |
| **Best / average / worst case** | Quicksort O(n log n) average, O(n²) worst. `HashMap.get` O(1) average. Interviewers want worst case unless you say otherwise. |
| **Log bases don't matter** | log₂ n and log₁₀ n differ by a constant factor. |
| **Input size vs input value** | Loop `for (i = 0; i < n; i++)` where `n` is a *value* up to 10⁹ is too slow even though it's "O(n)". |

Practical use: in [FlowGrid](../18-projects/flowgrid/README.md) (Weeks 4–8) you will choose between a `HashSet` (O(1) lookup) and `List.contains` (O(n)) when de-duplicating order lines, and between an in-memory scan and an indexed SQL query when listing inventory — the same analysis, with `EXPLAIN` instead of pen and paper.

---

## 2. Prerequisite knowledge

- Java loops, arrays, methods — [`01-java/01-syntax-basics.md`](../01-java/01-syntax-basics.md)
- High-school algebra: exponents, logarithms (log₂ 1024 = 10, log₂ 10⁶ ≈ 20, log₂ 10⁹ ≈ 30).
- Nothing else — this is file 00.

---

## 3. Java implementation — code you should be able to analyse on sight

```java
import java.util.*;

class ComplexityZoo {
    // O(1): constant work regardless of n
    static int first(int[] a) { return a[0]; }

    // O(log n): search space halves each iteration
    static int binarySearch(int[] a, int t) {
        int lo = 0, hi = a.length - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if (a[mid] == t) return mid;
            if (a[mid] < t) lo = mid + 1; else hi = mid - 1;
        }
        return -1;
    }

    // O(n): one pass
    static long sum(int[] a) { long s = 0; for (int x : a) s += x; return s; }

    // O(n log n): sort dominates the linear scan
    static boolean hasDuplicateSort(int[] a) {
        int[] b = a.clone();
        Arrays.sort(b);                                    // O(n log n)
        for (int i = 1; i < b.length; i++) if (b[i] == b[i - 1]) return true;   // O(n)
        return false;
    }

    // O(n) time, O(n) space: trade memory for time
    static boolean hasDuplicateSet(int[] a) {
        Set<Integer> seen = new HashSet<>();
        for (int x : a) if (!seen.add(x)) return true;
        return false;
    }

    // O(n^2): nested loops over the same input
    static int countPairs(int[] a, int target) {
        int c = 0;
        for (int i = 0; i < a.length; i++)
            for (int j = i + 1; j < a.length; j++)         // n-1 + n-2 + ... + 1 = n(n-1)/2
                if (a[i] + a[j] == target) c++;
        return c;
    }

    // O(n * m): two independent inputs — do NOT call it O(n^2)
    static int common(int[] a, int[] b) {
        int c = 0;
        for (int x : a) for (int y : b) if (x == y) c++;
        return c;
    }

    // O(2^n) time, O(n) space (stack depth): naive Fibonacci
    static long fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }

    // O(n) time and space after memoisation: each subproblem solved once
    static long fibMemo(int n, long[] memo) {
        if (n < 2) return n;
        if (memo[n] != 0) return memo[n];
        return memo[n] = fibMemo(n - 1, memo) + fibMemo(n - 2, memo);
    }

    // O(n^2) HIDDEN: looks like one loop, but += on String copies every time
    static String joinSlow(String[] parts) {
        String s = "";
        for (String p : parts) s += p;                     // O(total length) per iteration
        return s;
    }

    // O(n): StringBuilder append is amortised O(1)
    static String joinFast(String[] parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(p);
        return sb.toString();
    }

    // O(n log n) via the harmonic series: sum over i of n/i
    static int harmonic(int n) {
        int ops = 0;
        for (int i = 1; i <= n; i++)
            for (int j = i; j <= n; j += i) ops++;         // n/1 + n/2 + ... + n/n ≈ n ln n
        return ops;
    }

    // O(sqrt n): trial division
    static boolean isPrime(int n) {
        if (n < 2) return false;
        for (int d = 2; (long) d * d <= n; d++) if (n % d == 0) return false;
        return true;
    }

    public static void main(String[] args) {
        int[] a = {4, 1, 3, 1};
        System.out.println(first(a) + " " + sum(a) + " " + hasDuplicateSort(a) + " " + hasDuplicateSet(a));
        System.out.println(countPairs(a, 4) + " " + common(a, new int[]{1, 9}) + " " + fib(20) + " " + fibMemo(50, new long[51]));
        System.out.println(joinSlow(new String[]{"a", "b"}) + joinFast(new String[]{"c"}) + harmonic(10) + isPrime(97));
        System.out.println(binarySearch(new int[]{1, 3, 5, 7}, 5));
    }
}
```

### Measure it — "Break" step

Predict, then run: double `n` and see how the time scales (O(n) → ×2, O(n²) → ×4, O(2ⁿ) → explodes).

```java
class Timing {
    static long fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }

    public static void main(String[] args) {
        for (int n = 30; n <= 40; n += 2) {
            long t0 = System.nanoTime();
            fib(n);
            System.out.printf("n=%d  %.1f ms%n", n, (System.nanoTime() - t0) / 1e6);  // ~×2.6 per +2
        }
    }
}
```

(JIT warm-up makes micro-benchmarks noisy — this is for intuition, not for publishing numbers.)

---

## 4. Common patterns (sub-variants of analysis)

| Code shape | Complexity | Why |
|---|---|---|
| `for i in 0..n` then separate `for j in 0..n` | O(n) | Add: n + n |
| `for i` { `for j` } over same n | O(n²) | Multiply |
| `for (i = 1; i < n; i *= 2)` | O(log n) | Number of doublings to reach n |
| Two pointers moving toward each other | O(n) | Each pointer moves ≤ n times total |
| Sliding window with inner `while` | O(n) | Left pointer moves ≤ n times **in total**, not per outer iteration (amortised) |
| Sort then scan | O(n log n) | Sort dominates |
| BFS/DFS on graph | O(V + E) | Each vertex and edge processed once |
| Grid DFS | O(R · C) | Each cell visited once |
| Heap of size k over n items | O(n log k) | n pushes/pops at log k each |
| Recursion, b branches, depth d | O(bᵈ) | Subsets: O(2ⁿ · n) including copying |
| Memoised DP with S states and T transitions per state | O(S · T) | Each state computed once |
| Binary search on answer range [lo, hi] with O(n) check | O(n log(hi − lo)) | log of the *value range* |

---

## 5. How to recognise what complexity you need

Use the constraints **before** designing. ~10⁸ simple operations/sec in Java.

| Constraint | Target | Signals the pattern… |
|---|---|---|
| n ≤ 10 | O(n!) | permutations |
| n ≤ 20 | O(2ⁿ) | subsets / bitmask |
| n ≤ 500 | O(n³) | triple loop / interval DP |
| n ≤ 5·10³ | O(n²) | double loop / 2-D DP |
| n ≤ 10⁵–2·10⁵ | **O(n log n) or better** | sort, heap, binary search |
| n ≤ 10⁶+ | O(n) | hash, two pointers, window, prefix sums |
| value ≤ 10⁹ with no array of that size | O(log V) | binary search on answer, math |

"Follow-up: can you do it in O(1) extra space?" → two pointers / in-place marking / bit tricks.
"Follow-up: can you do better than O(n log n)?" → hashing, bucket sort, or counting sort.

---

## 6. Beginner exercises (analysis drills, no LeetCode)

Do these on paper; write complexity, then check by running with n doubled.

| # | Drill | Answer to check yourself (hidden below) |
|---:|---|---|
| D1 | Every method in `ComplexityZoo` above: state time and space | — |
| D2 | `for (i = n; i > 0; i /= 2) for (j = 0; j < i; j++)` | see below |
| D3 | `for (i = 0; i < n; i++) for (j = 0; j < i * i; j++)` | see below |
| D4 | A method that calls `list.contains(x)` inside a loop over `list` | see below |
| D5 | Recursive `sum(int[] a, int i)` that returns `a[i] + sum(a, i + 1)` — time and space | see below |

<details>
<summary>Answers D2–D5</summary>

- **D2:** n + n/2 + n/4 + … ≤ 2n → **O(n)** (geometric series), not O(n log n).
- **D3:** Σ i² ≈ n³/3 → **O(n³)**.
- **D4:** O(n) iterations × O(n) `contains` → **O(n²)**; switch to a `HashSet` for O(n).
- **D5:** O(n) time, **O(n) space** for the call stack (and `StackOverflowError` near n ≈ 10⁴–10⁵).

</details>

---

## 7. Interview-style analysis drills

<details>
<summary>D6–D10 (try first, then open)</summary>

| # | Question | Answer |
|---:|---|---|
| D6 | What is the complexity of generating all subsets of n elements and adding each to a result list? | O(2ⁿ · n) time (2ⁿ subsets, each copied in O(n)); O(n) auxiliary stack, O(2ⁿ · n) output |
| D7 | Why is the sliding-window loop with an inner `while` still O(n)? | Each index enters the window once and leaves at most once → ≤ 2n pointer moves total (amortised) |
| D8 | Complexity of Dijkstra with a binary heap? | O((V + E) log V) |
| D9 | `HashMap.put` is O(1) — when is it not? | Worst case many collisions (O(log n) with treeified buckets in Java 8+), plus occasional O(n) resize (amortised O(1)) |
| D10 | Top-K frequent with a heap of size k vs sorting all counts | Heap O(n log k) vs sort O(n log n); bucket sort O(n) |

</details>

---

## 8. Recommended practice order

1. Read §1 and §4. 2. Analyse every method in §3 on paper. 3. Run `Timing`. 4. D2–D5. 5. D6–D10.
6. **From now on:** every problem you solve in the tracker gets a "Time / Space" line in Notes. Never submit without stating complexity.

---

## 9. Target number of problems

- **0 LeetCode problems** in this guide (Week 1's 6 new problems belong to [`01-arrays-strings.md`](./01-arrays-strings.md)).
- **10 analysis drills** (D1–D10) completed in Week 1.

---

## 10. Mistakes beginners commonly make

- Calling O(n + m) "O(n)" or two independent inputs "O(n²)" when it's O(n·m).
- Forgetting the **recursion stack** in space complexity.
- Forgetting the **sort**: "my loop is O(n)" — but you called `Arrays.sort` first → O(n log n).
- Treating `String +=`, `substring`, `list.contains`, `list.remove(0)` as O(1) (Java-specific hidden costs).
- Saying a sliding window with a nested `while` is O(n²).
- Confusing "n is a value" with "n is a length" — iterating up to 10⁹ is too slow.
- Ignoring output size: returning all permutations is at least O(n · n!).
- Declaring `HashMap` "always O(1)" without the word *average*.

---

## 11. Mastery criteria

- [ ] Given any solution you've written, state time **and** space within 10 seconds, including recursion stack.
- [ ] Given a constraint line, state the target complexity and two candidate patterns.
- [ ] Explain amortised O(1) of `ArrayList.add` in ≤ 4 sentences.
- [ ] Explain why BFS is O(V + E) and a grid DFS is O(R·C).
- [ ] Score 9/10 on D1–D10 without opening the answers.

---

## 12. Revision schedule

| When | What |
|---|---|
| Day 0 (Week 1) | All drills |
| Day 3 | Re-do D2, D3, D7 without notes |
| Day 7 (Week 2) | Explain amortised analysis out loud |
| Day 14 (Week 3) | Analyse your Week 2–3 solutions in the tracker |
| Day 30 (Week 5) | Explain monotonic-stack amortisation while doing [06](./06-stack-queue.md) |
| Revisit | **Week 4** (sliding-window amortisation, [05](./05-sliding-window.md)), **Week 8** (recursion trees + merge sort, [09](./09-recursion.md)), **Week 15** (DP state × transition analysis), **Checkpoint 4 and 8** |

---

## Worked example — analyse and improve "Contains Duplicate"

**Clarify.** Input `int[] nums`, up to 10⁵ elements, values up to ±10⁹. Return `true` if any value appears twice. Empty array → `false`.

**Brute force.** Compare every pair: O(n²) time, O(1) space. With n = 10⁵ that's 5·10⁹ comparisons → too slow (constraint says O(n log n) or better).

**Optimise.**
- Option A: sort, then check neighbours → O(n log n) time, O(1) extra (O(log n) for sort recursion; modifies input unless cloned).
- Option B: `HashSet`, return on first failed `add` → O(n) average time, O(n) space.

**Code (Java).**

```java
import java.util.*;

class ContainsDuplicate {
    public boolean containsDuplicate(int[] nums) {
        Set<Integer> seen = new HashSet<>(nums.length * 2);   // pre-size: avoid rehashing
        for (int x : nums) {
            if (!seen.add(x)) return true;                    // add returns false if already present
        }
        return false;
    }

    public static void main(String[] args) {
        ContainsDuplicate s = new ContainsDuplicate();
        System.out.println(s.containsDuplicate(new int[]{1, 2, 3, 1}));   // true
        System.out.println(s.containsDuplicate(new int[]{1, 2, 3}));      // false
        System.out.println(s.containsDuplicate(new int[]{}));             // false
    }
}
```

**Test cases.** `[1,2,3,1]` → true · `[1,2,3]` → false · `[]` → false · `[7]` → false · `[-1000000000, -1000000000]` → true.

**Complexity.** Time O(n) average; space O(n). Trade-off to say out loud: "If memory is tight I'd sort in place for O(n log n) time and O(1) extra space."

(This is LeetCode 217, which you solve for real in Week 2 — see [`02-hashing.md`](./02-hashing.md).)

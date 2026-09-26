# The Coding-Interview Method

A live coding round is graded on four things — **problem solving, coding, communication,
testing** (the rubric in [`mock-interviews.md`](./mock-interviews.md)). Getting the optimal
answer silently in 40 minutes can score *worse* than reaching it out loud with a clean
trace. This method makes every signal visible.

Practise it on every DSA problem from Week 5 (weekly think-aloud sessions), in every mock from
Week 10, and silently in OAs ([`../OA_PREP.md`](../OA_PREP.md)).

---

## The 9 steps

```
1 Clarify → 2 Examples → 3 Edge cases → 4 Brute force → 5 Optimize
→ 6 Communicate the plan → 7 Code → 8 Test by tracing → 9 Big-O
```

### 1. Clarify requirements (2–3 min)

Restate the problem in your own words, then ask about what the prompt left open.

| Ask about | Example question |
|---|---|
| Input size | "Roughly how large can `n` get — thousands or millions?" |
| Values | "Can the numbers be negative? Zero? Can they overflow an `int`?" |
| Duplicates | "Can the array contain duplicates? Does the answer need unique pairs?" |
| Empty / null | "Can the input be empty? Should I return `0`, `-1`, or throw?" |
| Sortedness | "Is the input sorted? Can I modify it in place?" |
| Output | "Return indices or values? Any order? If there are several answers, any one?" |
| Characters | "ASCII only, or Unicode? Case-sensitive?" |
| Graph | "Directed or undirected? Can there be cycles? Disconnected components?" |

Don't ask 10 questions. Ask the 2–4 that change your algorithm.

### 2. Examples (2 min)

Work the given example by hand **out loud**. Then build one small example of your own that's not symmetrical or trivially sorted — it will catch assumptions.

### 3. Edge cases (1 min)

List them, and write them in a comment at the top of the editor so you can test them in step 8:

```java
// edge: [] -> 0 | [5] -> 5 | all negative | duplicates | max n = 1e5 -> need O(n log n) or better
```

### 4. Brute force (2 min)

State it and its complexity, even if it's obviously slow. It proves you understand the problem and gives you a fallback.

> "The brute force is to check every pair — O(n²) time, O(1) space. With n up to 10⁵ that's 10¹⁰ operations, too slow, so let me look for something better."

Only code the brute force if you can't find anything better within ~5 minutes (or the interviewer asks).

### 5. Optimize (5–8 min)

Ask yourself, out loud:

- "What work is the brute force repeating?" → cache it (**hash map**, **prefix sums**, **memoization**).
- "Would sorting help?" → **two pointers**, **binary search**, greedy.
- "Is there a contiguous range?" → **sliding window**.
- "Do I need the smallest/largest k repeatedly?" → **heap**.
- "Is there a 'next greater/smaller'?" → **monotonic stack**.
- "Is it a grid, network, or dependency?" → **BFS/DFS**, **topological sort**, **union-find**.
- "Is there an optimal substructure with overlapping subproblems?" → **DP**.
- "Is the answer monotonic in some parameter?" → **binary search on the answer**.
- "What does the constraint table suggest?" (n ≤ 20 → exponential okay; n ≤ 10⁵ → n log n.)

Pattern guides: [`../03-dsa/`](../03-dsa/).

### 6. Communicate the plan (1 min) — then get buy-in

Say the algorithm in 3–5 sentences plus the complexity **before** coding.

> "I'll use a hash map from value to index. For each element, I check if `target - x` is already in the map; if so, I return both indices, otherwise I store `x`. That's one pass: O(n) time and O(n) space. Does that sound good before I code it?"

That last question is the cheapest insurance in the interview. If the interviewer has concerns, you hear them now, not at minute 38.

### 7. Code (12–15 min)

- Narrate *intent*, not syntax: "Now I'm handling the window shrink" — not "int i equals zero".
- Meaningful names (`left`, `right`, `seen`, `freq`), not `a`, `b`, `m`.
- Extract helpers (`isValid`, `neighbors`, `buildGraph`) — say "I'll write this helper after the main logic" and stub it.
- If you're unsure of an API, say so and pick a reasonable one: "I believe it's `getOrDefault` — I'll use that."
- Don't go silent for more than ~30 seconds. If you need to think, say "Let me think about this for a moment" — then think.

### 8. Test by tracing (4–5 min)

**Don't** say "I think it works." Trace:

1. Run the small example through the code **line by line**, tracking variables in a comment table.
2. Run each edge case from step 3.
3. Look for classic bugs: off-by-one in loop bounds, empty input, integer overflow, not updating a pointer, returning inside the loop too early.

```java
// trace nums=[2,7,11], target=9
// i=0 x=2 need=7 seen={}        -> put 2:0
// i=1 x=7 need=2 seen={2:0}     -> return [0,1] ✓
```

When you find a bug yourself, say so calmly and fix it — self-found bugs are a positive signal.

### 9. Big-O analysis (1 min)

State time **and** space, and justify both with the code: "The loop runs n times, each map operation is O(1) average, so O(n) time. The map holds at most n entries, so O(n) space." Mention trade-offs: "If memory were tight I could sort and use two pointers — O(n log n) time, O(1) extra space, but I'd lose the original indices."

---

## Exact phrases

| Moment | Say |
|---|---|
| Starting | "Let me restate the problem to make sure I have it right…" |
| Clarifying | "Before I start, a couple of questions about the input…" |
| Assumption | "I'll assume the input fits in memory and values fit in an `int`; let me know if not." |
| Brute force | "The straightforward approach is… which is O(…). Let me see if we can do better." |
| Thinking | "Let me think about this for a moment." (then actually pause, ~20–30 s max) |
| Proposing | "I think a sliding window works here because… Does that approach sound reasonable?" |
| Before coding | "I'm going to code this now; I'll talk through the key parts." |
| Unsure of API | "I don't remember whether `TreeMap` has `floorKey` or `floorEntry` — I'll use `floorKey` and we can check." |
| Found a bug | "Wait — this fails when the array is empty. Let me add a guard." |
| Testing | "Let me trace through the example to verify." |
| Finished | "Time is O(n) and space O(n). One follow-up improvement would be…" |
| Don't know | "I haven't used that before. My understanding is… I'd verify it by…" |

---

## Taking hints

A hint is information, not a failure. Handling it well is scored.

1. **Stop and listen.** Don't keep typing.
2. **Repeat it back** in your own words: "So you're suggesting I don't need to recompute the whole window each time?"
3. **Connect it** to your approach out loud: "Right — if I keep a running sum, I can subtract the element leaving the window. That makes each step O(1)."
4. **Adjust the plan and complexity**, then code.
5. **Thank briefly**, move on. Don't apologise repeatedly.

If you don't understand the hint, say so: "I'm not sure I see how sorting helps — could you say a bit more?" That's far better than nodding and flailing.

## When you're stuck

| Step | Action |
|---|---|
| 1 | Say it: "I'm stuck on how to avoid the nested loop. Let me go back to the example." |
| 2 | Work a **new, bigger example** by hand. Watch what information you reuse. |
| 3 | Walk the pattern list in step 5 out loud. |
| 4 | Simplify: solve a smaller version (sorted input? only positives? k = 1?) then generalise. |
| 5 | Code the brute force cleanly: "Let me get a working O(n²) solution, then optimize." |
| 6 | Ask a targeted question: "Is there a property of the input I should exploit?" |

Never sit in silence for several minutes. Never give up — a correct brute force with good tests and communication can still pass a junior round.

---

## Time budget: 45-minute round

| Minutes | Phase |
|---:|---|
| 0–5 | Introductions (keep your intro to 45 s — see [`behavioral.md`](./behavioral.md)) |
| 5–8 | Clarify + examples + edge cases |
| 8–15 | Brute force → optimized approach → **plan agreed** |
| 15–30 | Code |
| 30–36 | Trace + edge cases + fix |
| 36–38 | Complexity + follow-ups |
| 38–45 | **Your questions** for the interviewer |

Checkpoints: if you haven't agreed an approach by **minute 17**, code the best one you have. If code isn't done by **minute 33**, tell the interviewer what's left and finish the core path. Two-problem rounds: aim to finish the first by minute 22.

---

## Java-specific interview tips

**Idioms to know cold** (full list: [`../03-dsa/java-dsa-toolkit.md`](../03-dsa/java-dsa-toolkit.md)):

```java
Map<Integer, Integer> freq = new HashMap<>();
freq.merge(x, 1, Integer::sum);                       // count
freq.getOrDefault(x, 0);
map.computeIfAbsent(key, k -> new ArrayList<>()).add(v); // multimap

Deque<Integer> stack = new ArrayDeque<>();            // stack: push/pop/peek — not java.util.Stack
Deque<int[]> queue = new ArrayDeque<>();              // queue: offer/poll/peek

PriorityQueue<Integer> minHeap = new PriorityQueue<>();
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());
PriorityQueue<int[]> byDist = new PriorityQueue<>((a, b) -> Integer.compare(a[0], b[0]));

int[][] intervals; Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]));
char[] cs = s.toCharArray(); Arrays.sort(cs); String key = new String(cs);
int[] count = new int[26]; count[c - 'a']++;
StringBuilder sb = new StringBuilder(); sb.append(c); sb.reverse(); sb.toString();
List<List<Integer>> res = new ArrayList<>(); res.add(new ArrayList<>(path)); // copy!
TreeMap<Integer, Integer> tm; tm.floorKey(x); tm.ceilingKey(x);
```

**Traps that cost points:**

| Trap | Fix |
|---|---|
| `(a, b) -> a - b` comparator overflows for large/negative values | `Integer.compare(a, b)` |
| `Integer` compared with `==` (works only in −128..127 cache) | `.equals()` or unbox to `int` |
| `int` overflow in sums, products, `mid = (lo + hi) / 2` | `long`; `lo + (hi - lo) / 2` |
| `String +=` in a loop (O(n²)) | `StringBuilder` |
| Adding `path` itself to results in backtracking | `new ArrayList<>(path)` |
| `list.remove(i)` vs `list.remove(Integer.valueOf(x))` on `List<Integer>` | be explicit |
| Recursion depth > ~10⁴ → `StackOverflowError` | iterative DFS / BFS |
| `Arrays.asList(int[])` gives `List<int[]>` | loop, or `Arrays.stream(a).boxed().toList()` |
| Modifying a collection during for-each → `ConcurrentModificationException` | iterator `remove()` or collect then remove |
| `char` arithmetic: `'a' + 1` is an `int` | cast `(char) ('a' + 1)` |
| `Arrays.fill` on a 2-D array fills rows with the same reference | loop over rows |

**Style signals interviewers notice:** a class-level method signature matching the prompt, `final` not required, early returns for edge cases, small helpers, no premature micro-optimization, `var` is fine but don't obscure types in tricky code. If asked "why Java?": "It's the language I use daily; the Collections Framework makes the data structures explicit, and I know its performance characteristics."

**Language choice:** Java for everything in this roadmap. If a company's platform only offers Python for a round, see [`../19-python/python-for-java-devs.md`](../19-python/python-for-java-devs.md) — but ask the recruiter first; Java is almost always available.

---

## After every practice problem (2 minutes)

- [ ] Could I explain the approach in 3 sentences before coding?
- [ ] Did I state complexity with justification?
- [ ] Did I trace at least one example and one edge case?
- [ ] Where did I go silent?
- [ ] Log it in [`../trackers/dsa-tracker.md`](../trackers/dsa-tracker.md) with the honest status.

## Interview questions this method answers well

- Any LeetCode-style Medium in 35 minutes.
- "Can you do better?" → step 5 checklist.
- "What's the complexity?" → step 9.
- "How would you test this?" → step 8 + the edge-case list in [`../OA_PREP.md`](../OA_PREP.md).
- "What if the input doesn't fit in memory?" → mention streaming / external sort / chunking; say what you'd trade.

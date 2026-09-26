# The Coding-Interview Method (Track A — Python)

A live coding round is graded on four things — **problem solving, coding, communication,
testing** (the rubric in [`mock-interviews.md`](./mock-interviews.md)). Getting the optimal
answer silently in 40 minutes can score *worse* than reaching it out loud with a clean
trace. This method makes every signal visible.

**Language: Python** — the Track A language for every LeetCode problem, OA and live coding
round (ROADMAP §10). Templates and idioms live in
[`../PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md); pitfalls in
[`../19-python/03-pitfalls-and-complexity.md`](../19-python/03-pitfalls-and-complexity.md).
Java is the Track B language (projects, résumé, backend) — you do ≈1 already-solved problem per
week in Java for collections fluency, not for interviews.

Practise this method on every DSA problem from Week 5 (weekly think-aloud sessions), in every
mock from Week 10, and silently in OAs ([`../OA_PREP.md`](../OA_PREP.md)).

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
| Values | "Can the numbers be negative? Zero? Are they bounded?" |
| Duplicates | "Can the list contain duplicates? Does the answer need unique pairs?" |
| Empty / None | "Can the input be empty? Should I return `0`, `-1`, `None`, or raise?" |
| Sortedness | "Is the input sorted? Can I modify it in place?" |
| Output | "Return indices or values? Any order? If there are several answers, any one?" |
| Characters | "ASCII only, or Unicode? Case-sensitive?" |
| Graph | "Directed or undirected? Can there be cycles? Disconnected components?" |

Don't ask 10 questions. Ask the 2–4 that change your algorithm.

### 2. Examples (2 min)

Work the given example by hand **out loud**. Then build one small example of your own that's not symmetrical or trivially sorted — it will catch assumptions.

### 3. Edge cases (1 min)

List them, and write them in a comment at the top of the editor so you can test them in step 8:

```python
# edge: [] -> 0 | [5] -> 5 | all negative | duplicates | n = 1e5 -> need O(n log n) or better
```

### 4. Brute force (2 min)

State it and its complexity, even if it's obviously slow. It proves you understand the problem and gives you a fallback.

> "The brute force is to check every pair — O(n²) time, O(1) space. With n up to 10⁵ that's 10¹⁰ operations, far too slow in Python, so let me look for something better."

Only code the brute force if you can't find anything better within ~5 minutes (or the interviewer asks).

### 5. Optimize (5–8 min)

Ask yourself, out loud:

- "What work is the brute force repeating?" → cache it (**dict / set**, **prefix sums**, **`@lru_cache` memoization**).
- "Would sorting help?" → **two pointers**, **`bisect`**, greedy.
- "Is there a contiguous range?" → **sliding window**.
- "Do I need the smallest/largest k repeatedly?" → **`heapq`**.
- "Is there a 'next greater/smaller'?" → **monotonic stack**.
- "Is it a grid, network, or dependency?" → **BFS/DFS**, **topological sort**, **union-find**.
- "Is there an optimal substructure with overlapping subproblems?" → **DP**.
- "Is the answer monotonic in some parameter?" → **binary search on the answer**.
- "What does the constraint table suggest?" (n ≤ 20 → exponential okay; n ≤ 10⁵ → n log n; Python is ~10–50× slower than C++, so aim one notch lower than you would in Java.)

Pattern guides: [`../03-dsa/`](../03-dsa/).

### 6. Communicate the plan (1 min) — then get buy-in

Say the algorithm in 3–5 sentences plus the complexity **before** coding.

> "I'll use a dict from value to index. For each element, I check if `target - x` is already in the dict; if so, I return both indices, otherwise I store `x`. That's one pass: O(n) time and O(n) space. Does that sound good before I code it?"

That last question is the cheapest insurance in the interview. If the interviewer has concerns, you hear them now, not at minute 38.

### 7. Code (12–15 min)

- Narrate *intent*, not syntax: "Now I'm shrinking the window" — not "i equals zero".
- Meaningful names (`left`, `right`, `seen`, `freq`), not `a`, `b`, `m`.
- Extract helpers (`is_valid`, `neighbors`, `build_graph`) — say "I'll write this helper after the main logic" and stub it with `pass`.
- If you're unsure of an API, say so and pick a reasonable one: "I believe `bisect_left` returns the insertion point — I'll use that."
- Don't go silent for more than ~30 seconds. If you need to think, say "Let me think about this for a moment" — then think.

### 8. Test by tracing (4–5 min)

**Don't** say "I think it works." Trace:

1. Run the small example through the code **line by line**, tracking variables in a comment table.
2. Run each edge case from step 3.
3. Look for classic bugs: off-by-one in `range` bounds, empty input, not advancing a pointer, returning inside the loop too early, mutating a list you're iterating over.

```python
# trace nums=[2,7,11], target=9
# i=0 x=2 need=7 seen={}        -> seen[2]=0
# i=1 x=7 need=2 seen={2:0}     -> return [0,1] ✓
```

When you find a bug yourself, say so calmly and fix it — self-found bugs are a positive signal.

### 9. Big-O analysis (1 min)

State time **and** space, and justify both with the code: "The loop runs n times, each dict operation is O(1) average, so O(n) time. The dict holds at most n entries, so O(n) space." Mention trade-offs: "If memory were tight I could sort and use two pointers — O(n log n) time, O(1) extra space, but I'd lose the original indices." Know the hidden costs: slicing is O(k), `in` on a list is O(n), `sorted` is O(n log n), string concatenation in a loop is O(n²).

---

## Exact phrases

| Moment | Say |
|---|---|
| Starting | "Let me restate the problem to make sure I have it right…" |
| Clarifying | "Before I start, a couple of questions about the input…" |
| Assumption | "I'll assume the input fits in memory and the values are integers; let me know if not." |
| Brute force | "The straightforward approach is… which is O(…). Let me see if we can do better." |
| Thinking | "Let me think about this for a moment." (then actually pause, ~20–30 s max) |
| Proposing | "I think a sliding window works here because… Does that approach sound reasonable?" |
| Before coding | "I'm going to code this now; I'll talk through the key parts." |
| Unsure of API | "I don't remember whether `heapq` has a max-heap — I'll push negated values, which I know works." |
| Found a bug | "Wait — this fails when the list is empty. Let me add a guard." |
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

## Python-specific interview tips

**Idioms to know cold** (full templates: [`../PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md)):

```python
from collections import Counter, defaultdict, deque
import heapq, bisect
from functools import lru_cache
from itertools import accumulate, combinations, permutations

freq = Counter(nums); freq.most_common(k)               # counting
graph = defaultdict(list); graph[u].append(v)           # adjacency list / multimap
seen = set(); seen.add(x); x in seen                    # O(1) membership

q = deque([start]); q.append(x); q.popleft()            # BFS queue (never list.pop(0))
stack = []; stack.append(x); stack.pop()                # stack

heap = []; heapq.heappush(heap, (dist, node)); heapq.heappop(heap)   # min-heap by tuple
heapq.heappush(heap, -x)                                 # max-heap: negate
heapq.nlargest(k, nums)

intervals.sort(key=lambda iv: iv[0])                    # sort by start (stable)
words.sort(key=lambda w: (-freq[w], w))                 # multi-key: count desc, then lexical
key = "".join(sorted(s))                                # anagram key, or tuple(count) for O(n)
i = bisect.bisect_left(sorted_list, x)                  # first index with value >= x
prefix = [0] + list(accumulate(nums))                   # prefix sums

@lru_cache(maxsize=None)                                # memoized recursion (top-down DP)
def dp(i, j): ...

res.append(path[:])                                     # copy! not res.append(path)
for r, c in ((r+1, c), (r-1, c), (r, c+1), (r, c-1)):  # grid neighbours
grid = [[0] * cols for _ in range(rows)]                # not [[0]*cols]*rows (aliased rows)
lo, hi = 0, len(a) - 1; mid = (lo + hi) // 2            # no overflow worries in Python
parent = list(range(n))                                 # union-find
```

**Traps that cost points:**

| Trap | Fix |
|---|---|
| `[[0] * m] * n` — every row is the same list | `[[0] * m for _ in range(n)]` |
| Mutable default argument `def f(x, memo={})` | `memo=None` then `memo = {} if memo is None else memo`, or `@lru_cache` |
| `list.pop(0)` / `list.insert(0, x)` in a loop (O(n)) | `deque` |
| `x in some_list` inside a loop (O(n)) | `set` / `dict` |
| String `+=` in a loop (O(n²)) | collect in a list, `"".join(parts)` |
| Slicing inside a loop (`s[i:]`) is O(k) | pass indices instead |
| Deep recursion (> ~1000 frames) → `RecursionError` | `sys.setrecursionlimit(10**6)` **and** prefer iterative DFS/BFS for paths ≥ 10⁴ |
| `sorted()` returns a new list; `list.sort()` returns `None` | don't write `a = a.sort()` |
| Shallow copy of nested lists (`copy.copy`, `[:]`) | `copy.deepcopy` when nesting matters |
| Tuple comparison in the heap breaks on incomparable payloads | push `(key, counter, obj)` or store indices |
| Integer division of negatives: `-7 // 2 == -4`, `-7 % 2 == 1` | use `int(a / b)` when truncation toward zero is required |
| `is` vs `==` (`x is 1000` can be `False`) | always `==` for values; `is None` only for `None` |
| Modifying a list while iterating over it | iterate over a copy or build a new list |
| Forgetting `nonlocal` when a nested function reassigns a counter | `nonlocal count`, or use a one-element list / `self` attribute |
| Reading input with `input()` for 10⁶ lines (slow) | `sys.stdin` — see [`../OA_PREP.md`](../OA_PREP.md) |

**Style signals interviewers notice:** a function signature matching the prompt (`def two_sum(nums: list[int], target: int) -> list[int]:`), early returns for edge cases, small helpers, no clever one-liners that hide logic, type hints where they aid reading. If asked "why Python?": "It lets me spend the interview on the algorithm; I know the complexity of every container operation I'm using. My backend work is in Java — happy to switch if the round calls for it."

**Language choice:** Python for every Track A round. If a company's platform doesn't offer Python (rare) or a round is explicitly "code in the language of the job", you can fall back to Java — see [`../03-dsa/java-dsa-toolkit.md`](../03-dsa/java-dsa-toolkit.md) — but ask the recruiter beforehand so it's never a surprise.

---

## After every practice problem (2 minutes)

- [ ] Could I explain the approach in 3 sentences before coding?
- [ ] Did I state complexity with justification?
- [ ] Did I trace at least one example and one edge case?
- [ ] Where did I go silent?
- [ ] Log it in [`../trackers/dsa-tracker.md`](../trackers/dsa-tracker.md) with the honest status (and tick the "Java rep" column for the one problem per week you redo in Java).

## Interview questions this method answers well

- Any LeetCode-style Medium in 35 minutes.
- "Can you do better?" → step 5 checklist.
- "What's the complexity?" → step 9, including Python's hidden costs.
- "How would you test this?" → step 8 + the edge-case list in [`../OA_PREP.md`](../OA_PREP.md).
- "What if the input doesn't fit in memory?" → mention streaming / external sort / chunking; say what you'd trade.
- "Why is dict lookup O(1)?" → hashing, load factor, worst case O(n); know it ([`../19-python/03-pitfalls-and-complexity.md`](../19-python/03-pitfalls-and-complexity.md)).

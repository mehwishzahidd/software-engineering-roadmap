# 06 — Stack & Queue (incl. Monotonic Stack)

> **Week 5** · NeetCode section: **Stack** · Target: **8 new problems** + stretch pool · Java rep: **739** (Week 5)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#06--stack--queue) · Python: [`19-python/02-interview-toolkit.md`](../19-python/02-interview-toolkit.md) (`list` as stack, `deque`) · Java: [quick reference](./java-dsa-toolkit.md)

A stack remembers "what's still open / unresolved, most recent first". A queue processes "in arrival order". A **monotonic stack** keeps elements in sorted order so that each element is pushed and popped once, answering "next greater/smaller" questions in O(n). Week 5 also introduces Java memory and threads for FlowGrid — the JVM call stack you read in stack traces is the same LIFO idea.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Stack (LIFO) | Python `list`: `append` / `pop()` / `st[-1]` — all O(1) |
| Queue (FIFO) | `collections.deque`: `append` / `popleft()` O(1). **Never** `list.pop(0)` (O(n)) |
| Deque | Both ends O(1); basis for the monotonic deque (sliding-window max, [05](./05-sliding-window.md)) |
| Array-backed implementation | Stack = array + top index; bounded queue = **circular buffer** with head/size modulo capacity |
| Matching / nesting | Push openers (or the expected closer); on a closer, the top must match |
| Expression evaluation | Postfix (RPN): operands pushed, operator pops two. Operand order matters for `-` and `/`; division truncates toward zero → `int(a / b)`, **not** `a // b` |
| Auxiliary stack | Min stack: store `(value, min_so_far)` tuples |
| Monotonic stack | Keep the stack increasing or decreasing; when the new element breaks the order, pop and **resolve** the popped element (its next greater/smaller is the current element) |
| Amortised O(n) | Each index pushed once and popped at most once |
| Two stacks → queue | Inbox/outbox; transfer only when outbox is empty → amortised O(1) |

Practical use: parsing nested structures, undo history, and — in [FlowGrid](../18-projects/flowgrid/README.md) — reading Java stack traces top-down when a reservation test fails.

---

## 2. Prerequisite knowledge

- [`01-arrays-strings.md`](./01-arrays-strings.md) — lists and strings.
- [`05-sliding-window.md`](./05-sliding-window.md) — you've seen `deque` as a deque.
- Python cost table in [`00-big-o.md`](./00-big-o.md) — why `pop(0)` is O(n).

---

## 3. Python implementation

### 3.1 Stack and circular queue from scratch

```python
class ArrayStack:
    def __init__(self) -> None:
        self._data: list[int] = []

    def push(self, x: int) -> None:
        self._data.append(x)                # amortised O(1)

    def pop(self) -> int:
        if not self._data:
            raise IndexError("pop from empty stack")
        return self._data.pop()             # O(1) from the END

    def peek(self) -> int:
        if not self._data:
            raise IndexError("peek at empty stack")
        return self._data[-1]

    def __len__(self) -> int:
        return len(self._data)


class RingQueue:
    """Bounded FIFO queue on a fixed list: O(1) offer/poll, no shifting."""

    def __init__(self, capacity: int) -> None:
        self._data: list[int | None] = [None] * capacity
        self._head = 0
        self._size = 0

    def offer(self, x: int) -> bool:
        if self._size == len(self._data):
            return False                     # full
        self._data[(self._head + self._size) % len(self._data)] = x
        self._size += 1
        return True

    def poll(self) -> int:
        if self._size == 0:
            raise IndexError("poll from empty queue")
        x = self._data[self._head]
        self._data[self._head] = None        # drop the reference
        self._head = (self._head + 1) % len(self._data)
        self._size -= 1
        return x


s = ArrayStack()
for i in range(20):
    s.push(i)
assert s.pop() == 19 and s.peek() == 18 and len(s) == 19
q = RingQueue(3)
assert q.offer(1) and q.offer(2) and q.offer(3) and not q.offer(4)
assert q.poll() == 1
q.offer(4)                                   # wraps around
assert [q.poll(), q.poll(), q.poll()] == [2, 3, 4]
print("stack/queue from scratch ok")
```

### 3.2 Pattern templates

```python
from collections import deque


def valid_brackets(s: str) -> bool:
    pairs = {")": "(", "]": "[", "}": "{"}
    st: list[str] = []
    for ch in s:
        if ch in pairs:
            if not st or st.pop() != pairs[ch]:
                return False
        else:
            st.append(ch)
    return not st


def next_greater_distance(a: list[int]) -> list[int]:
    """Monotonic decreasing stack of indices (LC 739 shape)."""
    ans = [0] * len(a)
    st: list[int] = []
    for i, x in enumerate(a):
        while st and a[st[-1]] < x:
            j = st.pop()                     # x is j's next strictly greater element
            ans[j] = i - j
        st.append(i)
    return ans                               # unresolved indices stay 0


def eval_rpn(tokens: list[str]) -> int:
    st: list[int] = []
    for t in tokens:
        if t in {"+", "-", "*", "/"}:
            b, a = st.pop(), st.pop()        # pop order: right operand first
            if t == "+":
                st.append(a + b)
            elif t == "-":
                st.append(a - b)
            elif t == "*":
                st.append(a * b)
            else:
                st.append(int(a / b))        # truncate toward zero; a // b would floor (-7 // 2 == -4)
        else:
            st.append(int(t))
    return st[-1]


class MinStack:
    def __init__(self) -> None:
        self._st: list[tuple[int, int]] = [] # (value, min so far)

    def push(self, v: int) -> None:
        self._st.append((v, min(v, self._st[-1][1]) if self._st else v))

    def pop(self) -> None:
        self._st.pop()

    def top(self) -> int:
        return self._st[-1][0]

    def get_min(self) -> int:
        return self._st[-1][1]


class MyQueue:
    """Queue via two stacks (LC 232): amortised O(1)."""

    def __init__(self) -> None:
        self._in: list[int] = []
        self._out: list[int] = []

    def push(self, x: int) -> None:
        self._in.append(x)

    def _shift(self) -> None:
        if not self._out:
            while self._in:
                self._out.append(self._in.pop())

    def pop(self) -> int:
        self._shift()
        return self._out.pop()

    def peek(self) -> int:
        self._shift()
        return self._out[-1]

    def empty(self) -> bool:
        return not self._in and not self._out


assert valid_brackets("({[]})") and not valid_brackets("(]") and not valid_brackets("((")
assert next_greater_distance([73, 74, 75, 71, 69, 72, 76, 73]) == [1, 1, 4, 2, 1, 1, 0, 0]
assert eval_rpn(["4", "13", "5", "/", "+"]) == 6 and eval_rpn(["-7", "2", "/"]) == -3
ms = MinStack()
for v in (-2, 0, -3):
    ms.push(v)
assert ms.get_min() == -3
ms.pop()
assert ms.top() == 0 and ms.get_min() == -2
mq = MyQueue()
mq.push(1)
mq.push(2)
assert mq.peek() == 1 and mq.pop() == 1 and not mq.empty()
bfs_queue = deque([1, 2])
bfs_queue.append(3)
assert bfs_queue.popleft() == 1                # O(1) FIFO
print("stack templates ok")
```

---

## 4. The same in Java (occasional reps)

**This week's Java rep (Week 5): 739 Daily Temperatures.**

| Python | Java |
|---|---|
| `st = []`, `st.append(x)`, `st.pop()`, `st[-1]` | `Deque<Integer> st = new ArrayDeque<>(); st.push(x); st.pop(); st.peek();` |
| `deque().append` / `popleft()` | `ArrayDeque`: `offer` / `poll` |
| `if not st` | `if (st.isEmpty())` |
| `int(a / b)` (truncate) | `a / b` (Java int division already truncates toward zero) |

```java
import java.util.*;

class DailyTemperaturesRep {
    static int[] dailyTemperatures(int[] t) {
        int[] ans = new int[t.length];
        Deque<Integer> st = new ArrayDeque<>();      // indices; never java.util.Stack
        for (int i = 0; i < t.length; i++) {
            while (!st.isEmpty() && t[st.peek()] < t[i]) {
                int j = st.pop();
                ans[j] = i - j;
            }
            st.push(i);
        }
        return ans;
    }

    public static void main(String[] args) {
        System.out.println(Arrays.toString(dailyTemperatures(new int[]{73, 74, 75, 71, 69, 72, 76, 73})));
        // [1, 1, 4, 2, 1, 1, 0, 0]
    }
}
```

Java traps: `st.peek()` on an empty `ArrayDeque` returns `null` → NPE when unboxed; `ArrayDeque` rejects `null` elements.

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Idea | Problems |
|---|---|---|
| Matching pairs | Map closer → opener; pop and compare | 20 |
| Design with O(1) extra query | Store aggregate alongside each element | 155 |
| Queue ↔ stack simulation | Two stacks (lazy transfer) | 232 |
| Expression evaluation | Operand stack; pop two for operators | 150 |
| Nested decoding | Stack of (string so far, repeat count) | 394 |
| Path normalisation | Split on `/`, stack of directory names | 71 |
| Monotonic stack: next greater/smaller | Pop while current beats top; resolve popped | 739, 901 |
| Monotonic stack: boundaries of each bar | First smaller on left/right → max rectangle | 84 |
| Sort + stack to merge groups | Sort by position; stack of arrival times | 853 |
| BFS queue | See [15](./15-graphs-bfs-dfs.md) | 994 |

---

## 6. How to recognise the pattern

- Nested or matching structure: brackets, tags, "valid", "balanced", decode `3[a2[c]]`.
- "Next greater / next smaller / previous smaller / how many days until…" → monotonic stack.
- The "most recent" unresolved item determines the answer; "undo", "backspace".
- Design "stack with getMin in O(1)".
- Histogram / "largest rectangle" / "span".
- Processing in arrival order, level by level → queue (BFS).
- n ≤ 10⁵ and the brute force scans right from every index → O(n) monotonic stack.

---

## 7. Beginner problems (Week 5)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 20 | [Valid Parentheses](https://leetcode.com/problems/valid-parentheses/) | Easy | Dict closer → opener; on a closer the stack must be non-empty and the top must match; end empty. |
| 232 | [Implement Queue using Stacks](https://leetcode.com/problems/implement-queue-using-stacks/) | Easy | Two lists; move `in` → `out` only when `out` is empty. |

---

## 8. Interview problems (Week 5)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 155 | [Min Stack](https://leetcode.com/problems/min-stack/) | Medium | 5 | <details><summary>show</summary>Push `(val, min(val, current_min))` tuples so every state remembers its own minimum.</details> |
| 150 | [Evaluate Reverse Polish Notation](https://leetcode.com/problems/evaluate-reverse-polish-notation/) | Medium | 5 | <details><summary>show</summary>Operand stack; pop `b` then `a`, push `a op b`. Division truncates toward zero: `int(a / b)`, not `a // b`.</details> |
| 739 | [Daily Temperatures](https://leetcode.com/problems/daily-temperatures/) | Medium | 5 | <details><summary>show</summary>Monotonic decreasing stack of indices; when a warmer day arrives, pop and set `ans[j] = i - j`.</details> |
| 853 | [Car Fleet](https://leetcode.com/problems/car-fleet/) | Medium | 5 | <details><summary>show</summary>Sort by position descending; arrival time `(target - pos) / speed` (true division); a car starts a new fleet only if its time is greater than the fleet ahead.</details> |
| 901 | [Online Stock Span](https://leetcode.com/problems/online-stock-span/) | Medium | 5 | <details><summary>show</summary>Stack of `(price, span)`; pop while top price ≤ today, accumulating spans.</details> |
| 84 | [Largest Rectangle in Histogram](https://leetcode.com/problems/largest-rectangle-in-histogram/) | Hard | 5 | <details><summary>show</summary>Increasing stack of indices; when popping bar j because h[i] < h[j], width = `i - new_top - 1` (or `i` if empty). Append a sentinel height 0.</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 394 | [Decode String](https://leetcode.com/problems/decode-string/) | Medium | <details><summary>show</summary>On `[` push `(current_string, k)` and reset; on `]` pop and set `current = prev + current * k`. Digits can be multi-digit.</details> |
| 71 | [Simplify Path](https://leetcode.com/problems/simplify-path/) | Medium | <details><summary>show</summary>`for part in path.split("/")`: skip `""` and `"."`, pop on `".."`, else push; return `"/" + "/".join(stack)`.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Stack**: 20 → 155 → 150 → 22 → 739 → 853 → 84. (22 Generate Parentheses is done in [14 Backtracking](./14-backtracking.md), where it belongs conceptually.)
Here: §3.1 → 20 → 232 → 155 → 150 → 739 → 901 → 853 → 84 → Java rep 739 → (W20–26) 394, 71.

---

## 10. Target number of problems

**8 new** in Week 5 + 2 stretch.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- Using `list.pop(0)` / `insert(0, x)` as a queue → O(n) per operation. Use `deque.popleft()`.
- `st[-1]` or `st.pop()` on an empty list → `IndexError`. Guard with `if st` / `while st and ...`.
- 150: `a // b` floors (−7 // 2 == −4) but the problem wants truncation (−3) → `int(a / b)`.
- 853: in Python 3 `/` is already true division (good); Python 2 habits or `//` give wrong fleets.
- Keeping **values** instead of **indices** on a monotonic stack when you need distances.
- `queue.Queue` is a thread-safe queue with locking overhead — for algorithms use `collections.deque`.

**General**
- 150: popping operands in the wrong order for `-` and `/`.
- 84: forgetting to flush the stack at the end (use a sentinel 0 height).
- Using `<=` vs `<` wrongly in the pop condition (strictly greater vs greater-or-equal matters in 739/901).

**Java-rep traps (739):** `java.util.Stack` is legacy; `peek()` on empty `ArrayDeque` → `null`.

---

## 12. Mastery criteria

- [ ] Implement `ArrayStack` and `RingQueue` from memory in ≤ 10 min; explain the modulo arithmetic.
- [ ] Solve 739 in ≤ 15 min and explain why it's O(n) despite the nested loop.
- [ ] Solve 155 and 150 in ≤ 12 min each from blank.
- [ ] Solve 84 in ≤ 35 min (Hard).
- [ ] Solve 2 unseen stack Mediums in ≤ 25 min each.
- [ ] Java rep: 739 in Java in ≤ 15 min.

---

## 13. Revision schedule

| Review | Week 5 set |
|---|---|
| Day 0 | W5 |
| Day 3 | W5/W6 |
| Day 7 | W6 |
| Day 14 | W7 |
| Day 30 | W9 |

**Revisit:** Week 8 (recursion ↔ explicit stack; review week: 739 and 853 timed), Week 9 (iterative tree traversals use a stack), Week 13 (BFS queue), Weeks 20–26 (394, 71).

---

## Worked example — 739. Daily Temperatures

**Clarify.** `temperatures: list[int]`, 1 ≤ n ≤ 10⁵, values 30–100. For each day, how many days until a **strictly** warmer day; 0 if none.

**Brute force.** For each i, scan right until warmer → O(n²) = 10¹⁰ worst case (decreasing temps). Hopeless in Python; n ≤ 10⁵ → O(n) or O(n log n).

**Optimise.** Days still waiting for a warmer day form a stack with **non-increasing** temperatures (if a later day were warmer, it would already have resolved the earlier ones). When day i arrives, it resolves every waiting day with a lower temperature — they're at the top. Pop them, set `ans[j] = i - j`, then push i. Each index pushed and popped once → O(n).

**Code (Python).**

```python
def daily_temperatures(temperatures: list[int]) -> list[int]:
    ans = [0] * len(temperatures)          # default 0 = "no warmer day"
    waiting: list[int] = []                # indices; temperatures non-increasing bottom → top
    for i, t in enumerate(temperatures):
        while waiting and temperatures[waiting[-1]] < t:
            j = waiting.pop()
            ans[j] = i - j
        waiting.append(i)
    return ans


assert daily_temperatures([73, 74, 75, 71, 69, 72, 76, 73]) == [1, 1, 4, 2, 1, 1, 0, 0]
assert daily_temperatures([30, 40, 50, 60]) == [1, 1, 1, 0]
assert daily_temperatures([60, 50, 40]) == [0, 0, 0]
assert daily_temperatures([50, 50, 51]) == [2, 1, 0]
assert daily_temperatures([70]) == [0]
print("739 worked example passed")
```

**Test cases.** Example → `[1,1,4,2,1,1,0,0]` · increasing → all 1 then 0 · decreasing → all 0 · equal temps `[50,50,51]` → `[2,1,0]` (strictly warmer — `<` not `<=` in the pop condition) · single day → `[0]`.

**Complexity.** Time O(n) amortised (each index pushed/popped once). Space O(n) for the stack in the worst case (decreasing input).

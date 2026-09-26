# 06 — Stack & Queue (incl. Monotonic Stack)

> **Week 6** · NeetCode section: **Stack** · Target: **6 new problems** + stretch pool
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#06--stack--queue) · Java APIs: [toolkit §6 `ArrayDeque`](./java-dsa-toolkit.md#6-arraydeque-as-stack-and-queue)

A stack remembers "what's still open / unresolved, most recent first". A queue processes "in arrival order". A **monotonic stack** keeps elements in sorted order so that each element is pushed and popped once, answering "next greater/smaller" questions in O(n). Week 6 also covers the JVM call stack in [`01-java/06-memory-jvm.md`](../01-java/06-memory-jvm.md) — the same LIFO idea.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Stack (LIFO) | push/pop/peek O(1). Java: `Deque<Integer> st = new ArrayDeque<>()` — **not** `Stack` |
| Queue (FIFO) | offer/poll/peek O(1). Java: `ArrayDeque` (or `LinkedList` if you need nulls) |
| Deque | Both ends O(1); basis for monotonic deque (sliding window max, [05](./05-sliding-window.md)) |
| Array-backed implementation | Stack = array + top index; queue = **circular buffer** with head/tail modulo capacity |
| Matching / nesting | Push openers; on closer, the top must match |
| Expression evaluation | Postfix (RPN): operands pushed, operator pops two. Operand order matters for `-` and `/` |
| Auxiliary stack | Min stack: store `(value, minSoFar)` pairs or a second stack |
| Monotonic stack | Keep stack increasing or decreasing; when the new element breaks the order, pop and **resolve** the popped element (its next greater/smaller is the current element) |
| Amortised O(n) | Each index pushed once and popped at most once |
| Two stacks → queue | Inbox/outbox; transfer only when outbox empty → amortised O(1) |

Practical use: undo history, parsing nested JSON/brackets, and the call stack you read in every Java stack trace ([`01-java/09-debugging-java.md`](../01-java/09-debugging-java.md)).

---

## 2. Prerequisite knowledge

- [`01-arrays-strings.md`](./01-arrays-strings.md) — arrays, `char` handling.
- [`05-sliding-window.md`](./05-sliding-window.md) — you've seen `ArrayDeque` as a deque.
- [Toolkit §6](./java-dsa-toolkit.md#6-arraydeque-as-stack-and-queue), [§10 Integer `==`](./java-dsa-toolkit.md#10-the-integer-caching--trap).

---

## 3. Java implementation

### 3.1 Stack and circular queue from scratch

```java
import java.util.*;

class IntStack {
    private int[] data = new int[8];
    private int top = 0;                                   // number of elements

    void push(int x) {
        if (top == data.length) data = Arrays.copyOf(data, top * 2);
        data[top++] = x;
    }
    int pop() {
        if (top == 0) throw new NoSuchElementException("stack empty");
        return data[--top];
    }
    int peek() {
        if (top == 0) throw new NoSuchElementException("stack empty");
        return data[top - 1];
    }
    boolean isEmpty() { return top == 0; }
}

class RingQueue {
    private final int[] data;
    private int head = 0, size = 0;                        // tail = (head + size) % capacity

    RingQueue(int capacity) { data = new int[capacity]; }

    boolean offer(int x) {
        if (size == data.length) return false;             // full
        data[(head + size) % data.length] = x;
        size++;
        return true;
    }
    int poll() {
        if (size == 0) throw new NoSuchElementException("queue empty");
        int x = data[head];
        head = (head + 1) % data.length;
        size--;
        return x;
    }
    boolean isEmpty() { return size == 0; }

    public static void main(String[] args) {
        IntStack s = new IntStack();
        for (int i = 0; i < 20; i++) s.push(i);
        System.out.println(s.pop() + " " + s.peek());      // 19 18
        RingQueue q = new RingQueue(3);
        q.offer(1); q.offer(2); q.offer(3);
        System.out.println(q.offer(4) + " " + q.poll());   // false 1
        q.offer(4);                                        // wraps around
        System.out.println(q.poll() + " " + q.poll() + " " + q.poll()); // 2 3 4
    }
}
```

### 3.2 Pattern templates

```java
import java.util.*;

class StackTemplates {
    // Bracket matching (LC 20)
    static boolean validBrackets(String s) {
        Deque<Character> st = new ArrayDeque<>();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '(' -> st.push(')');                  // push the EXPECTED closer
                case '[' -> st.push(']');
                case '{' -> st.push('}');
                default -> { if (st.isEmpty() || st.pop() != c) return false; }
            }
        }
        return st.isEmpty();
    }

    // Monotonic decreasing stack: next greater element to the right (distance version = LC 739)
    static int[] nextGreaterDistance(int[] a) {
        int[] ans = new int[a.length];
        Deque<Integer> st = new ArrayDeque<>();            // indices, values strictly decreasing
        for (int i = 0; i < a.length; i++) {
            while (!st.isEmpty() && a[st.peek()] < a[i]) {
                int j = st.pop();                          // a[i] is j's next greater element
                ans[j] = i - j;
            }
            st.push(i);
        }
        return ans;                                        // unresolved indices stay 0
    }

    // Evaluate RPN (LC 150)
    static int evalRPN(String[] tokens) {
        Deque<Integer> st = new ArrayDeque<>();
        for (String t : tokens) {
            switch (t) {
                case "+" -> st.push(st.pop() + st.pop());
                case "*" -> st.push(st.pop() * st.pop());
                case "-" -> { int b = st.pop(), a = st.pop(); st.push(a - b); }   // order matters
                case "/" -> { int b = st.pop(), a = st.pop(); st.push(a / b); }   // truncates toward 0
                default -> st.push(Integer.parseInt(t));
            }
        }
        return st.pop();
    }

    // Min stack with pairs (LC 155)
    static final class MinStack {
        private final Deque<int[]> st = new ArrayDeque<>(); // {value, minSoFar}
        void push(int v) { st.push(new int[]{v, st.isEmpty() ? v : Math.min(v, st.peek()[1])}); }
        void pop() { st.pop(); }
        int top() { return st.peek()[0]; }
        int getMin() { return st.peek()[1]; }
    }

    // Queue via two stacks (LC 232): amortised O(1)
    static final class MyQueue {
        private final Deque<Integer> in = new ArrayDeque<>(), out = new ArrayDeque<>();
        void push(int x) { in.push(x); }
        int pop() { shift(); return out.pop(); }
        int peek() { shift(); return out.peek(); }
        boolean empty() { return in.isEmpty() && out.isEmpty(); }
        private void shift() { if (out.isEmpty()) while (!in.isEmpty()) out.push(in.pop()); }
    }

    public static void main(String[] args) {
        System.out.println(validBrackets("({[]})") + " " + validBrackets("(]"));          // true false
        System.out.println(Arrays.toString(nextGreaterDistance(new int[]{73, 74, 75, 71, 69, 72, 76, 73})));
        // [1, 1, 4, 2, 1, 1, 0, 0]
        System.out.println(evalRPN(new String[]{"4", "13", "5", "/", "+"}));              // 6
        MinStack ms = new MinStack(); ms.push(-2); ms.push(0); ms.push(-3);
        System.out.print(ms.getMin() + " "); ms.pop(); System.out.println(ms.top() + " " + ms.getMin()); // -3 0 -2
        MyQueue q = new MyQueue(); q.push(1); q.push(2);
        System.out.println(q.peek() + " " + q.pop() + " " + q.empty());                  // 1 1 false
    }
}
```

---

## 4. Common patterns (sub-variants)

| Sub-pattern | Idea | Problems |
|---|---|---|
| Matching pairs | Push expected closer; pop and compare | 20 |
| Design with O(1) extra query | Store aggregate alongside each element | 155 |
| Queue ↔ stack simulation | Two stacks (lazy transfer) | 232 |
| Expression evaluation | Operand stack; pop two for operators | 150 |
| Monotonic stack: next greater/smaller | Pop while current beats top; resolve popped | 739, 901 |
| Monotonic stack: boundaries of each bar | For each bar, first smaller on left/right → max rectangle | 84 |
| Sort + stack to merge "fleets"/groups | Sort by position; stack of arrival times | 853 |
| BFS queue | See [15](./15-graphs-bfs-dfs.md) | 994, 102 |

---

## 5. How to recognise the pattern

- Nested or matching structure: brackets, tags, "valid", "balanced", decode `3[a2[c]]`.
- "Next greater / next smaller / previous smaller / how many days until…" → monotonic stack.
- "Most recent" unresolved item determines the answer; "undo", "backspace".
- Design "stack with getMin in O(1)".
- Histogram / "largest rectangle" / "span".
- Processing in arrival order, level by level → queue (BFS).
- n ≤ 10⁵ and the brute force scans right from every index → O(n) monotonic stack.

---

## 6. Beginner problems (Week 6)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 20 | [Valid Parentheses](https://leetcode.com/problems/valid-parentheses/) | Easy | Push the expected closer; on a closer, stack must be non-empty and top must match; end empty. |
| 232 | [Implement Queue using Stacks](https://leetcode.com/problems/implement-queue-using-stacks/) | Easy | Two stacks; move `in` → `out` only when `out` is empty. |

---

## 7. Interview problems (Week 6)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 155 | [Min Stack](https://leetcode.com/problems/min-stack/) | Medium | 6 | <details><summary>show</summary>Push `(val, min(val, currentMin))` pairs so every state remembers its own minimum.</details> |
| 150 | [Evaluate Reverse Polish Notation](https://leetcode.com/problems/evaluate-reverse-polish-notation/) | Medium | 6 | <details><summary>show</summary>Operand stack; for `-` and `/` pop `b` first, then `a`, push `a op b`. Java `/` truncates toward zero as required.</details> |
| 739 | [Daily Temperatures](https://leetcode.com/problems/daily-temperatures/) | Medium | 6 | <details><summary>show</summary>Monotonic decreasing stack of indices; when a warmer day arrives, pop and set `ans[j] = i - j`.</details> |
| 853 | [Car Fleet](https://leetcode.com/problems/car-fleet/) | Medium | 6 | <details><summary>show</summary>Sort by position descending; compute arrival time `(target - pos) / speed` as `double`; a car forms a new fleet only if its time is greater than the fleet ahead.</details> |

### Stretch pool (Weeks 21–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 84 | [Largest Rectangle in Histogram](https://leetcode.com/problems/largest-rectangle-in-histogram/) | Hard | <details><summary>show</summary>Increasing stack of indices; when popping bar j because h[i] < h[j], its width is `i - (newTop) - 1`. Append a sentinel height 0.</details> |
| 901 | [Online Stock Span](https://leetcode.com/problems/online-stock-span/) | Medium | <details><summary>show</summary>Stack of `(price, span)`; pop while top price ≤ today, accumulating spans.</details> |

---

## 8. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Stack**: 20 → 155 → 150 → 22 → 739 → 853 → 84. (22 Generate Parentheses is done in [14 Backtracking](./14-backtracking.md) where it belongs conceptually.)
Here: implement §3.1 → 20 → 232 → 155 → 150 → 739 → 853 → (W21–26) 84, 901.

---

## 9. Target number of problems

**6 new** in Week 6 (+ 2 in [07 Binary Search](./07-binary-search.md) = the week's 8) + 2 stretch.

---

## 10. Mistakes beginners commonly make

- Using `java.util.Stack` — legacy, synchronized; interviewers may ask why. Use `ArrayDeque`.
- `st.peek()` on an empty `ArrayDeque` returns `null` → `NullPointerException` when unboxed to `int`. Check `isEmpty()` first.
- `ArrayDeque` rejects `null` elements → `NullPointerException` on `push(null)`.
- Comparing `Integer` tops with `==` (`st.peek() == other.peek()`) → wrong above 127.
- 150: popping operands in the wrong order for `-` and `/`.
- 739: storing **values** instead of **indices** on the stack (you need positions for distances).
- 853: integer division for arrival times → wrong fleets. Use `double`.
- 84: forgetting to flush the stack at the end (use a sentinel 0 height).
- Mixing `push`/`offer` on the same deque (head vs tail) by accident.

---

## 11. Mastery criteria

- [ ] Implement `IntStack` and `RingQueue` from memory in ≤ 10 min; explain the modulo arithmetic.
- [ ] Solve 739 in ≤ 15 min and explain why it's O(n) despite the nested loop.
- [ ] Solve 155 and 150 in ≤ 12 min each from blank.
- [ ] Solve 2 unseen stack Mediums in ≤ 25 min each.

---

## 12. Revision schedule

| Review | Week 6 set |
|---|---|
| Day 0 | W6 |
| Day 3 | W6/W7 |
| Day 7 | W7 |
| Day 14 | W8 (review week — 739 and 853 timed) |
| Day 30 | W10 |

**Revisit:** Week 9 (recursion ↔ explicit stack), Week 10 (iterative tree traversals use a stack), Week 14 (BFS queue), Weeks 21–26 (84, 901).

---

## Worked example — 739. Daily Temperatures

**Clarify.** `int[] temperatures`, 1 ≤ n ≤ 10⁵, values 30–100. For each day, how many days until a **strictly** warmer day; 0 if none.

**Brute force.** For each i, scan right until warmer → O(n²) = 10¹⁰ worst case (decreasing temps). Too slow; n ≤ 10⁵ → O(n log n) or O(n).

**Optimise.** Days still waiting for a warmer day form a stack with **non-increasing** temperatures (if a later day were warmer, it would already have resolved the earlier ones). When day i arrives, it resolves every waiting day with a lower temperature — they're at the top. Pop them, set `ans[j] = i - j`, then push i. Each index pushed and popped once → O(n).

**Code.**

```java
import java.util.*;

class DailyTemperatures {
    public int[] dailyTemperatures(int[] temperatures) {
        int n = temperatures.length;
        int[] ans = new int[n];                      // default 0 = "no warmer day"
        Deque<Integer> waiting = new ArrayDeque<>(); // indices, temperatures non-increasing from bottom to top
        for (int i = 0; i < n; i++) {
            while (!waiting.isEmpty() && temperatures[waiting.peek()] < temperatures[i]) {
                int j = waiting.pop();
                ans[j] = i - j;
            }
            waiting.push(i);
        }
        return ans;
    }

    public static void main(String[] args) {
        DailyTemperatures s = new DailyTemperatures();
        System.out.println(Arrays.toString(s.dailyTemperatures(new int[]{73, 74, 75, 71, 69, 72, 76, 73}))); // [1, 1, 4, 2, 1, 1, 0, 0]
        System.out.println(Arrays.toString(s.dailyTemperatures(new int[]{30, 40, 50, 60})));                 // [1, 1, 1, 0]
        System.out.println(Arrays.toString(s.dailyTemperatures(new int[]{60, 50, 40})));                     // [0, 0, 0]
        System.out.println(Arrays.toString(s.dailyTemperatures(new int[]{50, 50, 51})));                     // [2, 1, 0]
    }
}
```

**Test cases.** Example → `[1,1,4,2,1,1,0,0]` · increasing → all 1 then 0 · decreasing → all 0 · equal temps `[50,50,51]` → `[2,1,0]` (strictly warmer — `<` not `<=` in the pop condition) · single day → `[0]`.

**Complexity.** Time O(n) amortised (each index pushed/popped once). Space O(n) for the stack in the worst case (decreasing input).

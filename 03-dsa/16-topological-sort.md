# 16 — Topological Sort

> **Week 14** · NeetCode section: **Graphs** (Course Schedule I/II) + **Advanced Graphs** (Alien Dictionary) · Target: **4 new problems** + stretch pool · Java rep: **207** (Week 14)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#16--topological-sort) · Python: [`19-python/02-interview-toolkit.md`](../19-python/02-interview-toolkit.md) (`deque`, `defaultdict`) · Java: [quick reference](./java-dsa-toolkit.md)

A topological order lists the nodes of a **directed acyclic graph (DAG)** so that every edge `u → v` has `u` before `v`. It answers "in what order can I do these tasks given their dependencies?" and "is there a circular dependency?". This pattern lands in Week 14 on purpose: in **Week 19** you implement DAG pipelines in [ForgeCI](../18-projects/forgeci/README.md) (`needs:` between jobs, fan-out/fan-in, fail-fast) — the same algorithm, running in production Java code.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| DAG | Directed graph with no cycles. A topological order exists **iff** the graph is a DAG |
| Edge direction convention | Decide and say it: "`[a, b]` means *b before a*" (LC 207) → edge `b → a` |
| In-degree | Number of incoming edges = number of unmet prerequisites |
| Kahn's algorithm (BFS) | Queue all in-degree-0 nodes; pop, append to order, decrement neighbours' in-degree; enqueue those that reach 0. If `len(order) < n` → cycle |
| DFS 3-colour | WHITE (unvisited) / GRAY (on the current path) / BLACK (done). Edge to a GRAY node = back edge = **cycle**. Reverse postorder = topological order |
| Multiple valid orders | Any order is accepted unless the problem asks for lexicographic → use a `heapq` instead of a `deque` in Kahn's |
| Levels / parallelism | Kahn's by levels gives "which jobs can run in parallel at step k" — exactly ForgeCI's fan-out scheduling |
| Reachability closure | "Is u a prerequisite of v?" for many queries → propagate ancestor sets in topological order (1462) |
| Complexity | O(V + E) time and space |
| stdlib | `graphlib.TopologicalSorter` (Python 3.9+) exists — know it, but implement Kahn's in interviews |

Interview angle: "How does Maven/Gradle decide build order?", "How would your CI system run jobs with dependencies?" — answer with Kahn's algorithm and cycle detection, then point to your ForgeCI implementation.

---

## 2. Prerequisite knowledge

- [`15-graphs-bfs-dfs.md`](./15-graphs-bfs-dfs.md) — adjacency lists, BFS with `deque`, iterative DFS.
- [`13-heap-priority-queue.md`](./13-heap-priority-queue.md) — for lexicographically smallest orders.
- [`09-recursion.md`](./09-recursion.md) — recursion limit (DFS variant on 10⁵ nodes).

---

## 3. Python implementation

```python
import heapq
from collections import deque
from graphlib import CycleError, TopologicalSorter


def kahn(n: int, edges: list[tuple[int, int]]) -> list[int]:
    """edges are (u, v) meaning u must come before v. Returns [] if there is a cycle."""
    adj: list[list[int]] = [[] for _ in range(n)]
    indeg = [0] * n
    for u, v in edges:
        adj[u].append(v)
        indeg[v] += 1
    q = deque(i for i in range(n) if indeg[i] == 0)
    order = []
    while q:
        u = q.popleft()
        order.append(u)
        for v in adj[u]:
            indeg[v] -= 1
            if indeg[v] == 0:
                q.append(v)
    return order if len(order) == n else []          # leftover nodes are on/after a cycle


def kahn_levels(n: int, edges: list[tuple[int, int]]) -> list[list[int]]:
    """Batches of nodes that can run in parallel (CI 'stages')."""
    adj: list[list[int]] = [[] for _ in range(n)]
    indeg = [0] * n
    for u, v in edges:
        adj[u].append(v)
        indeg[v] += 1
    level = [i for i in range(n) if indeg[i] == 0]
    levels = []
    while level:
        levels.append(level)
        nxt = []
        for u in level:
            for v in adj[u]:
                indeg[v] -= 1
                if indeg[v] == 0:
                    nxt.append(v)
        level = nxt
    return levels


def lexicographic_topo(n: int, edges: list[tuple[int, int]]) -> list[int]:
    adj: list[list[int]] = [[] for _ in range(n)]
    indeg = [0] * n
    for u, v in edges:
        adj[u].append(v)
        indeg[v] += 1
    heap = [i for i in range(n) if indeg[i] == 0]
    heapq.heapify(heap)
    order = []
    while heap:
        u = heapq.heappop(heap)                         # smallest available node first
        order.append(u)
        for v in adj[u]:
            indeg[v] -= 1
            if indeg[v] == 0:
                heapq.heappush(heap, v)
    return order if len(order) == n else []


def has_cycle_dfs(n: int, edges: list[tuple[int, int]]) -> bool:
    """Iterative 3-colour DFS (safe for deep graphs)."""
    WHITE, GRAY, BLACK = 0, 1, 2
    adj: list[list[int]] = [[] for _ in range(n)]
    for u, v in edges:
        adj[u].append(v)
    color = [WHITE] * n
    for s in range(n):
        if color[s] != WHITE:
            continue
        stack = [(s, iter(adj[s]))]
        color[s] = GRAY
        while stack:
            u, it = stack[-1]
            v = next(it, None)
            if v is None:
                color[u] = BLACK                        # all descendants done
                stack.pop()
            elif color[v] == GRAY:
                return True                             # back edge → cycle
            elif color[v] == WHITE:
                color[v] = GRAY
                stack.append((v, iter(adj[v])))
    return False


edges = [(0, 1), (0, 2), (1, 3), (2, 3)]
order = kahn(4, edges)
pos = {node: i for i, node in enumerate(order)}
assert all(pos[u] < pos[v] for u, v in edges)
assert kahn(2, [(0, 1), (1, 0)]) == []
assert kahn_levels(4, edges) == [[0], [1, 2], [3]]
assert lexicographic_topo(3, [(2, 0)]) == [1, 2, 0]
assert has_cycle_dfs(3, [(0, 1), (1, 2), (2, 0)]) and not has_cycle_dfs(4, edges)
ts = TopologicalSorter({3: {1, 2}, 1: {0}, 2: {0}})    # node -> its predecessors
assert list(ts.static_order())[0] == 0
try:
    list(TopologicalSorter({0: {1}, 1: {0}}).static_order())
except CycleError:
    print("graphlib detected the cycle")
print("topological-sort templates ok")
```

---

## 4. The same in Java (occasional reps)

**This week's Java rep (Week 14): 207 Course Schedule.**

| Python | Java |
|---|---|
| `[[] for _ in range(n)]` | `List<List<Integer>> adj = new ArrayList<>(); for (...) adj.add(new ArrayList<>());` |
| `indeg = [0] * n` | `int[] indeg = new int[n];` |
| `deque(i for i in range(n) if indeg[i] == 0)` | loop + `q.offer(i)` on an `ArrayDeque<Integer>` |
| `heapq` for lexicographic order | `PriorityQueue<Integer>` |

```java
import java.util.*;

class CourseScheduleRep {
    static boolean canFinish(int numCourses, int[][] prerequisites) {
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < numCourses; i++) adj.add(new ArrayList<>());
        int[] indeg = new int[numCourses];
        for (int[] p : prerequisites) { adj.get(p[1]).add(p[0]); indeg[p[0]]++; }   // p[1] before p[0]
        Deque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < numCourses; i++) if (indeg[i] == 0) q.offer(i);
        int taken = 0;
        while (!q.isEmpty()) {
            int u = q.poll();
            taken++;
            for (int v : adj.get(u)) if (--indeg[v] == 0) q.offer(v);
        }
        return taken == numCourses;
    }

    public static void main(String[] args) {
        System.out.println(canFinish(2, new int[][]{{1, 0}}) + " " + canFinish(2, new int[][]{{1, 0}, {0, 1}})); // true false
    }
}
```

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Technique | Problems |
|---|---|---|
| Can all tasks finish? (cycle detection) | Kahn's count == n, or 3-colour DFS | 207 |
| Produce one valid order | Kahn's order | 210 |
| Nodes not on/leading to a cycle | Reverse graph + Kahn's from terminal nodes, or 3-colour DFS "safe" = BLACK without reaching GRAY | 802 |
| Many "is u before v?" queries | Ancestor sets propagated in topological order (or DFS from each node) | 1462 |
| Derive edges from ordering evidence | Compare adjacent words; first differing char gives an edge | 269 |
| Parallel stages | Kahn's by levels | ForgeCI DAG scheduling (W19) |

---

## 6. How to recognise the pattern

- Words: "prerequisite", "dependency", "must be done before", "order of tasks/courses/builds", "compile order".
- A directed graph and a question about **ordering** or **circular dependency**.
- "Return any valid order" → Kahn's. "Is it possible?" → cycle detection.
- Directed-graph cycle detection (undirected cycles are simpler — see [15](./15-graphs-bfs-dfs.md) / [17](./17-union-find.md)).
- n up to 10⁵ with edges up to 10⁵–10⁶ → O(V + E) BFS; avoid recursion in Python.

---

## 7. Beginner problems (Week 14)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 207 | [Course Schedule](https://leetcode.com/problems/course-schedule/) | Medium | `[a, b]` means b before a → edge `b → a`. Kahn's: return `taken == numCourses`. |

---

## 8. Interview problems (Week 14)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 210 | [Course Schedule II](https://leetcode.com/problems/course-schedule-ii/) | Medium | 14 | <details><summary>show</summary>Same as 207 but return the Kahn order; `[]` if its length < n.</details> |
| 802 | [Find Eventual Safe States](https://leetcode.com/problems/find-eventual-safe-states/) | Medium | 14 | <details><summary>show</summary>Reverse every edge; terminal nodes have out-degree 0 → Kahn's from them on the reversed graph; everything popped is safe. Return sorted.</details> |
| 1462 | [Course Schedule IV](https://leetcode.com/problems/course-schedule-iv/) | Medium | 14 | <details><summary>show</summary>Process in topological order; `ancestors[v] \|= ancestors[u] \| {u}` for each edge u→v; answer each query with a set lookup. (n ≤ 100, so Floyd–Warshall-style reachability also works.)</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 269 | [Alien Dictionary](https://leetcode.com/problems/alien-dictionary/) *(LeetCode Premium — free on NeetCode)* | Hard | <details><summary>show</summary>Adjacent words give at most one edge (first differing char). Invalid if a word is followed by its own proper prefix. Kahn's over all seen letters; cycle → `""`.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150: **Graphs** 207 → 210; **Advanced Graphs** 269.
Here: §3 from memory → 207 → 210 → 802 → 1462 → Java rep 207 → union-find ([17](./17-union-find.md)) the same week → (W20–26) 269.

---

## 10. Target number of problems

**4 new** in Week 14 (+ 4 union-find = 8) + 1 stretch.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- Recursive 3-colour DFS on 10⁵ nodes → `RecursionError`. Use Kahn's or the iterative DFS in §3.
- `[[]] * n` adjacency lists (aliasing) — every node shares one list.
- `list.pop(0)` for the Kahn queue → O(V²). Use `deque`.
- `defaultdict(list)` graphs: nodes with no edges never appear as keys — initialise all nodes (269: every letter that appears in any word).
- Sorting the output "to be safe" when the problem accepts any order (wastes time) — but 802 *does* require sorted output.

**General**
- Reversing the edge direction for `[a, b]` pairs — say the convention out loud.
- Forgetting the cycle check (`len(order) < n`).
- 269: missing the prefix edge case (`["abc", "ab"]` is invalid) and adding more than one edge per adjacent pair.
- Confusing directed cycle detection (needs GRAY state) with undirected (parent check).

**Java-rep traps (207):** `List<List<Integer>>` initialisation for all n nodes; `int[]` pairs `p[0]`/`p[1]` direction.

---

## 12. Mastery criteria

- [ ] Write Kahn's algorithm with cycle detection from memory in ≤ 8 min.
- [ ] Explain the 3-colour DFS and why a GRAY neighbour means a cycle.
- [ ] Solve 210 in ≤ 15 min and 802 in ≤ 25 min from blank.
- [ ] Explain how you'd schedule dependent CI jobs in parallel stages (Kahn's by levels) — and later point to your ForgeCI M6 code.
- [ ] Java rep: 207 in Java in ≤ 15 min.

---

## 13. Revision schedule

| Review | Week 14 set (207, 210, 802, 1462) |
|---|---|
| Day 0 | W14 |
| Day 3 | W14/W15 |
| Day 7 | W15 |
| Day 14 | W16 (Checkpoint 16) |
| Day 30 | W18 |

**Revisit:** **Week 19** — ForgeCI M6 DAG pipelines: re-solve 210 timed the day before you design job dependencies, then explain your production implementation in the project deep-dive. Weeks 20–26 (269).

---

## Worked example — 210. Course Schedule II

**Clarify.** `numCourses` n (1 ≤ n ≤ 2000), `prerequisites[i] = [a, b]` means **take b before a** (up to n(n−1) pairs, no duplicates). Return any valid order of all courses, or `[]` if impossible (cycle). Courses with no prerequisites can go anywhere valid.

**Brute force.** Try permutations and check each → O(n! · E). Hopeless.

**Optimise.** Build edges `b → a`, count in-degrees. Kahn's: start with every course that has no prerequisites; each time we "take" a course, its dependants lose one unmet prerequisite; when that hits 0 they become available. If we can't take all n courses, the remaining ones are stuck on a cycle.

**Code (Python).**

```python
from collections import deque


def find_order(num_courses: int, prerequisites: list[list[int]]) -> list[int]:
    adj: list[list[int]] = [[] for _ in range(num_courses)]
    indeg = [0] * num_courses
    for course, pre in prerequisites:
        adj[pre].append(course)            # pre → course
        indeg[course] += 1
    q = deque(c for c in range(num_courses) if indeg[c] == 0)
    order: list[int] = []
    while q:
        c = q.popleft()
        order.append(c)
        for nxt in adj[c]:
            indeg[nxt] -= 1
            if indeg[nxt] == 0:
                q.append(nxt)
    return order if len(order) == num_courses else []


def is_valid(order: list[int], n: int, prereqs: list[list[int]]) -> bool:
    pos = {c: i for i, c in enumerate(order)}
    return len(order) == n and all(pos[b] < pos[a] for a, b in prereqs)


assert find_order(2, [[1, 0]]) == [0, 1]
p = [[1, 0], [2, 0], [3, 1], [3, 2]]
assert is_valid(find_order(4, p), 4, p)
assert find_order(1, []) == [0]
assert find_order(2, [[0, 1], [1, 0]]) == []
assert find_order(3, [[1, 0], [1, 2], [0, 1]]) == []          # cycle 0 ↔ 1, course 2 still free
print("210 worked example passed")
```

**Test cases.** `n=2, [[1,0]]` → `[0,1]` · diamond `[[1,0],[2,0],[3,1],[3,2]]` → any order with 0 first and 3 last (check with a validator, not a fixed list) · single course → `[0]` · 2-cycle → `[]` · partial cycle with a free course → `[]`.

**Complexity.** Time O(V + E). Space O(V + E) for the adjacency lists, in-degrees and queue.

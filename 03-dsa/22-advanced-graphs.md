# 22 — Advanced Graphs (Dijkstra, MST awareness)

> **Week 19** · NeetCode section: **Advanced Graphs** · Target: **4 new problems** + stretch pool · Java rep: **743** (Week 19)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#22--advanced-graphs) · Python: [`19-python/02-interview-toolkit.md`](../19-python/02-interview-toolkit.md) (`heapq`) · Java: [quick reference](./java-dsa-toolkit.md)

BFS finds shortest paths when every edge costs 1. When edges have **weights**, you need Dijkstra (non-negative weights), Bellman-Ford (negative weights or "at most k edges"), and for "connect everything as cheaply as possible", a **minimum spanning tree** (Prim or Kruskal). For junior interviews, Dijkstra must be fluent; MST and Bellman-Ford at "can explain and implement with a template" level.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Dijkstra | Min-heap of `(dist, node)`; pop the closest unsettled node; relax its edges. Correct only for **non-negative** weights. O((V + E) log V) |
| Lazy deletion | `heapq` has no decrease-key: push duplicates and skip stale entries (`if d > dist[u]: continue`) |
| Minimax / bottleneck paths | Same skeleton, but path cost = `max(edge)` instead of sum (1631, 778) |
| Bellman-Ford | Relax **all** edges V − 1 times; handles negative weights; detects negative cycles. With "at most k edges": k + 1 rounds using a **copy** of the previous distances (787) |
| MST | Tree connecting all V nodes with minimum total weight (V − 1 edges) |
| Prim | Grow one tree with a heap of crossing edges — like Dijkstra with key = edge weight, not path length. Dense graphs: O(V²) array version |
| Kruskal | Sort edges; add if endpoints are in different components (union-find, [17](./17-union-find.md)). O(E log E) |
| Eulerian path (awareness) | Use every edge once — Hierholzer's algorithm (332) |
| Floyd–Warshall (awareness) | All-pairs shortest paths, O(V³), V ≤ ~400 |
| When BFS suffices | All weights equal (or 0/1 → 0-1 BFS with a deque) |

Practical use: ForgeCI's DAG scheduling in [ForgeCI](../18-projects/forgeci/README.md) M6 (this week) uses **topological order** ([16](./16-topological-sort.md)), and the critical path through a DAG of job durations is a longest-path DP over that order — a good "Dijkstra vs DAG DP" discussion for your project deep-dive.

---

## 2. Prerequisite knowledge

- [`15-graphs-bfs-dfs.md`](./15-graphs-bfs-dfs.md) — adjacency lists, BFS.
- [`13-heap-priority-queue.md`](./13-heap-priority-queue.md) — `heapq`, tuple ordering, lazy deletion.
- [`17-union-find.md`](./17-union-find.md) — for Kruskal.

---

## 3. Python implementation

```python
import heapq
from collections import defaultdict


def dijkstra(n: int, edges: list[tuple[int, int, int]], src: int) -> list[float]:
    """Directed weighted edges (u, v, w), w >= 0. Returns dist list (inf = unreachable)."""
    adj: defaultdict[int, list[tuple[int, int]]] = defaultdict(list)
    for u, v, w in edges:
        adj[u].append((v, w))
    dist = [float("inf")] * n
    dist[src] = 0
    heap = [(0, src)]                                 # (distance, node): ties compare ints — safe
    while heap:
        d, u = heapq.heappop(heap)
        if d > dist[u]:
            continue                                  # stale entry (lazy deletion)
        for v, w in adj[u]:
            nd = d + w
            if nd < dist[v]:
                dist[v] = nd
                heapq.heappush(heap, (nd, v))
    return dist


def bellman_ford_k_edges(n: int, edges: list[tuple[int, int, int]], src: int, dst: int, k: int) -> int:
    """Cheapest src→dst using at most k+1 edges (LC 787 shape)."""
    INF = float("inf")
    dist = [INF] * n
    dist[src] = 0
    for _ in range(k + 1):
        nxt = dist[:]                                 # COPY: read only last round's values
        for u, v, w in edges:
            if dist[u] + w < nxt[v]:
                nxt[v] = dist[u] + w
        dist = nxt
    return -1 if dist[dst] == INF else int(dist[dst])


def prim_mst(points: list[list[int]]) -> int:
    """Dense complete graph (LC 1584): O(V^2) Prim without a heap."""
    n = len(points)
    in_tree = [False] * n
    best = [float("inf")] * n                         # cheapest edge connecting each node to the tree
    best[0] = 0
    total = 0
    for _ in range(n):
        u = min((i for i in range(n) if not in_tree[i]), key=best.__getitem__)
        in_tree[u] = True
        total += best[u]
        ux, uy = points[u]
        for v in range(n):
            if not in_tree[v]:
                d = abs(ux - points[v][0]) + abs(uy - points[v][1])
                if d < best[v]:
                    best[v] = d
    return int(total)


def kruskal_mst(n: int, edges: list[tuple[int, int, int]]) -> int:
    parent = list(range(n))

    def find(x: int) -> int:
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    total = used = 0
    for w, u, v in sorted((w, u, v) for u, v, w in edges):
        ru, rv = find(u), find(v)
        if ru != rv:
            parent[ru] = rv
            total += w
            used += 1
            if used == n - 1:
                break
    return total if used == n - 1 else -1             # -1: graph not connected


def min_effort(heights: list[list[int]]) -> int:
    """Minimax Dijkstra (LC 1631): path cost = max step along the path."""
    R, C = len(heights), len(heights[0])
    effort = [[float("inf")] * C for _ in range(R)]
    effort[0][0] = 0
    heap = [(0, 0, 0)]
    while heap:
        e, r, c = heapq.heappop(heap)
        if (r, c) == (R - 1, C - 1):
            return e
        if e > effort[r][c]:
            continue
        for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nr, nc = r + dr, c + dc
            if 0 <= nr < R and 0 <= nc < C:
                ne = max(e, abs(heights[nr][nc] - heights[r][c]))
                if ne < effort[nr][nc]:
                    effort[nr][nc] = ne
                    heapq.heappush(heap, (ne, nr, nc))
    return 0


assert dijkstra(4, [(0, 1, 1), (1, 2, 2), (0, 2, 5), (2, 3, 1)], 0) == [0, 1, 3, 4]
assert dijkstra(2, [], 0)[1] == float("inf")
flights = [(0, 1, 100), (1, 2, 100), (2, 0, 100), (1, 3, 600), (2, 3, 200)]
assert bellman_ford_k_edges(4, flights, 0, 3, 1) == 700 and bellman_ford_k_edges(4, flights, 0, 3, 2) == 400
assert prim_mst([[0, 0], [2, 2], [3, 10], [5, 2], [7, 0]]) == 20
assert kruskal_mst(4, [(0, 1, 1), (1, 2, 2), (0, 2, 3), (2, 3, 4)]) == 7 and kruskal_mst(3, [(0, 1, 1)]) == -1
assert min_effort([[1, 2, 2], [3, 8, 2], [5, 3, 5]]) == 2
print("advanced-graph templates ok")
```

---

## 4. The same in Java (occasional reps)

**This week's Java rep (Week 19): 743 Network Delay Time.**

| Python | Java |
|---|---|
| `heapq` of `(dist, node)` tuples | `PriorityQueue<int[]>((a, b) -> Integer.compare(a[0], b[0]))` |
| `float("inf")` | `Integer.MAX_VALUE` as "unvisited" — but never add to it; check before `d + w` |
| `defaultdict(list)` of `(v, w)` | `List<List<int[]>> adj` |
| `dist[:]` copy (Bellman-Ford) | `dist.clone()` |

```java
import java.util.*;

class NetworkDelayRep {
    static int networkDelayTime(int[][] times, int n, int k) {
        List<List<int[]>> adj = new ArrayList<>();
        for (int i = 0; i <= n; i++) adj.add(new ArrayList<>());
        for (int[] t : times) adj.get(t[0]).add(new int[]{t[1], t[2]});
        int[] dist = new int[n + 1];
        Arrays.fill(dist, Integer.MAX_VALUE);
        dist[k] = 0;
        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> Integer.compare(a[0], b[0]));
        pq.offer(new int[]{0, k});
        while (!pq.isEmpty()) {
            int[] cur = pq.poll();
            int d = cur[0], u = cur[1];
            if (d > dist[u]) continue;                      // stale
            for (int[] e : adj.get(u)) {
                int nd = d + e[1];                          // d is finite here → no overflow
                if (nd < dist[e[0]]) { dist[e[0]] = nd; pq.offer(new int[]{nd, e[0]}); }
            }
        }
        int ans = 0;
        for (int i = 1; i <= n; i++) {
            if (dist[i] == Integer.MAX_VALUE) return -1;
            ans = Math.max(ans, dist[i]);
        }
        return ans;
    }

    public static void main(String[] args) {
        System.out.println(networkDelayTime(new int[][]{{2, 1, 1}, {2, 3, 1}, {3, 4, 1}}, 4, 2));   // 2
    }
}
```

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Algorithm | Problems |
|---|---|---|
| Single-source shortest times, non-negative weights | Dijkstra | 743 |
| Minimax path (bottleneck) on a grid | Dijkstra with `max` instead of `+` (or binary search + BFS) | 1631, 778 |
| Minimum cost to connect everything | Prim (dense) / Kruskal (sparse) | 1584 |
| Shortest path with an edge-count limit | Bellman-Ford with k + 1 rounds (or BFS by stops with pruning) | 787 |
| Use every edge exactly once, lexicographically smallest | Hierholzer with sorted adjacency (min-heap per node) | 332 |

---

## 6. How to recognise the pattern

- **Weighted** edges + "shortest", "cheapest", "minimum time/delay" → Dijkstra (if weights ≥ 0).
- "Within k stops/edges" → Bellman-Ford rounds, or Dijkstra over `(node, stops)` states.
- "Minimise the maximum step/height/effort along a path" → minimax Dijkstra.
- "Connect all points/cities with minimum total cost" → MST.
- Negative weights → not Dijkstra.
- Unweighted → plain BFS ([15](./15-graphs-bfs-dfs.md)); weights 0/1 → 0-1 BFS with a `deque`.

---

## 7. Beginner problems (Week 19)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 743 | [Network Delay Time](https://leetcode.com/problems/network-delay-time/) | Medium | Plain Dijkstra from `k`; answer = max distance, or −1 if any node is unreachable. Nodes are 1-indexed. |
| 1631 | [Path With Minimum Effort](https://leetcode.com/problems/path-with-minimum-effort/) | Medium | Dijkstra on the grid where a path's cost is the **max** step; return when popping the target. |

---

## 8. Interview problems (Week 19)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 1584 | [Min Cost to Connect All Points](https://leetcode.com/problems/min-cost-to-connect-all-points/) | Medium | 19 | <details><summary>show</summary>Complete graph with Manhattan weights: O(V²) Prim with a `best` array beats building all V² edges for Kruskal (V ≤ 1000).</details> |
| 787 | [Cheapest Flights Within K Stops](https://leetcode.com/problems/cheapest-flights-within-k-stops/) | Medium | 19 | <details><summary>show</summary>k stops = k + 1 edges → k + 1 Bellman-Ford rounds, each relaxing from a **copy** of the previous round's distances. Plain Dijkstra on node alone is wrong (a cheaper path may use too many stops).</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 778 | [Swim in Rising Water](https://leetcode.com/problems/swim-in-rising-water/) | Hard | <details><summary>show</summary>Minimax Dijkstra: cost of a path = max elevation on it; pop cells in order of `max(t, grid[r][c])`.</details> |
| 332 | [Reconstruct Itinerary](https://leetcode.com/problems/reconstruct-itinerary/) | Hard | <details><summary>show</summary>Hierholzer: adjacency lists sorted in reverse (pop from the end = smallest), iterative DFS, append airports on backtrack, reverse the result.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Advanced Graphs**: 332 → 1584 → 743 → 778 → 269 → 787. (269 Alien Dictionary is in [16](./16-topological-sort.md).)
Here: Dijkstra from memory → 743 → 1631 → 1584 → 787 (Week 19) → Java rep 743 → bits ([23](./23-bit-manipulation.md)) the same week → (W20–26) 778, 332.

---

## 10. Target number of problems

**4 new** in Week 19 (+ 3 bit manipulation = 7) + 2 stretch.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- Pushing `(dist, node_object)` where nodes aren't comparable → `TypeError` on ties; use ints or add a tie-breaker.
- Forgetting the stale-entry check → correct but slower (and wrong if you count "visited" on push).
- Bellman-Ford without `dist[:]` copy → relaxations chain within one round → violates the k-stop limit.
- `min(range(n), key=...)` inside Prim is O(V) per step (fine: O(V²) total) — but building all V² edges into a heap is ~5·10⁵ tuples and slower in Python.
- `float("inf")` compared with ints is fine; returning `inf` where the problem expects −1 is a bug.
- Mutating a list while iterating it in Hierholzer — use `pop()` from the end.

**General**
- Using Dijkstra with negative weights.
- Marking a node visited when it's **pushed** instead of when it's **popped** with its final distance.
- 787: running Dijkstra on node only (ignores the stop limit).
- 743: nodes are 1-indexed; allocate n + 1.
- Confusing MST (connect all nodes cheaply) with shortest-path tree (cheapest path from one source).

**Java-rep traps (743):** `Integer.MAX_VALUE + w` overflow if you don't skip unreached nodes; comparator overflow with `a[0] - b[0]`.

---

## 12. Mastery criteria

- [ ] Write Dijkstra with `heapq` and lazy deletion from memory in ≤ 8 min; state O((V + E) log V) and why negative weights break it.
- [ ] Solve 743 in ≤ 15 min and 787 in ≤ 25 min from blank.
- [ ] Explain Prim vs Kruskal and when each is better (dense vs sparse).
- [ ] Solve 1 unseen weighted-graph Medium in ≤ 30 min.
- [ ] Java rep: 743 in Java in ≤ 20 min.

---

## 13. Revision schedule

| Review | Week 19 set (743, 1631, 1584, 787) |
|---|---|
| Day 0 | W19 |
| Day 3 | W19/W20 |
| Day 7 | W20 (Checkpoint 20) |
| Day 14 | W21 |
| Day 30 | W23 |

**Revisit:** Week 19 ForgeCI M6 (compare Dijkstra with DAG longest-path for the pipeline critical path), Week 22 system-design mock (routing/shortest-path talking points), Weeks 20–26 (778, 332).

---

## Worked example — 743. Network Delay Time

**Clarify.** n nodes labelled 1..n (1 ≤ n ≤ 100), directed edges `times[i] = (u, v, w)` with 0 ≤ w ≤ 100 (up to 6000 edges). A signal starts at node `k`. Return the time for **all** nodes to receive it, or −1 if some node is unreachable.

**Brute force.** DFS every path from k, keeping the minimum arrival time per node → exponential in the worst case. BFS by edge count is wrong because edges have different weights.

**Optimise.** Dijkstra from k: all weights are non-negative, so the first time a node is popped from the min-heap its distance is final. The answer is the **maximum** of the shortest distances (the last node to hear the signal); if any distance stays infinite → −1.

**Code (Python).**

```python
import heapq
from collections import defaultdict


def network_delay_time(times: list[list[int]], n: int, k: int) -> int:
    adj: defaultdict[int, list[tuple[int, int]]] = defaultdict(list)
    for u, v, w in times:
        adj[u].append((v, w))
    dist: dict[int, int] = {}                    # finalised distances (node → time)
    heap = [(0, k)]
    while heap:
        d, u = heapq.heappop(heap)
        if u in dist:
            continue                             # already finalised with a smaller d
        dist[u] = d
        for v, w in adj[u]:
            if v not in dist:
                heapq.heappush(heap, (d + w, v))
    return max(dist.values()) if len(dist) == n else -1


assert network_delay_time([[2, 1, 1], [2, 3, 1], [3, 4, 1]], 4, 2) == 2
assert network_delay_time([[1, 2, 1]], 2, 1) == 1
assert network_delay_time([[1, 2, 1]], 2, 2) == -1          # node 1 unreachable from 2
assert network_delay_time([[1, 2, 5], [1, 3, 1], [3, 2, 1]], 3, 1) == 2   # indirect path is cheaper
assert network_delay_time([], 1, 1) == 0
print("743 worked example passed")
```

**Test cases.** LeetCode example → 2 · two nodes → 1 · unreachable node → −1 · cheaper indirect path (5 direct vs 1 + 1) → 2 · single node → 0.

**Complexity.** Time O(E log E) with lazy deletion (≡ O(E log V)). Space O(V + E).

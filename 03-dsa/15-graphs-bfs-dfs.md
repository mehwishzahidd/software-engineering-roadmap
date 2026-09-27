# 15 — Graphs: BFS / DFS

> **Week 13** · NeetCode section: **Graphs** · Target: **8 new problems** + stretch pool · Java rep: **994** (Week 13)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#15--graphs-bfs--dfs) · Python: [`19-python/02-interview-toolkit.md`](../19-python/02-interview-toolkit.md) (`deque`, `defaultdict`) · Java: [quick reference §6](./java-dsa-toolkit.md#6-node-classes-and-traversal-templates)

A graph is nodes + edges. Trees were graphs without cycles and with a root; now you need a **`visited`** set, and you choose between **DFS** (explore deep: connectivity, components, reachability) and **BFS** (explore by distance: shortest path in unweighted graphs, "minimum steps", simultaneous spreading). Most interview graph problems are **grids** in disguise.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Representations | Adjacency list `defaultdict(list)` / `[[] for _ in range(n)]` (sparse, default), adjacency matrix (dense, O(n²) memory), edge list, implicit grid (neighbours = 4 directions) |
| Directed vs undirected | Undirected: add both `u→v` and `v→u` |
| DFS | Recursive or explicit stack; O(V + E). Components, reachability, flood fill, cycle detection |
| BFS | `collections.deque`; visits nodes in order of distance → **shortest path in unweighted graphs**; O(V + E) |
| `visited` timing | BFS: mark when **enqueued** (else duplicates flood the queue). DFS: mark on entry |
| Multi-source BFS | Enqueue **all** sources at distance 0 (rotting oranges, walls and gates) |
| Level-by-level BFS | `for _ in range(len(q))` → distance = number of levels |
| Connected components | Loop over all nodes; start a DFS/BFS from each unvisited one; count starts |
| Reverse thinking | "Which cells can reach the ocean?" → search **from** the ocean inward (417, 130) |
| Cloning graphs | Dict old → new node; DFS/BFS creating copies (133) |
| Grid conventions | `DIRS = ((1, 0), (-1, 0), (0, 1), (0, -1))`; bounds `0 <= r < R and 0 <= c < C`; visited as a `set` of `(r, c)` tuples or in-place marking |
| Python recursion risk | Recursive DFS on a 300×300 grid can reach depth 9·10⁴ → `RecursionError`. Prefer BFS or an explicit stack for big grids |
| Complexity | Grid: O(R·C) time and space. Graph: O(V + E) |

Practical use: [ForgeCI](../18-projects/forgeci/README.md) (Weeks 14–19) models pipeline jobs as a dependency graph; network topology and GitHub repository relationships are graphs too.

---

## 2. Prerequisite knowledge

- [`10-trees.md`](./10-trees.md) — DFS/BFS on trees (the BFS level loop is identical).
- [`14-backtracking.md`](./14-backtracking.md) — grid DFS with marking (79). Difference here: **no** unmarking — a visited cell stays visited.
- [`06-stack-queue.md`](./06-stack-queue.md) — `deque`, list-as-stack; [`09-recursion.md`](./09-recursion.md) — recursion limit.

---

## 3. Python implementation

```python
from collections import defaultdict, deque

DIRS = ((1, 0), (-1, 0), (0, 1), (0, -1))


def build_graph(n: int, edges: list[list[int]]) -> list[list[int]]:
    g: list[list[int]] = [[] for _ in range(n)]      # NOT [[]] * n (aliasing!)
    for u, v in edges:
        g[u].append(v)
        g[v].append(u)
    return g


def dfs_recursive(g: list[list[int]], u: int, seen: set[int]) -> None:
    seen.add(u)
    for v in g[u]:
        if v not in seen:
            dfs_recursive(g, v, seen)


def component_size_iterative(g: list[list[int]], src: int, seen: set[int]) -> int:
    stack, size = [src], 0                            # explicit stack: no recursion limit
    seen.add(src)
    while stack:
        u = stack.pop()
        size += 1
        for v in g[u]:
            if v not in seen:
                seen.add(v)
                stack.append(v)
    return size


def shortest_path(g: list[list[int]], src: int, dst: int) -> int:
    dist = {src: 0}                                   # doubles as "visited"
    q = deque([src])
    while q:
        u = q.popleft()
        if u == dst:
            return dist[u]
        for v in g[u]:
            if v not in dist:
                dist[v] = dist[u] + 1
                q.append(v)
    return -1


def count_components(n: int, edges: list[list[int]]) -> int:
    g, seen, count = build_graph(n, edges), set(), 0
    for i in range(n):
        if i not in seen:
            count += 1
            component_size_iterative(g, i, seen)
    return count


def num_islands(grid: list[list[str]]) -> int:
    """Grid BFS flood fill, sinking visited land in place."""
    R, C, count = len(grid), len(grid[0]), 0
    for r in range(R):
        for c in range(C):
            if grid[r][c] != "1":
                continue
            count += 1
            grid[r][c] = "0"
            q = deque([(r, c)])
            while q:
                cr, cc = q.popleft()
                for dr, dc in DIRS:
                    nr, nc = cr + dr, cc + dc
                    if 0 <= nr < R and 0 <= nc < C and grid[nr][nc] == "1":
                        grid[nr][nc] = "0"            # mark when enqueued
                        q.append((nr, nc))
    return count


def nearest_source(grid: list[list[int]]) -> list[list[float]]:
    """Multi-source BFS: distance to the nearest 0-cell; -1 cells are walls."""
    R, C = len(grid), len(grid[0])
    dist = [[float("inf")] * C for _ in range(R)]
    q = deque()
    for r in range(R):
        for c in range(C):
            if grid[r][c] == 0:
                dist[r][c] = 0
                q.append((r, c))
    while q:
        r, c = q.popleft()
        for dr, dc in DIRS:
            nr, nc = r + dr, c + dc
            if 0 <= nr < R and 0 <= nc < C and grid[nr][nc] != -1 and dist[nr][nc] == float("inf"):
                dist[nr][nc] = dist[r][c] + 1
                q.append((nr, nc))
    return dist


def has_cycle_undirected(n: int, edges: list[list[int]]) -> bool:
    g, seen = build_graph(n, edges), set()
    for s in range(n):
        if s in seen:
            continue
        seen.add(s)
        stack = [(s, -1)]                             # (node, parent)
        while stack:
            u, parent = stack.pop()
            for v in g[u]:
                if v == parent:
                    continue
                if v in seen:
                    return True
                seen.add(v)
                stack.append((v, u))
    return False


g = build_graph(5, [[0, 1], [1, 2], [3, 4]])
assert shortest_path(g, 0, 2) == 2 and shortest_path(g, 0, 4) == -1
assert count_components(5, [[0, 1], [1, 2], [3, 4]]) == 2
assert component_size_iterative(g, 0, set()) == 3
seen: set[int] = set()
dfs_recursive(g, 3, seen)
assert seen == {3, 4}
assert num_islands([list("110"), list("010"), list("001")]) == 2
inf = float("inf")
assert nearest_source([[0, 1, 1], [1, -1, 1], [1, 1, 1]]) == [[0, 1, 2], [1, inf, 3], [2, 3, 4]]
assert has_cycle_undirected(3, [[0, 1], [1, 2], [2, 0]]) and not has_cycle_undirected(3, [[0, 1], [1, 2]])
adj = defaultdict(list)                               # dict-based graph for string/sparse ids
adj["a"].append("b")
assert adj["a"] == ["b"] and adj["zzz"] == []         # note: reading adj["zzz"] inserted it
print("graph templates ok")
```

---

## 4. The same in Java (occasional reps)

**This week's Java rep (Week 13): 994 Rotting Oranges.**

| Python | Java |
|---|---|
| `deque([(r, c)])`, `popleft()` | `Deque<int[]> q = new ArrayDeque<>(); q.offer(new int[]{r, c}); q.poll()` |
| `for dr, dc in DIRS` | `for (int[] d : DIRS)` with `int[][] DIRS = {{1,0},{-1,0},{0,1},{0,-1}}` |
| `set` of `(r, c)` tuples | `boolean[][] seen` (or encode `r * C + c` in a `HashSet<Integer>`) |
| `[[] for _ in range(n)]` | `List<List<Integer>> g = new ArrayList<>(); for (...) g.add(new ArrayList<>());` |

```java
import java.util.*;

class RottingOrangesRep {
    static int orangesRotting(int[][] grid) {
        int R = grid.length, C = grid[0].length, fresh = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        Deque<int[]> q = new ArrayDeque<>();
        for (int r = 0; r < R; r++)
            for (int c = 0; c < C; c++) {
                if (grid[r][c] == 2) q.offer(new int[]{r, c});
                else if (grid[r][c] == 1) fresh++;
            }
        int minutes = 0;
        while (!q.isEmpty() && fresh > 0) {
            for (int size = q.size(); size > 0; size--) {
                int[] cur = q.poll();
                for (int[] d : dirs) {
                    int nr = cur[0] + d[0], nc = cur[1] + d[1];
                    if (nr < 0 || nr >= R || nc < 0 || nc >= C || grid[nr][nc] != 1) continue;
                    grid[nr][nc] = 2;
                    fresh--;
                    q.offer(new int[]{nr, nc});
                }
            }
            minutes++;
        }
        return fresh == 0 ? minutes : -1;
    }

    public static void main(String[] args) {
        System.out.println(orangesRotting(new int[][]{{2, 1, 1}, {1, 1, 0}, {0, 1, 1}}));   // 4
    }
}
```

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Technique | Problems |
|---|---|---|
| Flood fill | DFS/BFS from one cell | 733 |
| Reachability on an edge list | adjacency list + BFS/DFS (or union-find) | 1971 |
| Count components on a grid | BFS/DFS from each unvisited land cell | 200 |
| Component size / max area | DFS returns size | 695 |
| Clone a graph | dict old → new + DFS/BFS | 133 |
| Multi-source BFS, time steps | all sources at t = 0, level loop | 994, 286 |
| Reverse search from boundaries | start from edges/oceans | 417, 130 |
| Shortest transformation sequence | BFS over an implicit graph of words | 127 |

---

## 6. How to recognise the pattern

- A grid of characters/ints and words like "island", "region", "connected", "flood", "spread", "infect", "rot".
- "Minimum number of steps/moves/minutes" in an **unweighted** setting → BFS.
- "Can X reach Y", "number of groups/provinces/components" → DFS/BFS (or union-find, [17](./17-union-find.md)).
- Edges given as pairs, or relationships between entities (friends, flights, equations).
- Weighted edges → [22 Advanced Graphs](./22-advanced-graphs.md). Dependencies / ordering → [16 Topological Sort](./16-topological-sort.md).
- Grid up to 300×300 = 9·10⁴ cells → O(R·C) expected; recursive DFS might exceed Python's recursion limit → BFS.

---

## 7. Beginner problems (Week 13)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 733 | [Flood Fill](https://leetcode.com/problems/flood-fill/) | Easy | DFS/BFS from `(sr, sc)`; return immediately if the new colour equals the old one (infinite loop otherwise). |
| 1971 | [Find if Path Exists in Graph](https://leetcode.com/problems/find-if-path-exists-in-graph/) | Easy | Adjacency list + BFS from the source with a `seen` set. n up to 2·10⁵ → iterative, not recursive. |
| 200 | [Number of Islands](https://leetcode.com/problems/number-of-islands/) | Medium | For each `"1"`, count += 1 and sink the whole island (BFS or DFS). |

---

## 8. Interview problems (Week 13)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 695 | [Max Area of Island](https://leetcode.com/problems/max-area-of-island/) | Medium | 13 | <details><summary>show</summary>DFS/BFS returns the component size; take the max over all starts.</details> |
| 133 | [Clone Graph](https://leetcode.com/problems/clone-graph/) | Medium | 13 | <details><summary>show</summary>`copies: dict[Node, Node]`; create the copy **before** visiting neighbours so cycles terminate.</details> |
| 994 | [Rotting Oranges](https://leetcode.com/problems/rotting-oranges/) | Medium | 13 | <details><summary>show</summary>Multi-source BFS from all rotten oranges; count fresh; minutes = levels processed; −1 if fresh remain.</details> |
| 417 | [Pacific Atlantic Water Flow](https://leetcode.com/problems/pacific-atlantic-water-flow/) | Medium | 13 | <details><summary>show</summary>Search uphill from each ocean's border cells into two sets; answer = intersection.</details> |
| 130 | [Surrounded Regions](https://leetcode.com/problems/surrounded-regions/) | Medium | 13 | <details><summary>show</summary>Mark `"O"`s connected to the border as safe (e.g. `"T"`), flip the remaining `"O"` → `"X"`, then `"T"` → `"O"`.</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 286 | [Walls and Gates](https://leetcode.com/problems/walls-and-gates/) *(LeetCode Premium — free on NeetCode as "Islands and Treasure")* | Medium | <details><summary>show</summary>Multi-source BFS from all gates; fill `INF` cells with distance on first visit.</details> |
| 127 | [Word Ladder](https://leetcode.com/problems/word-ladder/) | Hard | <details><summary>show</summary>BFS over words; neighbours via wildcard patterns (`h*t` → words) in a `defaultdict(list)`, or try 26 letters per position; remove words from the set when visited.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Graphs**: 200 → 695 → 133 → 286 → 994 → 417 → 130 → 207 → 210 → 684 → 323 → 261 → 127. (207/210 → [16](./16-topological-sort.md); 684/323/261 → [17](./17-union-find.md).)
Here: §3 from memory → 733 → 1971 → 200 → 695 → 133 → 994 → 417 → 130 → Java rep 994 → (W20–26) 286, 127.

---

## 10. Target number of problems

**8 new** in Week 13 + 2 stretch.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- Recursive DFS on large grids → `RecursionError`. Use BFS or an explicit stack (or `sys.setrecursionlimit` with care for moderate sizes).
- `[[]] * n` for adjacency lists → all nodes share one list.
- `list.pop(0)` as the BFS queue → O(V²). Use `deque.popleft()`.
- `visited` as a `list` and `if x in visited` → O(n) per check. Use a `set` or a boolean grid.
- Iterating `for nb in adj[u]` on a `defaultdict` silently inserts missing `u` keys — harmless, but it changes `len(adj)`.
- Grid bounds with chained comparisons: `0 <= nr < R and 0 <= nc < C` — check **before** indexing (negative indices don't raise; they wrap!).

**General**
- Marking visited when **dequeued** in BFS instead of when enqueued → the same cell is enqueued many times (TLE/MLE).
- Forgetting the reverse direction for undirected edges.
- 994: counting minutes per orange instead of per level; returning 1 when no fresh oranges existed (should be 0).
- 733: not handling `new_color == old_color` → infinite loop.
- 133: creating the clone after recursing → infinite loop on cycles.
- Mutating the input grid when the interviewer expects it unchanged — ask, or use a `seen` set.

**Java-rep traps (994):** `ArrayDeque<int[]>` + `q.size()` frozen per level; bounds checks before indexing (Java throws, Python wraps).

---

## 12. Mastery criteria

- [ ] Write adjacency-list build, iterative DFS, BFS with distances, and grid BFS from memory in ≤ 12 min total.
- [ ] Explain why BFS gives shortest paths only in unweighted graphs.
- [ ] Solve 200 and 994 in ≤ 15 min each; 417 in ≤ 25 min.
- [ ] Solve 2 unseen graph Mediums in ≤ 25 min each with correct O(V + E) / O(R·C) analysis.
- [ ] Java rep: 994 in Java in ≤ 20 min.

---

## 13. Revision schedule

| Review | Week 13 set |
|---|---|
| Day 0 | W13 |
| Day 3 | W13/W14 |
| Day 7 | W14 |
| Day 14 | W15 |
| Day 30 | W17 (OA simulation #1 — grid BFS is the most common OA graph problem) |

**Revisit:** Week 14 (topological sort and union-find build directly on this), Week 19 (Dijkstra = BFS with a heap; ForgeCI DAG scheduling), Weeks 20–26 (286, 127).

---

## Worked example — 994. Rotting Oranges

**Clarify.** Grid R×C (1 ≤ R, C ≤ 10) with `0` empty, `1` fresh, `2` rotten. Each minute, fresh oranges 4-directionally adjacent to rotten ones rot. Return minutes until no fresh remain, or −1 if impossible. No fresh at all → 0.

**Brute force.** Simulate minute by minute: scan the whole grid each minute and rot neighbours of rotten cells (using a copy) → O((R·C)²). Works for 10×10, but interviewers want the BFS insight.

**Optimise.** All initially rotten oranges spread **simultaneously** → multi-source BFS. Enqueue every rotten orange at time 0 and count fresh oranges. Process level by level; each level = one minute; rot fresh neighbours (mark immediately), decrement `fresh`. At the end, `fresh > 0` → −1.

**Code (Python).**

```python
from collections import deque


def oranges_rotting(grid: list[list[int]]) -> int:
    R, C = len(grid), len(grid[0])
    q: deque[tuple[int, int]] = deque()
    fresh = 0
    for r in range(R):
        for c in range(C):
            if grid[r][c] == 2:
                q.append((r, c))
            elif grid[r][c] == 1:
                fresh += 1
    minutes = 0
    while q and fresh:                         # stop once nothing is left to rot
        for _ in range(len(q)):                # one level = one minute
            r, c = q.popleft()
            for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nr, nc = r + dr, c + dc
                if 0 <= nr < R and 0 <= nc < C and grid[nr][nc] == 1:
                    grid[nr][nc] = 2           # mark when enqueued
                    fresh -= 1
                    q.append((nr, nc))
        minutes += 1
    return minutes if fresh == 0 else -1


assert oranges_rotting([[2, 1, 1], [1, 1, 0], [0, 1, 1]]) == 4
assert oranges_rotting([[2, 1, 1], [0, 1, 1], [1, 0, 1]]) == -1
assert oranges_rotting([[0, 2]]) == 0
assert oranges_rotting([[1]]) == -1
assert oranges_rotting([[2, 1, 2]]) == 1
print("994 worked example passed")
```

**Test cases.** Example 1 → 4 · isolated fresh orange → −1 · no fresh oranges → 0 (the `while q and fresh` guard prevents counting an extra minute) · fresh but no rotten → −1 · two sources meeting in the middle → 1.

**Complexity.** Time O(R·C) — each cell enqueued at most once. Space O(R·C) for the queue in the worst case. (We mutate the input grid; mention it and copy with `[row[:] for row in grid]` if the caller needs the original.)

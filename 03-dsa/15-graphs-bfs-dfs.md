# 15 — Graphs: BFS / DFS

> **Week 14** · NeetCode section: **Graphs** · Target: **8 new problems** + stretch pool
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#15--graphs-bfs--dfs) · Java APIs: [toolkit §6 `ArrayDeque`](./java-dsa-toolkit.md#6-arraydeque-as-stack-and-queue), [§4 adjacency lists](./java-dsa-toolkit.md#4-hashmap)

A graph is nodes + edges. Trees were graphs without cycles and with a root; now you need a **`visited`** set, and you choose between **DFS** (explore deep: connectivity, components, reachability) and **BFS** (explore by distance: shortest path in unweighted graphs, "minimum steps", simultaneous spreading). Most interview graph problems are **grids** in disguise.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Representations | Adjacency list `List<List<Integer>>` (sparse, default), adjacency matrix `boolean[n][n]` (dense, O(n²) memory), edge list `int[][] edges`, implicit grid (neighbours = 4 directions) |
| Directed vs undirected | Undirected: add both `u→v` and `v→u` |
| DFS | Recursive or explicit stack; O(V + E). Good for components, reachability, flood fill, cycle detection |
| BFS | Queue; visits nodes in order of distance from the source → **shortest path in unweighted graphs**; O(V + E) |
| `visited` timing | BFS: mark when **enqueued** (else duplicates flood the queue). DFS: mark on entry |
| Multi-source BFS | Enqueue **all** sources at distance 0 (rotting oranges, walls and gates) |
| Level-by-level BFS | `for (int size = q.size(); size > 0; size--)` → distance = number of levels |
| Connected components | Loop over all nodes; start a DFS/BFS from each unvisited one; count starts |
| Reverse thinking | "Which cells can reach the ocean?" → search **from** the ocean inward (417, 130) |
| Cloning graphs | Map old → new node; DFS/BFS creating copies (133) |
| Grid encoding | `(r, c)` ↔ `r * cols + c`; `int[][] DIRS = {{1,0},{-1,0},{0,1},{0,-1}}` |
| Complexity | Grid: O(R·C) time and space. Graph: O(V + E) |

Practical use: dependency graphs (Maven, [16 Topological Sort](./16-topological-sort.md)), network topology, social graphs, and the permission/role relationships in [P3 TeamBoard](../18-projects/p3-teamboard/README.md).

---

## 2. Prerequisite knowledge

- [`10-trees.md`](./10-trees.md) — DFS/BFS on trees (BFS level loop is identical).
- [`14-backtracking.md`](./14-backtracking.md) — grid DFS with marking (79). Difference here: **no** unmarking — a visited cell stays visited.
- [`06-stack-queue.md`](./06-stack-queue.md), [Toolkit §12 recursion depth](./java-dsa-toolkit.md#12-recursion-depth) — large grids may need BFS/iterative DFS.

---

## 3. Java implementation

```java
import java.util.*;

class GraphTemplates {
    static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    // Build an undirected adjacency list from an edge list
    static List<List<Integer>> buildGraph(int n, int[][] edges) {
        List<List<Integer>> g = new ArrayList<>(n);
        for (int i = 0; i < n; i++) g.add(new ArrayList<>());
        for (int[] e : edges) { g.get(e[0]).add(e[1]); g.get(e[1]).add(e[0]); }
        return g;
    }

    // Recursive DFS: reachability
    static void dfs(List<List<Integer>> g, int u, boolean[] seen) {
        seen[u] = true;
        for (int v : g.get(u)) if (!seen[v]) dfs(g, v, seen);
    }

    // Iterative DFS with an explicit stack (safe for deep graphs)
    static int componentSizeIterative(List<List<Integer>> g, int src, boolean[] seen) {
        Deque<Integer> st = new ArrayDeque<>();
        st.push(src);
        seen[src] = true;
        int size = 0;
        while (!st.isEmpty()) {
            int u = st.pop();
            size++;
            for (int v : g.get(u)) if (!seen[v]) { seen[v] = true; st.push(v); }
        }
        return size;
    }

    // BFS shortest path (number of edges) in an unweighted graph; -1 if unreachable
    static int shortestPath(List<List<Integer>> g, int src, int dst) {
        int[] dist = new int[g.size()];
        Arrays.fill(dist, -1);
        Deque<Integer> q = new ArrayDeque<>();
        q.offer(src);
        dist[src] = 0;                                    // dist != -1 doubles as "visited"
        while (!q.isEmpty()) {
            int u = q.poll();
            if (u == dst) return dist[u];
            for (int v : g.get(u)) {
                if (dist[v] == -1) { dist[v] = dist[u] + 1; q.offer(v); }
            }
        }
        return -1;
    }

    // Count connected components
    static int countComponents(int n, int[][] edges) {
        List<List<Integer>> g = buildGraph(n, edges);
        boolean[] seen = new boolean[n];
        int count = 0;
        for (int i = 0; i < n; i++) if (!seen[i]) { count++; dfs(g, i, seen); }
        return count;
    }

    // Grid DFS flood fill: count islands of '1' (LC 200 core), sinking visited land
    static int numIslands(char[][] grid) {
        int count = 0;
        for (int r = 0; r < grid.length; r++)
            for (int c = 0; c < grid[0].length; c++)
                if (grid[r][c] == '1') { count++; sink(grid, r, c); }
        return count;
    }
    private static void sink(char[][] g, int r, int c) {
        if (r < 0 || r >= g.length || c < 0 || c >= g[0].length || g[r][c] != '1') return;
        g[r][c] = '0';                                    // mark visited (no restore — unlike backtracking)
        for (int[] d : DIRS) sink(g, r + d[0], c + d[1]);
    }

    // Multi-source BFS on a grid: distance from every cell to the nearest source (value 0); walls = -1
    static int[][] nearestSource(int[][] grid) {
        int R = grid.length, C = grid[0].length;
        int[][] dist = new int[R][C];
        Deque<int[]> q = new ArrayDeque<>();
        for (int r = 0; r < R; r++)
            for (int c = 0; c < C; c++) {
                if (grid[r][c] == 0) { q.offer(new int[]{r, c}); dist[r][c] = 0; }
                else dist[r][c] = Integer.MAX_VALUE;      // unvisited (walls stay MAX too)
            }
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            for (int[] d : DIRS) {
                int nr = cur[0] + d[0], nc = cur[1] + d[1];
                if (nr < 0 || nr >= R || nc < 0 || nc >= C || grid[nr][nc] == -1) continue;
                if (dist[nr][nc] != Integer.MAX_VALUE) continue;
                dist[nr][nc] = dist[cur[0]][cur[1]] + 1;
                q.offer(new int[]{nr, nc});
            }
        }
        return dist;
    }

    // Undirected cycle detection with parent tracking
    static boolean hasCycleUndirected(List<List<Integer>> g) {
        boolean[] seen = new boolean[g.size()];
        for (int i = 0; i < g.size(); i++)
            if (!seen[i] && cycleDfs(g, i, -1, seen)) return true;
        return false;
    }
    private static boolean cycleDfs(List<List<Integer>> g, int u, int parent, boolean[] seen) {
        seen[u] = true;
        for (int v : g.get(u)) {
            if (v == parent) continue;
            if (seen[v] || cycleDfs(g, v, u, seen)) return true;
        }
        return false;
    }

    public static void main(String[] args) {
        int[][] edges = {{0, 1}, {1, 2}, {3, 4}};
        List<List<Integer>> g = buildGraph(5, edges);
        System.out.println(shortestPath(g, 0, 2) + " " + shortestPath(g, 0, 4));  // 2 -1
        System.out.println(countComponents(5, edges));                             // 2
        System.out.println(componentSizeIterative(g, 0, new boolean[5]));          // 3
        char[][] grid = {"110".toCharArray(), "010".toCharArray(), "001".toCharArray()};
        System.out.println(numIslands(grid));                                      // 2
        System.out.println(Arrays.deepToString(nearestSource(new int[][]{{0, 1, 1}, {1, -1, 1}, {1, 1, 1}})));
        // [[0, 1, 2], [1, 2147483647, 3], [2, 3, 4]]
        System.out.println(hasCycleUndirected(buildGraph(3, new int[][]{{0, 1}, {1, 2}, {2, 0}})) + " "
                + hasCycleUndirected(g));                                          // true false
    }
}
```

---

## 4. Common patterns (sub-variants)

| Sub-pattern | Technique | Problems |
|---|---|---|
| Flood fill | DFS from one cell | 733 |
| Reachability on an edge list | build adjacency list + BFS/DFS (or union-find) | 1971 |
| Count components on a grid | DFS/BFS from each unvisited land cell | 200 |
| Component size / max area | DFS returns size | 695 |
| Clone a graph | `Map<Node, Node>` + DFS/BFS | 133 |
| Multi-source BFS, time steps | all sources at t = 0, level loop | 994, 286 |
| Reverse search from boundaries | start from edges/oceans | 417, 130 |
| Shortest transformation sequence | BFS over implicit graph of words | 127 |

---

## 5. How to recognise the pattern

- A grid of characters/ints and words like "island", "region", "connected", "flood", "spread", "infect", "rot".
- "Minimum number of steps/moves/minutes" in an **unweighted** setting → BFS.
- "Can X reach Y", "number of groups/provinces/components" → DFS/BFS (or union-find, [17](./17-union-find.md)).
- Edges given as pairs, or relationships between entities (friends, flights, equations).
- Weighted edges → [22 Advanced Graphs](./22-advanced-graphs.md). Dependencies / ordering → [16 Topological Sort](./16-topological-sort.md).
- Grid up to 300×300 = 9·10⁴ cells → O(R·C) is expected.

---

## 6. Beginner problems (Week 14)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 733 | [Flood Fill](https://leetcode.com/problems/flood-fill/) | Easy | DFS from `(sr, sc)`; return immediately if the new colour equals the old one (infinite recursion otherwise). |
| 1971 | [Find if Path Exists in Graph](https://leetcode.com/problems/find-if-path-exists-in-graph/) | Easy | Build an adjacency list, BFS from source with a `boolean[] seen`. n up to 2·10⁵ → prefer iterative. |
| 200 | [Number of Islands](https://leetcode.com/problems/number-of-islands/) | Medium | For each `'1'`, count++ and sink the whole island (DFS or BFS). |

---

## 7. Interview problems (Week 14)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 695 | [Max Area of Island](https://leetcode.com/problems/max-area-of-island/) | Medium | 14 | <details><summary>show</summary>DFS returns `1 + sum(dfs(neighbours))`; take the max over all starts.</details> |
| 133 | [Clone Graph](https://leetcode.com/problems/clone-graph/) | Medium | 14 | <details><summary>show</summary>`Map<Node, Node> copies`; create the copy **before** recursing into neighbours so cycles terminate.</details> |
| 994 | [Rotting Oranges](https://leetcode.com/problems/rotting-oranges/) | Medium | 14 | <details><summary>show</summary>Multi-source BFS from all rotten oranges; count fresh; minutes = levels processed; −1 if fresh remain.</details> |
| 417 | [Pacific Atlantic Water Flow](https://leetcode.com/problems/pacific-atlantic-water-flow/) | Medium | 14 | <details><summary>show</summary>Search uphill from each ocean's border cells into two `boolean[][]`; answer = cells in both.</details> |
| 130 | [Surrounded Regions](https://leetcode.com/problems/surrounded-regions/) | Medium | 14 | <details><summary>show</summary>Mark `'O'`s connected to the border as safe (e.g. `'T'`), flip remaining `'O'` → `'X'`, then `'T'` → `'O'`.</details> |

### Stretch pool (Weeks 21–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 286 | [Walls and Gates](https://leetcode.com/problems/walls-and-gates/) *(LeetCode Premium — free on NeetCode as "Islands and Treasure")* | Medium | <details><summary>show</summary>Multi-source BFS from all gates; fill `INF` cells with distance on first visit.</details> |
| 127 | [Word Ladder](https://leetcode.com/problems/word-ladder/) | Hard | <details><summary>show</summary>BFS over words; neighbours via wildcard patterns (`h*t`) map or by trying 26 letters per position; remove words from the set when visited.</details> |

---

## 8. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Graphs**: 200 → 695 → 133 → 286 → 994 → 417 → 130 → 207 → 210 → 684 → 323 → 261 → 127. (207/210 → [16](./16-topological-sort.md); 684/323/261 → [17](./17-union-find.md).)
Here: §3 from memory → 733 → 1971 → 200 → 695 → 133 → 994 → 417 → 130 (W14) → (W21–26) 286, 127.

---

## 9. Target number of problems

**8 new** in Week 14 + 2 stretch.

---

## 10. Mistakes beginners commonly make

- Marking visited when **dequeued** in BFS instead of when enqueued → the same cell is enqueued many times (TLE/MLE).
- Missing bounds checks in grid DFS, or checking them after indexing.
- Forgetting the undirected edge's reverse direction.
- Using `HashSet<String>` of `"r,c"` for visited → slow string building; use `boolean[][]` or in-place marking.
- 994: counting minutes per **orange** instead of per **level**; returning the count when no fresh oranges existed (should be 0).
- 733: not handling `newColor == oldColor` → infinite recursion → `StackOverflowError`.
- Recursive DFS on 10⁵+ nodes (1971) in environments with small stacks → use BFS/iterative.
- 133: creating the clone after recursing → infinite loop on cycles.
- Modifying the input grid when the interviewer expects it unchanged — ask, or use `visited`.

---

## 11. Mastery criteria

- [ ] Write adjacency-list build, recursive DFS, iterative BFS with distance, and grid DFS from memory in ≤ 12 min total.
- [ ] Explain why BFS gives shortest paths only in unweighted graphs.
- [ ] Solve 200 and 994 in ≤ 15 min each; 417 in ≤ 25 min.
- [ ] Solve 2 unseen graph Mediums in ≤ 25 min each with correct O(V + E) / O(R·C) analysis.

---

## 12. Revision schedule

| Review | Week 14 set |
|---|---|
| Day 0 | W14 |
| Day 3 | W14/W15 |
| Day 7 | W15 |
| Day 14 | W16 |
| Day 30 | W18 |

**Revisit:** Week 15 (topological sort and union-find build directly on this), Week 20 (Dijkstra = BFS with a heap), Weeks 21–26 (286, 127), OA sims (grid BFS is the most common OA graph problem).

---

## Worked example — 994. Rotting Oranges

**Clarify.** Grid R×C (1 ≤ R, C ≤ 10) with `0` empty, `1` fresh, `2` rotten. Each minute, fresh oranges 4-directionally adjacent to rotten ones rot. Return minutes until no fresh remain, or −1 if impossible. No fresh at all → 0.

**Brute force.** Simulate minute by minute: scan the whole grid each minute and rot neighbours of rotten cells (using a copy) → O((R·C)²). Works for 10×10, but interviewers want the BFS insight.

**Optimise.** All initially rotten oranges spread **simultaneously** → multi-source BFS. Enqueue every rotten orange at time 0, count fresh oranges. Process level by level; each level = one minute; rot fresh neighbours (mark immediately), decrement `fresh`. At the end, `fresh > 0` → −1.

**Code.**

```java
import java.util.*;

class RottingOranges {
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public int orangesRotting(int[][] grid) {
        int R = grid.length, C = grid[0].length, fresh = 0;
        Deque<int[]> q = new ArrayDeque<>();
        for (int r = 0; r < R; r++) {
            for (int c = 0; c < C; c++) {
                if (grid[r][c] == 2) q.offer(new int[]{r, c});
                else if (grid[r][c] == 1) fresh++;
            }
        }
        int minutes = 0;
        while (!q.isEmpty() && fresh > 0) {           // stop once nothing is left to rot
            for (int size = q.size(); size > 0; size--) {
                int[] cur = q.poll();
                for (int[] d : DIRS) {
                    int nr = cur[0] + d[0], nc = cur[1] + d[1];
                    if (nr < 0 || nr >= R || nc < 0 || nc >= C || grid[nr][nc] != 1) continue;
                    grid[nr][nc] = 2;                 // mark when enqueued
                    fresh--;
                    q.offer(new int[]{nr, nc});
                }
            }
            minutes++;
        }
        return fresh == 0 ? minutes : -1;
    }

    public static void main(String[] args) {
        RottingOranges s = new RottingOranges();
        System.out.println(s.orangesRotting(new int[][]{{2, 1, 1}, {1, 1, 0}, {0, 1, 1}}));  // 4
        System.out.println(s.orangesRotting(new int[][]{{2, 1, 1}, {0, 1, 1}, {1, 0, 1}}));  // -1
        System.out.println(s.orangesRotting(new int[][]{{0, 2}}));                          // 0
        System.out.println(s.orangesRotting(new int[][]{{1}}));                             // -1
        System.out.println(s.orangesRotting(new int[][]{{2, 1, 2}}));                       // 1  (two sources)
    }
}
```

**Test cases.** Example 1 → 4 · isolated fresh orange → −1 · no fresh oranges → 0 (the `fresh > 0` loop guard prevents counting an extra minute) · fresh but no rotten → −1 · two sources meeting in the middle → 1.

**Complexity.** Time O(R·C) — each cell enqueued at most once. Space O(R·C) for the queue in the worst case. (We mutate the input grid; mention it and copy if the caller needs the original.)

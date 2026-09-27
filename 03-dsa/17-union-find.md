# 17 — Union-Find (Disjoint Set Union)

> **Week 14** · NeetCode section: **Graphs** (Redundant Connection, Connected Components, Graph Valid Tree) · Target: **4 new problems** + stretch pool · Java rep: Week 14's rep is **207** in [16](./16-topological-sort.md) (optional extra: 684)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#17--union-find) · Python: [cheatsheet](../PYTHON_INTERVIEW_CHEATSHEET.md) · Java: [quick reference](./java-dsa-toolkit.md)

Union-Find maintains a partition of elements into disjoint groups with two near-O(1) operations: `find(x)` (which group is x in?) and `union(a, b)` (merge two groups). It shines when edges arrive **one at a time** and you keep asking "are these already connected?" — detecting the edge that creates a cycle, counting components as connections are added, merging accounts. It's also the core of Kruskal's MST ([22](./22-advanced-graphs.md)).

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Representation | `parent[i]` (root points to itself) + `rank[i]` or `size[i]` |
| `find` with path compression | Point every visited node directly at the root → trees become almost flat |
| `union` by rank / size | Attach the shorter/smaller tree under the taller/larger root → height stays O(log n) even without compression |
| Complexity | With both optimisations: O(α(n)) amortised per operation (inverse Ackermann ≤ 4 for any realistic n) — "effectively constant" |
| Components counter | Start at n; every successful union decrements it |
| Cycle detection (undirected) | Edge (a, b) with `find(a) == find(b)` closes a cycle |
| Valid tree | n − 1 edges **and** no cycle (⇒ connected) |
| Non-integer elements | Map strings (emails, accounts) to indices with a dict, or use a dict-based parent map |
| vs BFS/DFS | Static graph, one question → BFS/DFS is fine. Dynamic edge stream / many connectivity queries → union-find |
| Limitation | No efficient "split"/delete; only merges |

Practical use: grouping duplicate entities (the same customer/account under different emails — 721), network connectivity, and Kruskal's MST.

---

## 2. Prerequisite knowledge

- [`15-graphs-bfs-dfs.md`](./15-graphs-bfs-dfs.md) — components via BFS/DFS (the alternative you must compare against).
- [`09-recursion.md`](./09-recursion.md) — recursive `find` depth; the iterative version avoids Python's recursion limit.

---

## 3. Python implementation

```python
class DSU:
    """Union-Find with path compression + union by rank."""

    def __init__(self, n: int) -> None:
        self.parent = list(range(n))
        self.rank = [0] * n
        self.components = n

    def find(self, x: int) -> int:
        root = x
        while self.parent[root] != root:              # 1) locate the root (iterative: no recursion limit)
            root = self.parent[root]
        while self.parent[x] != root:                 # 2) path compression
            self.parent[x], x = root, self.parent[x]
        return root

    def union(self, a: int, b: int) -> bool:
        ra, rb = self.find(a), self.find(b)
        if ra == rb:
            return False                              # already connected → this edge closes a cycle
        if self.rank[ra] < self.rank[rb]:
            ra, rb = rb, ra
        self.parent[rb] = ra                          # attach the shorter tree under the taller
        if self.rank[ra] == self.rank[rb]:
            self.rank[ra] += 1
        self.components -= 1
        return True

    def connected(self, a: int, b: int) -> bool:
        return self.find(a) == self.find(b)


class DictDSU:
    """Same idea for hashable non-integer keys (emails, names)."""

    def __init__(self) -> None:
        self.parent: dict = {}
        self.size: dict = {}

    def find(self, x):
        if x not in self.parent:
            self.parent[x], self.size[x] = x, 1
        root = x
        while self.parent[root] != root:
            root = self.parent[root]
        while self.parent[x] != root:
            self.parent[x], x = root, self.parent[x]
        return root

    def union(self, a, b) -> bool:
        ra, rb = self.find(a), self.find(b)
        if ra == rb:
            return False
        if self.size[ra] < self.size[rb]:             # union by size
            ra, rb = rb, ra
        self.parent[rb] = ra
        self.size[ra] += self.size[rb]
        return True


d = DSU(5)
assert d.union(0, 1) and d.union(1, 2) and not d.union(0, 2)   # third edge closes a cycle
assert d.connected(0, 2) and not d.connected(0, 3) and d.components == 3
deep = DSU(100_000)
for i in range(1, 100_000):
    deep.parent[i] = i - 1                            # worst-case chain (bypassing union on purpose)
assert deep.find(99_999) == 0 and deep.parent[99_999] == 0      # compressed, no RecursionError
dd = DictDSU()
dd.union("a@x.com", "b@x.com")
assert dd.find("a@x.com") == dd.find("b@x.com") != dd.find("c@x.com")
print("DSU ok")
```

> The `self.parent[x], x = root, self.parent[x]` line relies on right-to-left evaluation of the tuple **before** assignment: the old `parent[x]` is read first, then `parent[x]` is set to `root`, then `x` moves on. Write it as three lines if that reads more clearly to you.

---

## 4. The same in Java (occasional reps)

Week 14's mandatory Java rep is **207** ([16](./16-topological-sort.md)). Optional extra: **684 Redundant Connection** with a Java DSU.

| Python | Java |
|---|---|
| `list(range(n))` | `int[] parent = new int[n]; for (int i = 0; i < n; i++) parent[i] = i;` |
| iterative `find` | recursive one-liner `parent[x] == x ? x : (parent[x] = find(parent[x]))` is common in Java (depth is small once ranks are used) |
| `ra, rb = rb, ra` | `int t = ra; ra = rb; rb = t;` |

```java
import java.util.*;

class RedundantConnectionRep {
    private static int[] parent, rank;

    static int find(int x) { return parent[x] == x ? x : (parent[x] = find(parent[x])); }

    static boolean union(int a, int b) {
        int ra = find(a), rb = find(b);
        if (ra == rb) return false;
        if (rank[ra] < rank[rb]) { int t = ra; ra = rb; rb = t; }
        parent[rb] = ra;
        if (rank[ra] == rank[rb]) rank[ra]++;
        return true;
    }

    static int[] findRedundantConnection(int[][] edges) {
        int n = edges.length;
        parent = new int[n + 1]; rank = new int[n + 1];        // nodes are 1..n
        for (int i = 0; i <= n; i++) parent[i] = i;
        for (int[] e : edges) if (!union(e[0], e[1])) return e;
        return new int[0];
    }

    public static void main(String[] args) {
        System.out.println(Arrays.toString(findRedundantConnection(new int[][]{{1, 2}, {1, 3}, {2, 3}}))); // [2, 3]
    }
}
```

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Technique | Problems |
|---|---|---|
| Count components from an adjacency matrix / edge list | union every edge; `components` counter | 547, 323 |
| First edge that creates a cycle | `union` returns False | 684 |
| Is the graph a tree? | `len(edges) == n - 1` and every union succeeds | 261 |
| Merge entities sharing an attribute | dict of attribute → first owner; union owners | 721 |
| Kruskal's MST | sort edges by weight; union if not connected | 1584 ([22](./22-advanced-graphs.md)) |
| Reachability with a changing edge set | union as edges arrive | 1971 (alternative to BFS) |

---

## 6. How to recognise the pattern

- "Number of connected components / provinces / groups" — especially when edges are **given as a list** or arrive over time.
- "Which edge can be removed so the graph becomes a tree?", "redundant connection".
- "Merge accounts/sets that share any element".
- "Is this graph a valid tree?"
- "Minimum cost to connect all points" → Kruskal (union-find) or Prim (heap).
- Undirected graphs only — directed cycles need [16](./16-topological-sort.md).

---

## 7. Beginner problems (Week 14)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 547 | [Number of Provinces](https://leetcode.com/problems/number-of-provinces/) | Medium | Union `i, j` for every `isConnected[i][j] == 1` with j > i; return `components`. |

---

## 8. Interview problems (Week 14)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 684 | [Redundant Connection](https://leetcode.com/problems/redundant-connection/) | Medium | 14 | <details><summary>show</summary>Process edges in order; the first edge whose endpoints are already connected is the answer (it's also the last such edge in input order, as required).</details> |
| 323 | [Number of Connected Components in an Undirected Graph](https://leetcode.com/problems/number-of-connected-components-in-an-undirected-graph/) *(LeetCode Premium — free on NeetCode)* | Medium | 14 | <details><summary>show</summary>Start with n components; each successful union decrements. Compare with the BFS solution from [15](./15-graphs-bfs-dfs.md).</details> |
| 261 | [Graph Valid Tree](https://leetcode.com/problems/graph-valid-tree/) *(LeetCode Premium — free on NeetCode)* | Medium | 14 | <details><summary>show</summary>Exactly n − 1 edges **and** no union fails ⇒ connected and acyclic.</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 721 | [Accounts Merge](https://leetcode.com/problems/accounts-merge/) | Medium | <details><summary>show</summary>Map each email → first account index; union accounts sharing an email; group emails by root, `sorted()` each group, prepend the name.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Graphs**: 684 → 323 → 261 (after 207/210).
Here: `DSU` from memory → 547 → 684 → 323 → 261 (Week 14, after [16](./16-topological-sort.md)) → optional Java rep 684 → (W20–26) 721.

---

## 10. Target number of problems

**4 new** in Week 14 (+ 4 topological sort = 8) + 1 stretch.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- Recursive `find` without union by rank on a long chain → `RecursionError` (depth can reach n before compression). Use the iterative version.
- `parent = [0] * n` instead of `list(range(n))` → every node's root is 0 from the start.
- Tuple-assignment order in the compression loop (`x, self.parent[x] = ...` would move `x` first and then write the wrong slot).
- Class-level mutable defaults (`parent = []` on the class) shared between instances / test cases.
- 721: forgetting to `sorted()` the emails, or using the email list as a dict key (lists are unhashable).

**General**
- Comparing `parent[a] == parent[b]` instead of `find(a) == find(b)` — parents aren't necessarily roots.
- Forgetting union by rank/size → worst-case O(n) finds without compression.
- 1-indexed nodes (684) with a DSU of size n → `IndexError`; allocate n + 1.
- 261: checking only "no cycle" — a forest with no cycles isn't a tree; also require n − 1 edges.
- Using union-find for directed graphs.

**Java-rep traps (684):** `int[]` of size n + 1 for 1-indexed nodes; recursive `find` is fine in Java once ranks bound the height.

---

## 12. Mastery criteria

- [ ] Write `DSU` (iterative `find` with compression, `union` by rank, `components`) from memory in ≤ 8 min.
- [ ] Explain why path compression + union by rank gives near-constant amortised time (awareness: α(n)).
- [ ] Solve 684 and 261 in ≤ 15 min each from blank.
- [ ] Explain when you'd choose union-find over BFS/DFS (dynamic edges, many connectivity queries).

---

## 13. Revision schedule

| Review | Week 14 set (547, 684, 323, 261) |
|---|---|
| Day 0 | W14 |
| Day 3 | W14/W15 |
| Day 7 | W15 |
| Day 14 | W16 (Checkpoint 16) |
| Day 30 | W18 |

**Revisit:** Week 19 (Kruskal's MST in [22](./22-advanced-graphs.md) — 1584), Weeks 20–26 (721).

---

## Worked example — 684. Redundant Connection

**Clarify.** A tree with n nodes (labelled 1..n, 3 ≤ n ≤ 1000) plus **one** extra edge → exactly one cycle. `edges` has n entries. Return an edge that can be removed to leave a tree; if several answers exist, return the one that appears **last** in the input.

**Brute force.** For each edge (from the last), remove it and BFS to check the rest is a connected tree → O(n · (V + E)) = O(n²) ≈ 10⁶. Acceptable for n = 1000 but clumsy.

**Optimise.** Add edges one by one to a DSU. Before the extra edge arrives, every edge joins two different components. The first edge whose endpoints are **already connected** closes the cycle — and since all later edges can't be on a *new* cycle (there is only one), this first failure is also the last cycle edge in input order. O(n · α(n)).

**Code (Python).**

```python
def find_redundant_connection(edges: list[list[int]]) -> list[int]:
    n = len(edges)
    parent = list(range(n + 1))                 # labels are 1..n
    rank = [0] * (n + 1)

    def find(x: int) -> int:
        while parent[x] != x:
            parent[x] = parent[parent[x]]       # path halving (a simpler compression variant)
            x = parent[x]
        return x

    for a, b in edges:
        ra, rb = find(a), find(b)
        if ra == rb:
            return [a, b]                       # already connected → this edge closes the cycle
        if rank[ra] < rank[rb]:
            ra, rb = rb, ra
        parent[rb] = ra
        if rank[ra] == rank[rb]:
            rank[ra] += 1
    return []


assert find_redundant_connection([[1, 2], [1, 3], [2, 3]]) == [2, 3]
assert find_redundant_connection([[1, 2], [2, 3], [3, 4], [1, 4], [1, 5]]) == [1, 4]
assert find_redundant_connection([[3, 4], [1, 2], [2, 4], [3, 5], [2, 5]]) == [2, 5]
print("684 worked example passed")
```

**Test cases.** Triangle → `[2,3]` · cycle 1-2-3-4 with a tail → `[1,4]` · cycle closed by the last edge → that edge · labels not in order (third case) still work.

**Complexity.** Time O(n · α(n)) ≈ O(n). Space O(n). (Path **halving** — `parent[x] = parent[parent[x]]` — is a one-loop alternative to full compression with the same amortised bound.)

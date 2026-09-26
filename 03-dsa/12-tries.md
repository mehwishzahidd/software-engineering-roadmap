# 12 — Tries (Prefix Trees)

> **Week 10** · NeetCode section: **Tries** · Target: **3 new problems** + stretch pool · Java rep: Week 10's rep is **98** in [11](./11-bst.md) (optional extra: 208)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#12--tries) · Python: [cheatsheet](../PYTHON_INTERVIEW_CHEATSHEET.md) (classes, `dict`) · Java: [quick reference](./java-dsa-toolkit.md)

A trie stores strings character by character along root-to-node paths, so all words sharing a prefix share a path. Lookup of a word or prefix costs O(L) (L = word length) regardless of how many words are stored. Tries power autocomplete, spell-check, IP routing tables and word-search-on-a-board problems.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Node | `children: dict[str, Node]` (idiomatic Python) or a 26-slot list + `is_word: bool` (or `word: str \| None`) |
| Insert | Walk/create a child per char; mark the last node as a word end — O(L) |
| Search word vs prefix | Both walk the path; a word additionally requires `is_word` at the end |
| Dict vs list children | `dict`: sparse, any alphabet, simplest in Python. `[None] * 26`: fixed memory per node, index math. In Java the array version is common |
| Counting | Store `prefix_count` on each node → "how many words start with p" in O(L) |
| Wildcards | `.` means "try every child" → DFS branching (211) |
| Trie + DFS on a grid | Build a trie of target words; DFS the board while walking the trie; prune when no child (212) |
| Complexity | Build O(total chars); memory O(total chars) nodes |
| vs `set[str]` | A set: O(L) hash per lookup, no prefix queries. A trie: prefix queries, shared prefixes, ordered traversal |
| `__slots__` | Cuts per-node memory for large tries in Python |

Practical use: in [FlagForge](../18-projects/flagforge/README.md) (Weeks 20–23) you'll match flag keys and rule attributes; a trie is one option for prefix search in the dashboard — but in production you'd usually push search into PostgreSQL (`LIKE 'abc%'` with a B-tree index). Knowing *why* is the interview answer.

---

## 2. Prerequisite knowledge

- [`10-trees.md`](./10-trees.md) — a trie is an n-ary tree.
- [`02-hashing.md`](./02-hashing.md) — dict children; comparison with `set`.
- [`09-recursion.md`](./09-recursion.md) — wildcard DFS.
- Grid backtracking comes in [14](./14-backtracking.md) (needed for stretch problem 212).

---

## 3. Python implementation

```python
class TrieNode:
    __slots__ = ("children", "is_word", "prefix_count")

    def __init__(self) -> None:
        self.children: dict[str, "TrieNode"] = {}
        self.is_word = False
        self.prefix_count = 0          # words passing through this node


class Trie:
    def __init__(self) -> None:
        self.root = TrieNode()

    def insert(self, word: str) -> None:
        node = self.root
        for ch in word:
            node = node.children.setdefault(ch, TrieNode())   # create if missing
            node.prefix_count += 1
        node.is_word = True

    def _walk(self, s: str) -> TrieNode | None:
        node = self.root
        for ch in s:
            node = node.children.get(ch)                       # never create during lookups
            if node is None:
                return None
        return node

    def search(self, word: str) -> bool:
        node = self._walk(word)
        return node is not None and node.is_word

    def starts_with(self, prefix: str) -> bool:
        return self._walk(prefix) is not None

    def count_with_prefix(self, prefix: str) -> int:
        node = self._walk(prefix)
        return node.prefix_count if node else 0

    def autocomplete(self, prefix: str) -> list[str]:
        out: list[str] = []
        node = self._walk(prefix)
        path = list(prefix)

        def collect(n: TrieNode) -> None:
            if n.is_word:
                out.append("".join(path))
            for ch in sorted(n.children):                      # lexicographic order
                path.append(ch)
                collect(n.children[ch])
                path.pop()

        if node:
            collect(node)
        return out


t = Trie()
for w in ("apple", "app", "apply", "bat", "bath"):
    t.insert(w)
assert t.search("app") and not t.search("appl") and t.starts_with("appl")
assert t.count_with_prefix("app") == 3 and t.count_with_prefix("ba") == 2 and t.count_with_prefix("z") == 0
assert t.autocomplete("ap") == ["app", "apple", "apply"]
print("Trie ok")
```

### Array-children variant + wildcard search

```python
class WildcardDictionary:
    """26-slot children (the classic Java-style layout) + '.' wildcard DFS."""

    class _Node:
        __slots__ = ("next", "is_word")

        def __init__(self) -> None:
            self.next: list["WildcardDictionary._Node | None"] = [None] * 26
            self.is_word = False

    def __init__(self) -> None:
        self.root = self._Node()

    def add_word(self, word: str) -> None:
        node = self.root
        for ch in word:
            i = ord(ch) - ord("a")
            if node.next[i] is None:
                node.next[i] = self._Node()
            node = node.next[i]
        node.is_word = True

    def search(self, pattern: str) -> bool:
        def dfs(node: "WildcardDictionary._Node", i: int) -> bool:
            if i == len(pattern):
                return node.is_word
            ch = pattern[i]
            if ch == ".":
                return any(child is not None and dfs(child, i + 1) for child in node.next)
            child = node.next[ord(ch) - ord("a")]
            return child is not None and dfs(child, i + 1)
        return dfs(self.root, 0)


d = WildcardDictionary()
for w in ("bad", "dad", "mad"):
    d.add_word(w)
assert not d.search("pad") and d.search("bad") and d.search(".ad") and d.search("b..") and not d.search("b.")
print("WildcardDictionary ok")
```

---

## 4. The same in Java (occasional reps)

Week 10's mandatory Java rep is **98** ([11](./11-bst.md)). Optional extra: **208 Implement Trie** — nested static class + `Node[26]`.

| Python | Java |
|---|---|
| `children: dict[str, TrieNode]` | `Node[] next = new Node[26]` or `Map<Character, Node>` |
| `node.children.setdefault(ch, TrieNode())` | `if (n.next[c] == null) n.next[c] = new Node(); n = n.next[c];` |
| `ord(ch) - ord("a")` | `ch - 'a'` |

```java
class TrieRep {
    private static final class Node { final Node[] next = new Node[26]; boolean isWord; }
    private final Node root = new Node();

    void insert(String w) {
        Node n = root;
        for (char ch : w.toCharArray()) {
            int c = ch - 'a';
            if (n.next[c] == null) n.next[c] = new Node();
            n = n.next[c];
        }
        n.isWord = true;
    }

    private Node walk(String s) {
        Node n = root;
        for (int i = 0; i < s.length() && n != null; i++) n = n.next[s.charAt(i) - 'a'];
        return n;
    }

    boolean search(String w) { Node n = walk(w); return n != null && n.isWord; }
    boolean startsWith(String p) { return walk(p) != null; }

    public static void main(String[] args) {
        TrieRep t = new TrieRep();
        t.insert("apple");
        System.out.println(t.search("apple") + " " + t.search("app") + " " + t.startsWith("app")); // true false true
    }
}
```

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Problems |
|---|---|
| Plain insert / search / startsWith | 208 |
| Shortest-prefix replacement (walk until the first word end) | 648 |
| Wildcard search with DFS branching | 211 |
| Trie + grid backtracking with pruning | 212 |
| Prefix counts / autocomplete | `prefix_count`, DFS collect (§3) |
| Longest common prefix of many strings | walk while the node has exactly one child and isn't a word end (alternative to 14 in [01](./01-arrays-strings.md)) |

---

## 6. How to recognise the pattern

- "Prefix", "starts with", "autocomplete", "dictionary of words", "replace with root word".
- Many queries against the **same** word list (build once, query many).
- Wildcard character in search queries.
- Finding many words on a grid at once — a trie lets one DFS search all words simultaneously.
- Constraint hints: up to 10⁴–3·10⁴ words of length ≤ 10–100; many `search` calls.

---

## 7. Beginner problems (Week 10)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 208 | [Implement Trie (Prefix Tree)](https://leetcode.com/problems/implement-trie-prefix-tree/) | Medium | Dict children + `is_word`; `search` and `startsWith` share a `_walk` helper. Type it from memory after studying §3. |

(Tries have no true Easy on LeetCode; 208 is the "hello world".)

---

## 8. Interview problems (Week 10)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 648 | [Replace Words](https://leetcode.com/problems/replace-words/) | Medium | 10 | <details><summary>show</summary>Insert all roots; for each word walk the trie and stop at the **first** `is_word` node — that's the shortest root. `" ".join(...)` the result.</details> |
| 211 | [Design Add and Search Words Data Structure](https://leetcode.com/problems/design-add-and-search-words-data-structure/) | Medium | 10 | <details><summary>show</summary>Trie + DFS; on `.` try all children, on a letter follow one child.</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 212 | [Word Search II](https://leetcode.com/problems/word-search-ii/) | Hard | <details><summary>show</summary>Trie of all words (store `word` at end nodes); DFS the board following trie edges; on a hit record the word and clear it to avoid duplicates; prune empty leaf nodes to avoid TLE in Python.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Tries**: 208 → 211 → 212.
Here: `Trie` from memory → 208 → 648 → 211 (Week 10) → 212 after backtracking ([14](./14-backtracking.md)) in the W20–26 pool.

---

## 10. Target number of problems

**3 new** in Week 10 (+ 5 BST = 8) + 1 stretch.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- `node.children.setdefault(ch, TrieNode())` in a **lookup** creates nodes → the trie mutates on searches. Use `.get(ch)` when reading.
- `setdefault(ch, TrieNode())` constructs a new `TrieNode` every call even when the key exists (harmless but wasteful); `if ch not in children: children[ch] = TrieNode()` avoids it.
- Class-level mutable attribute `children = {}` (declared on the class, not in `__init__`) → **every node shares one dict**. Always create per-instance in `__init__`.
- Using a mutable default parameter `def __init__(self, children={})` — same sharing bug.
- 212 in Python without pruning → TLE; remove found words and dead branches.
- Deep recursion is not a risk here (depth = word length), but DFS over a 12×12 board is branching-heavy — prune early.

**General**
- `search` returning True for a prefix (forgetting `is_word`).
- `ord(ch) - ord('a')` on uppercase or non-letters → negative index. Confirm the alphabet or use dict children.
- 211: stating complexity without mentioning the wildcard branching factor.

**Java-rep traps (208):** `Node[26]` of `null`s — always null-check before descending.

---

## 12. Mastery criteria

- [ ] Implement 208 from memory in ≤ 10 min with a shared `_walk` helper.
- [ ] Solve 211 in ≤ 20 min and state worst-case complexity for a query with dots (O(26^d · L), bounded by trie size).
- [ ] Explain trie vs `set[str]` vs a database index for autocomplete in ≤ 4 sentences.
- [ ] Solve 1 unseen trie Medium in ≤ 25 min.

---

## 13. Revision schedule

| Review | Week 10 set (208, 648, 211) |
|---|---|
| Day 0 | W10 |
| Day 3 | W10/W11 |
| Day 7 | W11 |
| Day 14 | W12 (combine with backtracking) |
| Day 30 | W14 |

**Revisit:** Week 12 (backtracking → attempt 212 early if confident), Week 16 (139 Word Break can be solved with a trie + DP), Weeks 20–26 (212).

---

## Worked example — 211. Design Add and Search Words Data Structure

**Clarify.** `addWord(word)` and `search(pattern)` where the pattern may contain `.` matching any **single** letter. Lowercase letters only; word length ≤ 25; at most 2 dots per search query (LeetCode constraint); up to 10⁴ calls.

**Brute force.** Store words in a list; for each search compare the pattern against every word of equal length → O(N · L) per query → 10⁴ · 10⁴ · 25 = 2.5·10⁹ in the worst case. Too slow. A `set` handles exact matches, but dots would need 26² replacements per query — workable only because of the 2-dot limit; say so, then do better.

**Optimise.** Trie. Letters follow a single edge; `.` tries every existing child (DFS). Cost per query: O(L) without dots; O(26^d · L) worst case with d dots, bounded in practice by the actual branches.

**Code (Python).**

```python
class WordDictionary:
    def __init__(self) -> None:
        self.root: dict = {}                 # nested dicts; "$" marks a word end

    def addWord(self, word: str) -> None:    # LeetCode method names are camelCase
        node = self.root
        for ch in word:
            node = node.setdefault(ch, {})
        node["$"] = True

    def search(self, word: str) -> bool:
        def dfs(node: dict, i: int) -> bool:
            if i == len(word):
                return "$" in node
            ch = word[i]
            if ch == ".":
                return any(dfs(child, i + 1) for key, child in node.items() if key != "$")
            return ch in node and dfs(node[ch], i + 1)
        return dfs(self.root, 0)


d = WordDictionary()
for w in ("bad", "dad", "mad"):
    d.addWord(w)
assert d.search("pad") is False
assert d.search("bad") and d.search(".ad") and d.search("b..") and d.search("...")
assert d.search("b.") is False and d.search("ba") is False
assert WordDictionary().search("a") is False
print("211 worked example passed")
```

**Test cases.** LeetCode example (above) · pattern longer/shorter than any word → False · a prefix of a word (`"ba"`) → False · all dots → True if any word of that length exists · search on an empty dictionary → False.

**Complexity.** `addWord` O(L). `search` O(L) without dots; O(26^d · L) worst case with d dots. Space O(total characters inserted). The nested-dict trie with a `"$"` sentinel is a common, compact Python idiom — the class-based version in §3 is clearer; use whichever you can write bug-free.

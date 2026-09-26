# 12 — Tries (Prefix Trees)

> **Week 11** · NeetCode section: **Tries** · Target: **3 new problems** + stretch pool
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#12--tries) · Java APIs: [`java-dsa-toolkit.md`](./java-dsa-toolkit.md)

A trie stores strings character by character along root-to-node paths, so all words sharing a prefix share a path. Lookup of a word or prefix costs O(L) (L = word length) regardless of how many words are stored. Tries power autocomplete, spell-check, IP routing tables and word-search-on-a-board problems.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Node | `children` (array of 26 or `HashMap<Character, Node>`) + `boolean isWord` (or `String word`) |
| Insert | Walk/create a child per char; mark the last node as a word end — O(L) |
| Search word vs prefix | Both walk the path; word additionally requires `isWord` at the end |
| Array vs map children | `Node[26]`: fastest, 26 refs per node (memory); `HashMap`: sparse, any alphabet, slower |
| Counting | Store `prefixCount` on each node → "how many words start with p" in O(L) |
| Wildcards | `.` means "try every child" → DFS branching (211) |
| Trie + DFS on a grid | Build a trie of target words; DFS the board while walking the trie; prune when no child (212) |
| Complexity | Build O(total chars); memory O(total chars × alphabet) worst case |
| vs `HashSet<String>` | Hash set: O(L) to hash, no prefix queries. Trie: prefix queries, shared prefixes, ordered traversal |

Practical use: search-as-you-type in [P3 TeamBoard](../18-projects/p3-teamboard/README.md) could use a trie in memory — but in production you'd use a database index (`LIKE 'abc%'` with a B-tree, or PostgreSQL trigram indexes). Knowing *why* is the interview answer.

---

## 2. Prerequisite knowledge

- [`10-trees.md`](./10-trees.md) — a trie is an n-ary tree.
- [`02-hashing.md`](./02-hashing.md) — map-based children; comparison with `HashSet`.
- [`09-recursion.md`](./09-recursion.md) — wildcard DFS.
- Backtracking on grids comes in [14](./14-backtracking.md) (needed for stretch problem 212).

---

## 3. Java implementation

```java
import java.util.*;

class Trie {
    private static final class Node {
        final Node[] next = new Node[26];
        boolean isWord;
        int prefixCount;                      // number of inserted words passing through this node
    }

    private final Node root = new Node();

    public void insert(String word) {
        Node n = root;
        for (int i = 0; i < word.length(); i++) {
            int c = word.charAt(i) - 'a';
            if (n.next[c] == null) n.next[c] = new Node();
            n = n.next[c];
            n.prefixCount++;
        }
        n.isWord = true;
    }

    public boolean search(String word) {
        Node n = walk(word);
        return n != null && n.isWord;
    }

    public boolean startsWith(String prefix) {
        return walk(prefix) != null;
    }

    public int countWithPrefix(String prefix) {
        Node n = walk(prefix);
        return n == null ? 0 : n.prefixCount;
    }

    private Node walk(String s) {
        Node n = root;
        for (int i = 0; i < s.length() && n != null; i++) n = n.next[s.charAt(i) - 'a'];
        return n;
    }

    // All words with a given prefix, in lexicographic order (autocomplete)
    public List<String> autocomplete(String prefix) {
        List<String> out = new ArrayList<>();
        Node n = walk(prefix);
        if (n != null) collect(n, new StringBuilder(prefix), out);
        return out;
    }

    private void collect(Node n, StringBuilder sb, List<String> out) {
        if (n.isWord) out.add(sb.toString());
        for (int c = 0; c < 26; c++) {
            if (n.next[c] == null) continue;
            sb.append((char) ('a' + c));
            collect(n.next[c], sb, out);
            sb.setLength(sb.length() - 1);
        }
    }

    public static void main(String[] args) {
        Trie t = new Trie();
        for (String w : List.of("apple", "app", "apply", "bat", "bath")) t.insert(w);
        System.out.println(t.search("app") + " " + t.search("appl") + " " + t.startsWith("appl")); // true false true
        System.out.println(t.countWithPrefix("app") + " " + t.countWithPrefix("ba"));             // 3 2
        System.out.println(t.autocomplete("ap"));                                                 // [app, apple, apply]
    }
}
```

### Map-based children (any alphabet) + wildcard search

```java
import java.util.*;

class WildcardDictionary {
    private static final class Node {
        final Map<Character, Node> kids = new HashMap<>();
        boolean isWord;
    }

    private final Node root = new Node();

    void addWord(String w) {
        Node n = root;
        for (char c : w.toCharArray()) n = n.kids.computeIfAbsent(c, k -> new Node());
        n.isWord = true;
    }

    boolean search(String pattern) { return dfs(root, pattern, 0); }

    private boolean dfs(Node n, String p, int i) {
        if (i == p.length()) return n.isWord;
        char c = p.charAt(i);
        if (c == '.') {
            for (Node child : n.kids.values()) if (dfs(child, p, i + 1)) return true;
            return false;
        }
        Node child = n.kids.get(c);
        return child != null && dfs(child, p, i + 1);
    }

    public static void main(String[] args) {
        WildcardDictionary d = new WildcardDictionary();
        d.addWord("bad"); d.addWord("dad"); d.addWord("mad");
        System.out.println(d.search("pad") + " " + d.search("bad") + " " + d.search(".ad") + " " + d.search("b..")); // false true true true
    }
}
```

---

## 4. Common patterns (sub-variants)

| Sub-pattern | Problems |
|---|---|
| Plain insert / search / startsWith | 208 |
| Shortest-prefix replacement (walk until first word end) | 648 |
| Wildcard search with DFS branching | 211 |
| Trie + grid backtracking with pruning | 212 |
| Prefix counts / autocomplete | `prefixCount`, DFS collect (§3) |
| Longest common prefix of many strings | walk while the node has exactly one child and isn't a word end (alternative to 14 in [01](./01-arrays-strings.md)) |

---

## 5. How to recognise the pattern

- "Prefix", "starts with", "autocomplete", "dictionary of words", "replace with root word".
- Many queries against the **same** word list (build once, query many).
- Wildcard character in search queries.
- Finding many words on a grid at once — a trie lets one DFS search all words simultaneously.
- Constraint hints: up to 10⁴–3·10⁴ words of length ≤ 10–100; many `search` calls.

---

## 6. Beginner problems (Week 11)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 208 | [Implement Trie (Prefix Tree)](https://leetcode.com/problems/implement-trie-prefix-tree/) | Medium | `Node[26]` + `isWord`; `search` and `startsWith` share a `walk` helper. Type it from memory after studying §3. |

(Tries have no true Easy on LeetCode; 208 is the "hello world".)

---

## 7. Interview problems (Week 11)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 648 | [Replace Words](https://leetcode.com/problems/replace-words/) | Medium | 11 | <details><summary>show</summary>Insert all roots; for each word walk the trie and stop at the **first** `isWord` node — that's the shortest root. Build output with `StringBuilder`.</details> |
| 211 | [Design Add and Search Words Data Structure](https://leetcode.com/problems/design-add-and-search-words-data-structure/) | Medium | 11 | <details><summary>show</summary>Trie + DFS; on `.` try all non-null children, on a letter follow one child.</details> |

### Stretch pool (Weeks 21–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 212 | [Word Search II](https://leetcode.com/problems/word-search-ii/) | Hard | <details><summary>show</summary>Trie of all words (store `String word` at end nodes); DFS the board following trie edges; on a hit add the word and null it out to avoid duplicates; prune leaf nodes.</details> |

---

## 8. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Tries**: 208 → 211 → 212.
Here: `Trie` from memory → 208 → 648 → 211 (W11) → 212 after backtracking ([14](./14-backtracking.md)) in the W21–26 pool.

---

## 9. Target number of problems

**3 new** in Week 11 (+ 5 BST = 8) + 1 stretch.

---

## 10. Mistakes beginners commonly make

- `search` returning true for a prefix (forgetting `isWord`).
- `c - 'a'` on uppercase or non-letters → negative index → `ArrayIndexOutOfBoundsException`. Confirm the alphabet; use a map otherwise.
- Creating nodes during `search` (mutating the trie on lookups).
- Using `HashMap<Character, Node>` everywhere: correct but slower and more boxing; mention the trade-off.
- 211: iterating all 26 slots including `null`s without checking.
- 212: not removing found words → duplicates in output; not pruning → TLE.
- Memory: `Node[26]` per node × 10⁶ nodes ≈ 100+ MB — say "array for small alphabets, map for large/sparse".

---

## 11. Mastery criteria

- [ ] Implement 208 from memory in ≤ 10 min with a shared `walk` helper.
- [ ] Solve 211 in ≤ 20 min and state worst-case complexity for a query with all dots (O(26^L) bounded by trie size).
- [ ] Explain trie vs `HashSet<String>` vs database index for autocomplete in ≤ 4 sentences.
- [ ] Solve 1 unseen trie Medium in ≤ 25 min.

---

## 12. Revision schedule

| Review | Week 11 set (208, 648, 211) |
|---|---|
| Day 0 | W11 |
| Day 3 | W11/W12 |
| Day 7 | W12 |
| Day 14 | W13 (combine with backtracking) |
| Day 30 | W15 |

**Revisit:** Week 13 (backtracking → attempt 212 early if confident), Week 16 (139 Word Break can be solved with a trie + DP), Weeks 21–26 (212).

---

## Worked example — 211. Design Add and Search Words Data Structure

**Clarify.** `addWord(word)` and `search(pattern)` where pattern may contain `.` matching any **single** letter. Lowercase letters only; word length ≤ 25; at most 2 dots per search query (per LeetCode's constraints); up to 10⁴ calls.

**Brute force.** Store words in a list; for each search compare the pattern against every word of equal length → O(N · L) per query. With N = 10⁴ and 10⁴ queries → 2.5·10⁹. Too slow. A `HashSet` handles exact matches but not dots (you'd have to enumerate 26² replacements per query — workable only because of the 2-dot limit; say so, then do better).

**Optimise.** Trie. Letters follow a single edge; `.` tries every existing child (DFS). Cost per query: O(L) without dots; with d dots O(26^d · L) worst case, and in practice limited by actual trie branches.

**Code.**

```java
class WordDictionary {
    private static final class Node {
        final Node[] next = new Node[26];
        boolean isWord;
    }

    private final Node root = new Node();

    public void addWord(String word) {
        Node n = root;
        for (int i = 0; i < word.length(); i++) {
            int c = word.charAt(i) - 'a';
            if (n.next[c] == null) n.next[c] = new Node();
            n = n.next[c];
        }
        n.isWord = true;
    }

    public boolean search(String word) {
        return dfs(root, word, 0);
    }

    // Contract: true iff some word in n's subtree matches word[i..]
    private boolean dfs(Node n, String word, int i) {
        if (i == word.length()) return n.isWord;
        char ch = word.charAt(i);
        if (ch == '.') {
            for (Node child : n.next) {
                if (child != null && dfs(child, word, i + 1)) return true;
            }
            return false;
        }
        Node child = n.next[ch - 'a'];
        return child != null && dfs(child, word, i + 1);
    }

    public static void main(String[] args) {
        WordDictionary d = new WordDictionary();
        d.addWord("bad"); d.addWord("dad"); d.addWord("mad");
        System.out.println(d.search("pad"));   // false
        System.out.println(d.search("bad"));   // true
        System.out.println(d.search(".ad"));   // true
        System.out.println(d.search("b.."));   // true
        System.out.println(d.search("b."));    // false (length mismatch)
        System.out.println(d.search("..."));   // true
    }
}
```

**Test cases.** LeetCode example (above) · pattern longer/shorter than any word → false · prefix of a word (`"ba"`) → false · all dots → true if any word of that length exists · search on an empty dictionary → false.

**Complexity.** `addWord` O(L) time, O(L) new nodes worst case. `search` O(L) without dots; O(26^d · L) worst case with d dots. Space O(total characters inserted × 26) references.

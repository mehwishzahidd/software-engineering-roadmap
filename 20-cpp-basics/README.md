# ⚙️ 20 — C++ Basics (capped: ≈3–6 hours, Week 19)

> **Why this exists:** CS interviews and fundamentals questions assume you understand
> pointers, references, manual memory, the stack vs the heap, and undefined behavior. Java
> hides all of that behind references and a garbage collector. A few hours of C++ makes
> those Java answers ([../01-java/06-memory-jvm.md](../01-java/06-memory-jvm.md)) concrete.
>
> **Why it's capped:** Java is your interview language ([ROADMAP §2.5](../ROADMAP.md#25-weighting-problems-fixed)).
> C++ is **not** a second DSA language here. Stop when the checklist is done.

| Block | Time | Content |
|---|---|---|
| 1 | 45 min | §1–3: compile/run, types, pointers vs references, pass by value/ref/const ref |
| 2 | 60 min | §4–5: stack vs heap, `new`/`delete`, RAII, `unique_ptr`, `shared_ptr` |
| 3 | 45 min | §6–7: arrays vs `std::vector`, `std::string`, `std::unordered_map` |
| 4 | 30 min | §8: undefined behavior; run examples with sanitizers |
| 5 | 60–120 min | §10: the 6 exercises |
| (opt) | ≤ 60 min | Re-read §9 mapping to Java and answer the interview questions out loud |

---

## 1. Compile and run

```bash
g++ --version                       # or clang++; any compiler supporting C++17/20
cat > hello.cpp <<'EOF'
#include <iostream>
#include <string>

int main(int argc, char* argv[]) {
    std::string name = argc > 1 ? argv[1] : "world";
    std::cout << "Hello, " << name << "!\n";
    return 0;                        // exit code
}
EOF
g++ -std=c++20 -Wall -Wextra -g -o hello hello.cpp
./hello Ada

# Debug build that catches memory bugs at runtime (use this for every exercise):
g++ -std=c++20 -Wall -Wextra -g -fsanitize=address,undefined -o hello hello.cpp
```

| Java | C++ |
|---|---|
| `javac` → bytecode → JVM | `g++` → **native machine code** for one platform (compile + link) |
| One class per file, packages | Headers (`.h`, declarations) + sources (`.cpp`, definitions); `#include` is textual inclusion |
| Runtime checks (bounds, null) | **No** runtime checks by default — bugs become undefined behavior |
| GC | Deterministic destruction (RAII) |

---

## 2. Types

```cpp
int i = 42;                 // size is platform-dependent (usually 32-bit); Java's is fixed
long long big = 9'000'000'000LL;
unsigned int u = 3;         // no unsigned in Java (except char); careful: 0u - 1 wraps to 4294967295
double d = 3.14;
char c = 'A';               // 1 byte (Java char is 2 bytes)
bool ok = true;             // ints convert to bool implicitly: if (5) is true (Java forbids this)
auto x = 10;                // like Java's var
const int LIMIT = 100;      // like final
std::size_t n = v.size();   // unsigned size type used by containers
```

In C++, objects of class type are **values** by default, not references:

```cpp
struct Point { int x; int y; };
Point a{1, 2};
Point b = a;        // COPIES the whole object (Java would copy the reference)
b.x = 99;           // a.x is still 1
```

---

## 3. Pointers vs references; passing arguments

```cpp
int value = 10;
int* p = &value;     // pointer: holds an address; can be null (nullptr), can be re-pointed
int& r = value;      // reference: an alias; must be initialized; can't be re-bound; never null

*p = 20;             // dereference: value is now 20
r = 30;              // value is now 30
p = nullptr;         // fine for a pointer
// *p = 1;           // UB: dereferencing null (Java: NullPointerException; C++: anything can happen)
```

| | Pointer `T*` | Reference `T&` |
|---|---|---|
| Can be null | yes (`nullptr`) | no |
| Re-seatable | yes | no |
| Syntax | `*p`, `p->field` | like the object itself |
| Arithmetic | yes (`p + 1`) | no |
| Closest Java analogue | A Java reference variable (nullable, re-assignable) — but without GC and with arithmetic | none exactly |

### Pass by value / reference / const reference

```cpp
#include <vector>

void byValue(std::vector<int> v)          { v.push_back(1); }  // copies the whole vector; caller unaffected
void byRef(std::vector<int>& v)           { v.push_back(1); }  // caller's vector modified
void byConstRef(const std::vector<int>& v){ /* read-only, no copy */ }
void byPointer(std::vector<int>* v)       { if (v) v->push_back(1); }

std::vector<int> nums{1, 2, 3};
byValue(nums);      // nums still has 3 elements
byRef(nums);        // now 4
```
Rule of thumb: small types (int, double) by value; large objects you only read by **const
reference**; objects you must modify by reference. Java has only one mode: pass-by-value of a
primitive or of a reference — which behaves like C++'s "pass a pointer by value".

---

## 4. Stack vs heap, `new`/`delete`

```cpp
void f() {
    int a = 5;                     // stack: freed automatically at end of scope
    Point p{1, 2};                 // stack: the whole object lives in f's frame
    Point* h = new Point{3, 4};    // heap: lives until YOU delete it
    delete h;                      // forget this → memory leak; do it twice → UB (double free)
    int* arr = new int[100];
    delete[] arr;                  // arrays need delete[] (mismatching is UB)
}
```

In Java every object is on the heap and the GC frees it. In C++ you choose, and manual
`new`/`delete` is error-prone: leaks, double frees, use-after-free, and exceptions skipping the `delete`.
**Modern C++ code almost never writes `new`/`delete` directly.**

---

## 5. RAII and smart pointers

**RAII (Resource Acquisition Is Initialization):** tie a resource's lifetime to an object on the
stack; the destructor releases it when the object goes out of scope — even when an exception is
thrown. It's C++'s version of try-with-resources, but applied to *everything* (memory, files, locks).

```cpp
#include <fstream>
#include <memory>
#include <mutex>

std::mutex m;
void safeWork() {
    std::lock_guard<std::mutex> guard(m);       // locks now; unlocks in destructor (like synchronized)
    std::ofstream out("log.txt");               // file closed in destructor
    out << "done\n";
}                                               // destructors run here, in reverse order

struct Monitor {
    std::string url;
    explicit Monitor(std::string u) : url(std::move(u)) {}
    ~Monitor() { /* destructor: runs deterministically */ }
};

void owners() {
    std::unique_ptr<Monitor> a = std::make_unique<Monitor>("https://example.com");
    // std::unique_ptr<Monitor> b = a;          // compile error: unique ownership can't be copied
    std::unique_ptr<Monitor> b = std::move(a);  // ownership transferred; a is now null

    std::shared_ptr<Monitor> s1 = std::make_shared<Monitor>("https://x.dev");
    std::shared_ptr<Monitor> s2 = s1;           // reference count = 2
    std::cout << s1.use_count() << "\n";        // 2
}   // b deletes its Monitor; last shared_ptr deletes the other one
```

| Tool | Ownership | Java analogy |
|---|---|---|
| Stack object | Scope owns it | none (escape-analysed locals, roughly) |
| `std::unique_ptr<T>` | Exactly one owner, move-only, zero overhead | A reference that must be the only one |
| `std::shared_ptr<T>` | Shared, reference-counted; freed when count hits 0 | GC — but **reference counting leaks cycles** (use `std::weak_ptr` to break them); Java's tracing GC handles cycles |
| Raw `T*` / `T&` | Non-owning observer | — |

---

## 6. Arrays vs `std::vector`, `std::string`

```cpp
#include <vector>
#include <string>
#include <algorithm>

int raw[5] = {1, 2, 3, 4, 5};        // fixed size, no bounds checks, decays to a pointer when passed
std::vector<int> v = {5, 3, 8};       // ≈ ArrayList<Integer> but stores ints inline (no boxing)
v.push_back(1);                       // amortized O(1), grows geometrically (like ArrayList)
v[10];                                // UB: no bounds check
v.at(10);                             // throws std::out_of_range (checked access)
std::sort(v.begin(), v.end());
for (int x : v) std::cout << x << ' ';
v.size();                             // unsigned! `for (int i = 0; i < v.size() - 1; ...)` breaks when empty

std::string s = "ledger";             // MUTABLE, value semantics (unlike Java's immutable String)
s += "-cli";                          // modifies s in place
s[0] = 'L';
std::string t = s;                    // copy
bool same = (s == t);                 // == compares CONTENT in C++ (in Java it compares references)
s.substr(1, 3);                       // (pos, length) — Java's substring is (begin, end)!
```

Warning: a pointer/reference/iterator into a `vector` becomes **dangling** after `push_back`
reallocates. Java's `ArrayList` avoids this because you hold references to elements, not to the backing array.

---

## 7. `std::unordered_map` (≈ HashMap) and `std::map` (≈ TreeMap)

```cpp
#include <unordered_map>
#include <map>

std::unordered_map<std::string, int> counts;
for (const std::string& w : words) counts[w]++;   // operator[] INSERTS a default (0) if missing!
if (counts.count("x")) { /* present */ }          // or counts.find("x") != counts.end(); C++20: contains
auto it = counts.find("ledger");
if (it != counts.end()) std::cout << it->first << "=" << it->second << "\n";
for (const auto& [word, n] : counts) std::cout << word << " " << n << "\n";   // structured bindings

std::map<std::string, int> sorted(counts.begin(), counts.end());   // red-black tree, ordered keys
```
Trap: `if (counts["x"] > 0)` silently adds `"x"` with value 0. Java's `get` never inserts.

---

## 8. Undefined behavior (UB) — why Java has runtime checks

UB means the language standard places **no requirements** on what happens: it may crash, print
garbage, "work" today and fail tomorrow, or be optimized in surprising ways. Java defines behavior
(throws an exception) for every one of these:

```cpp
// ub.cpp — compile with: g++ -std=c++20 -g -fsanitize=address,undefined ub.cpp -o ub && ./ub N
#include <iostream>
#include <vector>
#include <climits>
#include <cstdlib>

int* dangling() { int local = 42; return &local; }   // returns address of a dead stack variable

int main(int argc, char* argv[]) {
    int which = argc > 1 ? std::atoi(argv[1]) : 0;
    std::vector<int> v{1, 2, 3};
    switch (which) {
        case 1: std::cout << v[3] << "\n"; break;                  // out of bounds   (Java: ArrayIndexOutOfBoundsException)
        case 2: { int* p = nullptr; std::cout << *p << "\n"; break; } // null deref (Java: NullPointerException)
        case 3: { int* p = new int(5); delete p; std::cout << *p << "\n"; break; } // use-after-free (impossible in Java)
        case 4: { int x = INT_MAX; x = x + 1; std::cout << x << "\n"; break; }    // signed overflow is UB (Java: defined wraparound)
        case 5: { int* p = dangling(); std::cout << *p << "\n"; break; }           // dangling pointer to a dead frame
        case 6: { int y; std::cout << y << "\n"; break; }                          // uninitialized read (Java: compile error)
        default: std::cout << "pass 1..6\n";
    }
}
```
Run each case with and **without** `-fsanitize`. Without sanitizers several "work" and print
something plausible — that's the danger. This is also the root of many security vulnerabilities
(buffer overflows), and the reason memory-safe languages (Java, Python, Rust) are preferred for most services.

---

## 9. How this maps to Java and to interview questions

| Interview question | C++ insight | Java answer |
|---|---|---|
| "Is Java pass-by-reference?" | C++ has real references (`T&`) that can rebind the caller's variable contents | No — Java passes reference **values**, like passing a C++ pointer by value |
| "Stack vs heap?" | You choose: `Point p;` vs `new Point` | Objects always on heap (logically), locals/refs on stack |
| "Why does Java have GC? Downsides?" | Manual `delete` → leaks, double frees, use-after-free; RAII solves it deterministically | GC eliminates those bugs, costs pauses/memory overhead; leaks still possible via reachability |
| "Reference counting vs tracing GC?" | `shared_ptr` can leak cycles | Java's tracing GC collects cycles |
| "What is a memory leak?" | Forgot `delete` | Object reachable but unused |
| "What is a null pointer?" | Deref is UB | Deref throws `NullPointerException` |
| "What is a buffer overflow?" | Writing past an array is UB → exploitable | Bounds-checked → exception |
| "What does `==` do on strings?" | Compares content | Compares references; use `equals` |
| "Why is `String` immutable in Java?" | `std::string` is mutable, so copies are made defensively | Immutable → sharing is safe, pool possible |
| "What's a destructor / finalizer?" | Deterministic at scope end | No destructors; `finalize` deprecated; use try-with-resources |
| "How does a vector/ArrayList grow?" | Geometric reallocation invalidates pointers/iterators | Geometric growth; you hold element refs, not addresses |

---

## 10. Six small exercises (compile with `-Wall -Wextra -fsanitize=address,undefined`)

1. **Swap three ways.** Write `swapByValue(int, int)`, `swapByPointer(int*, int*)`, `swapByRef(int&, int&)`. Print before/after in `main`.
   - [ ] Only the pointer and reference versions swap; write one sentence on why Java can't write `swap(int, int)` at all.
2. **Stack vs heap lifetimes.** Class `Tracer` whose constructor/destructor print its name. Create one on the stack in a nested scope, one with `new` + `delete`, one with `make_unique`, two `shared_ptr`s sharing an object.
   - [ ] Output shows destructor order; you can predict each line before running.
3. **Leak and fix.** Write a loop that `new`s an `int[1000]` 1000 times without `delete[]`; run with `-fsanitize=address` (LeakSanitizer reports it). Fix with `std::vector<int>(1000)`.
   - [ ] Sanitizer report before, clean run after.
4. **Word count.** Read words from `std::cin` into `std::unordered_map<std::string,int>`, print the top 5 by count (copy into a `std::vector<std::pair<...>>` and `std::sort` with a lambda comparator).
   - [ ] Uses `const std::string&` in loops; no raw `new`.
5. **Two Sum in C++.** LeetCode 1 with `std::unordered_map<int,int>` and `std::vector<int>`.
   - [ ] Compare line-by-line with your Java solution; list 3 differences.
6. **UB tour.** Run all six cases of `ub.cpp` (§8) with and without sanitizers.
   - [ ] Table: case → what happened without sanitizer → sanitizer message → Java equivalent behavior.

---

## 🚫 What NOT to spend time on

- Templates beyond using `std::vector<T>`; template metaprogramming; concepts.
- Classes in depth: rule of 3/5/0, copy/move constructors, operator overloading, multiple/virtual inheritance (know that `virtual` enables dynamic dispatch — that's enough).
- Build systems (CMake, Make), header/source file organization, linkers, ABI.
- Move semantics beyond "`std::move` transfers ownership of a `unique_ptr`".
- Iterators/algorithms beyond `begin()`/`end()` + `std::sort`.
- Concurrency in C++, the preprocessor, macros, `const_cast`/`reinterpret_cast`.
- Competitive-programming C++ (`bits/stdc++.h`, macros) — your DSA language is Java.
- Any C++ project. If you feel like building something in C++, build it in Java instead.

---

## 🎤 Interview questions

<details><summary>1. Pointer vs reference in C++?</summary>

A pointer stores an address, can be null, can be re-pointed, supports arithmetic, and is
dereferenced explicitly. A reference is an alias that must be bound at initialization, can't be
null or re-bound, and is used like the object.
</details>

<details><summary>2. What is RAII?</summary>

Binding a resource's lifetime to an object's scope: acquire in the constructor, release in the
destructor, which runs deterministically when the object leaves scope (including during
exceptions). Smart pointers, `lock_guard`, and file streams are RAII types. Java's closest equivalent is try-with-resources.
</details>

<details><summary>3. unique_ptr vs shared_ptr?</summary>

`unique_ptr`: sole ownership, non-copyable, movable, no overhead. `shared_ptr`: shared ownership
via atomic reference counting; object freed when the last owner goes; cycles leak unless broken with `weak_ptr`.
</details>

<details><summary>4. What is undefined behavior? Give examples.</summary>

Operations for which the standard imposes no requirements: out-of-bounds access, null
dereference, use-after-free, signed overflow, reading uninitialized variables, data races. The
program may do anything. Java instead defines behavior (exceptions, wraparound, compile errors).
</details>

<details><summary>4b. How does memory management in C++ compare to Java?</summary>

C++: deterministic, programmer-controlled (stack objects, RAII, smart pointers), no GC pauses, but
memory-safety bugs are possible. Java: all objects heap-allocated and garbage-collected; no
use-after-free/double free; leaks only via lingering references; GC costs CPU/memory/pauses.
</details>

---

## ✅ Mastery checklist (then stop)

- [ ] Compile and run with `g++ -std=c++20 -Wall -Wextra -fsanitize=address,undefined`
- [ ] Explain pointer vs reference and pass by value / reference / const reference
- [ ] Explain stack vs heap with `Point p;` vs `new Point`
- [ ] Explain RAII and use `unique_ptr`, `shared_ptr`, `lock_guard`
- [ ] Use `std::vector`, `std::string`, `std::unordered_map` for a small program
- [ ] Demonstrate 3 kinds of UB and what the sanitizer reports
- [ ] Answer every row of the §9 table from the Java side
- [ ] Six exercises done — total time ≤ 6 hours

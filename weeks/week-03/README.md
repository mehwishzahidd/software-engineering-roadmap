# Week 3 — Modern Java; SQL fundamentals; first HTTP endpoint

[← Week 2](../week-02/) · [Roadmap](../../ROADMAP.md) · [Week 4 →](../week-04/)

**Phase 0 · Foundation** (exercises only — the Spring Boot API is throwaway, but its *shape* is FlowGrid's)

| Block | Hours | What it means this week |
|---|---:|---|
| Project (foundation coding) | 20 | A tiny Spring Boot 3 API with **2 endpoints + tests**, backed by an in-memory store; SQL practice against Postgres in Docker; a Python script with pytest |
| Learning | 15 | Python `pytest`/type hints/scripting; lambdas, streams, records, `Optional`, IDE debugger; SQL basics + `psql`; HTTP/REST/JSON; Spring Boot intro (DI, `@RestController`, layers) |
| DSA | 7 | Two Pointers + Prefix Sums — **8 new problems in Python** + Day-3/7/14 reviews + 1 Java rep |
| Interview / review | 3 | Explain out loud; Sunday test |

---

## 1. Main objective

This is the last week without FlowGrid. Its job is to make Monday of Week 4 start on
*warehouses and SKUs*, not on "what is a bean". By Sunday:

- a Spring Boot 3 application you wrote from an empty Initializr project answers
  `GET /api/items` and `POST /api/items` with JSON, validates input, returns proper status codes,
  and has a `@SpringBootTest`/`MockMvc` test for each endpoint;
- you can write `SELECT … WHERE … ORDER BY … LIMIT`, `INSERT`, `UPDATE`, `DELETE` against
  PostgreSQL 16 in `psql`, and know what an HTTP request/response actually contains;
- you use lambdas, streams, records and `Optional` naturally in Java, and the IDE debugger instead of `println`;
- in Python you write a tested (`pytest`), type-hinted script with `argparse` — the shape of every
  FlowGrid `tools/` script in Weeks 7–8.

## 2. Prerequisites

- Week 2 kata tagged `kata-v1`; JUnit + Maven comfortable.
- **Docker Desktop / Docker Engine installed** by Monday (only `docker run` this week; Compose is Week 4 — see [`11-docker/README.md`](../../11-docker/README.md)).
- Python 3.12 with a virtualenv: `python3 -m venv .venv && source .venv/bin/activate && pip install pytest requests`.

## 3. Learning topics

| Topic | Subtopics | Folder file |
|---|---|---|
| **Python testing & scripting** | `pytest` (asserts, fixtures, `parametrize`, `raises`), type hints (`list[int]`, `dict[str, int]`, `Optional`, `TypedDict`/dataclass), `argparse`, `pathlib`, `csv`/`json`, `requests`, `if __name__ == "__main__"` | [`19-python/04-testing-and-scripting.md`](../../19-python/04-testing-and-scripting.md), [`19-python/exercises.md`](../../19-python/exercises.md) |
| Modern Java | lambdas, functional interfaces, method refs, streams (`map`/`filter`/`collect`/`groupingBy`/`sorted`), records, `Optional`, sealed interfaces (reading), pattern matching for `instanceof`/`switch` | [`01-java/04-modern-java.md`](../../01-java/04-modern-java.md) |
| IDE debugger | breakpoints, conditional breakpoints, step over/into, evaluate expression, watches; reading a Spring stack trace | [`01-java/09-debugging-java.md`](../../01-java/09-debugging-java.md) |
| SQL basics + PostgreSQL | `psql` meta-commands, `CREATE TABLE`, types, `PRIMARY KEY`/`NOT NULL`/`CHECK`/`UNIQUE`, `SELECT`…`WHERE`…`ORDER BY`…`LIMIT`/`OFFSET`, `INSERT`/`UPDATE`/`DELETE`, `RETURNING`, `NULL` semantics | [`04-sql-databases/01-sql-basics.md`](../../04-sql-databases/01-sql-basics.md), [`04-sql-databases/practice-schema.sql`](../../04-sql-databases/practice-schema.sql), [`04-sql-databases/sql-practice.md`](../../04-sql-databases/sql-practice.md) |
| HTTP for APIs | request/response anatomy, methods, status codes, headers (`Content-Type`, `Accept`, `Location`), idempotency of methods, JSON, `curl -v` | [`06-rest-apis/http-for-apis.md`](../../06-rest-apis/http-for-apis.md), [`06-rest-apis/curl-postman.md`](../../06-rest-apis/curl-postman.md), [`06-rest-apis/README.md`](../../06-rest-apis/README.md) |
| REST design (first pass) | resources & nouns, plural paths, `201 Created` + `Location`, `404` vs `400` vs `422`, versioning by path | [`06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md) |
| Spring Boot intro | Initializr, `@SpringBootApplication`, DI & beans, constructor injection, `@RestController`/`@Service`/`@Repository` layers, `@RequestBody`/`@PathVariable`/`@RequestParam`, `ResponseEntity`, `application.yml`, `spring-boot-starter-test` | [`05-spring-boot/01-core-di.md`](../../05-spring-boot/01-core-di.md), [`05-spring-boot/02-web-layer.md`](../../05-spring-boot/02-web-layer.md), [`05-spring-boot/README.md`](../../05-spring-boot/README.md) |
| Spring testing (first pass) | `@SpringBootTest`, `@AutoConfigureMockMvc`, `MockMvc`, `jsonPath` | [`09-testing/spring-testing.md`](../../09-testing/spring-testing.md) |

## 4. Concepts to learn

### 4.1 Python: a tested, typed script

```python
# tools/lowstock.py
import argparse, csv
from pathlib import Path

def low_stock(rows: list[dict[str, str]], threshold: int) -> list[tuple[str, int]]:
    out = [(r["sku"], int(r["qty"])) for r in rows if int(r["qty"]) < threshold]
    return sorted(out, key=lambda p: (p[1], p[0]))

def main() -> None:
    p = argparse.ArgumentParser(); p.add_argument("file", type=Path); p.add_argument("--threshold", type=int, default=5)
    a = p.parse_args()
    with a.file.open(newline="") as f:
        for sku, qty in low_stock(list(csv.DictReader(f)), a.threshold): print(f"{sku},{qty}")

if __name__ == "__main__": main()
```

```python
# tests/test_lowstock.py
import pytest
from tools.lowstock import low_stock
@pytest.mark.parametrize("threshold,expected", [(5, [("NUT", 0)]), (10, [("NUT", 0), ("BOLT", 7)])])
def test_low_stock(threshold, expected):
    rows = [{"sku": "BOLT", "qty": "7"}, {"sku": "NUT", "qty": "0"}, {"sku": "WASHER", "qty": "12"}]
    assert low_stock(rows, threshold) == expected
```

- **Interview angle:** Python screens often ask "how would you test this?" — `pytest` + `parametrize` is the expected answer; type hints signal care.
- **FlowGrid uses this:** exactly this shape becomes `tools/generate_orders.py` (Week 7) and `tools/simulate_load.py` (Week 8), both pytest-tested.

### 4.2 Java: lambdas and streams that read like the requirement

```java
record Item(String sku, String name, int qty, String zone) {}

Map<String, Integer> qtyByZone = items.stream()
    .collect(Collectors.groupingBy(Item::zone, Collectors.summingInt(Item::qty)));

List<String> lowStock = items.stream()
    .filter(i -> i.qty() < 5)
    .sorted(Comparator.comparingInt(Item::qty).thenComparing(Item::sku))
    .map(Item::sku)
    .toList();                     // Java 16+: unmodifiable list

Optional<Item> first = items.stream().filter(i -> i.sku().equals("BOLT-M6")).findFirst();
String name = first.map(Item::name).orElseThrow(() -> new NoSuchElementException("BOLT-M6"));
```

Rules: streams for transformations, loops for side effects; never `Optional.get()` without a check; `Optional` as a return type, not a field or parameter.

- **Interview angle:** "What is the difference between `map` and `flatMap`?" "Are streams lazy?" (yes — intermediate ops run only on a terminal op). "Why not use `Optional` as a field?"
- **FlowGrid uses this:** `groupingBy` builds "inventory by warehouse" summaries (M1) and candidate lists per SKU in the allocator (M3); repositories return `Optional<Entity>`.

### 4.3 Java: pattern matching and sealed types (read now, use in M2)

```java
sealed interface Result permits Ok, Err {}
record Ok(int id) implements Result {}
record Err(String message) implements Result {}

static String describe(Result r) {
    return switch (r) {              // exhaustive — compiler checks all permitted subtypes
        case Ok ok   -> "created " + ok.id();
        case Err err -> "failed: " + err.message();
    };
}
```

- **Interview angle:** "What did records and sealed types change about modelling in Java?" (algebraic data types; exhaustiveness).
- **FlowGrid uses this:** reservation outcomes (`Reserved`, `Insufficient`, `Conflict`) in M2.

### 4.4 SQL: the statements you will write a thousand times

```sql
CREATE TABLE item (
  id      bigserial PRIMARY KEY,
  sku     text NOT NULL UNIQUE,
  name    text NOT NULL,
  qty     integer NOT NULL CHECK (qty >= 0),
  zone    text
);
INSERT INTO item (sku, name, qty, zone) VALUES ('BOLT-M6', 'Bolt M6', 7, 'north') RETURNING id;
SELECT sku, qty FROM item WHERE qty < 5 AND zone IS NOT NULL ORDER BY qty, sku LIMIT 20 OFFSET 0;
UPDATE item SET qty = qty - 1 WHERE sku = 'BOLT-M6' AND qty >= 1;   -- 0 rows affected = insufficient
DELETE FROM item WHERE id = 42;
```

`NULL` is not a value: `zone = NULL` is never true; use `IS NULL`. `UPDATE … WHERE qty >= 1` with
"rows affected" is your first taste of a conditional update — M2's reservation logic is built on it.

- **Interview angle:** "Difference between `WHERE` and `HAVING`?" (Week 4). "What does `LIMIT` without `ORDER BY` give you?" (undefined order). "What is a `CHECK` constraint for?" (the DB enforces the invariant even if the app is wrong).
- **FlowGrid uses this:** `inventory_level.available >= 0` as a `CHECK`; every list endpoint is `ORDER BY … LIMIT … OFFSET` (then keyset pagination in LedgerX).

### 4.5 HTTP: what a request actually is

```
POST /api/items HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Accept: application/json

{"sku":"BOLT-M6","name":"Bolt M6","qty":7}
```

```
HTTP/1.1 201 Created
Location: /api/items/1
Content-Type: application/json

{"id":1,"sku":"BOLT-M6","name":"Bolt M6","qty":7}
```

`GET`/`PUT`/`DELETE` are idempotent by definition; `POST` is not — which is why M2 adds an
`Idempotency-Key` header. `curl -v` shows all of this; use it every day.

- **Interview angle:** "Which HTTP methods are idempotent and what does that mean for retries?" "`401` vs `403`?" "`400` vs `404` vs `409` vs `422`?"
- **FlowGrid uses this:** every endpoint; `201 + Location` on create; `409 Conflict` on duplicate SKU; `Idempotency-Key` in M2.

### 4.6 Spring Boot: DI, layers, one controller

```java
@RestController
@RequestMapping("/api/items")
class ItemController {
    private final ItemService service;           // constructor injection — no @Autowired needed
    ItemController(ItemService service) { this.service = service; }

    @GetMapping
    List<ItemResponse> list(@RequestParam(defaultValue = "0") int page) { return service.list(page); }

    @PostMapping
    ResponseEntity<ItemResponse> create(@Valid @RequestBody CreateItemRequest req) {
        var created = service.create(req);
        return ResponseEntity.created(URI.create("/api/items/" + created.id())).body(created);
    }
}
record CreateItemRequest(@NotBlank String sku, @NotBlank String name, @Min(0) int qty) {}
```

Controller = HTTP in/out only. Service = rules. Repository = storage (a `ConcurrentHashMap` this
week; JPA next week). The container creates each bean once (singleton scope) and wires constructor
arguments by type.

- **Interview angle:** "What is dependency injection and why constructor injection?" (testable, immutable, fails fast on cycles). "What does `@SpringBootApplication` do?" (`@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`). "How does Spring decide which bean to inject?"
- **FlowGrid uses this:** the same three layers, plus DTOs, for every resource in M1.

### 4.7 A `MockMvc` test per endpoint

```java
@SpringBootTest
@AutoConfigureMockMvc
class ItemControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void createReturns201AndLocation() throws Exception {
        mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON)
                .content("""{"sku":"BOLT-M6","name":"Bolt M6","qty":7}"""))
           .andExpect(status().isCreated())
           .andExpect(header().string("Location", startsWith("/api/items/")))
           .andExpect(jsonPath("$.sku").value("BOLT-M6"));
    }

    @Test
    void createWithNegativeQtyReturns400() throws Exception {
        mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON)
                .content("""{"sku":"BOLT-M6","name":"Bolt M6","qty":-1}"""))
           .andExpect(status().isBadRequest());
    }
}
```

- **Interview angle:** "How do you test a controller without starting a server?" (`MockMvc` drives the `DispatcherServlet` in-process).
- **FlowGrid uses this:** `@WebMvcTest` slices in Week 5; full `@SpringBootTest` + Testcontainers for the concurrency test.

## 5. Resources

- Python: [pytest docs — Getting started](https://docs.pytest.org/en/stable/getting-started.html); [`argparse` tutorial](https://docs.python.org/3/howto/argparse.html); [typing docs](https://docs.python.org/3/library/typing.html); [Requests quickstart](https://requests.readthedocs.io/en/latest/user/quickstart/).
- Java: [java.util.stream package docs](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html); *Effective Java* Items 42–48 (lambdas & streams), 55 (`Optional`); [JEP 395 records](https://openjdk.org/jeps/395), [JEP 409 sealed classes](https://openjdk.org/jeps/409), [JEP 441 pattern matching for switch](https://openjdk.org/jeps/441).
- SQL: [PostgreSQL 16 tutorial (chapter 2)](https://www.postgresql.org/docs/16/tutorial-sql.html); [`psql` docs](https://www.postgresql.org/docs/16/app-psql.html); [Postgres Docker image](https://hub.docker.com/_/postgres).
- HTTP: [MDN — HTTP overview](https://developer.mozilla.org/en-US/docs/Web/HTTP/Overview), [MDN — status codes](https://developer.mozilla.org/en-US/docs/Web/HTTP/Status); [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110).
- Spring: [Spring Boot reference — Developing with Spring Boot](https://docs.spring.io/spring-boot/reference/using/index.html); [Spring Boot testing](https://docs.spring.io/spring-boot/reference/testing/index.html); [Spring Framework — Web MVC](https://docs.spring.io/spring-framework/reference/web/webmvc.html); [start.spring.io](https://start.spring.io/).
- DSA: [`03-dsa/03-two-pointers.md`](../../03-dsa/03-two-pointers.md), [`03-dsa/04-prefix-sums.md`](../../03-dsa/04-prefix-sums.md), NeetCode "Two Pointers".

## 6. Exercises + coding assignments

### 6.1 Tiny Spring Boot API (main assignment, ~12 h, throwaway repo `items-api`)

Generate with Initializr: Maven, Java 21, Spring Boot 3.x, dependencies **Spring Web**,
**Validation**. Resource: `item` (`id`, `sku`, `name`, `qty`). Storage: in-memory `ConcurrentHashMap`
behind an `ItemRepository` interface (so Week 4 swaps in JPA without touching the controller).

**Acceptance criteria**

- [ ] `GET /api/items?page=0&size=20` returns a JSON array sorted by `sku`; `size` capped at 100.
- [ ] `POST /api/items` → `201 Created` + `Location` header + body; duplicate `sku` → `409 Conflict`; invalid body → `400` with a JSON body naming the field (default Spring error body is fine this week; ProblemDetail is Week 4).
- [ ] `sku` normalised (trim + upper-case) in the service, not the controller.
- [ ] ≥ 4 `MockMvc` tests (happy path each endpoint, validation failure, duplicate) + ≥ 3 plain JUnit tests for the service; `mvn verify` green.
- [ ] `application.yml` sets `server.port` and a custom property `items.max-page-size` injected with `@ConfigurationProperties` or `@Value`.
- [ ] README: how to run, two `curl` examples, and a paragraph "what happens between `curl` and my method".

### 6.2 SQL drills (~3 h)

```bash
docker run --name pg-practice -e POSTGRES_PASSWORD=practice -p 5432:5432 -d postgres:16
docker exec -it pg-practice psql -U postgres
```

Load [`04-sql-databases/practice-schema.sql`](../../04-sql-databases/practice-schema.sql) and do
the basics section of [`04-sql-databases/sql-practice.md`](../../04-sql-databases/sql-practice.md).
Then create your own `item` table (§4.4), insert 20 rows from a `COPY`/`\copy` of a CSV, and write
10 queries: filters, `ORDER BY` two columns, `LIMIT/OFFSET` paging, conditional `UPDATE` with
rows-affected check, `DELETE … RETURNING`.

### 6.3 Python script + pytest (~2.5 h)

`tools/lowstock.py` from §4.1, extended: `--format json|csv`, reads from a file *or* from
`http://localhost:8080/api/items` with `requests` when `--url` is given. `pytest` with ≥ 5 tests
(parametrized thresholds, empty file, bad qty raises `ValueError`, JSON output shape). Type hints
everywhere; run `python -m pytest -q`.

### 6.4 Modern-Java drills (~2 h)

[`01-java/exercises.md`](../../01-java/exercises.md) streams/records/`Optional` section; then
rewrite Week 2's `LowStockReport` with streams and compare line counts and readability.

### Break it

- Remove the constructor from `ItemController` and add a second `ItemService` bean. Start the app. Read the `NoUniqueBeanDefinitionException` fully. Fix with `@Primary` or `@Qualifier`, then undo — one implementation is right here.
- Send `POST /api/items` with `Content-Type: text/plain`. Which status? (`415`). Send malformed JSON → `400` with `HttpMessageNotReadableException` in the log. Explain both.
- In SQL: `UPDATE item SET qty = qty - 10 WHERE sku = 'BOLT-M6';` with qty 7. Read the `CHECK` violation. That constraint is the DB defending the invariant your Java forgot.
- Python: call `low_stock(rows, "5")` — no error until comparison. Add a runtime check or `mypy --strict` and see the difference between hints and enforcement.

### Debug it

- Put a breakpoint in `ItemService.create`, run the `MockMvc` test in debug mode, step *into* the repository call and *over* the validation. Use "Evaluate expression" to call `repo.findBySku("BOLT-M6")` mid-flight.
- Make a test fail with a `NullPointerException` from `Optional.get()` on an empty `Optional`; read the helpful NPE message (JEP 358); replace with `orElseThrow`.
- `pytest -x --pdb` on a failing test: inspect `rows` in the debugger prompt, `q` to quit.

## 7. DSA — Two Pointers + Prefix Sums (8 new, in Python)

Guides: [`03-dsa/03-two-pointers.md`](../../03-dsa/03-two-pointers.md), [`03-dsa/04-prefix-sums.md`](../../03-dsa/04-prefix-sums.md); templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md).
Limits: Easy 20 min, Medium 35 min.

| Day | Problem | Difficulty | Limit | Focus |
|---|---|---|---|---|
| Mon | [125. Valid Palindrome](https://leetcode.com/problems/valid-palindrome/) | Easy | 20 min | `str.isalnum`, converging pointers |
| Mon | [167. Two Sum II - Input Array Is Sorted](https://leetcode.com/problems/two-sum-ii-input-array-is-sorted/) | Medium | 25 min | why sortedness lets pointers replace the dict |
| Tue | [15. 3Sum](https://leetcode.com/problems/3sum/) | Medium | 35 min | sort + fix one + two pointers; skip duplicates |
| Wed | [11. Container With Most Water](https://leetcode.com/problems/container-with-most-water/) | Medium | 30 min | move the shorter side — argue why |
| Wed | [1480. Running Sum of 1d Array](https://leetcode.com/problems/running-sum-of-1d-array/) | Easy | 10 min | `itertools.accumulate` and by hand |
| Thu | [724. Find Pivot Index](https://leetcode.com/problems/find-pivot-index/) | Easy | 20 min | total − left − nums[i] |
| Thu | [303. Range Sum Query - Immutable](https://leetcode.com/problems/range-sum-query-immutable/) | Easy | 20 min | prefix array with leading 0; class design |
| Sat | [560. Subarray Sum Equals K](https://leetcode.com/problems/subarray-sum-equals-k/) | Medium | 35 min | prefix sum + `Counter` of seen sums |

**Java rep (Sat, 30 min):** re-do **560. Subarray Sum Equals K** in Java with `HashMap<Integer,Integer>` + `getOrDefault`/`merge`.

**Spaced reviews due:** Day-14 of Week 1's problems — Reverse String, Remove Element (Mon), Remove
Duplicates from Sorted Array (Tue), Move Zeroes (Wed), Max Consecutive Ones, Best Time to Buy and
Sell Stock (Thu). Day-7 of Week 2's — Contains Duplicate, Valid Anagram (Mon), Two Sum, Group
Anagrams (Tue), Top K Frequent Elements (Wed), Product of Array Except Self, Valid Sudoku (Thu),
Longest Consecutive Sequence (Sat). Day-3 of this week's — Valid Palindrome, Two Sum II (Thu), 3Sum
(Fri), Container With Most Water, Running Sum (Sat), Find Pivot Index, Range Sum Query (Sun).
Reviews are ≤ half the original limit, from blank, in Python.

## 8. Project work — foundation: the tiny Spring Boot API

Task checklist (the API is throwaway, the *habits* are not):

- [ ] Mon: Initializr project, run it, `GET /api/items` returns `[]`; commit "chore: initial spring boot app".
- [ ] Tue: `Item` record, `ItemRepository` interface + in-memory impl, `ItemService`, `POST` with validation; first `MockMvc` tests.
- [ ] Wed: paging + sorting on `GET`, `409` on duplicate, `application.yml` property; service unit tests.
- [ ] Thu: SQL drills against Postgres in Docker; README with `curl` examples; Python `lowstock.py` reading the API.
- [ ] Fri: Break-it + Debug-it; `mvn verify` green; pytest green.
- [ ] Sat: read [`18-projects/flowgrid/README.md`](../../18-projects/flowgrid/README.md) and [`18-projects/flowgrid/milestones.md`](../../18-projects/flowgrid/milestones.md) M1 in full; write 10 questions you have; sketch the M1 entity list on paper.

## 9. Git activity

- `items-api` repo with branches per task (`feat/post-item`, `feat/paging`, `test/mockmvc`); merge with `--no-ff`; tag `foundation-done` at the end.
- Add `.gitignore` from Initializr plus Python entries.
- First **pull request to yourself**: open a PR from `feat/paging`, write a description (what/why/how tested), review your own diff line by line, merge via GitHub. PR discipline starts here and never stops.
- Conventional Commits with scope: `feat(api): POST /api/items with validation`, `test(api): 409 on duplicate sku`, `feat(tools): lowstock script with pytest`.

## 10. Interview preparation

Weeks 1–4 ramp: explain each Python DSA solution out loud. This week add: after every solve, name
the *pattern* and the *signal* that told you to use it ("sorted input → two pointers"; "subarray
sum → prefix sums + hash map").

Questions to answer out loud:

Track A (Python): 1. When do two pointers beat a hash map, and when not? 2. What is a prefix sum and how does it turn range queries into O(1)? 3. How do you avoid duplicate triplets in 3Sum? 4. How would you test a Python function with several inputs in `pytest`?
Track B (Java/Spring/SQL/HTTP): 5. What is dependency injection; why constructor injection? 6. What does `@SpringBootApplication` expand to? 7. `map` vs `flatMap`; are streams lazy? 8. Which HTTP methods are idempotent and why does it matter for retries? 9. What does a `CHECK` constraint give you that application validation does not?

## 11. Revision work

- Redo Week 2's `equals`/`hashCode` explanation out loud; then explain why JPA entities (next week) must be careful with them.
- Rewrite the HTTP request/response pair in §4.5 from memory, including headers.
- Re-solve Week 1's Kata 4 (SKU counts) as a single SQL `GROUP BY` query (preview of Week 4) and as a Python `Counter` one-liner.
- Update [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md): Python (testing/scripting), Java (modern), SQL basics, HTTP, Spring Boot intro.

## 12. Daily plan

| Day | Blocks |
|---|---|
| **Mon** (8 h) | Learning 3.5: HTTP anatomy + `curl -v`; Spring Boot intro (DI, layers); Initializr · Project 2.5: app runs, `GET /api/items` · DSA 2: 125, 167 + Day-14 (Reverse String, Remove Element) + Day-7 (Contains Duplicate, Valid Anagram) |
| **Tue** (8 h) | Learning 2: lambdas, streams, records, `Optional` · Project 3.5: `POST` + validation + first `MockMvc` tests · DSA 2: 15 + Day-14 (Remove Duplicates) + Day-7 (Two Sum, Group Anagrams) · Interview 0.5: explain out loud |
| **Wed** (8 h) | Learning 3: SQL basics; start Postgres in Docker; `psql` · Project 3: paging/sorting, `409`, config property · DSA 2: 11, 1480 + Day-14 (Move Zeroes) + Day-7 (Top K Frequent) |
| **Thu** (8 h) | Learning 2.5: Python pytest, type hints, `argparse`, `requests`; IDE debugger · Project 3: SQL drills; `lowstock.py` + tests · DSA 2: 724, 303 + Day-14 (Max Consecutive Ones, Best Time) + Day-7 (Product Except Self, Valid Sudoku) + Day-3 (125, 167) · Docs 0.5: README |
| **Fri** (5 h) | Project 2.5: Break-it + Debug-it; pull request to yourself · DSA 1.5: Day-3 (15) · Retro 1 |
| **Sat** (6 h) | Project 3: read FlowGrid spec + M1; entity sketch; questions list · DSA 1.5: 560 + Day-7 (Longest Consecutive) + Day-3 (11, 1480) + **Java rep** (560) · Interview 1.5: record 2 explanations; "about me" v2 |
| **Sun** (2–3 h) | End-of-week test (§13) · Day-3 (724, 303) · trackers · plan Week 4 (create the FlowGrid GitHub repo + milestone M1 skeleton) · rest |

## 13. End-of-week test (100 min, timed)

**Part A — DSA in Python (40 min).** [167. Two Sum II](https://leetcode.com/problems/two-sum-ii-input-array-is-sorted/)
and [560. Subarray Sum Equals K](https://leetcode.com/problems/subarray-sum-equals-k/) from blank; complexities out loud.

**Part B — Concepts (25 min).**

<details>
<summary>1. Why does <code>WHERE zone = NULL</code> return no rows even when some zones are NULL?</summary>

Comparison with `NULL` yields `NULL` (unknown), which `WHERE` treats as false. Use `IS NULL`.
</details>

<details>
<summary>2. <code>POST /api/items</code> succeeded but the client timed out and retried. What happens with your Week 3 API? What will Week 5 change?</summary>

Second `POST` → `409 Conflict` on the duplicate SKU here (a lucky natural key). For orders there
is no natural key, so Week 5 adds an `Idempotency-Key` header: same key → the stored response is
replayed instead of creating a second order.
</details>

<details>
<summary>3. What is the difference between <code>@SpringBootTest</code> with <code>MockMvc</code> and a test that starts the server on a random port?</summary>

`MockMvc` calls the `DispatcherServlet` in-process — no network, no servlet container, faster.
`webEnvironment = RANDOM_PORT` + `TestRestTemplate` goes through a real HTTP stack — slower, but
tests filters/serialization end to end.
</details>

<details>
<summary>4. <code>items.stream().filter(...).map(...)</code> with no terminal operation — what runs?</summary>

Nothing. Streams are lazy; intermediate operations are only executed when a terminal operation
(`collect`, `toList`, `forEach`, `findFirst`…) pulls elements.
</details>

<details>
<summary>5. Python: why do we write <code>def low_stock(rows: list[dict[str, str]], threshold: int)</code> if Python does not enforce types?</summary>

Hints document intent, enable editor completion and `mypy` static checks, and make tests/reviews
easier. Enforcement is optional (via `mypy`/`pyright`), not runtime.
</details>

<details>
<summary>6. What does "constructor injection" protect you from that field injection does not?</summary>

Missing dependencies fail at construction (not as a later NPE), the field can be `final`, the
class is trivially constructible in a plain unit test without Spring, and circular dependencies
surface immediately.
</details>

<details>
<summary>7. Two pointers: why is moving the shorter side correct in Container With Most Water?</summary>

Area is limited by the shorter line; moving the taller one can never increase height and always
reduces width, so no larger area is skipped by moving the shorter side.
</details>

**Part C — Practical (30 min).** Add `GET /api/items/{id}` to the API returning `404` for a missing id
and `200` with the item otherwise, with two `MockMvc` tests; then write the SQL for the same lookup
and a Python `requests` one-liner that calls it. `mvn verify` green, pushed.

**Part D — Explain out loud (5 min).** "Trace a `POST /api/items` from `curl` to the `HashMap` and back, naming every Spring piece it passes."

## 14. Mastery checklist

- [ ] I can create a Spring Boot 3 app from Initializr and add a validated `POST` + paged `GET` with `MockMvc` tests without a tutorial.
- [ ] I can explain DI, bean scopes (singleton default), constructor injection and the three-layer split.
- [ ] I can write `SELECT/INSERT/UPDATE/DELETE` with `WHERE/ORDER BY/LIMIT/RETURNING` in `psql` against Postgres in Docker.
- [ ] I can read an HTTP request/response and choose the right status code.
- [ ] I use streams, records and `Optional` correctly and the debugger instead of `println`.
- [ ] I can write a typed Python script with `argparse` and ≥ 5 `pytest` tests.
- [ ] The 8 two-pointer/prefix-sum problems are re-solvable in Python within limits; Java rep done.

## 15. Expected deliverables

- `items-api` repo: 2 endpoints (+ the test's third), ≥ 7 tests green, one self-reviewed PR, tag `foundation-done`, `tools/lowstock.py` + pytest.
- SQL drill file `sql/week3.sql` with 10 queries and their results as comments.
- [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md): 22 problems total (Python), 3 Java reps, all reviews logged.
- [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md): Week 3 entry.
- [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md): Python, Java, SQL, HTTP/REST, Spring Boot, Docker (run only) rows updated.
- [`trackers/project-tracker.md`](../../trackers/project-tracker.md): Foundation complete; FlowGrid row created with M1 dates.

## 16. If you're behind / stretch

**Behind:** the Spring Boot API with `POST` + `GET` and their `MockMvc` tests is non-negotiable —
Week 4 depends on it. Cut the SQL drills to 5 queries and the Python script to the file-only path.
Keep all reviews; drop 560 (do it as Week 4's Java rep instead).

**Stretch:** replace the `ConcurrentHashMap` with `JdbcTemplate` against your Postgres container
(read [`04-sql-databases/08-jdbc-orm.md`](../../04-sql-databases/08-jdbc-orm.md) first half) to
see the SQL before JPA hides it; add `@ControllerAdvice` returning `ProblemDetail` early; solve
[42. Trapping Rain Water](https://leetcode.com/problems/trapping-rain-water/) in Python.

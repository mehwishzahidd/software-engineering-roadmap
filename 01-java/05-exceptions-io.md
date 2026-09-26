# 05 — Exceptions & I/O (Week 2 exceptions · Week 3 I/O)

> **Outcome:** design an exception strategy (what to throw, what to catch, where to translate);
> always release resources with try-with-resources; read and write files with `java.nio.file`;
> import a messy CSV reporting errors **per row** instead of dying on line 1 (Week 3 kata; the
> same idea returns in FlowGrid bulk stock imports and LedgerX reconciliation reports).

Related: [README](./README.md) · prev [04-modern-java.md](./04-modern-java.md) · next [06-memory-jvm.md](./06-memory-jvm.md) · reading stack traces: [09-debugging-java.md](./09-debugging-java.md)

---

## Part A — Exceptions (Week 2)

### 1. The hierarchy

```
Throwable
 ├─ Error                      (JVM-level; don't catch: OutOfMemoryError, StackOverflowError)
 └─ Exception                  ← CHECKED (compiler forces catch or `throws`)
     ├─ IOException, SQLException, InterruptedException, ...
     └─ RuntimeException       ← UNCHECKED
         ├─ NullPointerException, IllegalArgumentException, IllegalStateException
         ├─ IndexOutOfBoundsException, ArithmeticException, ClassCastException
         ├─ UnsupportedOperationException, ConcurrentModificationException
         └─ NumberFormatException (extends IllegalArgumentException), DateTimeParseException
```

| | Checked | Unchecked |
|---|---|---|
| Superclass | `Exception` (not `RuntimeException`) | `RuntimeException` or `Error` |
| Compiler | Must catch or declare `throws` | No requirement |
| Meaning | Recoverable condition outside your control (file missing, network down) | Programming error or violated precondition (null arg, bad index) |
| Modern practice | Used at I/O boundaries; often wrapped | Default for application/domain exceptions (Spring's `DataAccessException` is unchecked) |

### 2. throw, catch, finally

```java
public static Money parseAmount(String raw) {
    if (raw == null || raw.isBlank()) throw new IllegalArgumentException("amount is blank");
    try {
        return Money.of(new BigDecimal(raw.strip().replace(",", "")));
    } catch (NumberFormatException e) {
        throw new IllegalArgumentException("not a number: '" + raw + "'", e);  // keep the cause!
    }
}
```

- Catch **specific** exceptions. `catch (Exception e)` hides bugs.
- Multi-catch: `catch (IOException | SQLException e)`.
- Order matters: subclass catches before superclass (compiler enforces).
- `finally` runs whether or not an exception occurred (and even on `return`). Don't `return` from `finally` — it swallows the exception.
- **Always pass the cause** when wrapping (`new X(msg, e)`); otherwise the stack trace loses the root.

### 3. Custom exceptions

```java
public class CsvFormatException extends RuntimeException {
    private final int lineNumber;
    public CsvFormatException(int lineNumber, String message, Throwable cause) {
        super("line " + lineNumber + ": " + message, cause);
        this.lineNumber = lineNumber;
    }
    public int lineNumber() { return lineNumber; }
}

// FlowGrid M2 — domain exception later mapped to HTTP 409 by @RestControllerAdvice → ProblemDetail
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String sku, long warehouseId, int requested, int available) {
        super("SKU " + sku + " in warehouse " + warehouseId + ": requested " + requested + ", available " + available);
    }
}
```
Guidelines (*Effective Java* 69–77): use exceptions only for exceptional conditions; prefer
standard exceptions (`IllegalArgumentException`, `IllegalStateException`,
`UnsupportedOperationException`, `NoSuchElementException`); throw exceptions appropriate to the
abstraction (translate `SQLException` → `RepositoryException` at the repository boundary);
include failure-capture info in the message (the bad value, the id); don't ignore exceptions.

### 4. Exception translation at layer boundaries

```java
public final class JdbcTransactionRepository implements TransactionRepository {
    @Override public Optional<Transaction> findById(long id) {
        String sql = "SELECT id, date, merchant, amount_cents, category FROM transactions WHERE id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("findById failed for id=" + id, e);   // unchecked
        }
    }
}
```
Callers (services, CLI) no longer depend on JDBC types. This is exactly what Spring does for you with `DataAccessException`.

### 5. `InterruptedException` — never swallow it

```java
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();   // restore the flag so callers/executors see it
    return;                               // or throw a runtime exception
}
```
Details in [07-concurrency.md](./07-concurrency.md).

---

## Part B — Resources & I/O (Week 3)

### 6. try-with-resources

Any `AutoCloseable` declared in the `try (...)` header is closed automatically, **in reverse
order of declaration**, even if the body throws.

```java
try (BufferedReader in = Files.newBufferedReader(src, StandardCharsets.UTF_8);
     BufferedWriter out = Files.newBufferedWriter(dst, StandardCharsets.UTF_8)) {
    String line;
    while ((line = in.readLine()) != null) {
        out.write(line.toUpperCase());
        out.newLine();
    }
}   // out.close() then in.close()
```

**Under the hood:** the compiler expands it into try/finally with careful handling: if the body
throws exception A and then `close()` throws B, **A propagates and B is attached as a
suppressed exception** (`A.getSuppressed()`). The old manual `finally { in.close(); }` pattern
would lose A and throw B — hiding the real error.

```java
class Noisy implements AutoCloseable {
    @Override public void close() { throw new IllegalStateException("close failed"); }
}
try (Noisy n = new Noisy()) {
    throw new RuntimeException("body failed");
} catch (RuntimeException e) {
    System.out.println(e.getMessage());                     // body failed
    System.out.println(e.getSuppressed()[0].getMessage());  // close failed
}
```

Use it for: files, sockets, JDBC `Connection`/`PreparedStatement`/`ResultSet`, `ExecutorService`
(Java 19+ it's `AutoCloseable`), `Stream`s from `Files.lines`/`Files.list`.

### 7. `java.nio.file` — the modern file API

```java
Path dir = Path.of("data");
Path file = dir.resolve("transactions.csv");      // data/transactions.csv

Files.createDirectories(dir);                     // no error if exists
Files.writeString(file, "date,merchant,amount\n", StandardCharsets.UTF_8);
Files.write(file, List.of("2025-03-01,Coffee,-3.50"), StandardOpenOption.APPEND);

String all = Files.readString(file);              // small files only
List<String> lines = Files.readAllLines(file);    // small files only
try (Stream<String> s = Files.lines(file)) {      // lazy — MUST close (holds a file handle)
    long n = s.skip(1).filter(l -> !l.isBlank()).count();
}
boolean exists = Files.exists(file);
long size = Files.size(file);
Files.copy(file, dir.resolve("backup.csv"), StandardCopyOption.REPLACE_EXISTING);
Files.move(file, dir.resolve("archived.csv"), StandardCopyOption.ATOMIC_MOVE);
try (Stream<Path> entries = Files.list(dir)) { entries.forEach(System.out::println); }
Files.deleteIfExists(dir.resolve("backup.csv"));
```

- **Always specify the charset** (`UTF_8`); Java 18+ defaults to UTF-8, but explicit beats implicit.
- `Path` is immutable; `resolve`, `normalize`, `getFileName`, `getParent`, `toAbsolutePath`.
- Byte streams (`InputStream`/`OutputStream`) for binary; character readers/writers for text. Buffered wrappers reduce syscalls.
- Relative paths resolve against the **working directory** (`System.getProperty("user.dir")`) — a common "file not found" cause when running from an IDE vs terminal.

### 8. Kata: CSV import with per-row error reporting

```java
public record ImportError(int line, String raw, String reason) {}
public record ImportReport(List<Transaction> imported, List<ImportError> errors) {}

public final class CsvImporter {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    public ImportReport importFile(Path path) throws IOException {
        List<Transaction> ok = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = r.readLine();
            if (header == null) return new ImportReport(ok, List.of(new ImportError(1, "", "empty file")));
            String line;
            int lineNo = 1;
            while ((line = r.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;
                try {
                    ok.add(parseLine(line));
                } catch (IllegalArgumentException | DateTimeParseException e) {  // NumberFormatException is an IAE
                    errors.add(new ImportError(lineNo, line, e.getMessage()));
                }
            }
        }
        return new ImportReport(List.copyOf(ok), List.copyOf(errors));
    }

    static Transaction parseLine(String line) {
        String[] cols = line.split(",", -1);   // -1 keeps trailing empty columns
        if (cols.length != 3) throw new IllegalArgumentException("expected 3 columns, got " + cols.length);
        LocalDate date = LocalDate.parse(cols[0].strip(), DATE);
        String merchant = cols[1].strip();
        if (merchant.isEmpty()) throw new IllegalArgumentException("merchant is empty");
        BigDecimal amount = new BigDecimal(cols[2].strip());
        return new Transaction(date, merchant, amount);
    }
}
```
Design notes to explain in interviews:
- One bad row doesn't abort the import; the user gets a precise report (line number + reason).
- `IOException` (can't read the file at all) is a different class of failure from a bad row — it propagates.
- Naïve `split(",")` breaks on quoted fields (`"Smith, J"`). Mention it; for real bank files use a library (OpenCSV / Apache Commons CSV) or write a small quote-aware parser (exercise).

### 9. Checked exceptions inside lambdas

```java
// ❌ Files.readString throws IOException — doesn't fit Function<Path,String>
paths.stream().map(Files::readString).toList();

// ✅ Wrap at the boundary with UncheckedIOException (made for this)
paths.stream().map(p -> {
    try { return Files.readString(p); }
    catch (IOException e) { throw new UncheckedIOException(e); }
}).toList();
```

---

## 🔨 Break it

1. Replace try-with-resources with manual `finally { reader.close(); }` in a method whose body throws, and whose close throws too → which exception do you see? Now switch back and print `getSuppressed()`.
2. `Files.lines(path)` without closing inside a loop of 100 000 iterations → "Too many open files" (Linux).
3. Catch `Exception` around your CSV parser and log nothing → plant a bug (`cols[3]`) and try to find it.
4. Wrap without the cause (`new RuntimeException("failed")`) → compare stack traces with and without `e`.
5. `return` from `finally` after a `throw` in `try` → exception disappears.
6. Recursive method without base case → `StackOverflowError`; read the trace depth.
7. Run your importer from the IDE with a relative path, then from the terminal in another folder.

## ⚠️ Common mistakes

- Swallowing: `catch (Exception e) {}` or `e.printStackTrace()` in production code (use a logger).
- Losing the cause when wrapping.
- Using exceptions for normal control flow (e.g. `Integer.parseInt` in a loop to test "is numeric" on hot paths).
- Declaring `throws Exception` everywhere.
- Catching `Throwable`/`Error`.
- Forgetting charset; forgetting to close `Files.lines` / `Files.list` streams.
- Logging **and** rethrowing at every layer → the same error appears 5 times in logs. Log once, at the boundary that handles it.

## 🎤 Interview questions

<details><summary>1. Checked vs unchecked exceptions — and which do you prefer?</summary>

Checked extend `Exception` (not `RuntimeException`) and must be caught or declared; unchecked
extend `RuntimeException` and don't. Checked for recoverable external failures at API
boundaries; unchecked for programming errors and most application/domain errors. Modern
frameworks (Spring) favour unchecked and translate checked ones (`SQLException` → `DataAccessException`).
</details>

<details><summary>2. What does try-with-resources do, and what are suppressed exceptions?</summary>

Closes every declared `AutoCloseable` in reverse order after the block, even on exceptions.
If both the body and `close()` throw, the body's exception propagates and close's is attached
via `addSuppressed` — so the primary error is never masked.
</details>

<details><summary>3. Does finally always run?</summary>

Yes for normal completion, exceptions and `return`/`break`/`continue` — except if the JVM
exits (`System.exit`), the process is killed, or the thread never finishes (infinite loop / deadlock).
</details>

<details><summary>4. Error vs Exception?</summary>

`Error` = serious JVM/environment problems the application shouldn't try to handle
(`OutOfMemoryError`, `StackOverflowError`, `NoClassDefFoundError`). `Exception` = conditions an
application might catch.
</details>

<details><summary>5. How do you design exceptions in a layered application?</summary>

Throw domain-specific unchecked exceptions from the service layer (`InsufficientStockException`),
translate infrastructure exceptions at their boundary (repository), and convert to a response in
one place (`@RestControllerAdvice` → ProblemDetail with 404/409/422). Log once, keep causes.
</details>

<details><summary>6. What's the correct way to handle InterruptedException?</summary>

Either propagate it, or restore the interrupt flag with `Thread.currentThread().interrupt()` and
exit the task. Swallowing it breaks cancellation (e.g. `ExecutorService.shutdownNow`).
</details>

<details><summary>7. NoClassDefFoundError vs ClassNotFoundException?</summary>

`ClassNotFoundException` (checked) — explicit loading by name failed (`Class.forName`).
`NoClassDefFoundError` (Error) — a class present at compile time is missing at runtime, or its
static initializer failed earlier. Usually a classpath/dependency problem (see Maven dependency conflicts).
</details>

## ✅ Mastery checklist

- [ ] Draw the exception hierarchy and classify 10 common exceptions as checked/unchecked
- [ ] Write a custom exception that carries context and a cause
- [ ] Translate a checked exception at a repository boundary
- [ ] Demonstrate suppressed exceptions with try-with-resources
- [ ] Read/write/list files with `java.nio.file`, always with a charset and closed streams
- [ ] Implement CSV import with per-row error reporting and tests for bad rows
- [ ] Handle `InterruptedException` correctly
- [ ] Complete the exceptions/I-O exercises in [exercises.md](./exercises.md)

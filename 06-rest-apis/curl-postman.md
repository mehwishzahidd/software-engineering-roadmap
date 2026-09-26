# curl, HTTPie, and Postman

> **Week 3** onward. Every endpoint you build gets a curl example in the project README and a request in a Postman
> collection. Being fluent with `curl -v` is also an interview signal ("how would you debug this API?").

## 1. curl essentials

| Flag | Does | Example |
|---|---|---|
| `-v` | verbose: request line, headers sent (`>`), headers received (`<`), TLS info | `curl -v http://localhost:8080/actuator/health` |
| `-i` | include response headers in output | `curl -i localhost:8080/api/v1/venues/1` |
| `-s` / `-S` | silent (no progress bar) / still show errors | `curl -sS …` |
| `-X METHOD` | set the method (implied POST by `-d`; don't use `-X GET` with `-d`) | `curl -X DELETE …` |
| `-H 'Name: value'` | add a header | `-H 'Accept: application/json'` |
| `-d DATA` / `--data-raw` | request body (implies POST; `@file` reads a file with `-d`) | `-d @order.json` |
| `--json DATA` | curl ≥ 7.82: sets body + `Content-Type: application/json` + `Accept: application/json` | `--json '{"name":"x"}'` |
| `-u user:pass` | HTTP Basic | `-u admin:secret` |
| `-o FILE` / `-O` | write body to a file | `-o export.csv` |
| `-w FORMAT` | print variables after the transfer | `-w '%{http_code} %{time_total}s\n'` |
| `-L` | follow redirects | |
| `-f` / `--fail-with-body` | non-zero exit code on HTTP ≥ 400 (scripts, CI smoke tests) | |
| `--max-time N` | total timeout (seconds) | `--max-time 5` |
| `--retry N` | retry transient failures | `--retry 3` |
| `-N` | no buffering: needed to watch **SSE** streams | `curl -N -H 'Accept: text/event-stream' …` |
| `-F` | multipart form upload | `-F file=@stock.csv` |
| `-k` | skip TLS verification: **local debugging only, never in scripts or prod** | |

## 2. A full session against your API

```bash
BASE=http://localhost:8080/api/v1

# log in, extract the token with jq
TOKEN=$(curl -sS --json '{"email":"ops@example.com","password":"correct horse"}' \
        "$BASE/auth/login" | jq -r '.accessToken')

# authenticated GET, show status + timing
curl -sS -H "Authorization: Bearer $TOKEN" \
     -w '\nstatus=%{http_code} time=%{time_total}s\n' \
     "$BASE/warehouses?page=0&size=5&sort=name,asc"

# create, see the Location header
curl -i -sS -H "Authorization: Bearer $TOKEN" \
     --json '{"code":"TOR-1","name":"Toronto DC","region":"CA-ON"}' \
     "$BASE/warehouses"

# idempotent retry: send the SAME key twice, expect one order + identical responses
KEY=$(uuidgen)
for i in 1 2; do
  curl -sS -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $KEY" \
       --json @order.json -w ' -> %{http_code}\n' "$BASE/orders"
done

# conditional GET with ETag
ETAG=$(curl -sS -D - -o /dev/null -H "Authorization: Bearer $TOKEN" "$BASE/skus/1042" \
       | awk 'tolower($1)=="etag:"{print $2}' | tr -d '\r')
curl -sS -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
     -H "If-None-Match: $ETAG" "$BASE/skus/1042"          # expect 304

# watch a live SSE stream (ForgeCI M3)
curl -N -H "Authorization: Bearer $TOKEN" -H 'Accept: text/event-stream' "$BASE/builds/77/logs/stream"
```

(The paths and payloads illustrate the conventions in [`api-design-guide.md`](./api-design-guide.md). Your own API defines the real ones.)

**Concurrency smoke test from the shell** (the real proof is the JUnit concurrency test):

```bash
# 20 different orders racing for the last unit (each request gets its own key)
seq 1 20 | xargs -P 20 -I{} sh -c 'curl -sS -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer $0" -H "Idempotency-Key: $(uuidgen)" --json @last-unit-order.json "$1/orders"' "$TOKEN" "$BASE" \
  | sort | uniq -c        # expect: 1 x 201, 19 x 409 (insufficient stock)
```

`sh -c` runs `uuidgen` once **per request**. Now repeat with one fixed key for all 20 (the same order retried 20 times),
and predict the mix of 201 (original or replayed) and 409 (still in progress) before you run it.

### Reading `curl -v`

```
*   Trying 127.0.0.1:8080...
* Connected to localhost (127.0.0.1) port 8080
> POST /api/v1/orders HTTP/1.1          <- what you SENT
> Host: localhost:8080
> Content-Type: application/json
> Authorization: Bearer eyJ…
>
< HTTP/1.1 409                          <- what you GOT
< Content-Type: application/problem+json
< X-Request-Id: 6f1c2a9e-…               <- grep the server logs for this
```

Debugging order: did the request go where I think (host/port/path)? Did I send the headers I think? What status and
content type came back? Then look up the `X-Request-Id` in the server logs.

## 3. HTTPie (friendlier syntax)

```bash
http :8080/api/v1/warehouses "Authorization:Bearer $TOKEN"                       # GET, localhost shorthand
http POST :8080/api/v1/warehouses "Authorization:Bearer $TOKEN" \
     code=TOR-1 name="Toronto DC" capacity:=12000                                 # = string, := raw JSON (number)
http -v PATCH :8080/api/v1/skus/1042 "If-Match:\"7\"" active:=false
http --check-status --timeout=5 :8080/actuator/health                            # non-zero exit on 4xx/5xx
```

Pretty-printed, colored JSON by default. It's great for exploring; curl is what's installed on every server and CI runner.

## 4. Postman

### Collections and environments

- A **collection** = folders of requests (e.g. `Auth`, `Warehouses`, `Orders`). Commit its export to the project repo (`/postman/flowgrid.postman_collection.json`).
- An **environment** = variables per target: `baseUrl` = `http://localhost:8080/api/v1` (local) or `https://…` (AWS). Use `{{baseUrl}}/warehouses` in requests.
- **Secrets:** keep tokens and passwords in *current values* (local only), never in *initial values* (exported and shared). Don't commit environments containing secrets.
- Collection-level auth: Bearer Token `{{accessToken}}`, inherited by all requests.

### Scripts: capture a token, assert responses

Login request → **Tests** (post-response script):

```javascript
pm.test("login succeeds", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.environment.set("accessToken", body.accessToken);      // every later request uses {{accessToken}}
```

Create request → **Tests**:

```javascript
pm.test("201 with Location", () => {
  pm.response.to.have.status(201);
  pm.expect(pm.response.headers.get("Location")).to.match(/\/warehouses\/\d+$/);
});
pm.test("body shape", () => {
  const w = pm.response.json();
  pm.expect(w).to.have.property("id");
  pm.expect(w.code).to.eql("TOR-1");
});
pm.collectionVariables.set("warehouseId", pm.response.json().id);
```

Idempotency request → **Pre-request** script:

```javascript
pm.variables.set("idemKey", crypto.randomUUID());   // header: Idempotency-Key: {{idemKey}}
```

(`crypto.randomUUID()` is available in current Postman script sandboxes. In older ones, use the dynamic variable `{{$guid}}`.)

Error-path tests matter as much as happy paths: assert `Content-Type` is `application/problem+json` and that `type` has the expected value.

### Running collections

- **Collection Runner** in the app: run all requests in order, with data files (CSV/JSON) for parameterized runs.
- **Newman** (CLI) runs the same collection in CI against a Compose stack:

```bash
npm install -g newman
newman run postman/flowgrid.postman_collection.json -e postman/local.postman_environment.json --bail
```

- Postman is a **smoke/contract** layer. Your real test suite stays in JUnit (`@WebMvcTest`, Testcontainers); see [`../09-testing/README.md`](../09-testing/README.md).
- Git-friendly alternatives exist (e.g., Bruno stores requests as plain files, and IntelliJ HTTP Client uses `.http` files). Any is fine; being consistent matters more.

## 5. 🔨 Break it

1. `curl -d '{"name":"x"}' $BASE/warehouses` (no `Content-Type`). What status? Now with `--json`.
2. `curl -X GET -d '{}' …`. What does the server do with a GET body?
3. Send a valid request with `Authorization: bearer` (lowercase) and with `Bearer` followed by two spaces. Does your filter cope?
4. Use `-w '%{time_total}'` on a list endpoint with page size 20 and then 1000 (if you allow it). Should you allow it?
5. Export a Postman environment with a token in the *initial value* and read the JSON file. That's what would have been committed.

## 6. 🎤 Interview Q&A

<details><summary>How would you debug an API call that "doesn't work"?</summary>

Reproduce it with `curl -v` to see exactly what's sent and received (URL, method, headers, body, status, content type). Check auth (401 vs 403), content negotiation (415/406), and validation errors in the ProblemDetail. Then take the X-Request-Id to the server logs, check the application logs and SQL at that timestamp, and reproduce in a test.
</details>

<details><summary>How do you test APIs beyond unit tests?</summary>

Controller slice tests (@WebMvcTest), integration tests with Testcontainers, contract/smoke tests via a Postman collection run by Newman in CI, and load tests (k6) for performance baselines.
</details>

<details><summary>What does `curl -i` vs `-v` show?</summary>

-i prints response headers with the body. -v also shows the request line and headers sent, connection and TLS details. Use -v when you suspect the request itself.
</details>

## ✅ Mastery checklist

- [ ] Can log in, capture a token and call a protected endpoint with curl + jq from memory
- [ ] Every FlowGrid endpoint has a curl example in the README and a request with tests in the Postman collection
- [ ] Newman runs the collection in CI against the Compose stack (FlowGrid M5)
- [ ] Watched an SSE stream with `curl -N` (ForgeCI M3)

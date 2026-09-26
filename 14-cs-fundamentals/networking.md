# Networking (Week 9)

> **Practical use:** understand every hop between a button click in TeamBoard and a row in Postgres;
> debug "connection refused" vs "timeout" vs "CORS error"; configure security groups, DNS and TLS for P4.
> **Interview use:** "What happens when you type a URL?", TCP vs UDP, DNS, HTTPS, L4 vs L7 load balancers, CORS.

This file owns TCP/IP/DNS/TLS. HTTP **semantics for API design** (methods, status codes, headers,
idempotency) live in [`06-rest-apis/http-for-apis.md`](../06-rest-apis/http-for-apis.md).

---

## 1. Layer models

| OSI (7) | TCP/IP (4) | Unit | Examples | You debug it with |
|---|---|---|---|---|
| 7 Application | Application | message | HTTP, DNS, TLS\*, SMTP, SSH, PostgreSQL wire protocol | `curl -v`, browser DevTools |
| 6 Presentation | ″ | | encoding, (TLS often placed here) | |
| 5 Session | ″ | | | |
| 4 Transport | Transport | segment/datagram | **TCP, UDP** | `ss -tan`, `nc -zv host port` |
| 3 Network | Internet | packet | **IP**, ICMP | `ping`, `traceroute`, `ip route` |
| 2 Data link | Link | frame | Ethernet, Wi-Fi, ARP | (rarely, as a SWE) |
| 1 Physical | ″ | bits | cables, radio | |

Each layer **encapsulates** the one above: HTTP message → inside TCP segment → inside IP packet →
inside Ethernet frame. Interviews want the TCP/IP model and the ability to say which layer a thing
lives at (e.g. "an ALB is layer 7, an NLB is layer 4").

---

## 2. IP addressing

- **IPv4:** 32 bits, dotted quad `203.0.113.10`. **IPv6:** 128 bits, `2001:db8::1`.
- **CIDR:** `10.0.1.0/24` = first 24 bits fixed → 256 addresses. `/16` = 65,536. `/32` = one address (SG rule for "my IP").
- **Private ranges** (not routable on the internet): `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`. Your VPC and home network use these.
- **Loopback:** `127.0.0.1` / `::1` = this machine. **Inside a container, `localhost` is the container itself** — the #1 Docker networking confusion.
- `0.0.0.0` as a **bind** address = "all interfaces". A server bound to `127.0.0.1` is unreachable from outside its host/container.
- **Routing:** each host sends packets to the next hop per its routing table; routers forward hop by hop. `traceroute` shows the hops.

### NAT (Network Address Translation)

Rewrites addresses at a boundary so many private hosts share one public IP. Your home router does
it; so does an AWS NAT Gateway (outbound for private subnets) and Docker (container → host).
Outbound connections work; unsolicited inbound connections don't — which is why you need **port
publishing** (`-p 8080:8080`) in Docker, and why a private subnet is unreachable from the internet.

---

## 3. Ports and sockets

A **port** (16-bit, 0–65535) identifies a process/service on a host. A TCP connection is identified
by the **4-tuple** `(src IP, src port, dst IP, dst port)`.

| Port | Service | Port | Service |
|---:|---|---:|---|
| 22 | SSH | 443 | HTTPS |
| 53 | DNS | 5432 | PostgreSQL |
| 80 | HTTP | 6379 | Redis |
| 3306 | MySQL | 8080 | Common app port (Spring Boot default) |

Ports < 1024 need root to bind on Linux. Client sides use random **ephemeral ports** (≈32768–60999 on Linux).

---

## 4. TCP

Connection-oriented, reliable, ordered byte stream.

### Three-way handshake

```
Client                                   Server (LISTEN on :443)
  │ ── SYN (seq=x) ─────────────────────► │
  │ ◄──────────────── SYN-ACK (seq=y, ack=x+1)
  │ ── ACK (ack=y+1) ───────────────────► │   ESTABLISHED (1 RTT spent)
  │ ══ data ════════════════════════════► │
```

Why three? Both sides must choose and confirm initial sequence numbers.

### Reliability mechanisms

| Mechanism | What it does |
|---|---|
| **Sequence numbers + ACKs** | Receiver acknowledges bytes received; orders out-of-order segments |
| **Retransmission** | Unacknowledged data is resent after a timeout (or 3 duplicate ACKs) |
| **Checksums** | Detect corruption |
| **Flow control** | Receiver advertises a window: "I can buffer N more bytes" — protects the *receiver* |
| **Congestion control** | Slow start, congestion window, back off on loss — protects the *network* |

### Teardown

`FIN` → `ACK` → `FIN` → `ACK` (4 steps; each direction closes independently). The side that closes
first sits in **TIME_WAIT** (~60 s on Linux) — thousands of those on a client that opens a new connection
per request is why you use **connection pooling / keep-alive** (Hikari for DB, HTTP client pools).

### Failure signatures (memorize — it's how you debug)

| Symptom | What happened at TCP level | Typical cause |
|---|---|---|
| **Connection refused** (fast) | SYN answered with **RST** | Host reachable, nothing listening on that port (app down, wrong port, bound to 127.0.0.1) |
| **Connection timed out** (slow) | SYN got no answer | Firewall / security group dropping, wrong IP, no route |
| **Connection reset** mid-stream | RST during connection | Peer crashed, idle timeout on LB/NAT, proxy killed it |
| **Read timed out** | Connected, no response in time | Slow server, deadlock, long GC pause, slow query |

---

## 5. UDP

Connectionless datagrams: no handshake, no ordering, no retransmission, no congestion control
(built in). Lower latency and overhead.

| Use UDP when… | Examples |
|---|---|
| Late data is useless | Video calls, games, live metrics |
| Tiny request/response | **DNS** queries |
| You build reliability yourself on top | **QUIC** (HTTP/3) |

TCP vs UDP one-liner: *"TCP gives a reliable ordered stream at the cost of handshake latency and
head-of-line blocking; UDP gives raw datagrams and leaves reliability to the application."*

---

## 6. DNS resolution, step by step

Resolving `status.pulsewatch.example`:

```
Browser cache ─miss─► OS cache (/etc/hosts too) ─miss─► Recursive resolver (ISP, 1.1.1.1, 8.8.8.8, VPC resolver)
                                                              │ cache miss
                  ┌───────────────────────────────────────────┤
                  ▼                                           │
        1. Root server: "ask the .example TLD servers" ◄──────┤
        2. TLD server: "ask ns1.dnsprovider.com for pulsewatch.example"
        3. Authoritative server: "status.pulsewatch.example A 203.0.113.10, TTL 300"
                  │
                  ▼
   resolver caches for TTL → returns to OS → browser connects to 203.0.113.10
```

| Record | Meaning |
|---|---|
| `A` / `AAAA` | Name → IPv4 / IPv6 |
| `CNAME` | Alias to another name (e.g. to a load balancer's DNS name) |
| `MX` | Mail servers |
| `TXT` | Arbitrary text (domain verification, SPF) |
| `NS` | Authoritative name servers for a zone |

- **TTL** controls caching: low TTL = faster changes, more queries. Lower it *before* a migration.
- DNS uses UDP 53 (TCP for large responses).
- Java caches DNS too (`networkaddress.cache.ttl`) — relevant when an RDS failover changes the IP behind the endpoint.
- Debug: `dig status.pulsewatch.example +trace`, `nslookup`, `getent hosts name`.

---

## 7. HTTP/1.1 vs HTTP/2 vs HTTP/3

| | HTTP/1.1 | HTTP/2 | HTTP/3 |
|---|---|---|---|
| Transport | TCP | TCP | **QUIC over UDP** |
| Format | Text | Binary frames | Binary frames |
| Concurrency per connection | One request at a time (keep-alive reuses the connection); browsers open ~6 connections/host | **Multiplexed streams** over one connection | Multiplexed, independent streams |
| Head-of-line blocking | At HTTP level | Solved at HTTP level, still at **TCP** level (one lost packet stalls all streams) | Solved (per-stream loss recovery) |
| Header compression | None | HPACK | QPACK |
| TLS | Optional | Effectively required by browsers | Built into QUIC (TLS 1.3) |
| Handshake cost | TCP + TLS: ~2 RTT (TLS 1.3) | Same | ~1 RTT, 0-RTT on resumption |

Your Spring Boot APIs speak HTTP/1.1 behind nginx/ALB in most setups; the proxy can speak HTTP/2 to
browsers. Server push (HTTP/2) is effectively dead — don't bring it up as a feature.

---

## 8. TLS (HTTPS) handshake overview — TLS 1.3

Goals: **confidentiality** (encryption), **integrity** (tamper detection), **authentication** (you're
talking to the real server).

```
Client                                                   Server
  │ ClientHello: TLS versions, cipher suites, key share,  │
  │              SNI = status.pulsewatch.example ───────► │
  │ ◄─ ServerHello: chosen cipher, key share              │
  │ ◄─ {Certificate chain, CertificateVerify, Finished}   │  (already encrypted)
  │   client verifies cert: chain to trusted CA,          │
  │   hostname matches, not expired                       │
  │ ── {Finished} ──────────────────────────────────────► │
  │ ══ encrypted HTTP ══════════════════════════════════► │   1 RTT after TCP handshake
```

- **Asymmetric** crypto (ECDHE key exchange + certificate signatures) establishes a shared secret;
  **symmetric** crypto (AES-GCM / ChaCha20) encrypts the data — fast.
- **Forward secrecy:** ephemeral keys mean a later-stolen server key can't decrypt recorded traffic.
- **Certificates:** issued by a CA (Let's Encrypt, AWS ACM) binding a public key to a domain name.
- **SNI** lets one IP host many certificates. **mTLS**: the client presents a certificate too.
- **TLS termination:** often at the load balancer/nginx; traffic inside the VPC may be plain or re-encrypted.
- Debug: `openssl s_client -connect host:443 -servername host`, `curl -v https://…`.

---

## 9. What happens when you type `https://status.pulsewatch.example/monitors` and press Enter

1. **URL parsing** — scheme `https`, host, default port 443, path `/monitors`. Browser checks HSTS list (force HTTPS).
2. **Cache checks** — browser HTTP cache (maybe served without network), service worker.
3. **DNS** — browser → OS → recursive resolver → root → TLD → authoritative; get the IP (§6).
4. **TCP handshake** with the IP on 443 (§4), unless an existing keep-alive connection is reused. (HTTP/3: QUIC handshake over UDP instead.)
5. **TLS handshake** (§8): certificate validated against trusted CAs and hostname.
6. **HTTP request** sent: `GET /monitors HTTP/2`, headers `Host`, `Accept`, `Cookie`/`Authorization`, `User-Agent`.
7. **Load balancer / reverse proxy** (nginx in P4) receives it, maybe terminates TLS, picks a backend (§10), forwards with `X-Forwarded-For`/`X-Forwarded-Proto`.
8. **Application server** — Tomcat thread takes the request → Spring Security filter chain (JWT validation) → `DispatcherServlet` → controller → service → Redis cache check → on miss, JDBC over a pooled TCP connection to Postgres → query plan → B-tree index lookup → rows back → JSON serialization.
9. **Response**: status line `200 OK`, headers (`Content-Type`, `Cache-Control`, `ETag`), body.
10. **Browser renders**: parse HTML → build DOM; fetch CSS/JS/images (more requests, parallel over HTTP/2); CSSOM; run JS (React mounts, calls the API with `fetch`, which may trigger **CORS**); layout; paint.
11. **Connection kept alive** for reuse; eventually closed.

In an interview, go wide first (list the steps), then let the interviewer pick one to go deep —
DNS, TLS, the LB, or the backend. You own the backend part through your projects.

---

## 10. Load balancers: L4 vs L7

| | Layer 4 (transport) | Layer 7 (application) |
|---|---|---|
| Sees | IPs, ports, TCP/UDP | HTTP: path, host, headers, cookies |
| Routing by | Connection (5-tuple hash, round robin) | URL path (`/api` → API, `/` → frontend), host, header |
| TLS | Usually passes through (or terminates) | Terminates, can re-encrypt |
| Features | Very fast, any TCP protocol (Postgres, Redis) | Path routing, header rewrite, sticky sessions via cookie, WAF, retries, compression |
| AWS | NLB | ALB |
| Software | HAProxy (TCP mode), nginx `stream` | nginx `http`, HAProxy, Envoy, Traefik |

Algorithms: round robin, least connections, weighted, IP/consistent hash. LBs run **health checks**
and stop sending traffic to failing targets (P4's `/actuator/health`). P4 uses nginx as a
**reverse proxy** (same idea, one backend) — the step before a real load balancer.

Reverse proxy vs forward proxy: reverse sits in front of *servers* (nginx for your API); forward sits
in front of *clients* (corporate proxy).

---

## 11. CORS — a browser concept

**Same-origin policy:** a script from origin A (scheme + host + port) can't read responses from
origin B. `http://localhost:5173` (Vite) and `http://localhost:8080` (Spring) are **different origins**.

**CORS** lets the *server* opt in by sending headers. The browser enforces it — curl and Postman
ignore CORS entirely, which is why "it works in Postman" means nothing here.

```
Browser (origin http://localhost:5173)                      API http://localhost:8080
  │ OPTIONS /api/issues                                       │   ← preflight (non-simple request:
  │ Origin: http://localhost:5173                             │      JSON content-type, Authorization header,
  │ Access-Control-Request-Method: POST                       │      PUT/DELETE…)
  │ Access-Control-Request-Headers: authorization,content-type│
  │ ◄── 204  Access-Control-Allow-Origin: http://localhost:5173
  │          Access-Control-Allow-Methods: GET,POST,PUT,DELETE
  │          Access-Control-Allow-Headers: authorization,content-type
  │          Access-Control-Max-Age: 3600
  │ POST /api/issues (actual request) ───────────────────────►│
  │ ◄── 201 + Access-Control-Allow-Origin: http://localhost:5173
```

- CORS **does not protect your server** — it protects *users' browsers* from malicious sites reading responses using their credentials. Server-side authorization is still required.
- With cookies (`credentials: 'include'`), `Allow-Origin` can't be `*` and needs `Access-Control-Allow-Credentials: true`.
- Fix in Spring Security with a `CorsConfigurationSource` bean (see [`05-spring-boot/05-security-jwt.md`](../05-spring-boot/05-security-jwt.md)), or avoid CORS altogether by serving frontend and API from the **same origin** behind nginx (P3/P4 production setup) — or the Vite dev proxy locally.

---

## 12. Toolbox

| Question | Command |
|---|---|
| Is DNS right? | `dig +short host`, `dig host +trace` |
| Is the port open / reachable? | `nc -zv host 5432` |
| What's listening locally? | `ss -tlnp` (Linux), `lsof -iTCP -sTCP:LISTEN` |
| Full HTTP exchange incl. TLS | `curl -v https://host/path` |
| Timing breakdown | `curl -o /dev/null -s -w 'dns %{time_namelookup} connect %{time_connect} tls %{time_appconnect} ttfb %{time_starttransfer} total %{time_total}\n' https://host` |
| Certificate details | `openssl s_client -connect host:443 -servername host </dev/null` |
| Route to host | `traceroute host` / `mtr host` |

The curl timing line is also how PulseWatch's checker could report DNS/connect/TLS/TTFB per check.

---

## 13. Interview questions (quick)

<details><summary>TCP vs UDP?</summary>

TCP: connection-oriented, reliable, ordered, flow and congestion control; costs a handshake and
suffers head-of-line blocking. UDP: connectionless datagrams, no guarantees, minimal overhead; used
for DNS, real-time media, and QUIC.
</details>

<details><summary>What's the difference between "connection refused" and "connection timed out"?</summary>

Refused: the host replied with RST — reachable, but nothing listening on that port. Timed out: no
reply — a firewall/security group dropped packets, or the host/route doesn't exist.
</details>

<details><summary>Explain the TLS handshake briefly.</summary>

Client hello with supported ciphers and a key share; server replies with its key share and
certificate; both derive a symmetric session key via ECDHE; the client verifies the certificate
chain and hostname; then data flows encrypted with symmetric ciphers. TLS 1.3 needs one round trip.
</details>

<details><summary>L4 vs L7 load balancer?</summary>

L4 balances TCP/UDP connections by IP/port without reading content — fast, protocol-agnostic. L7
understands HTTP and can route by path/host/header, terminate TLS, and do sticky sessions.
</details>

<details><summary>Why do I get a CORS error in the browser but not in Postman?</summary>

CORS is enforced by browsers. The server didn't return an `Access-Control-Allow-Origin` matching the
page's origin (often on the preflight `OPTIONS`). Configure allowed origins/methods/headers on the
server, or serve UI and API from the same origin.
</details>

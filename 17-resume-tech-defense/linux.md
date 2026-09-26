# Linux — Résumé Tech Defense

> **Goal:** defend "Linux" with truthful past context and current command-line fluency: filesystem,
> permissions, processes and signals, `systemd`, networking tools, log inspection, `ssh`, and
> Bash scripting — exercised on the EC2 hosts of all four projects, inside every container you build,
> and in ForgeCI, whose workers spawn processes, forward signals and enforce timeouts inside containers.
>
> **Honesty rule:** "I used Linux daily as a developer (terminal, SSH to servers, reading logs)"
> is different from "I administered Linux servers". Claim the one that's true.

---

## Evidence in my projects

| Project | Linux evidence |
|---|---|
| **All projects** | Terminal-driven workflow from Week 1: Git, Maven, `psql`, `curl`; containers are Linux processes: signals + graceful shutdown, non-root users in Dockerfiles, `docker compose logs -f api \| grep requestId=` |
| **FlowGrid** M5 (W8) | EC2 (Amazon Linux 2023 / Ubuntu): installed Docker, `systemd` unit to start the Compose stack on boot, `journalctl`, disk checks (`df -h`, `du`), `ss -tlnp`, SSM Session Manager instead of open port 22; `scripts/smoke.sh`, `scripts/backup.sh` with `set -euo pipefail` |
| **ForgeCI** M1–M2 (W14–15) | **Linux deep dive** because workers run user-supplied steps in containers: processes, `fork`/`exec`, stdout/stderr capture, exit codes, signals (SIGTERM then SIGKILL on timeout), permissions, non-root user in the job image |
| **ForgeCI** M4–M6 (W17–19) | Graceful shutdown on SIGTERM (finish or release the current job), worker EC2 with the Docker socket (why that is root-equivalent), rotation of job logs |
| **Polish** (W24–26) | `scripts/teardown.sh`, runbooks per project |

## Where to learn it in this repo

- [`../10-linux/README.md`](../10-linux/README.md)
- [`../10-linux/commands.md`](../10-linux/commands.md)
- [`../10-linux/bash-scripting.md`](../10-linux/bash-scripting.md)
- [`../10-linux/exercises.md`](../10-linux/exercises.md)
- [`../14-cs-fundamentals/operating-systems.md`](../14-cs-fundamentals/operating-systems.md)
- Related: [`docker.md`](./docker.md)

---

## 1. Beginner questions

<details><summary><b>B1. Explain <code>-rwxr-x---</code> and <code>chmod 640</code>.</b></summary>

Type (`-` file, `d` dir) then owner/group/other triplets of read/write/execute. `rwx`=7, `r-x`=5, `---`=0 → 750. `chmod 640 f` → owner rw, group r, others none. On directories `x` means "can traverse".
</details>

<details><summary><b>B2. How do you find which process listens on port 8080?</b></summary>

`ss -tlnp | grep 8080` (or `lsof -i :8080`). Then `ps -fp <pid>`.
</details>

<details><summary><b>B3. How do you follow a log and filter it?</b></summary>

`tail -f app.log | grep --line-buffered ERROR`; for systemd services `journalctl -u flowgrid -f`; for containers `docker compose logs -f api`.
</details>

<details><summary><b>B4. Absolute vs relative paths, and what's in <code>/etc</code>, <code>/var/log</code>, <code>/home</code>, <code>/tmp</code>?</b></summary>

Absolute starts at `/`. `/etc` config, `/var/log` logs, `/var/lib` service state (Docker, Postgres), `/home` users, `/tmp` temporary (may be cleared), `/proc` kernel/process info.
</details>

<details><summary><b>B5. What are stdin, stdout, stderr and redirection?</b></summary>

FDs 0, 1, 2. `>` overwrite, `>>` append, `2>&1` merge stderr into stdout, `|` pipe stdout to next stdin. `cmd > out.log 2>&1`.
</details>

<details><summary><b>B6. What does <code>sudo</code> do?</b></summary>

Runs a command as another user (root by default) per `/etc/sudoers`. Use minimally; services should run as unprivileged users.
</details>

## 2. Intermediate questions

<details><summary><b>I1. Signals: SIGTERM vs SIGKILL vs SIGINT.</b></summary>

SIGTERM (15): polite stop, can be handled (Spring graceful shutdown). SIGINT (2): Ctrl-C. SIGKILL (9): cannot be caught, immediate — no cleanup. `kill -9` is last resort. Exit code 128+N means killed by signal N (137 = SIGKILL, 143 = SIGTERM).
</details>

<details><summary><b>I2. Process vs thread; how to see a Java process's threads?</b></summary>

Process: own address space; threads share it. `ps -eLf`, `top -H -p <pid>`, and `jcmd <pid> Thread.print` / `jstack` for Java stacks.
</details>

<details><summary><b>I3. Write a <code>systemd</code> unit for a Compose stack.</b></summary>

```ini
[Unit]
Description=FlowGrid stack
Requires=docker.service
After=docker.service network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/flowgrid
ExecStart=/usr/bin/docker compose up -d
ExecStop=/usr/bin/docker compose down

[Install]
WantedBy=multi-user.target
```
`systemctl daemon-reload && systemctl enable --now flowgrid`.
</details>

<details><summary><b>I4. The disk is full — how do you find what's using it?</b></summary>

`df -h` (which filesystem), `du -xh --max-depth=1 / | sort -h`, `docker system df`, check `/var/log`, `journalctl --disk-usage`. Also deleted-but-open files: `lsof +L1`. Also `df -i` for inode exhaustion.
</details>

<details><summary><b>I5. Load average and memory — how to read <code>top</code>/<code>free</code>?</b></summary>

Load average = runnable + uninterruptible tasks averaged over 1/5/15 min; compare to CPU count. `free -h`: "available" matters, not "free" (page cache is reclaimable). Check `dmesg` for OOM killer.
</details>

<details><summary><b>I6. <code>grep</code>/<code>sed</code>/<code>awk</code> — one practical use each.</b></summary>

`grep -c ' 500 ' access.log`; `sed -i 's/^LOG_LEVEL=.*/LOG_LEVEL=INFO/' .env`; `awk '{print $9}' access.log | sort | uniq -c | sort -rn` (status code counts).
</details>

<details><summary><b>I7. Bash script safety basics?</b></summary>

`#!/usr/bin/env bash`, `set -euo pipefail`, quote variables `"$var"`, `trap cleanup EXIT`, check args, `shellcheck`. Use `[[ ]]` for tests, `$(...)` for command substitution.
</details>

<details><summary><b>I8b. How does ForgeCI stop a job that exceeds its timeout?</b></summary>

The step runs as a process inside the job container. On timeout the worker sends `SIGTERM` (container `stop` with a grace period) so a well-behaved process can flush; if it is still alive after the grace period, `SIGKILL` (container `kill`). The worker records the exit code (`137` = killed by SIGKILL, `143` = SIGTERM) and marks the job `TIMED_OUT`, not `FAILED`, so the retry policy treats it differently. `finally` removes the container so a stuck process can't leak.
</details>

<details><summary><b>I8. SSH keys and hardening?</b></summary>

Key pair; public key in `~/.ssh/authorized_keys` (perm 600, dir 700). Disable password auth and root login; restrict SG to your IP — or avoid 22 entirely with SSM Session Manager (FlowGrid onward).
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "You list Linux — how comfortable are you on a server?"</b></summary>

Truthful past (daily terminal, SSH to dev/staging, log reading). Now: set up FlowGrid's EC2 host — Docker, systemd unit, log inspection, disk/port checks, Bash scripts — and repeated it three times; in ForgeCI I wrote the code that runs processes in containers and handles their signals and exit codes. Offer to do it live.
</details>

<details><summary><b>R2. "The API is slow on the server. What commands do you run first?"</b></summary>

`uptime`/`top` (CPU, load), `free -h`, `df -h`, `docker stats`, `ss -s`, app logs for latency and errors, `jcmd <pid> Thread.print` for stuck threads, DB connectivity (`pg_isready`). Then narrow to app vs DB vs network.
</details>

<details><summary><b>R3. "How do you find all ERROR lines for one request across logs?"</b></summary>

Request-ID in MDC → `grep 'requestId=abc123' *.log` or Logs Insights `filter requestId = "abc123"`.
</details>

<details><summary><b>R4. "Explain what happens when you type <code>java -jar app.jar</code>."</b></summary>

Shell resolves `java` via `PATH`, `fork` + `exec` creates the process, JVM starts, loads classes, Spring Boot starts embedded Tomcat and binds port 8080 (needs no root since > 1024), process inherits env vars and FDs.
</details>

<details><summary><b>R5. "Write a script that checks a URL every 10 seconds and logs failures."</b></summary>

```bash
#!/usr/bin/env bash
set -euo pipefail
url=${1:?usage: $0 URL}
while true; do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$url" || echo 000)
  [[ $code == 2* ]] || echo "$(date -Is) FAIL $url $code" >> failures.log
  sleep 10
done
```
Mention ForgeCI's per-job timeout is the "real" version of this loop: `SIGTERM`, wait, `SIGKILL`, record exit code 137/143.
</details>

## 4. Practical tasks (doable live)

1. Create a user, group, and a directory only that group can write.
2. Find the 10 largest files under `/var`.
3. Count HTTP status codes in an nginx access log with `awk`.
4. Kill a hung process gracefully, then forcefully, explaining exit codes.
5. Write a `backup.sh` that runs `pg_dump`, gzips with a dated filename, keeps the last 7.

## 5. Debugging questions

<details><summary><b>D1. "Permission denied" running <code>./deploy.sh</code>.</b></summary>

Missing execute bit (`chmod +x`), `noexec` mount, wrong owner, or CRLF line endings (`bad interpreter: /bin/bash^M` → `dos2unix`).
</details>

<details><summary><b>D2. Service won't start after reboot.</b></summary>

`systemctl status flowgrid`, `journalctl -u flowgrid -b`, unit not enabled, dependency (docker) not ready, working directory or env file missing.
</details>

<details><summary><b>D3. "Address already in use".</b></summary>

`ss -tlnp | grep :8080` → old process or another container; stop it or change the port.
</details>

<details><summary><b>D4. Java process killed with no stack trace.</b></summary>

Kernel OOM killer: `dmesg -T | grep -i kill`, exit 137. Reduce heap / raise memory / fix leak.
</details>

## 6. Architecture questions

<details><summary><b>A1. How do you run a service reliably on one Linux host?</b></summary>

systemd (or Docker restart policies) for restart-on-failure, logs to journald/CloudWatch, log rotation, unprivileged user, firewall/SG, automated security updates, monitoring + alarms, backups.
</details>

<details><summary><b>A2. Why Linux containers rather than configuring the host directly?</b></summary>

Host stays minimal (Docker + agent); app dependencies are versioned in images; reproducible rebuilds; the host becomes replaceable.
</details>

## 7. Common mistakes

- `chmod 777` to "fix" permissions.
- `kill -9` first.
- Unquoted variables in scripts (word splitting on spaces).
- Running services as root.
- Not rotating logs → disk full.
- Editing config on a server by hand without recording it (no reproducibility).

## 8. Terminology I must know

| Term | Meaning |
|---|---|
| Kernel / userspace | OS core / everything else |
| Process / PID | Running program / its id |
| Signal | Async notification to a process |
| File descriptor | Handle to open file/socket |
| Permissions / umask | rwx bits / default mask |
| systemd unit | Service definition |
| journald | systemd's log store |
| Load average | Runnable+waiting task average |
| Inode | File metadata record |
| PATH | Executable search path |
| Exit code | 0 success, non-zero failure |
| Pipe | Connect stdout → stdin |

## 9. When to use it

- Every server and container you'll touch; CI runners are Linux.
- Automating repetitive ops with Bash.

## 10. When NOT to use it

- Bash for complex logic/data processing — use Python or Java.
- Hand-configuring snowflake servers instead of scripts/images.

## 11. Trade-offs

| Choice | Gain | Cost |
|---|---|---|
| Bash script | Ubiquitous, quick | Fragile, hard to test |
| systemd vs Docker restart policy | Boot integration, deps | Another layer |
| SSH vs SSM | Familiar | Open port + key management |

## 12. How it interacts with the rest of my stack

- **Java/Spring Boot:** JVM is a Linux process; signals drive graceful shutdown; `jcmd`/`jstack` for diagnosis.
- **Postgres:** `psql`, `pg_dump`, `pg_isready` from the shell.
- **Docker:** containers = namespaced Linux processes; images are Linux filesystems.
- **AWS:** EC2 is Linux; SSM sessions; CloudWatch agent.
- **CI:** every `run:` step is Bash on Ubuntu.

## 13. One small hands-on exercise

**Ops toolkit for the FlowGrid host (local VM or container is fine).**

- [ ] `smoke.sh URL` exits non-zero if health isn't `UP` within 30 s (retry loop).
- [ ] `backup.sh` dumps Postgres, gzips, retains 7 files, logs to stderr on failure.
- [ ] systemd unit starts the Compose stack on boot; verified with a reboot.
- [ ] Shellcheck clean; `set -euo pipefail`; all vars quoted.

## 14. Mastery checklist

- [ ] Read and set permissions numerically and symbolically
- [ ] Find processes/ports/disk hogs quickly
- [ ] Explain signals and exit codes 137/143
- [ ] Write a safe Bash script from scratch
- [ ] Write a systemd unit and read journald logs
- [ ] Truthful 60-second answer on past Linux use + FlowGrid / ForgeCI bridge

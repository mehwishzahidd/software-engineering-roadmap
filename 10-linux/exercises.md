# Linux Exercises

> Tasks 1–4 in Week 1; 10 and 13 when FlowGrid first deploys (Week 8); everything in Week 14 (ForgeCI). 25 tasks: skills → incident drills → scripts → a ForgeCI drill.
> For each: **write the commands first, then run them**, then write one sentence on what you learned.
> Setup helpers use a disposable Ubuntu container unless noted: `docker run -it --rm --name lab ubuntu:24.04 bash`
> then `apt update && apt install -y curl procps iproute2 lsof dnsutils netcat-openbsd less jq`.

Legend: 🛠️ skill · 🚨 incident drill (time yourself; target ≤ 10 min) · 📜 scripting

---

## Part A — Skills

### 1. 🛠️ Filesystem tour
In 10 commands or fewer, find: your home dir, the OS release (`/etc/os-release`), where `bash` lives, the 5 largest
files under `/usr` (hint: `find … -printf '%s %p\n' | sort -n | tail -5` or `du -a | sort -h`), and how many CPUs (`nproc`, `/proc/cpuinfo`).

### 2. 🛠️ Permissions matrix
Create `/srv/demo/{public,team,secret}` and files inside. Make `public` readable by all, `team` read/write for group
`devs` only, `secret` owner-only. Create users `alice` (in `devs`) and `bob` (not). Verify with `sudo -u bob cat …`.
Write down each `chmod` in both octal and symbolic.

### 3. 🛠️ Directory `x` bit
`chmod 644` a directory and try `ls` and `cat dir/file`. Then `chmod 711` and try again. Explain r vs x on directories in one sentence.

### 4. 🛠️ Links
Create a file, a hard link and a symlink to it. `ls -li` (compare inode numbers). Delete the original. Which link still works? Why?

### 5. 🛠️ Processes and signals
Write `trap 'echo got TERM; exit 0' TERM; while true; do sleep 1; done` into `loop.sh`. Run it in the background.
Send `SIGTERM` — observe the message. Run again, send `SIGKILL` — no message. Check `$?` after `wait` for both (143 vs 137).

### 6. 🛠️ Job control
Start `sleep 300`, suspend with Ctrl+Z, list with `jobs`, resume in background with `bg`, bring it back with `fg`, stop with Ctrl+C.
Then run `nohup sleep 300 &`, close the terminal, reconnect, and find it with `pgrep -a sleep`.

### 7. 🛠️ systemd unit (VM/EC2 — needs systemd)
Write `/etc/systemd/system/hello.service` running `python3 -m http.server 9000` as a non-root user with `Restart=on-failure`.
`enable --now` it, `curl localhost:9000`, `kill -9` its PID, and watch systemd restart it (`systemctl status`, `journalctl -u hello`).

### 8. 🛠️ Networking toolkit
Resolve `github.com` with `dig +short`; check TCP 443 with `nc -zv github.com 443`; time a request with
`curl -sS -o /dev/null -w '%{http_code} %{time_connect} %{time_total}\n' https://github.com`; list listening sockets with `ss -ltn`.

### 9. 🛠️ Text processing warm-up
Generate a sample access log:
```bash
for i in $(seq 1 500); do
  s=$(( RANDOM % 10 == 0 ? 500 + RANDOM % 4 : 200 ))
  p=$(shuf -n1 -e /api/monitors /api/status /api/incidents /actuator/health)
  echo "10.0.0.$(( RANDOM % 20 )) - - [01/May/2026:10:$(printf %02d $(( i % 60 ))):00 +0000] \"GET $p HTTP/1.1\" $s 123 \"-\" \"curl/8\""
done > access.log
```
Answer with one pipeline each: total requests; number of 5xx; 5xx per path (sorted desc); top 3 client IPs; percentage of 5xx.

### 10. 🛠️ SSH keys (laptop → VM/EC2)
Generate an ed25519 key, install it on the server with `ssh-copy-id` (or `authorized_keys` manually), add a `~/.ssh/config`
alias, disable password auth in `sshd_config`, reload `sshd`, and confirm key login still works **before** closing your existing session.

### 11. 🛠️ Environment variables and Spring
Run any Spring Boot jar with `SERVER_PORT=9090 SPRING_PROFILES_ACTIVE=dev java -jar app.jar`. Confirm it listens on 9090 (`ss -ltn`).
Explain relaxed binding for `SPRING_DATASOURCE_URL`.

### 12. 🛠️ `find` + `xargs`
Create 50 files, some with spaces in names, some older than 7 days (`touch -d '10 days ago' f`). Delete only the old `.tmp`
files safely with `find … -print0 | xargs -0 rm`. Then do it with `find … -delete`. Dry-run first with `-print`.

---

## Part B — Incident drills 🚨

For each drill: state your hypothesis, the commands, the evidence, the fix, and the prevention. Say it out loud as if in an interview.

### 13. 🚨 "Port 8080 is already in use"
Setup: `python3 -m http.server 8080 &` then try to start your Spring Boot app (or a second `http.server`) on 8080.
Task: find which process holds port 8080, what command started it, who owns it, and stop it gracefully.
<details><summary>Reference path</summary>

```bash
sudo ss -ltnp 'sport = :8080'          # → users:(("python3",pid=1234,fd=3))
sudo lsof -iTCP:8080 -sTCP:LISTEN      # alternative
ps -o pid,user,etime,cmd -p 1234       # what/who/how long
kill 1234; sleep 2; ss -ltn 'sport = :8080'   # SIGTERM, verify; SIGKILL only if still there
```
If it's a container: `docker ps --filter publish=8080`. Prevention: consistent port allocation; `server.port` via env.
</details>

### 14. 🚨 Tail the Spring log and count 5xx
Setup: run FlowGrid with access logging, or use `access.log` from #9; for a Spring app log to file
with `--logging.file.name=app.log`. Generate traffic with a loop that includes bad requests.
Task: (a) follow the log live, highlighting errors; (b) count 5xx responses in the last N lines; (c) show a per-minute 5xx count.
<details><summary>Reference path</summary>

```bash
tail -F app.log | grep --line-buffered -E 'ERROR|WARN'            # live, errors only
# nginx/combined format: status is field 9
tail -n 10000 access.log | awk '$9 ~ /^5[0-9][0-9]$/' | wc -l
# per minute: timestamp field 4 looks like [01/May/2026:10:07:00 → keep up to minutes
awk '$9 ~ /^5/ {print substr($4, 2, 17)}' access.log | sort | uniq -c
# Spring Boot has no access log by default: enable Tomcat's (server.tomcat.accesslog.enabled=true) or log status in a filter.
# With structured JSON logs: jq -r 'select(.status >= 500) | .path' app.json | sort | uniq -c
```
</details>

### 15. 🚨 "Why is the disk full?"
Setup (in a VM or a container with a small tmpfs: `docker run -it --rm --tmpfs /data:size=50m ubuntu:24.04 bash`):
`fallocate -l 30M /data/huge.log` (or `dd if=/dev/zero of=/data/huge.log bs=1M count=30`), and start `tail -f /data/huge.log &` so it's held open.
Task: find what's using space, free it **without** restarting the holder, and explain why `rm` alone doesn't free it.
<details><summary>Reference path</summary>

```bash
df -h                                  # which filesystem is full
df -i                                  # rule out inode exhaustion
du -xh /data --max-depth=1 | sort -h   # drill down (repeat into the biggest dir)
rm /data/huge.log; df -h /data         # still full! file deleted but open
lsof +L1                               # (or lsof | grep deleted) → tail holds it
: > /proc/<pid>/fd/<fd>                # truncate via the fd, or restart the process
```
Real-world suspects: `/var/lib/docker` (`docker system df`, `docker image prune -a`, `docker builder prune`), journald
(`journalctl --vacuum-size`), app logs without rotation, old backups. Prevention: logrotate, Docker log limits
(`max-size`), retention in `backup-db.sh`, CloudWatch disk alarm (from Week 8). On a ForgeCI worker the usual culprit is job workspaces and pulled build images — which is why M2 requires cleanup in `finally`.
</details>

### 16. 🚨 "The app can't connect to the database"
Setup: on a VM, run Postgres in Docker **without** publishing the port (`docker run -d --name pg -e POSTGRES_PASSWORD=x postgres:16-alpine`) and try `psql -h localhost`.
Task: walk DNS → TCP → listener → auth, proving each step with a command.
<details><summary>Reference path</summary>

`nc -zv localhost 5432` fails → `ss -ltn | grep 5432` on the host shows nothing → `docker ps` shows no port mapping →
fix `-p 5432:5432` (or connect from another container on the same network). Then auth: `psql` error message tells
password vs `pg_hba` vs database-doesn't-exist.
</details>

### 17. 🚨 "Service didn't come back after reboot"
Setup: `hello.service` from #7 with `enable` removed (`systemctl disable hello`). Reboot the VM.
Task: find that it's not running, why (`systemctl is-enabled`), and fix permanently. Bonus: break `ExecStart` path and read the failure in `journalctl -u hello -b`.

### 18. 🚨 "Permission denied" on deploy
Setup: `/opt/flowgrid` owned by `root:root 755`; as `deploy`, try to write `.env`.
Task: fix with the right owner/group and minimal bits (not 777). Make `.env` readable only by `deploy`.

### 19. 🚨 "The server is slow"
Setup: `yes > /dev/null &` ×2 and a memory hog (`python3 -c "a=' '*800_000_000; input()"` — adjust to your RAM).
Task: identify CPU vs memory pressure with `top`/`htop` (load average, `%CPU`, `%MEM`, `free -h`, swap), find the culprits, stop them.
Explain load average relative to `nproc`.

### 20. 🚨 "SSH: Permission denied (publickey)"
Setup: `chmod 644 ~/.ssh/id_ed25519` locally, or `chmod 777 ~/.ssh` on the server.
Task: diagnose with `ssh -v`, then fix permissions on both sides. Know the other causes: wrong user (`ec2-user` vs `ubuntu`), wrong key, key not in `authorized_keys`, security group.

---

## Part C — Scripts 📜

### 21. 📜 `healthcheck.sh`
Takes URLs (args or a file with `-f`), prints `UP`/`DOWN` with HTTP status and time for each, exits non-zero if any is DOWN.
Uses `set -euo pipefail`, a function, `curl -w`, and a timeout (`--max-time 5`). (A tiny uptime checker — handy for smoke-testing every deploy.)

### 22. 📜 `backup-db.sh`
Implement the script from [bash-scripting.md §8](./bash-scripting.md#8-real-script-1--postgres-backup) against your local Compose Postgres. Schedule with cron every 5 min for testing, check the log, verify retention deletes files, then **restore** into a new database and count rows.

### 23. 📜 `log-report.sh`
Given an access log path, print: total requests, 5xx count and %, top 5 paths, top 5 IPs, slowest 5 requests (if your log includes duration). Accept `-n` for top-N via `getopts`.

### 24. 📜 `deploy.sh` dry run
Implement [bash-scripting.md §9](./bash-scripting.md#9-real-script-2--deploy-with-health-check-and-rollback) with a `-n` dry-run flag that prints actions instead of executing them. Simulate a failed health check (point `health_url` at a closed port) and confirm rollback + non-zero exit.

---

## Part D — ForgeCI drill (Week 14)

### 25. 🚨 "The job exited with 137" — timeout, cancel or OOM?
Setup: run three containers the way a CI worker would:
```bash
docker run -d --name oom  --memory 64m python:3.12-alpine python -c "b = bytearray(512 * 1024 * 1024)"
docker run -d --name slow alpine:3.20 sleep 600
docker run -d --name trap alpine:3.20 sh -c 'trap "echo bye; exit 0" TERM; while true; do sleep 1; done'
sleep 5; docker kill slow; docker stop -t 5 trap
```
Task: for each container, report exit code, `OOMKilled`, and how long `stop` took; classify each as *app failure*,
*timeout/cancel*, or *resource limit*, and say which one ForgeCI's retry policy should retry (hint: none of them are
infra failures). Explain why `trap` exits 0 while a PID-1 `sleep` needs SIGKILL.
<details><summary>Reference path</summary>

```bash
for c in oom slow trap; do
  docker inspect -f '{{.Name}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}}' "$c"
done
docker logs trap            # "bye" — the shell handled SIGTERM itself
docker rm -f oom slow trap
```
Expected: `oom` 137 + `OOMKilled=true`; `slow` 137 (SIGKILL from `docker kill`); `trap` 0 after a fast graceful stop.
</details>

---

## Explain it (Sunday, out loud, ≤ 60 s each)

- [ ] SIGTERM vs SIGKILL and what Spring Boot does on SIGTERM.
- [ ] `chmod 640 .env` — who can do what?
- [ ] How I found the process on port 8080.
- [ ] My "disk full" checklist.
- [ ] Why `rm` of an open log file doesn't free space.
- [ ] What `set -euo pipefail` protects against.

---

## Tracker

| # | Done | Time | Note |
|---|---|---|---|
| 1–12 | ☐ | | |
| 13–20 | ☐ | | |
| 21–24 | ☐ | | |
| 25 | ☐ | | |

Update [trackers/technology-tracker.md](../trackers/technology-tracker.md) (Linux row).

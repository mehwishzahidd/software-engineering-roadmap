# Linux Commands That Matter

> §1–§3 in Week 1; everything in Week 14 (ForgeCI) · ≈ 4 hours. Each section: the commands, what they're for in *your* projects, and the interview angle.
> Type every command. Use `man cmd` when a flag is unclear.

---

## 1. Filesystem hierarchy

| Path | Contains | You'll touch it when… |
|---|---|---|
| `/` | root of everything | — |
| `/home/<user>` (`~`) | user files, `~/.ssh`, dotfiles | SSH keys, cloning repos |
| `/root` | root's home | — |
| `/etc` | system configuration (text files) | `/etc/nginx/`, `/etc/systemd/system/`, `/etc/hosts`, `/etc/ssh/sshd_config` |
| `/var` | variable data | `/var/log/` logs, `/var/lib/docker/` images & volumes, `/var/lib/postgresql/` |
| `/opt` | self-contained third-party apps | `/opt/flowgrid/` (your deploy dir) |
| `/usr/bin`, `/usr/local/bin` | programs | where `java`, `docker`, your scripts live |
| `/tmp` | temporary files (often cleared on reboot) | scratch |
| `/proc` | virtual FS: kernel & process info | `/proc/<pid>/`, `/proc/meminfo`, `/proc/cpuinfo` |
| `/dev` | devices | `/dev/null`, disks (`/dev/nvme0n1`) |
| `/mnt`, `/media` | mount points | extra EBS volumes |

"Everything is a file": devices, sockets, pipes and process info are exposed as files.

---

## 2. Navigation and inspection

```bash
pwd                      # where am I
ls -lah                  # long, all (incl. dotfiles), human sizes
ls -lt | head            # newest first
cd /var/log; cd -; cd ~  # go, back, home
tree -L 2                # directory tree (install tree)
file app.jar             # what kind of file is this
stat app.jar             # size, perms, inode, timestamps
which java; type ll      # where a command resolves from
realpath ./app.jar       # absolute path
```

---

## 3. Files and directories

```bash
mkdir -p /opt/flowgrid/{config,backups}   # -p: parents, no error if exists; brace expansion
touch notes.txt
cp -r src/ dst/             # recursive copy
cp -a src/ dst/             # archive: preserve perms, owners, timestamps, symlinks
mv old new                  # move/rename
rm file; rm -r dir          # delete (no recycle bin!)
rm -rf "$DIR"/              # dangerous — never with an unquoted/empty variable (see bash-scripting.md)
ln -s /opt/flowgrid/releases/42 /opt/flowgrid/current   # symlink (used for atomic deploys)

cat file; less file         # less: / to search, n next, G end, q quit, F follow
head -n 20 file; tail -n 50 file
tail -f app.log             # follow appended lines
tail -F app.log             # follow by name — survives log rotation
find /var/log -name "*.log" -mtime -1        # modified in last day
find . -type f -size +100M                    # big files
find /tmp -type f -mtime +7 -delete           # clean old files (dry-run without -delete first!)
```

**Hard link vs symlink:** a hard link is another directory entry pointing to the **same inode** (same file; survives
deleting the "original"; same filesystem only; not for directories). A symlink is a small file containing a **path**
(can cross filesystems, can dangle if the target is removed).

---

## 4. Permissions

```
-rwxr-x---  1 deploy  flowgrid  4096 May  1 10:00 deploy.sh
│└┬┘└┬┘└┬┘     owner   group
│ │  │  └── others:  ---  (no access)
│ │  └───── group:   r-x  (read, execute)
│ └──────── owner:   rwx  (read, write, execute)
└────────── type: - file, d directory, l symlink
```

| | File | Directory |
|---|---|---|
| `r` (4) | read contents | list names (`ls`) |
| `w` (2) | modify contents | create/delete/rename entries inside |
| `x` (1) | execute | enter/traverse (`cd`, access files inside by name) |

```bash
chmod 750 deploy.sh        # rwx r-x ---   (7=4+2+1, 5=4+1, 0)
chmod 640 .env             # rw- r-- ---   config with secrets: owner writes, group reads
chmod 600 ~/.ssh/id_ed25519   # SSH refuses private keys readable by others
chmod 700 ~/.ssh
chmod u+x script.sh        # symbolic: add execute for owner
chmod -R g+rX shared/      # capital X: execute only on dirs (and files already executable)
chown deploy:flowgrid file   # change owner and group
chown -R 999:999 ./pgdata  # numeric ids (e.g. container user)
umask                      # default permission mask, typically 022 → new files 644, dirs 755
```

Common numbers: `644` (files), `755` (dirs/scripts), `600` (private keys, secrets), `700` (private dirs).

**Special bits (awareness):** setuid (`4xxx`, run as file owner — e.g. `passwd`), setgid (`2xxx`, on dirs: new files
inherit group), sticky (`1xxx`, on `/tmp`: only owners can delete their files).

**`sudo`:** run one command as root (or `-u user`). Configured in `/etc/sudoers` (edit only with `visudo`).
`sudo -i` for a root shell — use sparingly. Principle of least privilege: apps run as dedicated non-root users (same idea in Docker and IAM).

> **Break it:** `chmod 644 deploy.sh && ./deploy.sh` → "Permission denied". `chmod 000 dir && ls dir` → denied, even as the owner (not as root).
> `chmod 644 ~/.ssh/id_ed25519 && ssh host` → "UNPROTECTED PRIVATE KEY FILE!".

---

## 5. Users and groups

```bash
whoami; id                     # uid, gid, groups
sudo useradd --system --no-create-home --shell /usr/sbin/nologin flowgrid   # service account
sudo useradd -m -s /bin/bash deploy                                            # human/deploy user
sudo passwd deploy
sudo usermod -aG docker deploy # add to group (-a append! without it, other groups are removed); re-login to apply
groups deploy
getent passwd deploy           # user entry; /etc/passwd, /etc/group, /etc/shadow (hashes)
su - deploy                    # switch user
```

Being in the `docker` group is effectively root on that host (you can mount `/` into a container). Know this.

---

## 6. Processes and signals

```bash
ps aux                         # all processes: USER PID %CPU %MEM ... COMMAND
ps aux --sort=-%mem | head     # top memory users
ps -ef --forest                # parent/child tree
pgrep -af java                 # find PIDs by name, show command line
top                            # live; press M (mem), P (cpu), 1 (per-CPU), q
htop                           # nicer top (install)
jobs; fg %1; bg %1             # shell job control; Ctrl+Z suspends, Ctrl+C sends SIGINT
./long-task.sh &               # run in background of this shell
nohup java -jar app.jar > app.log 2>&1 &   # survive terminal hangup (SIGHUP); prefer systemd/Docker
kill <pid>                     # sends SIGTERM (15)
kill -9 <pid>                  # SIGKILL — last resort
kill -l                        # list signals
pkill -f 'flowgrid.*\.jar'   # by command-line pattern (careful)
```

| Signal | Number | Meaning | Catchable? |
|---|---:|---|---|
| `SIGHUP` | 1 | terminal closed; many daemons: reload config | yes |
| `SIGINT` | 2 | Ctrl+C | yes |
| `SIGKILL` | 9 | kill immediately (kernel) | **no** |
| `SIGTERM` | 15 | please terminate (default for `kill`, `docker stop`, systemd) | yes |
| `SIGSTOP` / `SIGCONT` | 19 / 18 | pause / resume (numbers vary by architecture) | STOP: no |

**SIGTERM vs SIGKILL:** SIGTERM lets the process clean up — the JVM runs shutdown hooks; Spring Boot's graceful
shutdown (`server.shutdown=graceful`, default in Boot 3.4+) stops accepting requests, finishes in-flight ones,
closes the connection pool. SIGKILL can't be caught: in-flight requests die, temp files remain, locks may linger.
`docker stop` sends SIGTERM, waits 10 s, then SIGKILL.

**Zombie:** a process that exited but whose parent hasn't `wait()`ed for it — an entry in the process table, no
resources. **Orphan:** parent died; adopted by PID 1. (Why containers need a proper PID 1 — see [11-docker/dockerfiles.md](../11-docker/dockerfiles.md).)

---

## 7. systemd and services

```bash
systemctl status docker            # state, PID, last log lines
sudo systemctl start|stop|restart|reload nginx
sudo systemctl enable --now docker # start now AND at boot
systemctl is-enabled docker
systemctl list-units --type=service --state=failed
sudo systemctl daemon-reload       # after editing unit files
```

A unit for running the FlowGrid Compose stack on EC2 at boot (Week 8; same pattern for every project):

```ini
# /etc/systemd/system/flowgrid.service
[Unit]
Description=FlowGrid (Docker Compose stack)
Requires=docker.service
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/flowgrid
User=deploy
ExecStart=/usr/bin/docker compose up -d --remove-orphans
ExecStop=/usr/bin/docker compose down
TimeoutStartSec=300

[Install]
WantedBy=multi-user.target
```

For a plain JAR (no Docker): `Type=simple`, `ExecStart=/usr/bin/java -jar /opt/app/app.jar`, `Restart=on-failure`,
`User=flowgrid`, `EnvironmentFile=/opt/app/app.env`.

### Logs with journald

```bash
journalctl -u flowgrid -f                 # follow a unit
journalctl -u docker --since "1 hour ago"
journalctl -u nginx -p err -b               # errors since boot
journalctl --disk-usage; sudo journalctl --vacuum-size=200M
```

Classic log files: `/var/log/syslog` (Debian/Ubuntu) or `/var/log/messages` (RHEL/Amazon Linux), `/var/log/auth.log`
(SSH logins), `/var/log/nginx/`. Rotation: `logrotate` (`/etc/logrotate.d/`).

---

## 8. Package managers

| Distro | Tool | Examples |
|---|---|---|
| Ubuntu/Debian | `apt` | `sudo apt update && sudo apt install -y curl jq` · `apt search` · `apt remove` |
| Amazon Linux 2023 / RHEL / Fedora | `dnf` (older: `yum`) | `sudo dnf install -y docker` |
| Alpine (containers) | `apk` | `apk add --no-cache curl` |
| macOS | `brew` | `brew install jq` |

`apt update` refreshes the index; `apt upgrade` installs newer versions. In Dockerfiles, run both in one `RUN` and clean caches ([11-docker/dockerfiles.md](../11-docker/dockerfiles.md)).

---

## 9. Networking tools

```bash
ip addr; ip route                 # interfaces/IPs; routes (ifconfig/route are legacy)
ping -c 3 db.internal             # ICMP reachability (often blocked by security groups!)
dig api.example.com +short        # DNS lookup (package: dnsutils/bind-utils)
nslookup api.example.com
traceroute example.com            # hops (or tracepath, mtr)
curl -v http://localhost:8080/actuator/health        # full request/response incl. headers & TLS
curl -sS -o /dev/null -w '%{http_code} %{time_total}\n' https://example.com
curl -X POST -H 'Content-Type: application/json' -d '{"name":"api"}' localhost:8080/api/monitors
ss -ltnp                          # Listening TCP sockets, numeric, with Process (sudo to see all processes)
ss -tnp state established         # current connections
sudo lsof -i :8080                # who holds port 8080
nc -zv db.internal 5432           # can I open a TCP connection to this port?
nc -l 9000                        # quick TCP listener for testing
netstat -tulpn                    # legacy equivalent of ss (net-tools)
cat /etc/hosts; cat /etc/resolv.conf
```

**Debugging "can't connect" in order:** DNS resolves? (`dig`) → route/port open? (`nc -zv`) → something listening
on the target? (`ss -ltn` on the server) → listening on `0.0.0.0` or only `127.0.0.1`? → firewall/security group? →
application-level error? (`curl -v`). `ping` failing proves little (ICMP is often blocked).

---

## 10. Disk

```bash
df -h                             # free space per filesystem
df -i                             # inodes — "No space left" with free GB = inode exhaustion
du -sh /var/log/*                 # size per entry
du -xh / --max-depth=1 2>/dev/null | sort -h | tail    # biggest top-level dirs on this FS
sudo du -sh /var/lib/docker
docker system df                  # images/containers/volumes/build cache usage
lsblk; mount | column -t          # block devices, mounts
sudo lsof +L1                     # deleted-but-still-open files (space not freed until process closes them)
```

---

## 11. Text processing and pipes

```bash
cmd > out.txt          # stdout to file (overwrite)
cmd >> out.txt         # append
cmd 2> err.txt         # stderr
cmd > all.log 2>&1     # both (order matters: redirect stdout, then point stderr at it)
cmd &> all.log         # bash shorthand for both
cmd < input.txt        # stdin from file
cmd1 | cmd2            # stdout of cmd1 → stdin of cmd2
cmd | tee out.txt      # see and save
cmd > /dev/null 2>&1   # discard everything
```

```bash
grep -n "ERROR" app.log                 # with line numbers
grep -i "timeout" app.log               # case-insensitive
grep -v "health" access.log             # invert
grep -E ' (5[0-9]{2}) ' access.log      # extended regex
grep -rn "spring.datasource" src/       # recursive
grep -c "ERROR" app.log                 # count matching lines
grep -A3 -B2 "NullPointerException" app.log   # context lines

cut -d',' -f2,3 transactions.csv        # columns 2 and 3 of CSV (naive: breaks on quoted commas)
sort | uniq -c | sort -rn | head        # frequency table, top N — memorise this idiom
wc -l file                              # line count
sed -n '100,120p' app.log               # print lines 100–120
sed 's/password=[^&]*/password=***/g' requests.log   # substitute (use -i to edit in place; -i.bak for backup)
awk '{print $1}' access.log             # first whitespace-separated field
awk '$9 >= 500 {c++} END {print c+0}' access.log      # count 5xx in combined log format (status = field 9)
awk -F',' '{sum += $3} END {printf "%.2f\n", sum}' tx.csv   # sum a CSV column
find . -name "*.tmp" -print0 | xargs -0 rm -f   # xargs: turn input lines into arguments; -print0/-0 handle spaces
jq '.status' health.json                # JSON (install jq) — e.g. curl -s .../actuator/health | jq
```

**Example — top 5 endpoints returning 5xx from an nginx access log:**
```bash
awk '$9 ~ /^5/ {print $7}' /var/log/nginx/access.log | sort | uniq -c | sort -rn | head -5
```

**Example — Spring Boot log (default pattern), count ERROR lines per logger:**
```bash
grep ' ERROR ' app.log | awk '{print $NF}' | head    # inspect first; field positions depend on your pattern
```
Always look at a few lines before writing the `awk` — log formats vary. For structured JSON logs (FlowGrid M5 onward), use `jq`.

---

## 12. Environment variables

```bash
env | sort                      # all env vars
echo "$HOME $PATH"
export SPRING_PROFILES_ACTIVE=prod  # visible to child processes
DB_HOST=localhost java -jar app.jar # set for one command only
unset DB_HOST
echo 'export PATH="$HOME/bin:$PATH"' >> ~/.bashrc && source ~/.bashrc
```

Spring Boot relaxed binding: `SPRING_DATASOURCE_URL` → `spring.datasource.url`. That's how Docker/Compose/EC2
configure your apps. Never `export` secrets in `.bashrc` on shared machines; use an `EnvironmentFile` with `chmod 600`, or AWS SSM Parameter Store/Secrets Manager (from Week 8).

---

## 13. SSH, keys, scp

```bash
ssh-keygen -t ed25519 -C "you@laptop"              # creates ~/.ssh/id_ed25519 (+ .pub)
ssh-copy-id deploy@server                          # appends pub key to server's ~/.ssh/authorized_keys
ssh -i ~/.ssh/flowgrid.pem ec2-user@<public-ip>  # EC2 (Amazon Linux user: ec2-user; Ubuntu: ubuntu)
scp ./compose.yaml deploy@server:/opt/flowgrid/  # copy file up
scp deploy@server:/opt/flowgrid/backups/db.sql.gz .   # copy down
rsync -avz --delete ./dist/ deploy@server:/opt/flowgrid/web/   # efficient sync
ssh -L 5433:localhost:5432 deploy@server           # tunnel: local 5433 → server's Postgres 5432
```

```
# ~/.ssh/config
Host flowgrid
  HostName 3.120.x.x
  User ec2-user
  IdentityFile ~/.ssh/flowgrid.pem
# → ssh flowgrid
```

Server hardening basics: key-only auth (`PasswordAuthentication no`), no root login, security group allows 22 only
from your IP. First connection asks you to verify the host key fingerprint → stored in `~/.ssh/known_hosts`.

---

## 14. Processes inside containers (Week 14, ForgeCI)

ForgeCI's worker starts a container per job, runs build steps in it, enforces timeouts, supports cancel, and must always
clean up. The Linux facts underneath:

| Fact | Why it matters for ForgeCI |
|---|---|
| A container is a **process tree** on the host, isolated by namespaces and limited by cgroups | `ps -ef` on the host shows the build's processes; `docker top <c>` shows them per container |
| The container's main process is **PID 1** inside its PID namespace | PID 1 gets no default signal handlers: a shell script as PID 1 may ignore SIGTERM → `docker stop` waits the grace period, then SIGKILLs. `docker run --init` adds a tiny init that forwards signals and reaps zombies |
| `docker stop` = SIGTERM, wait (default 10 s), SIGKILL; `docker kill` = SIGKILL immediately (by default) | cancel = graceful stop with a short grace period; timeout = kill |
| Exit code `128 + N` means killed by signal N | 137 (SIGKILL: timeout kill or **OOM**), 143 (SIGTERM). Distinguish them when classifying *app failure* vs *infra failure* |
| cgroup memory limit exceeded → kernel OOM killer | `docker inspect -f '{{.State.OOMKilled}}' <c>` tells you; a build that OOMs is an app/config failure, not an infra retry |
| `timeout 600 cmd` sends SIGTERM after 600 s (`-k 10` adds a SIGKILL 10 s later); exits 124 on timeout | handy for local experiments; ForgeCI enforces timeouts from the worker via the Docker API instead |
| Killing a parent doesn't necessarily kill its children | kill the whole container (or process group: `kill -TERM -<pgid>`) rather than one PID |
| Files created in a bind-mounted workspace are owned by the container's UID | if the build runs as root, the host workspace ends up root-owned and your cleanup (as a non-root worker user) fails with "Permission denied" |

```bash
docker run -d --name job42 --memory 256m eclipse-temurin:21-jdk sleep 600
docker top job42                                  # processes inside, with host PIDs
docker inspect -f '{{.State.Pid}}' job42          # host PID of the container's PID 1
sudo ls /proc/$(docker inspect -f '{{.State.Pid}}' job42)/ns   # its namespaces
docker stop -t 5 job42; docker inspect -f '{{.State.ExitCode}}' job42   # sleep ignores SIGTERM as PID 1 → killed → 137
docker rm job42
```

---

## Common mistakes

| Mistake | Fix |
|---|---|
| `chmod 777` to "fix" permissions | find the right owner/group; minimum bits |
| `kill -9` first | SIGTERM, wait, then SIGKILL |
| `usermod -G docker user` (no `-a`) | `-aG` |
| `nohup java …` in production | systemd unit or container with restart policy |
| Deleting a huge log a process still writes | space isn't freed; truncate with `: > file` or restart/rotate |
| `grep` a log pattern without looking at the format | `head` the log first |
| Running everything as root | service users + `sudo` for specific commands |

---

## Interview Q&A

<details><summary>What does <code>chmod 754 file</code> mean?</summary>

Owner rwx (7), group r-x (5), others r-- (4). For a directory, `x` means you can traverse into it.
</details>

<details><summary>SIGTERM vs SIGKILL?</summary>

SIGTERM (15) asks the process to terminate and can be handled — the JVM runs shutdown hooks and Spring Boot shuts down
gracefully. SIGKILL (9) is enforced by the kernel and can't be caught; no cleanup. Docker and systemd send SIGTERM
first and SIGKILL after a timeout.
</details>

<details><summary>How do you find which process is using port 8080?</summary>

`sudo ss -ltnp 'sport = :8080'` or `sudo lsof -i :8080` gives the PID; `ps -fp <pid>` shows the command. Then decide
whether to stop it (SIGTERM) or run my app on another port.
</details>

<details><summary>The disk is full. What do you do?</summary>

`df -h` to find the filesystem, `df -i` to rule out inodes, `du -xh --max-depth=1 | sort -h` to drill down. Usual
suspects: logs (`/var/log`, journald), Docker images/volumes/build cache (`docker system df`), core dumps, backups.
Check `lsof +L1` for deleted files still held open. Fix the cause (rotation, retention) not just the symptom.
</details>

<details><summary>Hard link vs symbolic link?</summary>

A hard link is another name for the same inode — same file data, survives deletion of the other name, same filesystem
only. A symlink stores a path to the target; it can cross filesystems and dangle if the target moves.
</details>

<details><summary>How do you make a service start on boot and see its logs?</summary>

Write a systemd unit in `/etc/systemd/system`, `systemctl daemon-reload`, `systemctl enable --now name`, then
`journalctl -u name -f` for logs and `systemctl status name` for state.
</details>

---

## Mastery checklist

- [ ] Explain the FHS directories I use for deploys, configs and logs.
- [ ] Set permissions in octal and symbolic form; fix an SSH key permission error.
- [ ] Create a service user and a deploy user with correct groups.
- [ ] Find a process by port and name; stop it gracefully; explain signals.
- [ ] Write, enable and debug a systemd unit; read logs with `journalctl`.
- [ ] Diagnose DNS vs port vs listener vs firewall with `dig`, `nc`, `ss`, `curl -v`.
- [ ] Produce a top-N report from a log with a pipe of `grep`/`awk`/`sort`/`uniq`.
- [ ] SSH to a box with a config alias, copy files with `scp`/`rsync`, open a tunnel.
- [ ] Explain PID 1 in a container, `docker stop` vs `docker kill`, and exit codes 124/137/143.

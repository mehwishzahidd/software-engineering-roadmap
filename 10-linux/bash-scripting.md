# Bash Scripting

> The deploy-script pattern (§9) is first needed in **Week 8** (FlowGrid M5 deploy); the full treatment is **Week 14** · ≈ 2.5 hours.
> Goal: write small, **safe** automation — a Postgres backup and a deploy script CI can call, reused for every project — and read other people's scripts without fear. For anything over ~150 lines or with complex
> data handling, reach for Python ([19-python/](../19-python/README.md)) instead.
> Lint every script with **ShellCheck** (`shellcheck script.sh`).

---

## 1. Skeleton every script starts from

```bash
#!/usr/bin/env bash
# Purpose: one line on what this does.
set -euo pipefail
IFS=$'\n\t'

main() {
  echo "hello"
}

main "$@"
```

- **Shebang** `#!/usr/bin/env bash` finds `bash` on `PATH`. `#!/bin/sh` means POSIX sh (on Ubuntu that's `dash`) — no arrays, no `[[ ]]`.
- `chmod +x script.sh && ./script.sh` (or `bash script.sh`).

### `set -euo pipefail`

| Flag | Effect | Without it |
|---|---|---|
| `-e` | exit on any command failing (non-zero) | script keeps going after `cd` fails, then `rm -rf *` runs in the wrong dir |
| `-u` | error on unset variables | `rm -rf "$BACKUP_DIR/"` with a typo'd var → `rm -rf /` |
| `-o pipefail` | pipeline fails if **any** command fails | `pg_dump db \| gzip > x.gz` "succeeds" even if `pg_dump` failed |

Caveats: `-e` is ignored inside `if` conditions and `&&`/`||` lists; a command expected to fail needs `|| true`
(`grep -c ERROR app.log || true` — `grep` exits 1 when there are no matches).

---

## 2. Variables and quoting

```bash
name="flowgrid"                   # no spaces around =
readonly APP_DIR="/opt/${name}"     # constant
echo "$name" "${name}-api"          # braces when followed by name characters
today="$(date +%F)"                 # command substitution (prefer $(…) over backticks)
count=$(( 3 + 4 ))                  # arithmetic
port="${PORT:-8080}"                # default if unset/empty
: "${DB_PASSWORD:?DB_PASSWORD must be set}"   # fail fast with message if missing
```

**Quote every expansion: `"$var"`, `"$@"`, `"$(cmd)"`.** Unquoted variables undergo word splitting and globbing:

```bash
file="my report.csv"
rm $file        # tries to delete "my" and "report.csv"
rm "$file"      # correct
```

| Quotes | Behaviour |
|---|---|
| `"double"` | expands `$var`, `$(cmd)`; no splitting/globbing |
| `'single'` | literal, nothing expands |
| `$'…'` | ANSI-C escapes (`$'\n'`) |

Arrays (bash only):

```bash
services=(api worker postgres redis)
for s in "${services[@]}"; do echo "$s"; done
echo "${#services[@]}"   # length
```

---

## 3. Conditionals

```bash
if [[ -f "$file" ]]; then echo "exists"; elif [[ -d "$file" ]]; then echo "dir"; else echo "missing"; fi

[[ -z "$var" ]]        # empty string       [[ -n "$var" ]]  non-empty
[[ "$a" == "$b" ]]     # string equal       [[ "$a" != "$b" ]]
[[ "$env" == prod* ]]  # glob match         [[ "$v" =~ ^[0-9]+$ ]]  regex
(( n > 5 ))            # numeric (or [[ $n -gt 5 ]])
[[ -f f ]] [[ -d d ]] [[ -x f ]] [[ -s f ]]   # file exists / dir / executable / non-empty
[[ cond1 && cond2 ]]   # logical

case "$1" in
  start|up) echo "starting" ;;
  stop)     echo "stopping" ;;
  *)        echo "usage: $0 {start|stop}" >&2; exit 2 ;;
esac
```

Prefer `[[ ]]` over `[ ]` in bash: no word-splitting surprises, supports `&&`, `==` patterns and `=~`.

---

## 4. Loops

```bash
for i in {1..5}; do echo "$i"; done
for f in /var/log/nginx/*.log; do echo "$f: $(wc -l < "$f")"; done
for (( i = 0; i < 3; i++ )); do echo "$i"; done

while read -r line; do                        # read a file line by line (-r: keep backslashes)
  echo "line: $line"
done < urls.txt

attempt=0
until curl -fsS http://localhost:8080/actuator/health > /dev/null; do   # wait for app to be healthy
  (( ++attempt > 30 )) && { echo "not healthy after 60s" >&2; exit 1; }
  sleep 2
done
```

Note: `(( attempt++ ))` returns status 1 when the old value is 0 → with `set -e` the script exits. Use `(( ++attempt ))` or `attempt=$((attempt + 1))`.

---

## 5. Functions, arguments, exit codes

```bash
log()  { printf '%s [%s] %s\n' "$(date -u +%FT%TZ)" "$1" "${*:2}" >&2; }   # logs to stderr
die()  { log ERROR "$*"; exit 1; }

require() {
  local cmd="$1"                              # local: don't leak into global scope
  command -v "$cmd" > /dev/null || die "missing dependency: $cmd"
}

is_healthy() {
  curl -fsS "$1" > /dev/null                  # function's exit status = last command's
}

if is_healthy "http://localhost:8080/actuator/health"; then log INFO "up"; fi
```

| Special | Meaning |
|---|---|
| `$0` | script name |
| `$1 … $9`, `${10}` | positional args |
| `$#` | number of args |
| `"$@"` | all args, each separately quoted (**use this**) |
| `$?` | exit status of last command |
| `$$` | PID of the script |

**Exit codes:** `0` = success, non-zero = failure (`1` general, `2` usage by convention, `126` not executable,
`127` command not found, `128+N` killed by signal N — e.g. `137` = 128+9 SIGKILL, often **OOM-killed containers**,
`143` = 128+15 SIGTERM). Functions return with `return N`; scripts with `exit N`.

---

## 6. Cleanup with `trap`

```bash
tmp="$(mktemp -d)"
cleanup() { rm -rf "$tmp"; }
trap cleanup EXIT              # runs on normal exit, error exit (set -e), and Ctrl+C
trap 'log ERROR "failed at line $LINENO"' ERR
```

---

## 7. Options with `getopts`

```bash
usage() { echo "usage: $0 [-e env] [-t tag] [-n] (dry-run)" >&2; exit 2; }

env="staging"; tag="latest"; dry_run=false
while getopts ":e:t:nh" opt; do
  case "$opt" in
    e) env="$OPTARG" ;;
    t) tag="$OPTARG" ;;
    n) dry_run=true ;;
    h) usage ;;
    :) echo "option -$OPTARG requires a value" >&2; usage ;;
    \?) echo "unknown option -$OPTARG" >&2; usage ;;
  esac
done
shift $((OPTIND - 1))          # remaining positional args in "$@"
```

`getopts` supports short options only (`-e prod`). For long options, parse `"$@"` with a `while`/`case` loop.

---

## 8. Real script #1 — Postgres backup

A reference pattern — adapt names and paths to your project. Runs on a box with Docker, dumps the DB from the Compose `postgres` service, compresses it,
keeps N days, optionally uploads to S3.

```bash
#!/usr/bin/env bash
# backup-db.sh — dump a Compose Postgres, keep N days, optional S3 upload.
# Usage: backup-db.sh [-d backup_dir] [-k keep_days] [-b s3_bucket]
set -euo pipefail
IFS=$'\n\t'

backup_dir="/opt/flowgrid/backups"
keep_days=7
s3_bucket=""
compose_dir="/opt/flowgrid"

log() { printf '%s [%s] %s\n' "$(date -u +%FT%TZ)" "$1" "${*:2}" >&2; }
die() { log ERROR "$*"; exit 1; }
usage() { echo "usage: $0 [-d dir] [-k days] [-b bucket]" >&2; exit 2; }

while getopts ":d:k:b:h" opt; do
  case "$opt" in
    d) backup_dir="$OPTARG" ;;
    k) keep_days="$OPTARG" ;;
    b) s3_bucket="$OPTARG" ;;
    h) usage ;;
    *) usage ;;
  esac
done
[[ "$keep_days" =~ ^[0-9]+$ ]] || die "keep_days must be a number"

command -v docker > /dev/null || die "docker not installed"
mkdir -p "$backup_dir"

ts="$(date -u +%Y%m%dT%H%M%SZ)"
out="${backup_dir}/flowgrid-${ts}.sql.gz"
tmp="${out}.partial"
trap 'rm -f "$tmp"' EXIT

log INFO "dumping database to $out"
# -T: no TTY (needed from cron/CI). Credentials come from the container's own env.
docker compose --project-directory "$compose_dir" exec -T postgres \
  sh -c 'pg_dump --no-owner -U "$POSTGRES_USER" "$POSTGRES_DB"' | gzip -9 > "$tmp"

[[ -s "$tmp" ]] || die "dump is empty"
gzip -t "$tmp" || die "dump is not valid gzip"
mv "$tmp" "$out"                      # atomic rename: never leave a half-written "good" file
log INFO "backup ok: $(du -h "$out" | cut -f1)"

if [[ -n "$s3_bucket" ]]; then
  command -v aws > /dev/null || die "aws cli not installed"
  aws s3 cp "$out" "s3://${s3_bucket}/db-backups/$(basename "$out")" --only-show-errors
  log INFO "uploaded to s3://${s3_bucket}/db-backups/"
fi

log INFO "deleting local backups older than ${keep_days} days"
find "$backup_dir" -name 'flowgrid-*.sql.gz' -type f -mtime +"$keep_days" -print -delete
```

Schedule it (cron, as the `deploy` user — `crontab -e`):

```cron
# m h dom mon dow  command
15 3 * * * /opt/flowgrid/bin/backup-db.sh -b my-flowgrid-backups >> /opt/flowgrid/logs/backup.log 2>&1
```

(Or a systemd timer.) **A backup you haven't restored is not a backup:** test restore with
`gunzip -c file.sql.gz | docker compose exec -T postgres psql -U "$USER" -d flowgrid_restore_test`.

---

## 9. Real script #2 — deploy with health check and rollback

```bash
#!/usr/bin/env bash
# deploy.sh — pull a new image tag and restart the stack, roll back on failed health check.
# Usage: deploy.sh -t <image_tag>
set -euo pipefail

compose_dir="/opt/flowgrid"
health_url="http://localhost:8080/actuator/health"
tag=""

log() { printf '%s [%s] %s\n' "$(date -u +%FT%TZ)" "$1" "${*:2}" >&2; }
die() { log ERROR "$*"; exit 1; }

while getopts ":t:" opt; do
  case "$opt" in
    t) tag="$OPTARG" ;;
    *) die "usage: $0 -t <image_tag>" ;;
  esac
done
[[ -n "$tag" ]] || die "image tag required (-t)"

cd "$compose_dir"
env_file=".env"
previous_tag="$(grep -E '^IMAGE_TAG=' "$env_file" | cut -d= -f2 || true)"
log INFO "deploying tag=$tag (previous=${previous_tag:-none})"

set_tag() { sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$1/" "$env_file"; }

wait_healthy() {
  local attempts=0
  until [[ "$(curl -fsS "$health_url" 2>/dev/null || true)" == *'"status":"UP"'* ]]; do
    attempts=$((attempts + 1))
    (( attempts >= 30 )) && return 1
    sleep 2
  done
}

set_tag "$tag"
docker compose pull api web          # the services built by CI (ForgeCI: api worker ui)
docker compose up -d --remove-orphans

if wait_healthy; then
  log INFO "deploy ok: $tag"
  docker image prune -f > /dev/null      # reclaim disk (see exercises: "why is the disk full")
else
  log ERROR "health check failed; rolling back to ${previous_tag}"
  docker compose logs --tail=100 api >&2 || true
  [[ -n "$previous_tag" ]] || die "no previous tag to roll back to"
  set_tag "$previous_tag"
  docker compose up -d
  wait_healthy || die "rollback also unhealthy — manual intervention needed"
  exit 1                                  # CI must go red even though service is restored
fi
```

Assumes `compose.yaml` uses `image: ghcr.io/<you>/flowgrid-api:${IMAGE_TAG}` ([11-docker/compose.md](../11-docker/compose.md)).

---

## Break it

1. Remove `set -e` and make `cd /nonexistent` the second line of a script whose third line is `ls`. What directory got listed?
2. Remove `pipefail` and make `pg_dump` fail (wrong DB name). The backup "succeeds" with a 20-byte gzip. The `-s`/`gzip -t` checks catch part of it — which part?
3. Unquote `"$file"` with a filename containing a space.
4. Use `(( attempts++ ))` under `set -e` and watch the script exit on the first iteration.
5. Run the backup from cron without `-T` on `docker compose exec` → "the input device is not a TTY".
6. Run `shellcheck` on your first draft. Fix every warning and understand each one.

---

## Common mistakes

| Mistake | Fix |
|---|---|
| Unquoted variables | `"$var"` always |
| No `set -euo pipefail` | first line after shebang |
| Parsing `ls` output | globs: `for f in dir/*` |
| `cd dir; rm -rf *` | `cd dir || exit 1`, or use absolute paths |
| Secrets in the script or `ps`-visible args | env files with `chmod 600`, secret managers |
| Silent failures in cron | redirect output to a log; exit non-zero; alert |
| Writing the final file directly | write to `.partial`, validate, `mv` atomically |

---

## Interview Q&A

<details><summary>What does <code>set -euo pipefail</code> do?</summary>

`-e` exits on the first failing command, `-u` treats unset variables as errors, `pipefail` makes a pipeline fail if any
stage fails rather than only the last. Together they make scripts fail loudly instead of continuing in a bad state.
</details>

<details><summary>Why quote <code>"$var"</code>?</summary>

Unquoted expansions are split on whitespace and glob-expanded, so filenames with spaces or `*` break commands or hit
the wrong files. Quoting keeps the value as one argument.
</details>

<details><summary>What does exit code 137 mean?</summary>

128 + 9: the process was killed by SIGKILL — in containers, very often the OOM killer. 143 is 128 + 15 (SIGTERM).
</details>

<details><summary><code>$@</code> vs <code>$*</code>?</summary>

Quoted `"$@"` expands to each argument as a separate word, preserving spaces; `"$*"` joins all arguments into one string.
Use `"$@"` to forward arguments.
</details>

<details><summary>Walk me through your backup script.</summary>

Strict mode, argument parsing with getopts, dependency checks, `pg_dump` through `docker compose exec -T` piped to gzip
into a `.partial` file, validation (non-empty, valid gzip), atomic rename, optional S3 upload, retention with `find -mtime`.
Scheduled by cron with output to a log, and I tested a restore into a scratch database.
</details>

---

## Mastery checklist

- [ ] Start every script from the skeleton; explain each line of strict mode.
- [ ] Parse options with `getopts` and validate inputs.
- [ ] Use `trap` for cleanup; explain exit codes 0/1/2/127/137/143.
- [ ] Write `backup-db.sh` for one of my projects, schedule it, and **test a restore**.
- [ ] Write `deploy.sh` with health-check wait and rollback.
- [ ] Both scripts pass ShellCheck with zero warnings.

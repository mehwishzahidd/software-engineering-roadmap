# 10 — Linux

> **Basics on Day 1 of Week 1** (terminal, navigation, files — enough for Git, Maven and Java).
> **Deep dive in Week 14** — ForgeCI M1/M2 executes other people's build steps in containers, so processes, signals,
> exit codes, permissions, disk and Bash stop being trivia. See [ROADMAP §6](../ROADMAP.md#6-just-in-time-learning-map).
> In between, you'll use Linux for real when FlowGrid first deploys to EC2 in **Week 8**.

You will SSH into EC2 instances, read container and service logs, find out why a port is taken, why a worker's disk
filled up with build workspaces, why a job was killed with exit code 137, and why a service didn't come back after a
reboot. Interviewers probe Linux mostly through these practical scenarios, plus a few classics (permissions, signals,
processes vs threads).

---

## Files

| File | Content | When |
|---|---|---|
| [commands.md](./commands.md) | Filesystem, navigation, files, permissions, users/groups, processes & signals, systemd, packages, networking tools, disk, text processing, env vars, SSH, logs, processes inside containers | §1–§3 W1; all W14 |
| [bash-scripting.md](./bash-scripting.md) | Shebang, variables, quoting, conditionals, loops, functions, exit codes, `set -euo pipefail`, `getopts`, real backup + deploy scripts | W8 (deploy script), W14 (full) |
| [exercises.md](./exercises.md) | 24 hands-on tasks and incident drills | W1 (1–4), W8 (10, 13), W14 (all) |

---

## Practice environment

Pick one (don't spend more than 20 minutes on setup):

| Option | Notes |
|---|---|
| Native Linux or WSL2 (Ubuntu) | best day-to-day |
| macOS terminal | BSD userland — some flags differ (`sed -i ''`, `ps`, no `ss`/`systemd`). Use a container for Linux-specific parts |
| Docker container | `docker run -it --rm ubuntu:24.04 bash` — disposable; no systemd by default |
| Multipass / a VM | full Ubuntu with systemd; good for `systemctl`/`journalctl` practice |
| An EC2 `t3.micro`/`t4g.micro` | the real target from Week 8; mind [12-aws/cost-safety.md](../12-aws/cost-safety.md) |

---

## Week 1 (≈ 3 h of the learning block)

- [ ] [commands.md §1–§3](./commands.md#1-filesystem-hierarchy): filesystem, navigation, files.
- [ ] §11 basics: pipes, redirection, `grep`.
- [ ] Exercises 1–4.

## Week 14 plan (Linux half; Docker internals is the other half — [11-docker](../11-docker/README.md))

ForgeCI weeks run ≈ 30 h project time; the learning block is ≈ 8 h and shared with Docker internals, OS fundamentals
and GitHub webhooks, so this is ≈ 4 h:

| Block | Content |
|---|---|
| Mon learning (2 h) | commands.md §4–§8: permissions, users, processes, signals, systemd, packages |
| Wed learning (2 h) | §9–§14: networking tools, disk, text processing, SSH, **processes inside containers**; bash-scripting.md skim |
| Project time | Apply immediately: ForgeCI's worker runs steps with timeouts, captures exit codes, kills on cancel, cleans workspaces |
| Sun review | Incident drills 13–20 from [exercises.md](./exercises.md), timed and out loud |

Related CS theory (processes vs threads, scheduling, virtual memory): [14-cs-fundamentals/operating-systems.md](../14-cs-fundamentals/operating-systems.md).

---

## Mapping to projects

| Skill | Used in |
|---|---|
| `ss -ltnp`, `curl -v` | "Why can't I start the API on 8080?" (every project) |
| `grep`/`awk`/`jq` on logs | counting 5xx in FlowGrid's access logs; parsing structured JSON logs |
| `systemctl`, `journalctl` | Docker on EC2 at boot (W8, W13, W19, W23); why a unit failed |
| `ssh`, keys, `scp` | EC2 access from Week 8 |
| Permissions, users | running containers as non-root; `chmod 600` on keys and `.env`; ForgeCI workspace ownership |
| Signals, exit codes | ForgeCI M4: timeout → kill container (137), cancel → SIGTERM then SIGKILL, graceful worker shutdown |
| `df`, `du` | disk full from Docker images, build caches and job workspaces on a ForgeCI worker |
| Bash scripting | Postgres backup script, deploy script invoked by CI (from W8) |

---

## Interview relevance

- File permissions: what does `chmod 750` mean? Why can't `nginx` read your file?
- `SIGTERM` vs `SIGKILL`; how does Spring Boot shut down gracefully? What does exit code 137 mean?
- Process vs thread; what's a zombie? What's special about PID 1 in a container?
- How would you find what's using port 8080? What's filling the disk?
- How do you see logs of a service? How do you make it start at boot?
- Hard link vs symlink; what's an inode?

Résumé defense: [17-resume-tech-defense/linux.md](../17-resume-tech-defense/linux.md).

---

## Resources

- `man <command>`, `<command> --help`, `tldr <command>` (community cheat sheets).
- *The Linux Command Line* (William Shotts) — free online; the best single book for this.
- GNU Bash manual (gnu.org/software/bash/manual); ShellCheck (shellcheck.net) — lint every script.
- systemd docs: `man systemd.service`, `man journalctl`; `man 7 signal`.
- More in [RESOURCES.md](../RESOURCES.md).

---

## Exit criteria (end of Week 14)

- [ ] I can navigate, inspect and edit files on a remote server over SSH without a GUI.
- [ ] I can explain and set permissions in both symbolic and octal form.
- [ ] I can find and stop the process on a port, and explain SIGTERM vs SIGKILL and exit codes 137/143.
- [ ] I can write a systemd unit, enable it, and read its logs with `journalctl`.
- [ ] I can answer "why is the disk full?" in under 5 minutes on a real box.
- [ ] I can extract counts and top-N lists from a log with `grep`/`awk`/`sort`/`uniq`.
- [ ] My Bash scripts use `set -euo pipefail`, quote variables, and pass ShellCheck.
- [ ] [exercises.md](./exercises.md) complete.

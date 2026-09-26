# 10 — Linux

> Shell basics on **Day 1 of Week 1**; deep treatment in **Week 19** alongside Docker, right before P4 PulseWatch
> runs on an EC2 Linux host (Week 21). ≈ 8 hours in Week 19 + continuous use.

You will deploy PulseWatch to an EC2 instance, read its logs over SSH, find out why a port is taken, why the disk
filled up, and why the service didn't start after a reboot. Interviewers probe Linux mostly through these practical
scenarios, plus a few classic questions (permissions, signals, processes vs threads).

---

## Files

| File | Content | Time |
|---|---|---:|
| [commands.md](./commands.md) | Filesystem, navigation, files, permissions, users/groups, processes & signals, systemd, packages, networking tools, disk, text processing, env vars, SSH, logs | 4 h |
| [bash-scripting.md](./bash-scripting.md) | Shebang, variables, quoting, conditionals, loops, functions, exit codes, `set -euo pipefail`, `getopts`, real deploy + backup scripts | 2.5 h |
| [exercises.md](./exercises.md) | 24 hands-on tasks and incident drills | 2 h + |

---

## Practice environment

Pick one (don't spend more than 20 minutes on setup):

| Option | Notes |
|---|---|
| Native Linux or WSL2 (Ubuntu) | best day-to-day |
| macOS terminal | BSD userland — some flags differ (`sed -i ''`, `ps`, no `ss`/`systemd`). Use a container for Linux-specific parts |
| Docker container | `docker run -it --rm ubuntu:24.04 bash` — disposable; no systemd by default |
| Multipass / a VM | full Ubuntu with systemd; good for `systemctl`/`journalctl` practice |
| An EC2 `t3.micro`/`t4g.micro` (Week 21) | the real target; mind [12-aws/cost-safety.md](../12-aws/cost-safety.md) |

---

## Week 19 plan (Linux half)

| Day | Block |
|---|---|
| Mon | commands.md §1–§5: filesystem, files, permissions, users |
| Tue | §6–§8: processes, signals, systemd, journald |
| Thu | §9–§12: networking tools, disk, text processing; exercises 1–12 |
| Sat | bash-scripting.md; write `backup-db.sh` for PulseWatch; exercises 13–24 |
| Sun | Review: incident drills from exercises (timed, out loud) |

Docker is the other half of Week 19: [11-docker/](../11-docker/README.md).

---

## Mapping to projects

| Skill | Used in |
|---|---|
| `ss -ltnp`, `curl -v` | "Why can't I start the API on 8080?" (all projects) |
| `grep`/`awk` on logs | counting 5xx in TicketHold/PulseWatch logs |
| `systemctl`, `journalctl` | running Docker/PulseWatch on EC2; checking why a unit failed |
| `ssh`, keys, `scp` | EC2 access (W21) |
| permissions, users | running containers as non-root; `chmod 600` on keys and `.env` files |
| `df`, `du` | disk full from Docker images / logs on the EC2 host |
| Bash scripting | backup script for Postgres, deploy script invoked by CI (W22) |

---

## Interview relevance

- File permissions: what does `chmod 750` mean? Why can't `nginx` read your file?
- `SIGTERM` vs `SIGKILL`; how does Spring Boot shut down gracefully?
- Process vs thread; what's a zombie? (Also [14-cs-fundamentals/operating-systems.md](../14-cs-fundamentals/operating-systems.md).)
- How would you find what's using port 8080? What's filling the disk?
- How do you see logs of a service? How do you make it start at boot?
- Hard link vs symlink; what's an inode?

Résumé defense: [17-resume-tech-defense/linux.md](../17-resume-tech-defense/linux.md).

---

## Resources

- `man <command>`, `<command> --help`, `tldr <command>` (community cheat sheets).
- *The Linux Command Line* (William Shotts) — free online; the best single book for this.
- GNU Bash manual (gnu.org/software/bash/manual); ShellCheck (shellcheck.net) — lint every script.
- systemd docs: `man systemd.service`, `man journalctl`.
- More in [RESOURCES.md](../RESOURCES.md).

---

## Exit criteria

- [ ] I can navigate, inspect and edit files on a remote server over SSH without a GUI.
- [ ] I can explain and set permissions in both symbolic and octal form.
- [ ] I can find and stop the process on a port, and explain SIGTERM vs SIGKILL.
- [ ] I can write a systemd unit, enable it, and read its logs with `journalctl`.
- [ ] I can answer "why is the disk full?" in under 5 minutes on a real box.
- [ ] I can extract counts and top-N lists from a log with `grep`/`awk`/`sort`/`uniq`.
- [ ] My Bash scripts use `set -euo pipefail`, quote variables, and pass ShellCheck.
- [ ] [exercises.md](./exercises.md) complete.

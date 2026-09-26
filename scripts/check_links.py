#!/usr/bin/env python3
"""Check that every relative Markdown link in the repo points to an existing file or directory.

Usage: python3 scripts/check_links.py   (exit code 1 if any link is broken)
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
FENCE = re.compile(r"^\s*(```|~~~)")


def links_in(path: pathlib.Path):
    in_fence = False
    for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if FENCE.match(line):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        line = re.sub(r"`[^`]*`", "", line)  # ignore inline code
        for target in LINK.findall(line):
            yield lineno, target


def main() -> int:
    broken = []
    for md in sorted(ROOT.rglob("*.md")):
        if any(part in {".git", "node_modules", "target"} for part in md.parts):
            continue
        for lineno, target in links_in(md):
            if re.match(r"^[a-z][a-z0-9+.-]*:", target) or target.startswith("#"):
                continue  # external URL, mailto:, or in-page anchor
            file_part = target.split("#", 1)[0]
            if not file_part:
                continue
            resolved = (md.parent / file_part).resolve()
            if not resolved.exists():
                broken.append(f"{md.relative_to(ROOT)}:{lineno}: {target}")
    for b in broken:
        print(b)
    print(f"{len(broken)} broken link(s)" if broken else "All relative links OK")
    return 1 if broken else 0


if __name__ == "__main__":
    sys.exit(main())

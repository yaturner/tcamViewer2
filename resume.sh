#!/usr/bin/env bash
# resume.sh — recap project state and continue a Claude Code session on tcamViewer2.
#
# Claude Code automatically loads this project's persistent memory
# (~/.claude/projects/-home-yaturner-Documents-GitHub-tcamViewer2/memory/MEMORY.md)
# at the start of any session run from this directory — there is no separate
# "restore" step required. This script is a convenience: it prints a quick
# recap (recent commits, working tree status, memory index) and then launches
# a fresh `claude` session primed to read that memory and pick up where the
# last session left off.
#
# Usage: ./resume.sh

set -euo pipefail
cd "$(dirname "$0")"

MEMORY_DIR="$HOME/.claude/projects/-home-yaturner-Documents-GitHub-tcamViewer2/memory"

echo "== Recent commits =="
git log --oneline -10
echo

echo "== Working tree status =="
git status --short
echo

echo "== Memory index =="
if [ -f "$MEMORY_DIR/MEMORY.md" ]; then
    cat "$MEMORY_DIR/MEMORY.md"
else
    echo "(no memory index found at $MEMORY_DIR)"
fi
echo

echo "== Launching Claude =="
exec claude "Resume work on tcamViewer2. Read MEMORY.md and the recent git log first, then give me a short recap of where things stand and ask what to work on next."

#!/usr/bin/env bash
# worktree-setup.sh <n> <worktree>
# Prepares a fresh worktree for the cook and the gates.
# stdout: `export` lines only (the station evals them); everything else goes to stderr.
# Ports, when the project needs some, derive from <n> — never hard-coded.
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <n> <worktree>" >&2
  exit 2
fi
n=$1
worktree=$2

case "$n" in
  ''|*[!0-9]*) echo "worktree-setup: <n> must be a non-negative integer, got '$n'" >&2; exit 2 ;;
esac
if [ ! -d "$worktree" ]; then
  echo "worktree-setup: no such directory: $worktree" >&2
  exit 2
fi
worktree=$(cd "$worktree" && pwd)

# The repository holds no code yet: no dependency to install, no build, no service.
# A ticket that adds a stack adds its install/build steps here (output to stderr).
echo "worktree-setup: nothing to install or build in $worktree (n=$n)" >&2

printf 'export SECONDES_WORKTREE=%q\n' "$worktree"

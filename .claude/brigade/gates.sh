#!/usr/bin/env bash
# gates.sh <worktree>
# Exit code is the verdict of the pass: 0 green, anything else red.
# No screen, no service left running, no timing-dependent check.
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <worktree>" >&2
  exit 2
fi
worktree=$1
if [ ! -d "$worktree" ]; then
  echo "gates: no such directory: $worktree" >&2
  exit 2
fi
cd "$worktree"

status=0

# 1. Every shell script of the repository parses.
while IFS= read -r -d '' f; do
  if ! bash -n "$f"; then
    echo "gates: syntax error in $f" >&2
    status=1
  fi
done < <(git ls-files -z --cached --others --exclude-standard -- '*.sh')

# 2. The brigade scripts exist and are executable.
for f in .claude/brigade/gates.sh .claude/brigade/worktree-setup.sh; do
  if [ ! -x "$f" ]; then
    echo "gates: $f is missing or not executable" >&2
    status=1
  fi
done

# 3. .claude/brigade/reseau: one host per line, optional `*.` prefix and `:port` suffix,
#    never an address; `#` comments and blank lines allowed.
reseau=.claude/brigade/reseau
if [ ! -f "$reseau" ]; then
  echo "gates: $reseau is missing" >&2
  status=1
else
  lineno=0
  while IFS= read -r line || [ -n "$line" ]; do
    lineno=$((lineno + 1))
    entry=${line%%#*}
    entry=$(printf '%s' "$entry" | tr -d '[:space:]')
    [ -z "$entry" ] && continue
    host=${entry#\*.}
    host=${host%:*}
    if [[ "$entry" == *:* ]] && ! [[ "${entry##*:}" =~ ^[0-9]+$ ]]; then
      echo "gates: $reseau:$lineno: bad port in '$entry'" >&2
      status=1
    elif [[ "$host" =~ ^[0-9.]+$ ]] || [[ "$entry" == *\[* ]]; then
      echo "gates: $reseau:$lineno: '$entry' is an address, a host name is required" >&2
      status=1
    elif ! [[ "$host" =~ ^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)+$ ]]; then
      echo "gates: $reseau:$lineno: '$entry' is not a host name" >&2
      status=1
    fi
  done < "$reseau"
fi

# 4. The Android unit tests pass. JAVA_HOME, ANDROID_HOME and GRADLE_USER_HOME come
#    from worktree-setup.sh; no Gradle daemon is left running.
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "gates: JAVA_HOME is not set to a JDK (eval the output of worktree-setup.sh)" >&2
  status=1
elif [ -z "${ANDROID_HOME:-}" ] || [ ! -d "$ANDROID_HOME" ]; then
  echo "gates: ANDROID_HOME is not set to an Android SDK (eval the output of worktree-setup.sh)" >&2
  status=1
elif ! ./gradlew --no-daemon --console=plain test; then
  echo "gates: ./gradlew test failed" >&2
  status=1
fi

if [ "$status" -eq 0 ]; then
  echo "gates: green" >&2
else
  echo "gates: red" >&2
fi
exit "$status"

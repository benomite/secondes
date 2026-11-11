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

# JDK 17: JAVA_HOME if it already points to one, else the usual install places.
java_home=""
for candidate in "${JAVA_HOME:-}" \
  "$(/usr/libexec/java_home -v 17 2>/dev/null || true)" \
  /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  /usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  /usr/lib/jvm/java-17-openjdk* /usr/lib/jvm/temurin-17*; do
  if [ -n "$candidate" ] && [ -x "$candidate/bin/java" ] \
    && "$candidate/bin/java" -version 2>&1 | grep -q 'version "17'; then
    java_home=$candidate
    break
  fi
done
if [ -z "$java_home" ]; then
  echo "worktree-setup: no JDK 17 found (set JAVA_HOME, or brew install openjdk@17)" >&2
  exit 1
fi

# Android SDK: ANDROID_HOME / ANDROID_SDK_ROOT if set, else the usual install places.
android_home=""
for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" \
  "$HOME/Library/Android/sdk" /opt/homebrew/share/android-commandlinetools \
  /usr/local/share/android-commandlinetools "$HOME/Android/Sdk"; do
  if [ -n "$candidate" ] && [ -d "$candidate/platforms" ]; then
    android_home=$candidate
    break
  fi
done
if [ -z "$android_home" ]; then
  echo "worktree-setup: no Android SDK found (set ANDROID_HOME to an SDK with platform 35)" >&2
  exit 1
fi

# Gradle's cache lives in the worktree: no write outside it, nothing shared between cooks.
gradle_user_home="$worktree/.gradle-user"
mkdir -p "$gradle_user_home"

echo "worktree-setup: JDK $java_home, Android SDK $android_home (n=$n)" >&2
(
  cd "$worktree"
  JAVA_HOME=$java_home ANDROID_HOME=$android_home GRADLE_USER_HOME=$gradle_user_home \
    ./gradlew --no-daemon --console=plain compileDebugUnitTestKotlin
) >&2

printf 'export SECONDES_WORKTREE=%q\n' "$worktree"
printf 'export JAVA_HOME=%q\n' "$java_home"
printf 'export ANDROID_HOME=%q\n' "$android_home"
printf 'export GRADLE_USER_HOME=%q\n' "$gradle_user_home"

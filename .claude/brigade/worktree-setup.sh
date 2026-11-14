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
android_user_home="$worktree/.android-user"
mkdir -p "$gradle_user_home/init.d" "$gradle_user_home/tmp" "$android_user_home"

# Temporary files and the Kotlin compiler stay in the build's JVM and in the worktree.
cat > "$gradle_user_home/gradle.properties" <<EOF
systemProp.java.io.tmpdir=$gradle_user_home/tmp
kotlin.compiler.execution.strategy=in-process
EOF

# Gradle ignores HTTPS_PROXY / HTTP_PROXY / NO_PROXY: this init script hands them to it
# each time Gradle runs, so the proxy's credentials are never written to a file.
cat > "$gradle_user_home/init.d/proxy.gradle" <<'EOF'
def env = System.getenv()
def useProxy = { String scheme, String value ->
  if (!value) return
  def uri = new URI(value.contains('://') ? value : "http://$value")
  if (!uri.host) return
  System.setProperty("${scheme}.proxyHost", uri.host)
  System.setProperty("${scheme}.proxyPort", String.valueOf(uri.port != -1 ? uri.port : 80))
  if (uri.rawUserInfo) {
    def parts = uri.rawUserInfo.split(':', 2)
    System.setProperty("${scheme}.proxyUser", URLDecoder.decode(parts[0], 'UTF-8'))
    if (parts.length > 1) System.setProperty("${scheme}.proxyPassword", URLDecoder.decode(parts[1], 'UTF-8'))
  }
}
useProxy('https', env.HTTPS_PROXY ?: env.https_proxy)
useProxy('http', env.HTTP_PROXY ?: env.http_proxy)
def noProxy = env.NO_PROXY ?: env.no_proxy
if (noProxy) {
  def hosts = noProxy.split(',')*.trim().findAll { it }.collect { it.startsWith('.') ? "*$it" : it }
  System.setProperty('http.nonProxyHosts', hosts.join('|'))
}
EOF

echo "worktree-setup: JDK $java_home, Android SDK $android_home (n=$n)" >&2
(
  cd "$worktree"
  JAVA_HOME=$java_home ANDROID_HOME=$android_home GRADLE_USER_HOME=$gradle_user_home \
    ANDROID_USER_HOME=$android_user_home ./gradlew --no-daemon --console=plain compileDebugUnitTestKotlin
) >&2

printf 'export SECONDES_WORKTREE=%q\n' "$worktree"
printf 'export JAVA_HOME=%q\n' "$java_home"
printf 'export ANDROID_HOME=%q\n' "$android_home"
printf 'export GRADLE_USER_HOME=%q\n' "$gradle_user_home"
printf 'export ANDROID_USER_HOME=%q\n' "$android_user_home"

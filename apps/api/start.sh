#!/usr/bin/env bash
# Startup guard: verify Java 21 is available before launching Spring Boot.

set -euo pipefail

# ── 1. Check that java is on PATH ────────────────────────────────────────────
if ! command -v java &>/dev/null; then
  echo "ERROR: 'java' not found on PATH." >&2
  echo "       Java 21 is required to run the API." >&2
  echo "       Check that the Nix environment is loaded and jdk21_headless is installed." >&2
  exit 1
fi

# ── 2. Resolve JAVA_HOME from the java binary ─────────────────────────────────
export JAVA_HOME
JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(which java)")")")

# ── 3. Verify the major version is 21 ────────────────────────────────────────
JAVA_VERSION_OUTPUT=$(java -version 2>&1 || true)
JAVA_MAJOR=$(echo "$JAVA_VERSION_OUTPUT" \
  | grep -oE '"([0-9]+)' \
  | head -1 \
  | tr -d '"')

if [[ -z "$JAVA_MAJOR" ]]; then
  echo "ERROR: Could not parse Java version from: $JAVA_VERSION_OUTPUT" >&2
  exit 1
fi

if [[ "$JAVA_MAJOR" -ne 21 ]]; then
  echo "ERROR: Java 21 is required, but found Java ${JAVA_MAJOR}." >&2
  echo "       Version output: $JAVA_VERSION_OUTPUT" >&2
  echo "       Check that jdk21_headless is listed in .replit [nix] packages." >&2
  exit 1
fi

echo "Java ${JAVA_MAJOR} detected (JAVA_HOME=${JAVA_HOME}). Starting Spring Boot..."

# ── 4. Launch the application ─────────────────────────────────────────────────
cd "$(dirname "$0")"
exec mvn spring-boot:run

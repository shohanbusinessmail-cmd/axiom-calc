#!/usr/bin/env bash
# =============================================================================
#  Axiom Calc - Kotlin syntax gate
# =============================================================================
#  Compiles every Kotlin source in the project and fails if kotlinc reports a
#  SYNTAX error.
#
#  Why this exists
#  ---------------
#  This sandbox has a real Kotlin compiler but no Android SDK, so androidx and
#  Compose types cannot be resolved and a full semantic check is impossible:
#  compiling the Android sources here produces thousands of "unresolved
#  reference" diagnostics that mean nothing. Those are ignored on purpose.
#
#  Syntax errors are different. They are decided by the parser before any type
#  is resolved, so they are just as visible here as they are in CI - and CI only
#  finds them after a five minute Gradle run. A stray closing brace left behind
#  by an edit cost exactly that: `:app:kspDebugAndroidTestKotlin FAILED`,
#  "Expecting a top level declaration", and a wasted emulator job.
#
#  The filter is `error: syntax error`, the parser's own diagnostic prefix, not
#  a hand-maintained list of semantic messages to exclude. An exclusion list
#  silently swallows anything nobody thought of; this pattern only ever matches
#  the parser.
#
#  Usage: bash tools/validate_syntax.sh
#  Exit status 0 = no syntax errors, 1 = syntax errors found, 2 = no compiler.
# =============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! command -v kotlinc >/dev/null 2>&1; then
  echo "SKIP - kotlinc is not on PATH; the syntax gate cannot run here."
  echo "       CI still compiles every source with the Android SDK."
  exit 2
fi

echo "========================================================================"
echo "Axiom Calc - Kotlin syntax validation"
echo "========================================================================"

OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

# Every Kotlin source, main + unit tests + instrumented tests.
mapfile -t SOURCES < <(find app/src -name '*.kt' | sort)
mapfile -t SHIM < <(find verification/shim -name '*.kt' 2>/dev/null | sort)
ALL=("${SOURCES[@]}" "${SHIM[@]}")
echo "Kotlin sources parsed ..... ${#ALL[@]}"

# -nowarn keeps the output to diagnostics that matter. Compilation is expected
# to fail on unresolved Android types; only the parser output is inspected.
kotlinc -nowarn -d "$OUT/classes" "${ALL[@]}" > "$OUT/log" 2>&1

SYNTAX="$(grep -E "error: syntax error" "$OUT/log" | sort -u)"
TOTAL_ERRORS="$(grep -cE "error:" "$OUT/log" || true)"

echo "Total kotlinc diagnostics . $TOTAL_ERRORS (unresolved Android types expected)"
if [ -n "$SYNTAX" ]; then
  COUNT="$(printf '%s\n' "$SYNTAX" | wc -l)"
  echo "Syntax errors ............. $COUNT"
  echo "------------------------------------------------------------------------"
  printf '%s\n' "$SYNTAX" | head -40
  echo "------------------------------------------------------------------------"
  echo "RESULT: FAIL - $COUNT syntax error(s). These are parser failures, not"
  echo "        missing-dependency noise, and they will fail CI the same way."
  exit 1
fi

echo "Syntax errors ............. 0"
echo "------------------------------------------------------------------------"
echo "RESULT: PASS - every Kotlin source parses. Semantic correctness against"
echo "        the Android SDK is verified by CI, not here."
exit 0

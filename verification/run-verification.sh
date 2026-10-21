#!/usr/bin/env bash
# ===========================================================================
#  Axiom Calc - standalone verification run
# ===========================================================================
#  Compiles the pure-Kotlin core (math/, core/, domain/) together with the REAL
#  unit tests that live in app/src/test, plus the documented JUnit4-compatible
#  shim harness, and executes them on the JVM.
#
#  This is NOT a substitute for `./gradlew test`: it is the way to obtain
#  executed test evidence in environments where the Android SDK and Maven
#  Central are unreachable. On a normal machine run `./gradlew test` instead;
#  the same test classes execute there under real JUnit 4.
#
#  Usage:
#    verification/run-verification.sh                # run everything
#    verification/run-verification.sh Parser         # run classes matching "Parser"
# ===========================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="${AXIOM_TOOLCHAIN_DIR:-$HOME/.cache/axiomtool}"
FILTER="${1:-}"

if [[ ! -x "$TOOLS/jdk17/bin/java" || ! -x "$TOOLS/kotlinc/bin/kotlinc" ]]; then
  bash "$ROOT/tools/bootstrap-verification-toolchain.sh"
fi

export JAVA_HOME="$TOOLS/jdk17"
export PATH="$JAVA_HOME/bin:$TOOLS/kotlinc/bin:$PATH"

OUT="$ROOT/verification/.out/classes"
rm -rf "$OUT"
mkdir -p "$OUT"

# Only the Android-free subset is compiled here. Three packages are excluded
# because they depend on artifacts this harness cannot resolve offline:
#   domain/repository  -> kotlinx.coroutines Flow contracts
#   core/ui            -> Compose + Android resources (R, @StringRes)
#   core/design        -> Compose Material 3 theme objects
# Everything else (math/, core/errors, core/formatting, domain/model,
# domain/calculator, domain/formula, domain/solver, domain/library) is compiled
# and tested here, and the excluded packages are covered by the instrumented
# suite documented in TESTING.md.
mapfile -t CORE_SOURCES < <(
  find "$ROOT/app/src/main/java/com/shohankhan/axiomcalc/math" \
       "$ROOT/app/src/main/java/com/shohankhan/axiomcalc/core/errors" \
       "$ROOT/app/src/main/java/com/shohankhan/axiomcalc/core/formatting" \
       "$ROOT/app/src/main/java/com/shohankhan/axiomcalc/domain/model" \
       "$ROOT/app/src/main/java/com/shohankhan/axiomcalc/domain/calculator" \
       "$ROOT/app/src/main/java/com/shohankhan/axiomcalc/domain/formula" \
       "$ROOT/app/src/main/java/com/shohankhan/axiomcalc/domain/solver" \
       "$ROOT/app/src/main/java/com/shohankhan/axiomcalc/domain/library" \
       -name '*.kt' 2>/dev/null | sort
)
mapfile -t TEST_SOURCES < <(find "$ROOT/app/src/test" -name '*.kt' 2>/dev/null | sort)
mapfile -t SHIM_SOURCES < <(find "$ROOT/verification/shim" -name '*.kt' | sort)

TOTAL=$(( ${#CORE_SOURCES[@]} + ${#TEST_SOURCES[@]} + ${#SHIM_SOURCES[@]} ))
echo ">> compiling $TOTAL Kotlin sources (core=${#CORE_SOURCES[@]} tests=${#TEST_SOURCES[@]} shim=${#SHIM_SOURCES[@]})"

kotlinc -nowarn -jvm-target 17 \
  "${CORE_SOURCES[@]}" "${TEST_SOURCES[@]}" "${SHIM_SOURCES[@]}" \
  -d "$OUT" 2> >(grep -v "^warning:" >&2 || true)

echo ">> running tests"
exec java -cp "$OUT:$TOOLS/kotlinc/lib/kotlin-stdlib.jar" JUnitRunnerKt "$OUT" "$FILTER"

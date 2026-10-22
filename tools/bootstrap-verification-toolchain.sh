#!/usr/bin/env bash
# ===========================================================================
#  Axiom Calc - bootstrap the standalone verification toolchain
# ===========================================================================
#  The repository is built with the Gradle wrapper on any normal machine. This
#  script exists only for restricted environments (such as the authoring
#  sandbox) where services.gradle.org, dl.google.com and Maven Central are not
#  reachable, so `./gradlew test` cannot run at all.
#
#  It obtains, from the two registries that ARE reachable there (PyPI and the
#  npm registry):
#     * Temurin JDK 17   via the `jdk4py` wheel   (files.pythonhosted.org)
#     * Kotlin compiler  via the `kotlin-compiler` tarball (registry.npmjs.org)
#  The versions match the project's documented toolchain exactly:
#     JDK 17 and Kotlin 2.0.21.
#
#  Nothing downloaded here is part of the application; it is only used to
#  compile and execute the pure-Kotlin core plus its real unit tests.
# ===========================================================================
set -euo pipefail

TOOLS="${AXIOM_TOOLCHAIN_DIR:-$HOME/.cache/axiomtool}"
JDK_VERSION="17.0.9.2"
KOTLIN_VERSION="2.0.21"

if [[ -x "$TOOLS/jdk17/bin/java" && -x "$TOOLS/kotlinc/bin/kotlinc" ]]; then
  echo "toolchain already present at $TOOLS"
  "$TOOLS/jdk17/bin/java" -version 2>&1 | head -1
  exit 0
fi

mkdir -p "$TOOLS"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo ">> downloading Temurin JDK ${JDK_VERSION} (jdk4py wheel) ..."
JDK_URL="$(curl -fsS "https://pypi.org/pypi/jdk4py/${JDK_VERSION}/json" | python3 -c '
import sys, json
d = json.load(sys.stdin)
for u in d["urls"]:
    if "manylinux" in u["filename"] and "x86_64" in u["filename"]:
        print(u["url"]); break
')"
curl -fsSL -o "$WORK/jdk.whl" "$JDK_URL"
mkdir -p "$WORK/jdkwhl"
( cd "$WORK/jdkwhl" && python3 -m zipfile -e "$WORK/jdk.whl" . )
mv "$WORK/jdkwhl/jdk4py/java-runtime" "$TOOLS/jdk17"

echo ">> downloading Kotlin compiler ${KOTLIN_VERSION} (npm tarball) ..."
curl -fsSL -o "$WORK/kc.tgz" "https://registry.npmjs.org/kotlin-compiler/-/kotlin-compiler-${KOTLIN_VERSION}.tgz"
mkdir -p "$WORK/kc"
tar xzf "$WORK/kc.tgz" -C "$WORK/kc"
mv "$WORK/kc/package" "$TOOLS/kotlinc"

# The jdk4py wheel is a zip, and extracting it does not preserve the execute
# bit on the launcher or the shared libraries. Without this the very next line
# fails with "Permission denied" on a clean environment, which is how this
# script was found to be broken: it had only ever been run where the toolchain
# was already in place.
echo ">> fixing execute permissions on the extracted JDK ..."
chmod -R u+x "$TOOLS/jdk17/bin" 2>/dev/null || true
find "$TOOLS/jdk17" \( -name '*.so' -o -name '*.dylib' \) -exec chmod u+x {} + 2>/dev/null || true
chmod -R u+x "$TOOLS/kotlinc/bin" 2>/dev/null || true

echo ">> verifying ..."
"$TOOLS/jdk17/bin/java" -version 2>&1 | head -1
JAVA_HOME="$TOOLS/jdk17" PATH="$TOOLS/jdk17/bin:$TOOLS/kotlinc/bin:$PATH" kotlinc -version 2>&1 | tail -1
echo "toolchain ready at $TOOLS"

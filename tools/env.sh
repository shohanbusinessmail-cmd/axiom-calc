# Shared toolchain env for verification (sourced by CI-less environments).
export JAVA_HOME="${AXIOM_TOOLCHAIN_DIR:-$HOME/.cache/axiomtool}/jdk17"
export PATH="$JAVA_HOME/bin:${AXIOM_TOOLCHAIN_DIR:-$HOME/.cache/axiomtool}/kotlinc/bin:$PATH"

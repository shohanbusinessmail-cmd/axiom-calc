# Axiom Calc — Third-Party Notices

Every third-party component this project depends on, its licence, and what it is
used for. Nothing here is bundled that is not listed.

---

## 1. Runtime dependencies (shipped inside the APK)

All of the following are distributed under the **Apache License 2.0** unless
stated otherwise. Apache 2.0 full text: <https://www.apache.org/licenses/LICENSE-2.0>.

| Component | Version | Purpose |
|-----------|---------|---------|
| Kotlin standard library (`org.jetbrains.kotlin:kotlin-stdlib`) | 2.0.21 | Language runtime |
| Kotlin coroutines (`org.jetbrains.kotlinx:kotlinx-coroutines-core`, `-android`) | 1.8.1 | `Flow`, `StateFlow`, structured concurrency |
| `androidx.core:core-ktx` | 1.15.0 | AndroidX core Kotlin extensions |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.7 | Lifecycle-aware scopes |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.8.7 | `collectAsStateWithLifecycle` |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.7 | `viewModel()` in Compose |
| `androidx.activity:activity-compose` | 1.9.3 | `ComponentActivity.setContent`, `enableEdgeToEdge` |
| `androidx.compose.ui:ui` | BOM 2024.10.01 | Compose runtime and input/semantics |
| `androidx.compose.ui:ui-graphics` | BOM 2024.10.01 | Drawing primitives |
| `androidx.compose.ui:ui-text`, `ui-unit`, `ui-geometry`, `ui-util` | BOM 2024.10.01 | Transitive Compose modules |
| `androidx.compose.foundation:foundation`, `foundation-layout` | BOM 2024.10.01 | Layout, scrolling, gestures, `combinedClickable` |
| `androidx.compose.material3:material3` | BOM 2024.10.01 | Material 3 components |
| `androidx.compose.material3:material3-window-size-class` | BOM 2024.10.01 | Window size classes |
| `androidx.compose.runtime:runtime`, `runtime-saveable` | BOM 2024.10.01 | Composition and saved state |
| `androidx.navigation:navigation-compose` | 2.8.4 | Navigation graph, back stack, typed nav args |
| `androidx.room:room-runtime`, `room-ktx` | 2.6.1 | SQLite ORM for history, saved calculations, favourites, recents |
| `androidx.room:room-common` | 2.6.1 | Room annotations |
| `androidx.sqlite:sqlite`, `sqlite-framework` | 2.4.x (transitive via Room) | SQLite access |
| `androidx.datastore:datastore-preferences` | 1.1.1 | Settings storage |
| `androidx.datastore:datastore-core` | 1.1.1 | DataStore runtime (transitive) |
| `androidx.work:work-runtime-ktx` | 2.9.1 | Best-effort periodic retention cleanup |
| `androidx.annotation:annotation`, `androidx.collection:collection`, `androidx.arch.core:core-*` | transitive | Support libraries |

Room's generated code is produced at build time by KSP from annotations in this
repository; the generator itself is not shipped.

---

## 2. Build-time tools (not shipped)

| Tool | Version | Licence | Purpose |
|------|---------|---------|---------|
| Gradle | 8.11.1 | Apache 2.0 | Build system; wrapper jar committed |
| Android Gradle Plugin | 8.7.3 | Apache 2.0 | Android build |
| Kotlin compiler | 2.0.21 | Apache 2.0 | Compilation |
| KSP (`com.google.devtools.ksp`) | 2.0.21-1.0.28 | Apache 2.0 | Room code generation |
| Kotlin Compose compiler plugin | 2.0.21 | Apache 2.0 | `@Composable` transformation |
| Android SDK platform 35 + build-tools | 35 | Android Software Development Kit License | Compile/target SDK |
| `aapt2`, `d8`, `zipalign`, `apksigner` | from build-tools 35 | Apache 2.0 / Android SDK License | Resource compilation, dexing, alignment, signing |

### Tools used only in the authoring sandbox

These were needed because the sandbox had no JDK and no Kotlin compiler, and its
network permitted only `github.com`, `api.github.com`, `codeload.github.com`,
`registry.npmjs.org`, `pypi.org` and `files.pythonhosted.org`. **None of them is
shipped in the APK, none is a project dependency, and none appears in
`gradle/libs.versions.toml`.** They live outside the repository in
`~/.cache/axiomtool/`.

| Tool | Source | Licence | Note |
|------|--------|---------|------|
| Eclipse Temurin JDK 17.0.9+9 | PyPI `jdk4py` 17.0.9.2 (37,815,804 bytes) | GPLv2 with Classpath Exception | Runtime-only distribution (no `javac`); the Classpath Exception permits compiling and running non-GPL code |
| Kotlin compiler 2.0.21 | npm `kotlin-compiler` 2.0.21 (85,728,294 bytes) | Apache 2.0 | The complete official compiler distribution |
| Gradle wrapper files | `gradle/gradle` at tag `v8.11.1`, via the GitHub contents API | Apache 2.0 | Blob SHA-1 verified against the API |

`tools/bootstrap-verification-toolchain.sh` fetches the two sandbox tools
idempotently and is used only by `verification/run-verification.sh`. A normal
build on a normal machine uses neither.

### CI actions (GitHub-hosted, not shipped)

`.github/workflows/android.yml` uses these published actions. They run on
GitHub's infrastructure and are never part of the APK.

| Action | Licence | Purpose |
|--------|---------|---------|
| `actions/checkout@v4` | MIT | Fetch the repository |
| `actions/setup-java@v4` | MIT | Install Temurin JDK 17 |
| `gradle/actions/setup-gradle@v4` | Apache 2.0 | Gradle caching and wrapper validation |
| `actions/upload-artifact@v4` | MIT | Publish the APK, test and lint reports |
| `reactivecircus/android-emulator-runner@v2` | MIT | Boot an emulator for instrumented tests |

---

## 3. Test-only dependencies (not shipped)

| Component | Version | Licence | Scope |
|-----------|---------|---------|-------|
| JUnit 4 (`junit:junit`) | 4.13.2 | **Eclipse Public License 1.0** | `testImplementation` |
| Hamcrest core | 1.3 (transitive via JUnit) | BSD 3-Clause | `testImplementation` |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.8.1 | Apache 2.0 | `testImplementation` |
| `app.cash.turbine:turbine` | 1.1.0 | Apache 2.0 | `testImplementation` |
| `androidx.test.ext:junit` | 1.2.1 | Apache 2.0 | `androidTestImplementation` |
| `androidx.test.espresso:espresso-core` | 3.6.1 | Apache 2.0 | `androidTestImplementation` |
| `androidx.compose.ui:ui-test-junit4` | BOM 2024.10.01 | Apache 2.0 | `androidTestImplementation` |
| `androidx.compose.ui:ui-test-manifest` | BOM 2024.10.01 | Apache 2.0 | `debugImplementation` |
| `androidx.room:room-testing` | 2.6.1 | Apache 2.0 | `androidTestImplementation` |
| `androidx.work:work-testing` | 2.9.1 | Apache 2.0 | `androidTestImplementation` |

EPL 1.0 text: <https://www.eclipse.org/legal/epl-v10.html>. JUnit and Hamcrest
are test-scope only and are excluded from release artifacts by Gradle.

### The JUnit shim — disclosure

`verification/shim/JUnitShim.kt` and `verification/shim/JUnitRunner.kt` in this
repository are **original code written for this project**, not a vendored copy of
JUnit. They re-declare the small part of the JUnit 4 API the tests use
(`@Test`, `@Before`, `@After`, `@Ignore`, `Assert`) and run it reflectively, so
that the 183 committed unit tests could actually be executed in an environment
where `repo1.maven.org` was unreachable and the real JUnit jar could not be
downloaded. The shim is confined to `verification/`; nothing in `app/src/main` or
`app/src/test` depends on it, and under Gradle the same test classes run against
real JUnit 4.13.2.

---

## 4. Assets

**No third-party assets are used.** Specifically:

- **Icons:** all 28 vector drawables in `app/src/main/res/drawable/` were
  hand-authored in this repository (path data written directly, generated by
  `tools/generate_launcher_icons.py` for the launcher, validated by
  `tools/validate_resources.py`). No Material Symbols package, no icon font, no
  third-party icon set is a dependency.
- **Launcher PNGs:** generated by `tools/generate_launcher_icons.py` from the
  hand-authored sigma mark; no stock artwork.
- **Fonts:** none are bundled. Typography uses `FontFamily.Default`, i.e. the
  device's own system font, so there is no font licence obligation and the system
  font scale is honoured.
- **Emoji:** none are used anywhere in the UI. Mathematical glyphs such as `× ÷ ±
  π τ φ √ ∛ Σ ⌫ ↶ ↷` are Unicode characters rendered by the system font, not
  emoji and not third-party assets. Action keys use the hand-authored vectors.
- **Sounds:** none.

---

## 5. Code derived from specifications, not from other projects

The mathematics in `app/src/main/java/com/shohankhan/axiomcalc/math/` is original
code implementing published, unencumbered algorithms — Euclid's algorithm,
Miller–Rabin primality testing, LU decomposition and Gauss–Jordan elimination with
partial pivoting, Newton's method, the stable quadratic formula, Heron's formula,
proleptic-Gregorian day-number arithmetic, and DECIMAL128 decimal arithmetic via
`java.math` in the JDK. No third-party mathematics library is used: there is no
Apache Commons Math, no `JScience`, no Symja, no JAMA, no Eigen binding, and no
`java.time` dependency for date arithmetic.

Constant values and their exactness classifications in
`math/constants/ConstantRegistry.kt` are the 2019 SI redefinition values and the
CODATA 2018 recommended values, which are published reference data and not
copyrighted expression. `G` is flagged in code as **measured** with its relative
standard uncertainty, so the app never presents a measured constant as exact.

---

## 6. Obligations summary

| Licence | Obligation | How it is met |
|---------|------------|---------------|
| Apache 2.0 | Retain copyright, licence and notice; state changes | This file is the notice. No Apache-2.0 source file was modified; all such components are consumed as unmodified published artifacts |
| EPL 1.0 (JUnit) | Make the notice available | Listed above; test scope only, not distributed in the APK |
| BSD 3-Clause (Hamcrest) | Retain the notice | Listed above; test scope only |
| GPLv2 + Classpath Exception (Temurin JDK) | None for compiled output | The Classpath Exception explicitly permits linking/compiling non-GPL code; the JDK is a build tool, not shipped |

If a future release adds a dependency with a copyleft licence (GPL, LGPL, AGPL,
MPL) or a component requiring an attribution string in the UI, this file and the
About screen must both be updated before shipping.

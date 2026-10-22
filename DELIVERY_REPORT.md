# Part 17 — Final Delivery Report

**Project:** Axiom Calc · **Report date:** 2026-10-21 · **Branch:** `arena/837f6b83-axiom-calc` · **Pull request:** [#1](https://github.com/shohanbusinessmail-cmd/axiom-calc/pull/1)

Every claim in this report is backed by an executed command, a committed file, or
a named blocker. Where something was not verified, it says so. No coverage
percentage is claimed anywhere, because none was measured.

---

## A. Project identity

| Field | Value | Verified by |
|-------|-------|-------------|
| Application ID | `com.shohankhan.axiomcalc` | `app/build.gradle.kts` |
| Owner | **Shohan Khan** | `res/values/strings.xml/about_owner_name`, rendered by `AboutScreen.kt` |
| Owner email | **helloiamshohan@gmail.com** | `about_owner_email`; opened via `Intent.ACTION_SENDTO` + `mailto:` |
| versionName / versionCode | 1.0.0 / 1 | `app/build.gradle.kts` |
| minSdk / compileSdk / targetSdk | 24 / 35 / 35 | `gradle/libs.versions.toml` |
| Language / UI toolkit | Kotlin 2.0.21 / Jetpack Compose (BOM 2024.10.01), Material 3 | 64 Kotlin files, zero XML layouts |
| JDK / Gradle / AGP / KSP | 17 (Temurin 17.0.9) / 8.11.1 / 8.7.3 / 2.0.21-1.0.28 | wrapper jar SHA-1 `a4b76b95…9189`; versions confirmed against GitHub tags |
| Permissions requested | **0** | XML parse of the manifest: `uses-permission elements: []` |
| Repository state | 4 commits, root `3204bf3` preserved, no force-push or history rewrite | `git log --oneline` |

Commits on this branch:

```
9e72c81 M9: instrumented test suite and the eight mandatory documents
3b31e5b M6-M8: Android data layer, design system, six primary destinations and lab modules
299de80 M2-M5: pure-Kotlin mathematics engine, domain layer and verified test suite
3204bf3 Initial commit
```

Invented identity that was deliberately **not** added: no company name, no
website, no social links, no privacy-policy URL, no legal claims. The About screen
contains only the owner's name and email, the version, the offline statement and a
list of this repository's own documents.

---

## B. Implementation summary per tab

Counts below were obtained by **executing the compiled classes**, not by reading
source or estimating:

```
categories=13   forms=77   tools=27
libraryCategories=13   libraryEntries=58   libraryLinked=29   constants=11
```

### 1. Standard — `ic_nav_standard` (calculator)
Token-level editing buffer, not string editing: backspace removes one whole token
(so `sin(` disappears as a unit), CE clears the trailing entry, AC clears the
expression but never memory or `Ans`. Operator keys replace a trailing operator.
Parentheses are auto-closed **only at evaluation**, so the buffer never contains
invented tokens while typing. Snapshot undo/redo. 400-token cap. Memory
MS/MR/M+/M−/MC. `Ans` reuse. Copy result/expression, sanitized paste, hardware
keyboard input (the keypad is `focusable()`, requests focus, and consumes KeyDown
events plus Enter/Backspace/Delete/Escape). Context-sensitive `%` key.

### 2. Scientific — `ic_nav_scientific` (sigma)
Compact panel (always visible) plus an expanded panel toggled by a chip and
surviving process death via `rememberSaveable`. Trig with a DEG/RAD toggle that is
an evaluation parameter, not global state; inverse (`asin/acos/atan`) and
hyperbolic (`sinh/cosh/tanh/asinh/acosh/atanh`). Powers, `x²`, `x³`, `√`, `∛`,
`ⁿ√`, `1/x` (as `^-1`, exact because unary minus binds directly after `^`),
`10ˣ`, `EE`. `ln`, `log10`, `eˣ`. Constants π, e, τ, φ inserted as typographic
glyphs that the engine resolves via symbol aliases. Combinatorics nCr, nPr, gcd,
lcm, factorial, abs, mod.

Complex numbers are **not** evaluated inside the real-number engine. Instead of
claiming otherwise, this tab links directly to the complex module in Math Lab,
which implements rectangular and polar arithmetic fully and is unit-tested.

### 3. Formulas — `ic_nav_formulas` (percent)
**77 forms** across **13 categories A–M**:

| Cat | Category | Forms | | Cat | Category | Forms |
|-----|----------|-------|-|-----|----------|-------|
| A | Percentage | 8 | | H | Installments | 2 |
| B | Discount | 6 | | I | Geometry | 13 |
| C | VAT & percentage tax | 4 | | J | Algebra | 4 |
| D | Profit, loss, markup & margin | 8 | | K | Fractions | 8 |
| E | Ratio & proportion | 5 | | L | Time arithmetic | 4 |
| F | Average & statistics | 5 | | M | Date arithmetic | 5 |
| G | Interest | 5 | | | **Total** | **77** |

Forms take raw text (`Map<String,String>`) and parse internally, because a form
may need a dataset (`"2, 4, 6"`) or a date (`"2024-02-28"`), which a numeric map
cannot express. Every form returns result lines, the substitution string, and
explanation. Tax rates are always user inputs — no country's current rate is
hardcoded anywhere.

### 4. Quick Solve — `ic_nav_quick_solve` (zap)
**27 named tools** grouped by domain: percentage (incl. reverse and successive
discounts), ratio and proportion, fractions, integer math (gcd, lcm, prime
factors, primality), linear equation, simultaneous 2× and 3×, quadratic,
arithmetic and geometric sequences, rounding, significant figures, scientific
notation, and six statistics tools. Every tool returns **step-by-step working**,
and the test suite requires each step list to contain a `Relationship:`, a
`Final:` and a `Restriction:` line — so "shows working" is a checked property.

### 5. Formula Library — `ic_nav_library` (book)
**58 reference entries** across 13 categories, each with expression, every
variable's meaning, conditions/restrictions, and units where applicable. Search by
title, expression, category and keywords; filters for All / Favourites / Recents;
category chips. Favourites and recents persist in Room (recents capped at 20 by
`trimRecent()`). **29 entries link to the implementing calculator**; the resolver
accepts either a Formula Calculator form or a Quick Solve tool, and a test fails
if any link dangles. Reference-only entries state that plainly instead of offering
a button that leads nowhere.

### 6. Math Lab — `ic_nav_math_lab` (flask)
Six modules:
- **Matrices** — shape, `A+B`, `A−B`, `A·B`, `k·A`, `Aᵀ`, determinant, rank,
  inverse (with an `A·A⁻¹` identity verification line), and solve `A·x = b`
  delegated to the authoritative system solver with residual verification.
- **Statistics** — count, sum, mean, median, mode (multimodal reported), min, max,
  range, population and sample variance and standard deviation, weighted mean.
- **Complex** — rectangular or polar input, `+ − × ÷`, conjugate, magnitude,
  argument (radians and degrees), and form conversion; results always shown in
  both forms.
- **Numerical solvers** — bisection and Newton–Raphson on any expression, with
  tolerance, iteration count, residual, and explicit `INVALID_BRACKET` /
  `NO_CONVERGENCE` statuses.
- **Number bases** — convert and compute in 2/8/10/16; digits outside the source
  base are rejected, not skipped.
- **Engineering formatting** — decimal, scientific, engineering, significant
  figures, rounding modes and truncation on one value, plus an `inspection` line
  showing the internal exact representation.

### Secondary destinations
History (date grouping Today/Yesterday/date, search, time-window and origin
filters, multi-select delete, delete all, retention control) · Saved calculations
(permanent, separate table, its own selection and delete) · Settings · About ·
formula detail · form/tool runner · lab module.

---

## C. Architecture as-built

Five layers, dependency direction strictly downward, described in full in
[`ARCHITECTURE.md`](ARCHITECTURE.md).

| Layer | Files | Lines | Android imports |
|-------|-------|-------|-----------------|
| `math/` | 22 | 3,262 | none |
| `domain/` | 7 | 2,254 | none |
| `feature/` | 19 | 4,897 | Compose + lifecycle |
| `core/` | 6 | 1,015 | `core/ui`, `core/design` only |
| `data/` | 3 | 388 | Room, DataStore |
| `ui/`, `infra/`, `navigation/`, root | 6 | ~673 | Compose, WorkManager, Activity |
| **Total** | **64** | **12,472** | |

Because `math/` and `domain/` import nothing from `android.*`, the engine and all
183 unit tests compile and run **without the Android SDK present** — that is what
made real test evidence possible in this sandbox.

**One authoritative engine.** `ExpressionEngine` is the only public expression
surface (`evaluate`, `evaluateTree`, `parseTree`, `parseError`, `preview`). Every
consumer goes through it or through the `math/solvers/*` modules: the calculator
buffer, `PercentagePolicy`, all 77 forms, all 27 tools, and the Math Lab modules.
The only arithmetic in `feature/` is lab-module orchestration and `Double`
formatting — no screen implements its own math.

**Display text never re-enters the engine.** `NumberFormatter` renders
`CalcNumber → String`; the reverse does not exist in the codebase. Persisted rows
store a separate canonical value, and a test asserts that a grouped display string
(`1,234,567`) is **rejected** by `CalcNumber.fromCanonical`.

**DI.** A hand-written `AppContainer` (no DI framework — a code-generation
dependency isn't justified at this scale, and an explicit graph is easier to
audit). `axiomViewModel { container, appContext -> … }` captures the application
context only, so no ViewModel can retain an Activity. `AxiomCalcApplication`
implements WorkManager's `Configuration.Provider`, and the manifest removes
`androidx.startup`'s `WorkManagerInitializer` so nothing runs before the retention
preference is read.

**Navigation.** Sealed `Destination` hierarchy: six primary routes plus `history`,
`saved`, `settings`, `about`, `form/{formId}`, `solver/{toolId}`, `lab/{moduleId}`,
`formula/{formulaId}`. All arguments are typed nav args; no route carries formatted
display text.

---

## D. Mathematical verification

Normative detail in [`MATHEMATICS_SPEC.md`](MATHEMATICS_SPEC.md). Pipeline:
source → `Lexer` → tokens → `Parser` → `Expr` AST → `Evaluator` → `CalcNumber`.
No JavaScript, no WebView, no reflection, no script evaluation.

**Executed command:**

```
$ bash verification/run-verification.sh
>> compiling 38 Kotlin sources (core=31 tests=5 shim=2)
>> running tests
TOTAL 183   PASSED 183   FAILED 0   ERRORS 0   SKIPPED 0
RESULT: PASS
```

**All 15 mandatory reference values, green:**

| Expression | Result | | Expression | Result |
|---|---|---|---|---|
| `2+2` | 4 | | 20% off 500 | 400 |
| `2+3×4` | 14 | | 80 → 100 | 25% |
| `(2+3)×4` | 20 | | `GCD(48,18)` | 6 |
| `√81` | 9 | | `LCM(12,18)` | 36 |
| `log10(1000)` | 3 | | `x²−5x+6=0` | 2, 3 |
| `ln(e)` | 1 | | `mean(2,4,6)` | 4 |
| `sin(30°)` | 0.5 | | `200×15%` | 30 |
| `cos(60°)` | 0.5 | | | |

**Precedence and associativity, verified:** `-2^2 = -4`, `(-2)^2 = 4`,
`2^-3 = 0.125`, `2^3^2 = 512` (right-assoc), `1/2pi = 1/(2π)` (juxtaposition binds
tighter than `/`), `3(4+5) = 27`, `1.5e3 = 1500`, `2e = 2·e`, `7 mod 3 = 1`,
`5! = 120`, `0^0 = 1`, `0^-2` → DivisionByZero.

**Precision:** DECIMAL128 (34 significant digits, HALF_EVEN). Exact decimal
arithmetic — `0.1 + 0.2 = 0.3` exactly. Transcendentals and fractional powers are
computed in binary64 then re-rounded; `sqrt`/nth roots are exact via BigDecimal
Newton iteration. Constants are stored to 35 digits, so `τ/2` and `π` agree to
within DECIMAL128 rounding (< 1e-30) but are not bit-identical — a test asserts
the tolerance rather than pretending exact equality.

**Typed errors verified:** DivisionByZero; Domain for `sqrt(−1)`, `ln(0)`, log
base 1, `asin(2)`, `tan(90°)`, `(−3)!`, `2.5!`; ResourceLimit for `20000!` and a
4000-node expression; UnbalancedParentheses; Syntax for `1.2.3`;
UnknownFunction; InvalidArgument; DimensionMismatch; SingularMatrix; EmptyDataset;
InsufficientSample.

**Guard rails:** depth 256, nodes 4 000, literal digits 60, integer digits
100 000, exponent 100 000, factorial 10 000, matrix cells 4 096, factorisation
≤ 10¹⁸ and ≤ 20 M steps, buffer 400 tokens.

**Solver guarantees:** quadratic uses the stable `q = -(b ± √D)/2` form and keeps
the `1e-8` root of the ill-conditioned `x² − 1e8x + 1`; `a = 0` is never disguised
as a unique root. Linear systems classify UNIQUE / NO_SOLUTION / INFINITELY_MANY
from ranks and verify the residual before returning a solution. Numerical solvers
never claim convergence without a residual check. Sample statistics require n ≥ 2
and otherwise raise `InsufficientSample` rather than silently substituting the
population figure.

**What is not implemented** (no claim is made): symbolic integration or
differentiation, unrestricted symbolic algebra, a CAS, ODE solving, complex
expression parsing (`3+4i` literals), arbitrary-precision transcendentals, live
currency or exchange rates.

---

## E. Persistence

| Concern | Implementation | Verified by |
|---------|----------------|-------------|
| History | Room table `history`, `Flow`-observed, ordered newest first | `HistoryDatabaseTest` (15 tests, NOT TESTED on device) |
| Saved calculations | Separate table; **never** touched by retention | `savedCalculationsSurviveRetentionRuns`, `deleteAllClearsHistoryButNotSavedCalculations` |
| Favourites / recents | Separate tables; recents capped at 20 | `favoritesAndRecentsAreIndependentStores`, `recentsAreCappedAtTwentyEntries` |
| Settings | DataStore preferences; only enum **names**, booleans and integers are stored | `SettingsRepositoryTest` (7 tests, NOT TESTED) |
| Value vs display | `result_value` (canonical, exact) stored separately from `result_display` | `normalizedValueIsStoredSeparatelyFromDisplayText`; `PersistenceSemanticsTest` (executed, PASS) |
| Retention policies | 1 / 7 / 30 / 90 days / Never, default **30** | `PersistenceSemanticsTest` — 7 executed tests against a fixed injectable clock, including the exact boundary (kept at cutoff, expired 1 ms older) |

**Retention enforcement has three independent paths**, because a background job
cannot be relied upon:

1. **Read-time cutoff filter** in `RoomHistoryRepository.filterNotExpired()` — an
   expired row cannot be displayed even if no cleanup has ever run.
2. **Startup pass** — `AppContainer.enforceRetentionAtStartup()`.
3. **Best-effort WorkManager** — daily `RetentionWorker`, battery-not-low
   constraint, reporting `deleted`/`policy`/`error` through `workDataOf`. Its
   failure is non-fatal and is surfaced, never swallowed.

Deletion is real deletion (`DELETE FROM history WHERE timestamp < :cutoff`) — no
soft-delete, no archive elsewhere. Room schema is version 1 with
`exportSchema = true`, so future changes must ship tested migrations; the schema is
never dropped to avoid writing one, because that would delete user history.
Settings degrade to documented defaults on unknown or corrupt values, so an
upgrade cannot brick the settings screen.

History is recorded by the calculators on `=`, and by forms and tools through a
**600 ms debounce** (`ToolRunnerViewModel.RECORD_DEBOUNCE_MS`) so one logical
computation produces exactly one row rather than one per keystroke. Failures are
never recorded as results.

---

## F. Visual and accessibility verification

**Design system.** Compose tokens mirror the verified XML values: accent
`#38BDF8`, dark surface `#0B0E13`, light surface `#F7F8FA`. Contrast ratios are
documented in `core/design/Color.kt` and computed with the WCAG 2.1 relative
luminance formula:

| Pairing | Ratio | Level |
|---------|-------|-------|
| accent `#38BDF8` on dark surface `#0B0E13` | 9.3 : 1 | AAA |
| `#E6EDF3` on `#0B0E13` | 15.4 : 1 | AAA |
| `#9AA7B4` on `#0B0E13` (secondary text) | 6.4 : 1 | AA |
| `#10161C` on `#F7F8FA` | 16.4 : 1 | AAA |
| light-mode accent darkened to `#0284C7` for text/icons | 4.9 : 1 | AA |

Dynamic colour is honoured on Android 12+; brand tokens are the default below that.

**Icons.** 28 hand-authored vector drawables (6 navigation: calculator, Σ, %, zap,
book-open, flask; 20 action icons; launcher foreground), 10 generated launcher PNGs
across mdpi–xxxhdpi plus adaptive-icon XMLs. **No emoji anywhere.** Mathematical
glyphs (`× ÷ ± π τ φ √ ∛ Σ ⌫ ↶ ↷`) are Unicode characters rendered by the system
font; action keys use the vectors. Launcher safe-zone check passed
(31.12 ≤ 33 dp) and every icon renders non-empty ink.

```
$ python3 tools/validate_resources.py
RESULT: PASS - all resources well-formed, all vector path data valid,
        all intra-project resource references resolve.
```

**Accessibility — implemented:** every key has an explicit `contentDescription`
("Divided by", not "÷"); touch targets are ≥ 48 dp by construction
(`heightIn(min = 48.dp)` and `height(….coerceAtLeast(48.dp))`); tab items announce
selection (`"Tab Scientific, selected"`); the result announces `"Result 4"`; angle
mode announces `"Angle mode, currently degrees"`; the memory indicator announces
its value and exists only when memory holds one; all text sizes are in `sp` so the
system font scale applies. 255 string resources, all English.

**Responsiveness — implemented:** layouts branch on measured size
(`BoxWithConstraints`), not on device type; landscape puts the display beside the
keypad, portrait above it; key heights shrink in compact windows but never below
48 dp; no screen scrolls the keypad. `enableEdgeToEdge()` +
`consumeWindowInsets(WindowInsets.safeDrawing)` at the root, with
`windowSoftInputMode="adjustResize"`, so content clears the status bar, gesture
area, cutout and IME.

**Verification status.** These are code-level PASS. **Rendering, measured touch
targets and TalkBack behaviour are NOT TESTED** — `AccessibilityTest` (7 tests)
measures real bounds on a device and `NavigationSmokeTest` (22 tests) walks every
destination, but no device or emulator existed here. No screenshot is claimed,
because none was taken.

---

## G. Offline and privacy verification

**Executed audit — XML parse of `app/src/main/AndroidManifest.xml`:**

```
uses-permission elements: []
uses-permission-sdk-23 elements: []
application name: .AxiomCalcApplication
activities: [('.MainActivity', 'true')]
services: []
receivers: []
providers: ['androidx.startup.InitializationProvider']   # only to REMOVE the WorkManager initializer
queries intents: [('android.intent.action.SENDTO', 'mailto')]
```

Zero permissions are declared. **`android.permission.INTERNET` is absent**, so the
kernel denies socket creation for the whole process: no code path, including any
inside a library, can reach the network. That is a platform-enforced guarantee, not
a convention.

`grep -n uses-permission app/src/main/AndroidManifest.xml` matches **only a
comment line** stating that none is declared — the XML parse above is the
authoritative check.

**Dependency audit (static).** `gradle/libs.versions.toml` contains no networking,
analytics, advertising, crash-reporting, remote-configuration or licensing
coordinate: no OkHttp, Retrofit, Ktor, Volley, Firebase, Play Services, Facebook,
Sentry or Crashlytics. `dependenciesInfo` is disabled so no Play-encrypted
dependency metadata block is embedded. The complete list with licences is in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

**Data inventory.** History, saved calculations, favourites, recents and settings
stay in the app-private sandbox. In-session state (buffer, memory register, `Ans`,
undo stack) is never persisted. Nothing is collected: no accounts, no device or
advertising identifiers, no location, no contacts, no usage telemetry, no crash
reports. The only address the app knows is the owner's, a compile-time string shown
on About and placed in a `mailto:` URI only when the user taps it.

**No secrets.** No keys, tokens, passwords or credentials exist in the source,
resources, build files or Git history; `.gitignore` excludes keystores, `*.jks`,
`*.keystore` and signing properties.

**Gaps, stated plainly:**

| Item | Status |
|------|--------|
| Merged **release** manifest audit | **BLOCKED** — no Gradle build, so no merged manifest exists. Commands documented in `SECURITY_AND_PRIVACY.md` §2.5; `OfflineAndPrivacyTest` checks the installed package |
| `./gradlew app:dependencies` report | **BLOCKED** — same reason. The static catalog audit above is what was possible |
| Airplane-mode functional walkthrough | **NOT TESTED** — no device. The six-step procedure is written out in `SECURITY_AND_PRIVACY.md` §2.6 |
| `OfflineAndPrivacyTest` (10 tests, incl. "socket open fails without INTERNET") | **NOT TESTED** — written and committed |

Data is not encrypted at rest: the stored values are the user's own arithmetic with
no credentials or identity data, and encryption would add key-management risk
without protecting anything unavailable to an attacker on a rooted device. That is
a documented decision, not an oversight.

---

## H. Test evidence — per exact command

### H1. Executed and passing

```
$ bash verification/run-verification.sh
>> compiling 38 Kotlin sources (core=31 tests=5 shim=2)
>> running tests
====================================================================
AXIOM CALC - STANDALONE VERIFICATION RUN (JUnit4-compatible shim harness)
...
TOTAL 183   PASSED 183   FAILED 0   ERRORS 0   SKIPPED 0
====================================================================
RESULT: PASS
```

Raw output committed: [`verification/reports/unit-tests-latest.txt`](verification/reports/unit-tests-latest.txt).

| Suite | Tests | Status |
|-------|-------|--------|
| `math/ExpressionEngineTest` | 73 | PASS (executed) |
| `math/SolversTest` | 56 | PASS (executed) |
| `domain/CalculatorBehaviorTest` | 22 | PASS (executed) |
| `domain/CatalogCompletenessTest` | 16 | PASS (executed) |
| `domain/PersistenceSemanticsTest` | 16 | PASS (executed) |
| **Total** | **183** | **183 passed, 0 failed** |

What `CatalogCompletenessTest` proves, beyond individual cases: all **77 forms**
compute on valid sample input, all 77 reject garbage with a **typed** error, all 77
declare inputs/formula/explanation with sample keys exactly matching input ids, and
all **27 tools** return ≥3 steps containing `Relationship:`, `Final:` and
`Restriction:`. A new form or tool without a sample input **fails the suite**, so
the catalog cannot drift from its tests.

### H2. Structural verification of the Android sources

```
$ kotlinc -nowarn -jvm-target 17 $(find app/src/main/java -name '*.kt' | sort) -d /tmp/out
3724 diagnostics, of which:
  3432  unresolved reference '…'          (android/androidx/Compose/R — unobtainable offline)
   …    cascading inference/mismatch errors from the above
     0  syntax errors ("expecting", "unexpected token")
```

**Zero syntax errors across all 64 main-source files.** This sweep found two real
defects that type-level review had missed: `CalcError.UnsupportedOperation` had no
user-facing message, and `CalculatorViewModel` was final while subclassed. Both are
fixed. This proves the sources parse and are internally consistent; it does **not**
prove they compile against the real SDK or render correctly.

### H3. Resource verification

```
$ python3 tools/validate_resources.py
RESULT: PASS - all resources well-formed, all vector path data valid,
        all intra-project resource references resolve.
```

### H4. Written but NOT executed

| Command | Suites | Tests | Status |
|---------|--------|-------|--------|
| `./gradlew connectedDebugAndroidTest` | `StandardCalculatorUiTest`, `NavigationSmokeTest`, `AccessibilityTest`, `HistoryDatabaseTest`, `SettingsRepositoryTest`, `RetentionWorkerTest`, `OfflineAndPrivacyTest` | **75** | **NOT TESTED** — no device, emulator or `adb` |
| `./gradlew test` | the same 183 unit tests under real JUnit 4 | 183 | **BLOCKED** — Gradle distribution unobtainable |
| `./gradlew lint` | — | — | **BLOCKED** — no Android SDK |
| coverage | — | — | **NOT MEASURED** — no percentage is claimed |

### H5. Harness substitution — disclosure

`junit:junit:4.13.2` could not be downloaded (`repo1.maven.org` unreachable), so
`verification/shim/` provides `org.junit.{Test,Before,After,Ignore}` and
`org.junit.Assert` with the signatures the tests use, plus a reflective runner that
exits non-zero on any failure. The **test source files are unmodified** and run
unchanged under real JUnit 4 in Gradle. Nothing in `app/src/main` depends on the
shim, and it is confined to `verification/`. The two sandbox build tools (Temurin
JDK 17 via PyPI `jdk4py`, Kotlin 2.0.21 via npm `kotlin-compiler`) live outside the
repository in `~/.cache/axiomtool/` and are disclosed in
`THIRD_PARTY_NOTICES.md` §2.

---

## I. APK delivery

**No APK exists. None is claimed.**

```
$ find . -name '*.apk' | wc -l
0
```

| Artifact | Status | Reason |
|----------|--------|--------|
| `./gradlew assembleDebug` | **BLOCKED** | `services.gradle.org` unreachable → the Gradle distribution cannot be downloaded |
| Debug APK | **NOT PRODUCED** | No Android SDK: `dl.google.com` unreachable; no `aapt2`, `d8`, `zipalign`, `apksigner` |
| `./gradlew assembleRelease` | **BLOCKED** | Same |
| Owner-signed release APK | **NOT PRODUCED** | Same, **and no owner keystore exists**. None was invented or fabricated |
| APK inspection (size, archive validity, package ID, versionName/Code, SDK metadata, signing status) | **NOT PERFORMED** | No APK and no build tools |
| Install and launch on a device | **NOT TESTED** | No device, no emulator, no `adb` |

Network probe evidence from the sandbox (HTTP status `000` = connection failed):

| Host | Result |
|------|--------|
| `github.com`, `api.github.com`, `codeload.github.com` | reachable |
| `registry.npmjs.org`, `pypi.org`, `files.pythonhosted.org` | reachable |
| `services.gradle.org` | **blocked** |
| `dl.google.com`, `maven.google.com` | **blocked** |
| `repo1.maven.org`, `plugins.gradle.org` | **blocked** |

A successful Gradle task would not by itself prove installability, and no such task
ran — so no installability claim is made. **Source-repository completion and APK
delivery are separate acceptance criteria; the first is met, the second is not.**

**To deliver an APK**, on a machine with JDK 17, Android SDK 35 and access to the
five blocked hosts above:

```bash
./gradlew assembleDebug
ls -l app/build/outputs/apk/debug/app-debug.apk          # exists, non-zero size
unzip -t app/build/outputs/apk/debug/app-debug.apk       # valid archive
aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk   # package, version, SDKs
apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.shohankhan.axiomcalc/.MainActivity
```

Owner release signing is documented step by step in `RELEASE.md` §5, including the
requirement to keep credentials outside the repository and to preserve the same
keystore for the app's whole life.

A debug APK is acceptable for evaluation but is **not** equivalent to an
owner-signed production release.

---

## J. Defects and residual risk

### J1. Defects found and fixed during this work

Every one was found by an executed check, fixed in source, then pinned by a test:

| # | Defect | Found by | Fix |
|---|--------|----------|-----|
| 1 | Scientific/engineering mantissas rendered padded trailing zeros (`1.23456700000e+6`) | `ExpressionEngineTest` | `renderMantissa(mantissa, trim)` wired to `policy.autoPrecision` |
| 2 | `1.2.3` silently evaluated as implicit multiplication instead of a syntax error | `ExpressionEngineTest` | Lexer rejects a second decimal point → `CalcError.Syntax` |
| 3 | `200 + 10% + 5%` returned **230.005** instead of 231 | `CalculatorBehaviorTest` | `PercentagePolicy` reads the prefix from the already-rewritten token list |
| 4 | `sin(30)` could not be closed and `sqrt(16` was not auto-closed at evaluation | `CalculatorBehaviorTest` | `openCount()` counts `Atom` tokens ending in `(`; `closingSuffix()` uses it |
| 5 | `CalcError.UnsupportedOperation` had no user-facing message | compiler sweep | Added to the exhaustive mapping in `ErrorMessages.kt` |
| 6 | `CalculatorViewModel` final but subclassed by both calculator VMs | compiler sweep | Marked `open` |
| 7 | 3 library entries (gcd/lcm/prime factors) linked to Quick Solve ids the resolver rejected | `CatalogCompletenessTest` | `formId` documented as "form **or** tool"; resolution accepts both |
| 8 | Library had no markup/margin entry, so margin guidance was undocumented | `CatalogCompletenessTest` | Added `lib_markup_margin` (markup ÷ cost, margin ÷ selling) |
| 9 | Manifest pointed at `.app.AxiomCalcApplication` but the class is at the root package | manual audit | Corrected to `.AxiomCalcApplication` |
| 10 | Keypad requested focus without being `focusable()`, so hardware keys could not reach it | manual audit | Added `.focusable()` + `"Calculator keypad"` semantics on both calculators |
| 11 | Duplicated matrix arithmetic written inside the ViewModel | manual audit | Removed; the UI now uses `Matrix` operators and `LinearEquations.solveSystem`; `trace()`/`toRows()` added to the math layer where they belong |

One test failure was **not** a product defect: a 400-deep-parenthesis test assumed
parentheses are AST nodes. They are not — the parser collapses them — so depth
limits must be exercised with nested calls. The test was split into
`deepParenthesesAreGroupingNotNodes` and `excessiveNestingIsResourceLimit`.

### J2. Open defects

**None known.** No criterion in `FEATURE_ACCEPTANCE.md` is FAIL, and no known
unresolved critical or high-severity *code* defect is open.

The app is **not** claimed to be bug-free. The unexecuted verification below is
where remaining defects would most likely be found.

### J3. Residual risk

| # | Risk | Severity | Status | Mitigation |
|---|------|----------|--------|------------|
| 1 | The merged **release** manifest has not been audited; a transitive dependency could in principle add a permission at merge time | **High** (verification gap, not a known defect) | UNVERIFIED | Source manifest declares zero permissions (XML-parsed); `OfflineAndPrivacyTest` asserts zero requested and zero granted permissions on the installed package; audit commands documented |
| 2 | Compose UI has never been compiled against the real SDK or rendered | High | UNVERIFIED beyond the zero-syntax-error sweep | Build on a machine with the SDK; run the 22 navigation and 7 accessibility tests |
| 3 | No APK exists, so installability, launch and R8/minification effects are unknown | High | BLOCKED | `RELEASE.md` §4/§7 checklist |
| 4 | Room, DataStore and WorkManager behaviour is unverified on a device | Medium | NOT TESTED | 26 instrumented data tests written |
| 5 | Responsive layouts (landscape, tablets, foldables) unverified on real screens | Medium | NOT TESTED | Size-driven branching in code; `BoxWithConstraints` |
| 6 | No coverage measurement | Low | NOT MEASURED | No percentage claimed |
| 7 | Data not encrypted at rest | Low | Accepted by design | Documented in `SECURITY_AND_PRIVACY.md` §3 |
| 8 | WorkManager cleanup may never run on some devices | Low | Mitigated | Two independent enforcement paths (read-time filter + startup pass) |

### J4. Release readiness

**Not ready to publish.** Release acceptance requires zero known unresolved
critical defects and zero known unresolved high-severity defects. Risks 1–3 above
are open high-severity **verification gaps** that only a real build can close. They
are reported rather than waived, and no owner approval to ship past them has been
given or assumed.

---

## K. Final acceptance matrix

Statuses are drawn only from PASS / FAIL / BLOCKED / NOT TESTED. The full
criterion-by-criterion matrix (150 criteria across 15 parts) is
[`FEATURE_ACCEPTANCE.md`](FEATURE_ACCEPTANCE.md).

| Part | Area | PASS | FAIL | BLOCKED | NOT TESTED | Total |
|------|------|------|------|---------|------------|-------|
| A | Identity and platform | 7 | 0 | 0 | 0 | 7 |
| B | Toolchain and build | 6 | 0 | 6 | 1 | 13 |
| C | Standard Calculator | 15 | 0 | 0 | 1 | 16 |
| D | Scientific Calculator | 11 | 0 | 0 | 0 | 11 |
| E | Formula Calculator | 18 | 0 | 0 | 0 | 18 |
| F | Quick Solve | 7 | 0 | 0 | 0 | 7 |
| G | Formula Library | 6 | 0 | 0 | 0 | 6 |
| H | Math Lab | 6 | 0 | 0 | 0 | 6 |
| I | Persistence | 11 | 0 | 0 | 1 | 12 |
| J | Architecture and quality | 14 | 0 | 0 | 0 | 14 |
| K | Accessibility and responsiveness | 2 | 0 | 0 | 5 | 7 |
| L | Offline and privacy | 8 | 0 | 2 | 1 | 11 |
| M | Tests | 5 | 0 | 1 | 2 | 8 |
| N | Documentation | 8 | 0 | 0 | 0 | 8 |
| O | Version control | 6 | 0 | 0 | 0 | 6 |
| | **Total** | **130** | **0** | **9** | **11** | **150** |

Counts were produced by parsing all 150 criterion rows in
`FEATURE_ACCEPTANCE.md`. A criterion marked "PASS (code) / NOT TESTED (device)" is
counted once, in the NOT TESTED column, so nothing is double-counted: the
implementation is delivered and the device verification is honestly outstanding.

### Deliverables

| Deliverable | Status |
|-------------|--------|
| Complete Kotlin + Compose source (64 files, 12,472 lines) | **DELIVERED** |
| 183 unit tests, executed and passing | **DELIVERED** |
| 75 instrumented tests, written | **DELIVERED** (not executed) |
| Gradle Kotlin DSL build + verified wrapper | **DELIVERED** (not run) |
| Resources: 28 vectors, 10 launcher PNGs, 255 strings, themes, backup/locale rules | **DELIVERED**, validator PASS |
| 8 mandatory documents | **DELIVERED** |
| Version-controlled branch + PR #1 | **DELIVERED** |
| **APK** | **NOT DELIVERED — BLOCKED** |
| Owner-signed release | **NOT DELIVERED — BLOCKED** (no owner key exists) |

### Single action that closes every gap

Build on a machine with **JDK 17, Android SDK 35**, and network access to
`services.gradle.org`, `dl.google.com`, `maven.google.com`, `repo1.maven.org` and
`plugins.gradle.org`. Then run, in order:

```bash
./gradlew test                          # expect 183 passing under real JUnit 4
./gradlew lint
./gradlew assembleDebug                 # produce and inspect the APK (§I)
./gradlew connectedDebugAndroidTest     # expect 75 passing on a device
grep -n uses-permission app/build/outputs/logs/manifest-merger-release-report.txt   # expect nothing
./gradlew app:dependencies --configuration releaseRuntimeClasspath                 # audit
./gradlew assembleRelease               # then owner-sign per RELEASE.md §5
```

and complete the airplane-mode walkthrough in `SECURITY_AND_PRIVACY.md` §2.6.

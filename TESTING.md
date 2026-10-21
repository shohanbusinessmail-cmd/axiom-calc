# Axiom Calc — Testing

This document states exactly what is tested, how to run it, what was actually
executed, and what was **not** executed and why. Nothing here is aspirational: a
suite that has not been run is marked NOT TESTED, not "passing".

---

## 1. Test inventory

### Local unit tests — `app/src/test/` (183 tests, all executed and passing)

| Suite | Tests | Covers |
|-------|-------|--------|
| `math/ExpressionEngineTest.kt` | 73 | Lexer, parser, precedence, associativity, implicit multiplication, constants, function domains, DECIMAL128 exactness, guard rails, typed errors, `ans` substitution, preview behaviour, formatting (grouping, fixed decimals, scientific, engineering, significant figures, rounding modes) |
| `math/SolversTest.kt` | 56 | Statistics, matrices, complex, linear/simultaneous/quadratic, sequences, integer math, fractions, bases, finance, numeric solvers, dates/durations, percent math |
| `domain/CalculatorBehaviorTest.kt` | 22 | Keypad editing (backspace = one token, CE, AC), undo/redo, sign toggle, parentheses, memory keys, percentage-key policy, `Ans` retention, token cap |
| `domain/CatalogCompletenessTest.kt` | 16 | Every category has forms; every form and every Quick Solve tool has a sample input and computes; every form declares inputs, formula text and explanation; garbage input raises a **typed** error for every form; zero denominators and triangle inequality are rejected; every tool shows ≥3 steps including Relationship/Final/Restriction; library links resolve; library search works |
| `domain/PersistenceSemanticsTest.kt` | 16 | Retention cutoffs against a fixed injectable clock, boundary inclusivity, `fromName`/`fromDays` safety, canonical round-trip, rejection of grouped display text, reuse of a stored value through the engine, matrix `trace`/`toRows`, constant symbol aliases |

Executed output (committed at `verification/reports/unit-tests-latest.txt`):

```
>> compiling 38 Kotlin sources (core=31 tests=5 shim=2)
>> running tests
TOTAL 183   PASSED 183   FAILED 0   ERRORS 0   SKIPPED 0
RESULT: PASS
```

### Instrumented tests — `app/src/androidTest/` (75 tests, NOT TESTED here)

| Suite | Tests | Covers |
|-------|-------|--------|
| `ui/StandardCalculatorUiTest.kt` | 10 | Real Activity: `2+2=4`, precedence on screen, percentage-key semantics, division-by-zero message, backspace token granularity, AC keeping memory, undo, `Ans` reuse, hardware keyboard keys, accessibility descriptions on every key |
| `ui/NavigationSmokeTest.kt` | 22 | Exactly six primary tabs, each rendering real content; secondary destinations reachable; DEG/RAD toggle; form runner; solver steps; library search; Math Lab matrix determinant = −2; no placeholder/`NaN` text anywhere; back navigation |
| `ui/AccessibilityTest.kt` | 7 | Measured touch-target bounds ≥ 48 dp for all 31 keys; no node with an empty description; tab selection state announced; result announced as `"Result 4"`; angle-mode state announced; memory indicator announced only when memory holds a value |
| `data/HistoryDatabaseTest.kt` | 15 | Room in-memory: ordering, canonical value stored separately from display text, search, multi-delete, delete-all sparing saved calculations, retention deletion, `NEVER` deleting nothing, **read-time filtering of expired rows**, origin persistence, saved calculations surviving retention, favourites vs recents independence, recents capped at 20, count, entity round-trips |
| `data/SettingsRepositoryTest.kt` | 7 | Documented defaults, persistence across a fresh repository instance, partial updates, clamping, unknown enum values degrading to defaults, Flow emissions, no formatted display text stored |
| `data/RetentionWorkerTest.kt` | 4 | `RetentionWorker` via `TestListenableWorkerBuilder`: deletes expired rows and reports the count and policy; `NEVER` deletes nothing; enqueue is safe; startup pass works against the real database |
| `OfflineAndPrivacyTest.kt` | 10 | Installed-package audit: **zero requested permissions**, no `INTERNET`, no `ACCESS_NETWORK_STATE`, no granted permissions, no tracking/analytics/advertising components merged, only expected components, package identity, version metadata, release not debuggable, socket open fails without the permission, engine computes correctly on device |

**Status: NOT TESTED.** No Android SDK, emulator or physical device was available
in the authoring environment. These tests are committed, complete and runnable;
they have not been executed, and no result is claimed for them.

---

## 2. How to run everything

On a machine with the Android SDK, JDK 17 and network access to Google's and
Maven's repositories:

```bash
./gradlew test                          # 183 local unit tests (real JUnit 4)
./gradlew connectedDebugAndroidTest     # 75 instrumented tests (device/emulator)
./gradlew lint                          # Android lint
./gradlew assembleDebug                 # debug APK
./gradlew assembleRelease               # unsigned release APK
```

### Running unit tests without the Android SDK

```bash
bash tools/bootstrap-verification-toolchain.sh   # once: fetches JDK 17 + kotlinc
bash verification/run-verification.sh            # all suites
bash verification/run-verification.sh Parser     # only classes matching "Parser"
```

The script compiles the Android-free source subset together with the **real test
classes from `app/src/test`** and runs them on the JVM. Exit code is 0 only when
every test passes.

**What it compiles:** `math/**`, `core/errors`, `core/formatting`,
`domain/{model,calculator,formula,solver,library}`, all of `app/src/test`, and
`verification/shim`.

**What it excludes and why:**

| Excluded | Reason |
|----------|--------|
| `domain/repository` | Declares `kotlinx.coroutines` `Flow` contracts; coroutines are not resolvable offline |
| `core/ui` | Compose + `R` resources |
| `core/design` | Compose Material 3 |
| `data/**`, `feature/**`, `ui/**`, `infra/**`, `navigation/**` | Android framework, Room, DataStore, WorkManager, Compose |
| all of `app/src/androidTest` | Requires a device |

### The JUnit shim — full disclosure

`verification/shim/JUnitShim.kt` provides `org.junit.Test`, `org.junit.Before`,
`org.junit.After`, `org.junit.Ignore` and `org.junit.Assert` with the same
signatures the tests use; `verification/shim/JUnitRunner.kt` is a reflective
runner that discovers `@Test` methods, runs `@Before`/`@After`, honours `@Ignore`,
prints per-test PASS/FAIL lines and exits non-zero on any failure.

It exists because `junit:junit:4.13.2` could not be downloaded in the authoring
environment (`repo1.maven.org` unreachable). It is a **test-harness substitute,
disclosed here and in the delivery report**, not a change to the tests: the same
test source files compile and run unchanged under real JUnit 4 in Gradle. Nothing
in `app/src/main` depends on the shim.

---

## 3. Reference results verified end to end

The specification's mandatory reference suite, all executed and green:

| Expression | Result |
|------------|--------|
| `2+2` | 4 |
| `2+3×4` | 14 |
| `(2+3)×4` | 20 |
| `√81` | 9 |
| `log10(1000)` | 3 |
| `ln(e)` | 1 |
| `sin(30°)` | 0.5 |
| `cos(60°)` | 0.5 |
| `200×15%` | 30 |
| 20% off 500 | 400 |
| 80 → 100 | 25% increase |
| `GCD(48,18)` | 6 |
| `LCM(12,18)` | 36 |
| `x²−5x+6=0` | x = 2, 3 |
| `mean(2,4,6)` | 4 |

Also verified: `-2^2 = -4`, `(-2)^2 = 4`, `2^-3 = 0.125`, `2^3^2 = 512`,
`1/2pi = 1/(2π)`, `3(4+5) = 27`, `1.5e3 = 1500`, `2e = 2·e`, `7 mod 3 = 1`,
`5! = 120`, `ans+5`, `0^0 = 1`, `0^-2` → DivisionByZero, `0.1+0.2 = 0.3` exactly,
and typed errors for `sqrt(−1)`, `ln(0)`, `log(…,1)`, `asin(2)`, `tan(90°)`,
`(−3)!`, `2.5!`, `20000!`, a 4000-node expression, 400 nested calls, unbalanced
parentheses, `1.2.3` and unknown functions.

---

## 4. Defects the tests found (and the fixes)

Every one of these was a real defect discovered by an executed test, fixed in the
source, and then pinned by a test:

| # | Defect | Found by | Fix |
|---|--------|----------|-----|
| 1 | Scientific/engineering mantissas rendered padded trailing zeros: `1.23456700000e+6` | `ExpressionEngineTest` | `NumberFormatter.renderMantissa(mantissa, trim)` wired to `policy.autoPrecision` |
| 2 | `1.2.3` was silently evaluated as implicit multiplication instead of being a syntax error | `ExpressionEngineTest` | Lexer rejects a second decimal point with `CalcError.Syntax` |
| 3 | `200 + 10% + 5%` returned 230.005 instead of 231, because the percentage prefix was read from the unrewritten token list | `CalculatorBehaviorTest` | `PercentagePolicy` now takes the prefix from the already-rewritten output |
| 4 | `sin(30)` could not be closed and `sqrt(16` was not auto-closed at evaluation, because a function's implicit `(` was not counted | `CalculatorBehaviorTest` | `openCount()` counts `Atom` tokens ending in `(`; `closingSuffix()` uses it |
| 5 | A 400-deep parenthesis test failed on the assumption that parentheses are AST nodes | `ExpressionEngineTest` | Not a product defect: the test was wrong. Split into `deepParenthesesAreGroupingNotNodes` and `excessiveNestingIsResourceLimit` using nested calls |
| 6 | `CalcError.UnsupportedOperation` had no user-facing message | compiler sweep of all main sources | Added to the exhaustive mapping in `core/ui/ErrorMessages.kt` |
| 7 | `CalculatorViewModel` was final but subclassed by the Standard/Scientific ViewModels | compiler sweep | Marked `open` |
| 8 | Library entries for gcd/lcm/prime factors linked to Quick Solve ids that the resolver did not accept | `CatalogCompletenessTest` | `formId` documented as "form **or** tool"; resolution accepts both |
| 9 | A library search test for "margin" found no entry, because no markup/margin reference existed | `CatalogCompletenessTest` | Added `lib_markup_margin` documenting that markup divides by cost and margin by selling price |

---

## 5. What is **not** covered

Stated plainly:

- **No coverage measurement exists.** Coverage is **NOT MEASURED**; no percentage
  is claimed anywhere in this repository.
- Instrumented tests (UI, Room, DataStore, WorkManager, permission audit) have not
  been executed — no device, no emulator, no Android SDK.
- Compose code has had a **structural** check only: `kotlinc` was run over all 64
  main-source files, producing **zero syntax errors**; every remaining diagnostic
  is an unresolved reference to Android/Compose artifacts that cannot be
  downloaded offline. That proves the sources parse and are internally consistent,
  and it does **not** prove they compile against the real SDK or render correctly.
- No performance benchmarks, no memory-leak detection, no monkey/robo testing.
- No tests of dynamic colour on Android 12+, foldable posture changes, or
  tablet-specific layouts beyond the size-driven branching in code.
- Locale-specific number rendering is not tested; grouping is locale-independent
  by design.

---

## 6. Conventions for adding tests

- Engine and domain tests must assert on `CalcNumber` comparisons or exact strings,
  never on formatted display text, so a formatting change cannot mask a math
  regression.
- Every new `CalcError` subtype requires a mapping entry in `ErrorMessages.kt`
  (enforced by exhaustiveness) and a test that the producing input raises it.
- Every new form in `FormulaCatalog` and every new tool in `QuickSolve` requires a
  sample input map in `CatalogCompletenessTest`; the suite fails if one is missing,
  so the catalog cannot drift from its tests.
- Every library entry with a `formId` must resolve, checked by the same suite.

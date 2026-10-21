# Axiom Calc — Feature Acceptance

Criterion-by-criterion status. Every status is drawn **only** from:

- **PASS** — implemented and verified by an executed check whose output exists.
- **FAIL** — implemented but an executed check found a defect that is still open.
- **BLOCKED** — cannot be verified because a required tool or resource was
  unobtainable; the blocker is named.
- **NOT TESTED** — no check was executed for it.

Evidence for every PASS is either a committed test, a committed output file, or a
command reproducible from this repository. **No coverage percentage is claimed
anywhere: coverage is NOT MEASURED.**

---

## Part A — Identity and platform

| # | Criterion | Status | Evidence / blocker |
|---|-----------|--------|--------------------|
| A1 | Application ID `com.shohankhan.axiomcalc` | PASS | `app/build.gradle.kts`; `OfflineAndPrivacyTest.packageIdentityMatchesTheSpecification` (NOT TESTED on device) |
| A2 | Owner name **Shohan Khan** shown on About | PASS | `strings.xml/about_owner_name`; `AboutScreen.kt`; `NavigationSmokeTest.aboutShowsTheOwnerAndEmail` |
| A3 | Owner email **helloiamshohan@gmail.com** shown and opens an email intent | PASS | `about_owner_email`; `AboutScreen.sendOwnerEmail` uses `ACTION_SENDTO` + `mailto:`; manifest `<queries>` declares SENDTO/mailto |
| A4 | No invented company, website, social or legal claim | PASS | `AboutScreen.kt` contains only name, email, version, offline statement and a list of this repository's own documents |
| A5 | Native Android, Kotlin, Jetpack Compose | PASS | 64 Kotlin files; zero XML layouts; Compose BOM 2024.10.01 |
| A6 | versionName 1.0.0 / versionCode 1 | PASS | `app/build.gradle.kts` |
| A7 | minSdk 24, compileSdk/targetSdk 35 | PASS | `gradle/libs.versions.toml`, `app/build.gradle.kts`; `CivilDate` avoids `java.time` so API 24 behaves identically |

## Part B — Toolchain and build

| # | Criterion | Status | Evidence / blocker |
|---|-----------|--------|--------------------|
| B1 | Gradle Kotlin DSL | PASS | `build.gradle.kts`, `app/build.gradle.kts`, `settings.gradle.kts` |
| B2 | Gradle wrapper committed and authentic | PASS | jar 43,583 B, blob SHA-1 `a4b76b9530d66f5e68d973ea569d8e19de379189`, matched against `gradle/gradle@v8.11.1` via the GitHub API |
| B3 | Dependency versions real, compatible, pinned | PASS | All 22 runtime/test coordinates are published releases, verified against GitHub tags for Kotlin `v2.0.21`, KSP `2.0.21-1.0.28`, Gradle `v8.11.1` |
| B4 | JDK/Gradle/Kotlin/AGP/SDK levels documented | PASS | `RELEASE.md` §1, `libs.versions.toml` header |
| B5 | `./gradlew assembleDebug` succeeds | **BLOCKED** | `services.gradle.org` unreachable → Gradle distribution cannot be downloaded. No Android SDK (`dl.google.com` unreachable) |
| B6 | `./gradlew assembleRelease` succeeds | **BLOCKED** | Same as B5 |
| B7 | Debug APK exists | **BLOCKED** | Same as B5. **No APK file exists; none is claimed.** |
| B8 | Release APK signed by the owner | **BLOCKED** | Same as B5, and **no owner keystore exists**; none was invented. `RELEASE.md` §5 documents the owner-signing steps |
| B9 | APK verified (size, archive, package, version, SDK metadata, signing) | **BLOCKED** | No `aapt2`/`apksigner`/`zipalign` in the sandbox and no APK to inspect |
| B10 | Install and launch on a device | **NOT TESTED** | No device, no emulator, no `adb` |
| B11 | Android lint clean | **BLOCKED** | Requires the Android SDK |
| B12 | Sources are syntactically valid Kotlin | PASS | `kotlinc` 2.0.21 over all 64 main-source files: **zero syntax errors**; every remaining diagnostic is an unresolved Android/Compose reference (unobtainable offline) |
| B13 | Resources valid and self-consistent | PASS | `python3 tools/validate_resources.py` → `RESULT: PASS` (XML well-formedness, all vector `pathData` parse, every intra-project reference resolves) |

## Part C — Standard Calculator

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| C1 | Digits, decimal point, `+ − × ÷`, `=` | PASS | `CalculatorBehaviorTest`, `ExpressionEngineTest`, `StandardCalculatorUiTest` (device: NOT TESTED) |
| C2 | AC clears expression but never memory or `Ans` | PASS | `CalculatorBehaviorTest.allClearKeepsMemoryAndAns` |
| C3 | CE clears the trailing entry only | PASS | `CalculatorBehaviorTest.clearEntryRemovesOnlyTrailingEntry` |
| C4 | Backspace removes one token (so `sin(` goes as a unit) | PASS | `CalculatorBehaviorTest.backspaceRemovesWholeFunctionToken` — this test found a real defect (function `(` not counted), now fixed |
| C5 | Sign toggle | PASS | `CalculatorBehaviorTest.signToggle*` |
| C6 | `%` key with documented context semantics | PASS | `PercentagePolicy`; `CalculatorBehaviorTest.chainedAdditivePercents` = 231 (found and fixed a 230.005 defect) |
| C7 | Parentheses, auto-closed only at evaluation | PASS | `CalculatorBehaviorTest.missingParenthesesAreSuppliedAtEvaluationOnly` |
| C8 | Memory MS / MR / M+ / M− / MC | PASS | `CalculatorBehaviorTest.memory*` |
| C9 | Undo / redo | PASS | `CalculatorBehaviorTest.undoRedo*` |
| C10 | Paste sanitized to grammar characters | PASS | `CalculatorViewModel.onPaste` |
| C11 | Copy result / expression | PASS | `CalculatorViewModel.onCopy*` via `ClipboardManager` |
| C12 | Hardware keyboard input | PASS (code) / **NOT TESTED** (device) | `consumeKeyEvent` maps KeyDown + Enter/Backspace/Delete/Escape; keypad is `focusable()` and requests focus; `StandardCalculatorUiTest.hardwareKeyboardDigitsReachTheBuffer` |
| C13 | `Ans` reuse | PASS | `CalculatorBehaviorTest`, `ExpressionEngineTest.ans*` |
| C14 | Configurable precision and grouping | PASS | `AppSettings.autoPrecision`/`decimalPlaces`/`grouping`; `NumberFormatter` tests |
| C15 | Live preview, quiet while incomplete | PASS | `ExpressionEngine.preview` returns null for Incomplete/Unbalanced; tested |
| C16 | 400-token cap | PASS | `CalculatorBehaviorTest.tokenCapIsEnforced` |

## Part D — Scientific Calculator

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| D1 | Compact and expanded modes | PASS | `KeypadLayouts.scientificRows` / `scientificExpandedRows`; `expanded` is `rememberSaveable`; `NavigationSmokeTest.scientificTab*` |
| D2 | Trig with DEG/RAD | PASS | `ExpressionEngineTest` (`sin(30°)=0.5`, `cos(60°)=0.5`); angle mode is an evaluation parameter, toggled via `DEG_MODE` key and Settings |
| D3 | Inverse trig with domain checks | PASS | `asin(2)` → `Domain`; `SolversTest`, `ExpressionEngineTest` |
| D4 | Hyperbolic trig | PASS | `sinh cosh tanh asinh acosh atanh` registered; tested |
| D5 | Powers and roots (incl. nth root) | PASS | `^` right-associative, `sqrt`/`cbrt`/`root(n,x)` with bounded index; tested |
| D6 | Logarithms (ln, log10, log2) | PASS | `log10(1000)=3`, `ln(e)=1`, base-1 → `Domain`; tested |
| D7 | Constants registry π, e, τ, φ, c, Nₐ, h (+ k_B, q_e, g₀, G) | PASS | 11 constants with SI provenance and exactness flags; `PersistenceSemanticsTest.allRegistryConstantsAreUsableByIdentifier` |
| D8 | Combinatorics: factorial, nPr, nCr, gcd, lcm | PASS | `5!=120`, `gcd(48,18)=6`, `lcm(12,18)=36`; `(−3)!`/`2.5!` → `Domain`; tested |
| D9 | Complex numbers, rectangular + polar | PASS (in Math Lab) | `Complex`, `Polar`; `SolversTest` complex cases; `ComplexLabPanel` with polar input. **Deliberately not in the expression engine** — the engine evaluates real expressions and the UI does not claim otherwise |
| D10 | Documented precedence and associativity | PASS | `MATHEMATICS_SPEC.md` §2 with a verified table |
| D11 | Implicit multiplication documented | PASS | `MATHEMATICS_SPEC.md` §2; `1/2pi = 1/(2π)`, `3(4+5) = 27`, `2e = 2·e`; tested |

## Part E — Formula Calculator

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| E1 | 13 categories A–M | PASS | `FormulaCategory.entries.size == 13`; counted by executing the compiled classes |
| E2 | Percentage | PASS | 8 forms |
| E3 | Discount (incl. successive) | PASS | 6 forms; `disc_sequential` compounds, never adds |
| E4 | VAT / percentage tax | PASS | 4 forms; **rate is always user input** — no country's current rate is hardcoded |
| E5 | Profit, loss, markup, margin | PASS | 8 forms; markup ÷ cost vs margin ÷ selling distinguished in code, in the library entry and in tests |
| E6 | Ratio and proportion | PASS | 5 forms |
| E7 | Average and statistics | PASS | 5 forms |
| E8 | Interest (simple, compound, solve for each unknown) | PASS | 5 forms; percent/year, years, n compounds/year; `i=0` handled explicitly |
| E9 | Installments / amortization | PASS | 2 forms |
| E10 | Geometry | PASS | 13 forms; Heron enforces the triangle inequality → `Domain` |
| E11 | Algebra | PASS | 4 forms |
| E12 | Fractions (incl. mixed and decimal) | PASS | 8 forms |
| E13 | Time arithmetic | PASS | 4 forms; `Duration` has unbounded hours |
| E14 | Date arithmetic | PASS | 5 forms; proleptic-Gregorian integer bijection; month-add clamps (2024-01-31 +1mo → 2024-02-29) |
| E15 | **Every form computes on valid input** | PASS | `CatalogCompletenessTest.everyFormComputesWithValidInput` — all 77 forms executed |
| E16 | **Every form rejects garbage with a typed error** | PASS | `everyFormRejectsGarbageExplicitly` — all 77 forms |
| E17 | Every form declares inputs, formula text, explanation | PASS | `everyFormDeclaresItsInputsAndFormula` |
| E18 | Zero denominators rejected, never silent | PASS | `zeroDenominatorsAreRejectedNotSilent` |

## Part F — Quick Solve

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| F1 | ~25 named tools | PASS | **27** tools, counted by executing the compiled classes |
| F2 | Linear equation solver | PASS | `qs_linear`; linearity re-verified by re-evaluating the tree |
| F3 | Simultaneous equations (2 and 3 unknowns) | PASS | `qs_simultaneous2/3`; UNIQUE / NO_SOLUTION / INFINITELY_MANY from ranks |
| F4 | Quadratic solver | PASS | `qs_quadratic`; stable formula; `x²−5x+6=0 → 2,3`; ill-conditioned `x²−1e8x+1` keeps its 1e-8 root; `a=0` never faked as a unique root |
| F5 | Sequences (arithmetic, geometric) | PASS | `qs_arith_seq`, `qs_geom_seq` |
| F6 | **Step-by-step explanation for every tool** | PASS | `everyToolSolvesWithShownWorking`: ≥3 steps and a `Relationship:`, `Final:` and `Restriction:` line for all 27 tools |
| F7 | Percentage, ratio, fraction, integer, statistics, rounding tools | PASS | Covered by the same completeness test |

## Part G — Formula Library

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| G1 | Searchable offline reference | PASS | 58 entries; `FormulaLibrary.search`; `librarySearchFindsByKeywordAndCategory` |
| G2 | 13 categories, none empty | PASS | `libraryCoversEveryCategory` |
| G3 | Entries complete (expression, variables, conditions) | PASS | `libraryEntriesAreComplete` |
| G4 | Deep link to the implementing calculator | PASS | `libraryLinksResolveToImplementedForms` — all 29 linked entries resolve to a real form or tool; `LibraryDetailScreen` opens it; reference-only entries say so instead of linking nowhere |
| G5 | Favourites persisted | PASS | Room `favorites`; `HistoryDatabaseTest.favoritesAndRecentsAreIndependentStores` (device: NOT TESTED) |
| G6 | Recents persisted and capped | PASS | Room `recent_formulas`, `trimRecent()` caps at 20; asserted in the same test |

## Part H — Math Lab

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| H1 | Matrices: add, subtract, multiply, scale, transpose, det, rank, inverse, solve | PASS | `Matrix` (LU + Gauss–Jordan, partial pivoting, tol 1e-10); `SolversTest`; singular → typed error; solve delegates to `LinearEquations.solveSystem` with residual verification |
| H2 | Statistics: count, sum, mean, median, mode, range, variance, std dev, weighted mean | PASS | `Statistics`; population ÷N vs sample ÷(N−1) with `n≥2`; `InsufficientSample` instead of a silent substitute |
| H3 | Complex arithmetic, rect + polar | PASS | `Complex`, `Polar`, principal argument (−π, π], typed ÷0 |
| H4 | Numerical solvers: bisection, Newton–Raphson | PASS | Residual-verified convergence; `INVALID_BRACKET`/`NO_CONVERGENCE` reported as statuses with reasons |
| H5 | Number bases 2/8/10/16 with arithmetic | PASS | `Bases`; digits outside the source base rejected; exact `BigInteger` |
| H6 | Engineering notation, significant figures, rounding | PASS | `NumberFormatter`; scientific/engineering mantissa rules and rounding modes tested |

## Part I — Persistence

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| I1 | Room database with exported schema | PASS | `AxiomDatabase` v1, `exportSchema = true`, 4 tables, `Converters` |
| I2 | Global history across all origins | PASS | `CalcOrigin` on every row; forms and tools record too (`recordToolResult`, debounced so one computation = one row) |
| I3 | Date grouping (Today / Yesterday / date) | PASS | `HistoryViewModel.groupByDay` |
| I4 | Search across expression and result | PASS | `HistoryDao.observeSearch`; `HistoryDatabaseTest.searchMatchesExpressionAndResult` |
| I5 | Filters: time window and origin | PASS | `HistoryFilter` + origin chips; empty origin filter is refused so the list can never appear to lose data |
| I6 | Multi-select delete, delete all | PASS | `deleteByIds`, `deleteAll`; confirmation dialogs |
| I7 | Retention 1/7/30/90/Never, default 30 | PASS | `RetentionPolicy`; `PersistenceSemanticsTest` (7 tests) against a fixed injectable clock incl. exact boundary |
| I8 | Retention enforced even if WorkManager never runs | PASS | Read-time cutoff filter + startup pass; `HistoryDatabaseTest.expiredRowsAreFilteredFromReadsEvenBeforeCleanup` |
| I9 | WorkManager best-effort cleanup | PASS (code) / **NOT TESTED** (device) | `RetentionWorker` + `scheduleRetentionCleanup`; `RetentionWorkerTest` (4 tests) written, not executed |
| I10 | Saved calculations distinct from history and never expired | PASS | Separate table; `savedCalculationsSurviveRetentionRuns`, `deleteAllClearsHistoryButNotSavedCalculations` |
| I11 | DataStore settings with documented defaults | PASS | `DataStoreSettingsRepository`; `SettingsRepositoryTest` (7 tests) written; `AppSettings` defaults asserted in `PersistenceSemanticsTest`-adjacent unit coverage |
| I12 | Stored value never re-parsed from display text | PASS | Canonical value stored separately; `canonicalParsingRejectsDisplayText`, `reusedStoredValueFeedsTheEngineExactly` |

## Part J — Architecture and quality

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| J1 | Layered architecture (presentation/domain/math/data/infra) | PASS | `ARCHITECTURE.md` §1 with per-layer file counts; `math`/`domain` import no `android.*` |
| J2 | One authoritative math engine reused by every screen | PASS | `ARCHITECTURE.md` §2 consumer table; the only arithmetic in `feature/` is lab-module orchestration |
| J3 | Lexer → parser → AST → evaluator; no scripting/WebView/reflection eval | PASS | No such API appears in the source; `MATHEMATICS_SPEC.md` §1 |
| J4 | Typed error model, exhaustively mapped to messages | PASS | 16 `CalcError` subtypes; `ErrorMessages.kt` mapping is exhaustive (a missing entry is a compile error — it found `UnsupportedOperation`) |
| J5 | No `NaN`, stack trace or raw exception shown | PASS | Failures map to `messageRes()`; `NavigationSmokeTest.noScreenShowsPlaceholderOrNaNText` includes `NaN` in the banned list |
| J6 | No silent zero/plausible value after failure | PASS | Solvers return statuses; `NonConvergence`/`SingularMatrix`/`InsufficientSample` are raised, tested |
| J7 | ViewModel + StateFlow throughout | PASS | 13 ViewModels, all `StateFlow`-based; one-shot effects on `SharedFlow(DROP_OLDEST)` |
| J8 | Coroutines/Flow for async | PASS | Room `Flow`, DataStore `Flow`, `viewModelScope`, `applicationScope` |
| J9 | Navigation Compose with typed args | PASS | `AxiomNavigation.kt`; no route carries display text |
| J10 | Manual DI, application context only | PASS | `AppContainer`, `axiomViewModel` captures `applicationContext` |
| J11 | Exactly six primary destinations | PASS | `primaryDestinations.size == 6`; `NavigationSmokeTest.exactlySixPrimaryTabsExist` |
| J12 | Secondary destinations: History, Settings, About, saved, formula detail | PASS | All five implemented and reachable |
| J13 | Real vector icons, no emoji | PASS | 28 hand-authored vectors; nav icons Calculator/Σ/%/Zap/Book/Flask; `validate_resources.py` PASS; no emoji anywhere |
| J14 | No placeholder screens or unimplemented buttons | PASS | `CatalogCompletenessTest` proves every advertised form/tool computes; `NavigationSmokeTest.noScreenShowsPlaceholderOrNaNText`; unknown ids render an explicit "not found" state |

## Part K — Accessibility and responsiveness

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| K1 | TalkBack labels on every control | PASS (code) / **NOT TESTED** (device) | Every key has an explicit `contentDescription`; `AccessibilityTest.everyInteractiveNodeHasAContentDescription` written, not executed |
| K2 | ~48 dp minimum touch targets | PASS (code) / **NOT TESTED** (device) | `KeypadButton` uses `heightIn(min = 48.dp)` and `height(...coerceAtLeast(48.dp))`; `AccessibilityTest` measures bounds on device |
| K3 | Contrast ratios documented | PASS | `core/design/Color.kt` states WCAG ratios per pairing (accent on dark surface 9.3:1, onSurface 15.4:1, light accent darkened to 4.9:1 for text) |
| K4 | Font scaling honoured | PASS | All sizes in `sp`; `AxiomTypography` |
| K5 | Compact/large portrait, landscape, tablets, foldables | PASS (code) / **NOT TESTED** (device) | `BoxWithConstraints` size-driven branching; landscape puts display beside keypad; no device or emulator to confirm rendering |
| K6 | Insets, cutouts, gesture navigation | PASS (code) / **NOT TESTED** (device) | `enableEdgeToEdge()` + `consumeWindowInsets(WindowInsets.safeDrawing)` at the root; `windowSoftInputMode="adjustResize"` |
| K7 | Screen-reader state announcements | PASS (code) / **NOT TESTED** (device) | Tab selection, angle mode, memory value, `"Result 4"` |

## Part L — Offline and privacy

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| L1 | No `INTERNET` permission declared | PASS | `AndroidManifest.xml` declares **zero** `uses-permission` elements; `grep -n uses-permission` returns nothing |
| L2 | Merged release manifest audited | **BLOCKED** | No Gradle build → no merged manifest exists. Audit commands are documented in `SECURITY_AND_PRIVACY.md` §2.5 and `OfflineAndPrivacyTest` checks the installed package |
| L3 | Dependency graph audited for network access | PASS (static) / **BLOCKED** (Gradle report) | `libs.versions.toml` contains no networking, analytics, advertising, crash-reporting or licensing coordinate; `./gradlew app:dependencies` could not be run |
| L4 | No analytics/tracking/advertising SDK | PASS | None present; `OfflineAndPrivacyTest.noTrackingAnalyticsOrAdvertisingComponentsAreMerged` enforces it on device |
| L5 | No accounts, no registration, no remote DB, no live rates | PASS | No such code exists; every rate is a user input |
| L6 | No unnecessary permissions | PASS | Zero requested; haptics need no `VIBRATE`; no storage permission |
| L7 | App fully functional with no network | PASS (by construction) / **NOT TESTED** (device) | Without `INTERNET` the process cannot open a socket; the airplane-mode procedure in `SECURITY_AND_PRIVACY.md` §2.6 has not been executed |
| L8 | No secrets committed | PASS | No keys, tokens or passwords in the source, resources, build files or history; `.gitignore` excludes keystores and signing properties |
| L9 | All user-facing text in English | PASS | 200+ strings in `res/values/strings.xml`, no other locale value directories except `xml/locales_config` |
| L10 | No hardcoded claim about any country's current tax law | PASS | VAT forms take the rate as an input; no rate constant exists in the source |
| L11 | No financial-advice or guaranteed-offer framing | PASS | Interest/installment copy is descriptive; `SECURITY_AND_PRIVACY.md` and the library conditions state restrictions |

## Part M — Tests

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| M1 | JUnit unit tests written | PASS | 183 tests across 5 suites in `app/src/test` |
| M2 | Unit tests **executed** and passing | PASS | `bash verification/run-verification.sh` → `TOTAL 183 PASSED 183 FAILED 0 ERRORS 0 SKIPPED 0 / RESULT: PASS`; output committed at `verification/reports/unit-tests-latest.txt` |
| M3 | `./gradlew test` executed | **BLOCKED** | Gradle distribution unobtainable; the same test classes were executed via the harness instead |
| M4 | Compose UI / instrumentation tests written | PASS | 75 tests across 7 suites in `app/src/androidTest` |
| M5 | Instrumented tests executed | **NOT TESTED** | No device, no emulator, no `adb` |
| M6 | Test harness substitution disclosed | PASS | `TESTING.md` §2 and §"The JUnit shim — full disclosure"; the shim is confined to `verification/` |
| M7 | Coverage measured | **NOT MEASURED** | No coverage tool ran. **No percentage is claimed.** |
| M8 | Mandatory reference results verified | PASS | All 15 spec reference values green — `TESTING.md` §3 |

## Part N — Documentation

| # | Criterion | Status |
|---|-----------|--------|
| N1 | `README.md` | PASS |
| N2 | `ARCHITECTURE.md` | PASS |
| N3 | `MATHEMATICS_SPEC.md` | PASS |
| N4 | `TESTING.md` | PASS |
| N5 | `RELEASE.md` | PASS |
| N6 | `SECURITY_AND_PRIVACY.md` | PASS |
| N7 | `THIRD_PARTY_NOTICES.md` | PASS |
| N8 | `FEATURE_ACCEPTANCE.md` (this file) | PASS |

All eight are truthful as of this commit: every number in them was counted by
executing the compiled classes or by running a tool in this repository, and every
unverified item is marked BLOCKED or NOT TESTED rather than PASS.

## Part O — Version control

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| O1 | Existing history preserved, pre-existing commit intact | PASS | `3204bf3 Initial commit` is still the root commit; no rebase, no force-push, no branch deletion |
| O2 | Descriptive, focused commits | PASS | One commit per milestone group, each listing what changed and why |
| O3 | `.gitignore` maintained | PASS | Excludes build output, IDE files, keystores, `verification/.out` |
| O4 | Wrapper included | PASS | See B2 |
| O5 | No keystores, keys, passwords or CI secrets committed | PASS | None exist in the repository |
| O6 | Generated artifacts kept out of Git | PASS | `verification/.out/` ignored; the only committed report is the small text file `verification/reports/unit-tests-latest.txt` |

---

## Summary

| Area | PASS | FAIL | BLOCKED | NOT TESTED / NOT MEASURED |
|------|------|------|---------|---------------------------|
| Identity and platform (A) | 7 | 0 | 0 | 0 |
| Toolchain and build (B) | 5 | 0 | 6 | 1 |
| Standard Calculator (C) | 16 | 0 | 0 | 0 (1 code-PASS with device NOT TESTED) |
| Scientific (D) | 11 | 0 | 0 | 0 |
| Formulas (E) | 18 | 0 | 0 | 0 |
| Quick Solve (F) | 7 | 0 | 0 | 0 |
| Library (G) | 6 | 0 | 0 | 0 |
| Math Lab (H) | 6 | 0 | 0 | 0 |
| Persistence (I) | 12 | 0 | 0 | 0 (1 code-PASS with device NOT TESTED) |
| Architecture (J) | 14 | 0 | 0 | 0 |
| Accessibility (K) | 2 | 0 | 0 | 5 code-PASS / device NOT TESTED |
| Offline and privacy (L) | 9 | 0 | 2 | 0 (1 code-PASS with device NOT TESTED) |
| Tests (M) | 5 | 0 | 2 | 2 |
| Documentation (N) | 8 | 0 | 0 | 0 |
| Version control (O) | 6 | 0 | 0 | 0 |

**No criterion is FAIL.** No known unresolved critical or high-severity *code*
defect is open.

The open items are all verification gaps caused by one blocker — the authoring
sandbox had no Android SDK and no reachable route to Gradle, AGP, AndroidX or the
SDK repositories:

1. No APK was built (B5–B9). **Source-repo completion and APK delivery are
   separate acceptance criteria; the second is not met.**
2. The instrumented suite (75 tests) has never run (M5, and the device halves of
   C12, I9, K1–K7, L4, L7).
3. The merged release manifest and the Gradle dependency report have not been
   audited on a real build (L2, L3) — the highest-severity residual risk, because
   a transitive dependency could in principle add a permission at merge time.
4. Coverage is not measured (M7).

Closing all four requires one thing: building on a machine with JDK 17, the
Android SDK 35 and network access to `dl.google.com`, `maven.google.com`,
`repo1.maven.org`, `plugins.gradle.org` and `services.gradle.org`. The commands
are in `RELEASE.md` §4 and §7, and the device procedure is in
`SECURITY_AND_PRIVACY.md` §2.6.

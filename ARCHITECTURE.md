# Axiom Calc — Architecture (as built)

This document describes the architecture **as it exists in the repository**, not
as it was planned. Every path listed below is a real file; every rule stated
below is enforced by code, by a test, or by the platform.

---

## 1. Layering

```
┌──────────────────────────────────────────────────────────────────────┐
│ presentation   feature/*  ui/AxiomApp.kt  core/ui  core/design       │
│                Compose screens + ViewModels (StateFlow)              │
├──────────────────────────────────────────────────────────────────────┤
│ domain         domain/model  domain/calculator  domain/formula       │
│                domain/solver  domain/library  domain/repository      │
│                Pure Kotlin. No Android imports.                      │
├──────────────────────────────────────────────────────────────────────┤
│ math           math/lexer  math/parser  math/evaluator               │
│                math/functions  math/constants  math/precision        │
│                math/complex  math/matrices  math/statistics          │
│                math/solvers                                          │
│                Pure Kotlin. The single authoritative engine.         │
├──────────────────────────────────────────────────────────────────────┤
│ data           data/local (Room)  data/preferences (DataStore)       │
│                data/repository (implementations of domain contracts) │
├──────────────────────────────────────────────────────────────────────┤
│ infra          infra/di (manual container, ViewModel factories)      │
│                AxiomCalcApplication (retention, WorkManager config)  │
└──────────────────────────────────────────────────────────────────────┘
        core/errors (typed error model)  core/formatting (presentation of numbers)
        — shared by every layer above; they import nothing from it.
```

**Dependency direction is strictly downward.** `math` and `domain` never import
`android.*`, `androidx.*` or `R`. That is not a convention only: it is what makes
`verification/run-verification.sh` able to compile and execute 183 unit tests
without the Android SDK present.

### File inventory (64 Kotlin files, 12,472 lines)

| Layer | Files | Lines |
|-------|-------|-------|
| `math/` | 22 | 3,262 |
| `domain/` | 7 | 2,254 |
| `feature/` | 19 | 4,897 |
| `core/` | 6 | 1,015 |
| `data/` | 3 | 388 |
| `ui/` | 1 | 346 |
| `infra/` | 2 | 158 |
| `navigation/` | 1 | 79 |
| root (`MainActivity`, `AxiomCalcApplication`) | 2 | ~90 |

---

## 2. The single authoritative engine

`math/evaluator/ExpressionEngine.kt` is the **only** public calculation surface
for expression evaluation:

```kotlin
object ExpressionEngine {
    fun parseTree(source: String): Expr?
    fun parseError(source: String): CalcError?
    fun evaluate(source: String, angleMode: AngleMode, ans: CalcNumber): CalcResult
    fun evaluateTree(tree: Expr, angleMode: AngleMode, ans: CalcNumber): CalcResult
    fun preview(source: String, angleMode: AngleMode, ans: CalcNumber): CalcResult?
}
```

Pipeline: **source → `Lexer` → tokens → `Parser` → `Expr` AST → `Evaluator` →
`CalcNumber`**. There is no JavaScript engine, no WebView, no reflection and no
script evaluation anywhere in the app.

Consumers, all of which call the engine rather than reimplementing arithmetic:

| Consumer | Path |
|----------|------|
| Standard + Scientific calculators | `domain/calculator/CalculatorBuffer` → `ExpressionEngine` |
| Percentage key semantics | `domain/calculator/PercentagePolicy` → `ExpressionEngine` |
| Formula Calculator (13 categories, 77 forms) | `domain/formula/FormulaCatalog` → `PercentMath`, `Finance`, `IntegerMath`, `Fraction`, `CivilDate`, `ExpressionEngine` |
| Quick Solve (27 tools) | `domain/solver/QuickSolve` → the same solver modules |
| Formula Library links | `domain/library/FormulaLibrary.formId` → verified resolvable by `CatalogCompletenessTest` |
| Math Lab modules | `feature/mathlab/MathLabViewModels` → `Matrix`, `Statistics`, `Complex`, `NumericSolvers`, `Bases`, `NumberFormatter`, `LinearEquations` |

The solver modules (`math/solvers/*`) are themselves pure functions over
`CalcNumber`/`Double` and are unit-tested directly. No screen contains its own
arithmetic: the only math that ever appears in `feature/` is the lab modules'
*orchestration* of engine calls plus `Double` formatting.

### Guarantee: display text never re-enters the engine

`core/formatting/NumberFormatter` renders `CalcNumber → String`. The reverse
direction does not exist in the codebase — there is no `NumberFormatter.parse`.
Persisted results store a separate canonical value
(`CalcNumber.toCanonicalString()`), and `PersistenceSemanticsTest` asserts that a
grouped display string such as `1,234,567` is **rejected** by
`CalcNumber.fromCanonical`, which is precisely why the two fields are stored
separately.

---

## 3. Domain layer

### `domain/model/Models.kt`
`ThemeMode`, `RetentionPolicy` (with `cutoffMillis`/`isExpired` and an injectable
`TimeSource`), `CalcOrigin`, `HistoryEntry`, `SavedCalculation`, `AppSettings`.
All immutable data classes; `AppSettings` defaults are the documented product
defaults (theme System, haptics on, degrees, auto precision, 6 decimals, grouping
on, decimal notation, retention 30 days).

### `domain/calculator/CalculatorBuffer.kt`
Token-level editing buffer (not string editing). Tokens are `Num`, `Op`, `LParen`,
`RParen`, `Percent`, `Factorial`, `Atom`. Consequences that are visible in the UI
and asserted in `CalculatorBehaviorTest`:

- backspace removes **one token** (so `sin(` disappears as a unit);
- CE clears the trailing entry only;
- AC clears the expression but never memory and never `Ans`;
- operator keys replace a trailing operator instead of stacking;
- parentheses are auto-closed **only at evaluation time**, so the buffer never
  contains invented tokens while typing;
- undo/redo are whole-buffer snapshots;
- a 400-token cap prevents unbounded growth.

### `domain/calculator/PercentagePolicy.kt`
The `%` **key** is not the `%` **operator**. The engine's postfix `%` means
`×0.01`; the key is context-sensitive and rewrites the token list before
evaluation:

| Context | `a + b%` means |
|---------|----------------|
| after `+` or `−` | `a + (a·b/100)` |
| after `×`, `÷`, or at the start | `b/100` |
| prefix not evaluatable | `b/100` fallback |

Chained percentages compound, because the prefix is taken from the **already
rewritten** output list: `200 + 10% + 5%` = 231, not 230.005. That exact defect
was found by a test and fixed; the test now pins it.

### `domain/formula/FormulaCatalog.kt`
`FormulaCategory` (13 entries, ids `percentage … date`), `FormInput`,
`ResultLine`, `FormResult(lines, substitution, steps)`, `FormulaForm`, and
`FormulaCatalog` with `all`, `byCategory`, `byId`, `search`. Forms take
`Map<String, String>` — raw user text — and parse internally, because a form may
need a dataset (`"2, 4, 6"`) or a date (`"2024-02-28"`), which a numeric map
cannot express.

### `domain/solver/QuickSolve.kt`
27 `QuickTool`s, each returning `FormResult` with a `steps` list. Every step list
is asserted to contain a `Relationship:`, a `Final:` and a `Restriction:` line, so
"step-by-step explanation" is a checked property, not a claim.

### `domain/library/FormulaLibrary.kt`
58 `LibraryFormula` entries across 13 `LibraryCategory`s, each with expression,
variables, conditions, units, keywords and a `formId` that resolves to either a
`FormulaCatalog` form or a `QuickSolve` tool. `CatalogCompletenessTest` fails if
any link dangles.

### `domain/repository/Repositories.kt`
Four contracts: `HistoryRepository`, `SavedCalculationRepository`,
`FormulaFavoritesRepository`, `SettingsRepository`. The domain declares them;
`data/` implements them; ViewModels depend only on the interfaces.

---

## 4. Data layer

### Room (`data/local/AxiomDatabase.kt`)
Version 1, `exportSchema = true`, four tables:

| Table | Purpose | Retention |
|-------|---------|-----------|
| `history` | Every committed calculation | Removed past the policy cutoff |
| `saved_calculations` | User-named calculations | **Never** removed |
| `favorites` | Library favourites (PK = formula id) | Never |
| `recent_formulas` | Library recents, capped at 20 by `trimRecent()` | Capped |

`Converters` maps `CalcOrigin` ↔ its enum name. `history` stores
`result_value` (canonical, exact) **and** `result_display` (what the user saw).

### DataStore (`data/preferences/SettingsDataStore.kt`)
`DataStoreSettingsRepository` implements `SettingsRepository`. Only enum *names*,
booleans and integers are written. Reads fall back to documented defaults when a
value is missing or unparsable, and `update()` sanitises ranges
(`decimalPlaces` clamped to 0–16) before writing. A whole-store read failure
yields defaults instead of crashing — losing a preference is less harmful than
blocking the calculator.

### Retention enforcement — three independent paths
1. **Read-time filter** in `RoomHistoryRepository.filterNotExpired()`: expired rows
   cannot be displayed even if no cleanup has ever run.
2. **Startup pass** in `AppContainer.enforceRetentionAtStartup()`.
3. **Best-effort WorkManager** `RetentionWorker` (daily, battery-not-low). Its
   failure is non-fatal and it reports `deleted`/`policy`/`error` through
   `workDataOf`.

This ordering is deliberate: WorkManager is explicitly *not* relied upon, because
it may never run on a fresh install or under background restrictions.

---

## 5. Dependency injection

`infra/di/AppContainer.kt` is a hand-written container. No DI framework is used,
because a code-generation dependency is not justified at this scale and an
explicit graph is easier to audit.

```kotlin
val container = context.axiomContainer          // from AxiomCalcApplication
container.settings          : SettingsRepository
container.historyRepository : HistoryRepository
container.savedRepository   : SavedCalculationRepository
container.favoritesRepository: FormulaFavoritesRepository
container.applicationScope  : CoroutineScope     // SupervisorJob + Dispatchers.Default
```

`axiomViewModel { container, appContext -> … }` builds ViewModels through
`viewModelFactory { initializer { … } }`. It captures
`applicationContext` only, so no ViewModel can retain an Activity.

`AxiomCalcApplication` implements `Configuration.Provider` for WorkManager and
the `androidx.startup` `WorkManagerInitializer` is removed in the manifest, so no
background component runs before the retention preference has been read.

---

## 6. Presentation

### State
Every screen is driven by a `ViewModel` exposing `StateFlow<UiState>`:

| ViewModel | State |
|-----------|-------|
| `CalculatorViewModel` (open; `Standard…`/`Scientific…` subclasses) | `CalculatorUiState` + `SharedFlow<CalculatorEvent>` for haptics/messages |
| `ToolRunnerViewModel` | `ToolUiState` — one runner for all 104 forms and tools |
| `HistoryViewModel` | `HistoryUiState` with date groups, filters, selection |
| `SavedViewModel` | items + selection |
| `SettingsViewModel` | settings + history count |
| `LibraryViewModel` | `LibraryUiState` from a 6-way `combine` of query/category/filter/favourites/recents/settings |
| `MatrixLabViewModel`, `StatisticsLabViewModel`, `ComplexLabViewModel`, `SolverLabViewModel`, `BasesLabViewModel`, `FormattingLabViewModel` | `LabState` subclasses |

One-shot effects (haptics, snackbars, "open the save dialog") travel on a
`SharedFlow` with `DROP_OLDEST`, never as state, so they cannot be replayed after
a configuration change.

### Navigation
`navigation/AxiomNavigation.kt` declares a sealed `Destination` hierarchy: six
primary routes plus `history`, `saved`, `settings`, `about`, `form/{formId}`,
`solver/{toolId}`, `lab/{moduleId}`, `formula/{formulaId}`. Arguments are typed
nav args; no route carries formatted display text. `ui/AxiomApp.kt` hosts the
`NavHost`, the top bar (history shortcut + overflow for Saved/Settings/About) and
a `NavigationBar` with exactly the six primary destinations.

### Design system
`core/design/Color.kt` mirrors the verified XML tokens (accent `#38BDF8`, dark
surface `#0B0E13`, light surface `#F7F8FA`) and documents the WCAG contrast ratio
of each pairing in comments. `core/design/Theme.kt` adds the type scale, semantic
dimensions (`MinTouchTarget = 48.dp`) and `AxiomExtendedColors` for keypad roles
Material 3 does not define (digit/function/operator/equals/memory keys, preview
and error text). Dynamic colour is honoured on Android 12+; brand tokens are the
default below that.

`core/ui/Components.kt` holds the shared building blocks: `KeypadButton`,
`DisplayPanel`, `AxiomField`, `ResultRow`, `SectionCard`, `EmptyState`,
`NoticeBanner`. `core/ui/ErrorMessages.kt` maps every `CalcError` subtype to a
string resource — the mapping is exhaustive, so adding an error type without a
message is a compile error.

### Responsiveness
Layouts branch on measured size (`BoxWithConstraints`), not on device type: in
landscape the display sits beside the keypad; in portrait it sits above it. Key
heights shrink in compact windows but never below `MinTouchTarget`. No screen
scrolls the keypad. Insets are consumed at the root (`enableEdgeToEdge` +
`consumeWindowInsets(WindowInsets.safeDrawing)`), so content clears the status
bar, gesture area, cutout and IME.

### Accessibility
- Every key has an explicit `contentDescription` ("Divided by", not "÷").
- Touch targets are ≥ 48 dp by construction in `KeypadButton`.
- Tab items announce selection state (`"Tab Scientific, selected"`).
- The result line announces `"Result 4"`; the memory indicator announces its
  value and only exists when memory holds one.
- All text sizes are in `sp`, so the system font scale applies.
- `AccessibilityTest` measures target bounds on a device rather than assuming them.

---

## 7. Error handling

`core/errors/CalcError.kt` is a sealed hierarchy with a stable `code` and a
technical `detail`. `CalcException` carries it through the recursive evaluator and
**must not escape** the engine boundary: every public entry point catches it and
returns `CalcResult.Failure`.

Rules the UI follows:
- no `NaN`, no stack trace, no raw exception text is ever displayed;
- a failure never yields a silent `0` or a plausible-looking substitute;
- `CalcError.Incomplete` is not an error while typing — `preview()` returns `null`
  for incomplete input so the display keeps its last good preview instead of
  flashing a message per keystroke;
- solver modules report non-convergence and invalid brackets as statuses, because
  those are legitimate answers, not crashes.

---

## 8. What this architecture deliberately does **not** do

- No CAS. No symbolic integration or differentiation of arbitrary expressions, no
  unrestricted symbolic algebra, no ODE solving. Nothing in the UI offers them.
- No network layer of any kind — there is no `OkHttp`, `Retrofit`, `Ktor` or
  `URL` usage in the source, and no `INTERNET` permission in the manifest.
- No fragment-based navigation, no second Activity, no XML layouts (the XML that
  exists is resources: vectors, themes, strings, backup rules).
- No dependency-injection framework, no reactive library beyond coroutines/Flow.

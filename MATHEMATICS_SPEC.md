# Axiom Calc — Mathematics Specification

This is the normative description of what the engine actually computes. Every
rule below is implemented in `app/src/main/java/com/shohankhan/axiomcalc/math/`
and pinned by tests in `app/src/test/`. Where a rule was changed because a test
found a defect, that is stated.

---

## 1. Evaluation pipeline

```
source String
  → Lexer          tokens: number, identifier, operator, punctuation
  → Parser         Expr AST (precedence climbing)
  → Evaluator      CalcNumber (BigDecimal / BigInteger / Double as documented)
  → NumberFormatter presentation String
```

The AST is built once and can be re-evaluated with substituted variables
(`math/parser/Substitution.kt`), which is how solvers display steps whose numbers
are guaranteed to match the computed result — the steps are rendered from the same
tree that produced the value.

There is no scripting engine, no `eval` of strings from the UI layer, no
reflection and no WebView anywhere in the pipeline.

---

## 2. Operator precedence and associativity

Lowest to highest:

| Level | Operators | Associativity |
|-------|-----------|---------------|
| 1 | `+` `-` (binary) | left |
| 2 | `*` `/` `mod` | left |
| 3 | juxtaposition (implicit multiplication) | left, binds tighter than `*` |
| 4 | unary `+` `-` | right (prefix) |
| 5 | `^` | **right** |
| 6 | postfix `!` `%` | left |
| 7 | primary: number, constant, `f(…)`, `(…)` | — |

Verified consequences:

| Expression | Result | Why |
|------------|--------|-----|
| `2+3*4` | `14` | `*` binds tighter |
| `-2^2` | `-4` | unary minus is *below* `^`, so this is `-(2^2)` |
| `(-2)^2` | `4` | parentheses first |
| `2^3^2` | `512` | `^` is right-associative: `2^(3^2)` |
| `2^-3` | `0.125` | unary minus is legal directly after `^` |
| `1/2pi` | `1/(2π)` | juxtaposition binds tighter than `/` |
| `3(4+5)` | `27` | implicit multiplication |
| `7 mod 3` | `1` | infix `mod` at level 2 |
| `1.5e3` | `1500` | scientific literal |
| `2e` | `2·e` | a trailing `e` is Euler's number, not an exponent |
| `0^0` | `1` | defined convention |
| `0^-2` | DivisionByZero | not `Infinity` |

### Implicit multiplication
Juxtaposition is accepted between: number and identifier (`2pi`), number and open
parenthesis (`3(4)`), close parenthesis and identifier or number (`(2)(3)`,
`(2)3`), and identifier and open parenthesis only when the identifier is **not** a
known function (otherwise it is a call). Implicit multiplication binds tighter
than explicit `*` and `/`, which is what makes `1/2pi = 1/(2π)` rather than
`π/2`.

---

## 3. Numeric model and precision

`math/precision/CalcNumber.kt` wraps `BigDecimal` with:

- **MathContext:** `DECIMAL128` — 34 significant digits, `HALF_EVEN`.
- Exact addition, subtraction, multiplication and integer division.
- `0.1 + 0.2 = 0.3` exactly (verified in tests), because decimal arithmetic is
  used rather than binary floating point.
- Transcendental functions and fractional powers are computed in binary64
  (`Double`) and then re-rounded into DECIMAL128 through
  `CalcNumber.fromTranscendental`, which rejects `NaN` and `Infinity` as typed
  errors instead of storing them.
- `sqrt` and integer nth roots are computed exactly with `BigDecimal.sqrt` /
  Newton iteration (`bigNthRoot`), with a `1e-30` convergence floor rather than
  `ulp()`, which is unavailable on the target API levels.

Which representation is used where:

| Area | Representation |
|------|----------------|
| Expression engine, percentages, VAT, discount, profit/margin, interest, amortisation, fractions, integer math, dates/durations, statistics | `CalcNumber` / `BigDecimal` / `BigInteger` |
| Matrices, complex numbers, numerical root finding, base conversion rendering | IEEE-754 `Double` (`BigInteger` for base digit values) |

This split is explicit rather than accidental: LU/Gauss–Jordan elimination and
Newton iteration are numerically stable in `Double` and would be orders of
magnitude slower in `BigDecimal`, while money and percentage arithmetic must be
exact. Every result carries the tolerance or residual that justifies it (see §7,
§8).

### Guard rails
Resource limits are checked before work is done, and each produces
`CalcError.ResourceLimit` (or a typed domain error) rather than hanging or
crashing:

| Guard | Limit |
|-------|-------|
| `MAX_EXPONENT` | 100 000 |
| `MAX_FACTORIAL` | 10 000 |
| `MAX_LITERAL_DIGITS` | 60 |
| `MAX_INTEGER_DIGITS` | 100 000 |
| `MAX_EXPRESSION_DEPTH` | 256 |
| `MAX_EXPRESSION_NODES` | 4 000 |
| `MAX_MATRIX_CELLS` | 4 096 |
| Integer factorisation | ≤ 10¹⁸ and ≤ 20 M trial steps |
| Buffer tokens | 400 |

`20000!` therefore returns a typed error, not a hang and not a truncated number.

A note found by testing: **nested parentheses are not AST nodes** — the parser
collapses them — so 400 nested parentheses evaluate successfully and are *not* a
depth violation. Depth limits must be exercised with nested function calls
(`abs(abs(abs(…)))`). The test suite does exactly that, and both behaviours are
pinned by separate tests.

---

## 4. Lexer rules

- Numbers: digits with at most one decimal point, optional `e`/`E` exponent.
  **A second decimal point is a `CalcError.Syntax`** — `1.2.3` is rejected. This
  was a real defect: the lexer used to split it into `1.2` and `0.3` and evaluate
  the product silently. It is now an error, and a test pins it.
- Identifiers: Unicode letters/digits, resolved against `FunctionRegistry` then
  `ConstantRegistry`; anything else is `CalcError.UnknownFunction`.
- Operators: `+ - * / ^ ! %`, the word `mod`, and parentheses.
- Whitespace is insignificant.

---

## 5. Functions

`math/functions/FunctionRegistry.kt` defines ~40 functions with explicit arity
and domain checks:

- **Trigonometry:** `sin cos tan asin acos atan sinh cosh tanh asinh acosh atanh`
  plus `deg`, `rad`. Angle mode (`AngleMode.DEGREES` / `RADIANS`) is a parameter of
  evaluation, not global state, so two screens can differ safely.
  Domain: `asin`/`acos` require `|x| ≤ 1`; `tan(90°)` is a `Domain` error, not a
  huge number.
- **Roots and powers:** `sqrt cbrt root(n, x) hypot`. `root` bounds its index at
  10⁶. `sqrt(-1)`, `ln(0)`, `log` with base 1 are `Domain` errors.
- **Logarithms:** `ln log log10 log2`.
- **Exponential:** `exp`.
- **Rounding:** `round(x, decimals) trunc floor ceil sign abs`.
- **Combinatorics and integer math:** `fact gcd lcm ncr npr mod`. Factorial of a
  negative or non-integer argument is a `Domain` error (`(-3)!`, `2.5!`).
- **Aggregates:** `min max atan2`.

`math/constants/ConstantRegistry.kt` provides 11 constants with SI provenance and
exactness flags:

| id | symbol | value | exactness |
|----|--------|-------|-----------|
| `pi` | π | 3.14159265358979323846264338327950288 (35 digits) | exact |
| `e` | e | 2.71828182845904523536028747135266250 | exact |
| `tau` | τ | 6.28318530717958647692528676655900577 | exact |
| `phi` | φ | 1.61803398874989484820458683436563811 | exact |
| `c` | c | 299792458 m/s | exact by SI definition |
| `NA` | Nₐ | 6.02214076e23 1/mol | exact since 2019 redefinition |
| `h` | h | 6.62607015e-34 J·s | exact since 2019 redefinition |
| `kB` | k_B | 1.380649e-23 J/K | exact since 2019 redefinition |
| `q_e` | e⁻ | 1.602176634e-19 C | exact since 2019 redefinition |
| `g0` | g₀ | 9.80665 m/s² | conventional, exact by definition |
| `G` | G | 6.67430e-11 m³/(kg·s²) | **measured**, CODATA 2018, u_r = 2.2e-5 |

Lookup is case-insensitive by id and also accepts the typographic symbol, so the
Scientific keypad can insert `π`, `τ` and `φ` directly and the engine still
resolves them. `τ/2` and `π` agree to within DECIMAL128 rounding (< 1e-30) but
are not bit-identical, because the constants are stored to 35 digits and every
operation rounds to 34 — a test asserts the tolerance rather than exact equality.

---

## 6. Percentage semantics (two different things)

**The engine's postfix `%` operator** means `× 0.01`. So `200×15%` = 30.

**The calculator's `%` key** is context-sensitive, implemented in
`domain/calculator/PercentagePolicy.kt` by rewriting the token list before
evaluation:

| Preceding binary operator | `a OP b%` evaluates as |
|---------------------------|------------------------|
| `+` | `a + (a·b/100)` |
| `−` | `a − (a·b/100)` |
| `×`, `÷`, or none (start of expression) | `b/100` |
| inside a parenthesised group | `b/100` |
| prefix not evaluatable | `b/100` (documented fallback) |

Chained percentages compound, because the prefix is taken from the already
rewritten token list: `200 + 10% + 5%` = **231**. An earlier implementation read
the prefix from the original list and produced 230.005; a test caught it and it is
now fixed and pinned.

Verified reference values: `200×15% = 30`; 20% off 500 → 400; 80 → 100 is a 25%
increase; successive 10% then 20% discounts on 100 → 72 (they multiply, they do
not add to 30%).

---

## 7. Solver modules

### Integer math (`solvers/IntegerMath.kt`)
`BigInteger`-based: `gcd` (Euclid), `lcm` (via gcd, exact), primality
(deterministic Miller–Rabin over the supported range), trial factorisation
(bounded by 10¹⁸ and 20 M steps), modular exponentiation via `BigInteger`
multiply-mod (an earlier `let`-chain version was wrong and was rewritten).
Reference: `gcd(48,18) = 6`, `lcm(12,18) = 36`.

### Linear and simultaneous equations (`solvers/LinearEquations.kt`)
`linearCoefficients(expression, variable)` extracts coefficients by evaluating the
parsed tree at probe points **and re-checks linearity by re-evaluating**, so a
non-linear input (e.g. `x^2 = 4`) is rejected as a `Domain` error instead of being
silently fitted with wrong coefficients. `solveSystem` classifies outcomes as
`UNIQUE`, `NO_SOLUTION` or `INFINITELY_MANY` from rank(A) vs rank([A|b]), and a
unique solution is only returned after a residual check; a failing residual raises
`NonConvergence` rather than returning a number that does not satisfy the system.

### Quadratic (`solvers/Quadratic.kt`)
Uses the numerically stable form `q = -(b ± √D)/2`, with the second root as `c/q`,
so catastrophic cancellation is avoided. `a = 0` is never disguised as a unique
root — it is reported as a degenerate (linear) case. Discriminant sign determines
`TWO_DISTINCT_REAL`, `ONE_REPEATED_REAL`, `TWO_COMPLEX` or `NO_REAL`. Verified:
`x² − 5x + 6 = 0` → 2 and 3; the ill-conditioned `x² − 1e8x + 1` retains its
`1e-8` root.

### Sequences (`solvers/Sequences.kt`)
Arithmetic (`aₙ = a₁ + (n−1)d`, `Sₙ = n(a₁+aₙ)/2`) and geometric
(`aₙ = a₁·r^(n−1)`, sum with the `r = 1` special case).

### Numerical solvers (`solvers/NumericSolvers.kt`)
`bisection(f, a, b, tolerance, maxIterations)` and
`newtonRaphson(f, x0, tolerance, maxIterations)`. Both:
- bound iterations;
- **verify the residual** before declaring convergence — no convergence claim
  without `|f(x)| < tolerance`;
- return an explicit status: `CONVERGED`, `INVALID_BRACKET` (no sign change, or a
  non-finite endpoint), `NO_CONVERGENCE`, with a human-readable `note`.

A non-converged run is presented as a *status*, not as an error banner and never
as a guessed root.

### Fractions (`solvers/Fraction.kt`)
`BigInteger` numerator/denominator, always normalised (sign in the numerator,
divided by gcd). Parsing accepts `"3/4"`, `"-3/4"` and mixed form; `toDecimal`
uses DECIMAL128 division. Zero denominators are typed errors.

### Bases (`solvers/Bases.kt`)
Bases 2, 8, 10, 16. `parse` **rejects digits that do not belong to the source
base** rather than skipping them. Arithmetic is exact `BigInteger`; division
returns quotient and remainder.

### Dates and durations (`solvers/CivilDate.kt`)
A proleptic-Gregorian civil date implemented with exact integer arithmetic — an
integer day-number bijection, **not** `java.time` — so behaviour is identical on
every API level from 24 upward. Leap-year rules are the full Gregorian rules
(divisible by 4, except centuries, except multiples of 400). `Duration` has
unbounded hours (no day overflow), and month arithmetic clamps to the last valid
day of the target month (2024-01-31 + 1 month → 2024-02-29).

### Finance (`solvers/Finance.kt`)
Rates are **per year**, time is in **years**, and `n` is compounding periods per
year. `simpleInterest`, `compoundInterest` and `amortization` are exact
`BigDecimal`. A zero rate is handled explicitly (`i = 0` → principal/`N`, not a
division by zero and not a silently wrong schedule). No formula in the app
hardcodes any country's current VAT or tax rate: rates are always user-supplied
inputs, and the UI labels them as such.

### Percent math (`solvers/PercentMath.kt`)
`markupPercent` divides by **cost**; `marginPercent` divides by **selling price** —
they are not interchangeable, and the library entry says so.
`successiveDiscounts` compounds. `percentageChange`, `percentagePointChange`,
`originalBeforeIncrease`, `originalBeforeDecrease`, `simplifyRatio` and
`whatPercent` all reject zero denominators with typed errors.

### Complex numbers (`math/complex/Complex.kt`)
Rectangular `Complex(re, im)` and `Polar(r, thetaRadians)` with an exact
bijection between them. Argument is the **principal value in (−π, π]**. Division
by `0 + 0i` is a typed error, not `NaN` or `Infinity`. Magnitude uses `hypot`,
avoiding intermediate overflow.

Complex arithmetic is offered in Math Lab. The expression engine evaluates **real**
expressions; it does not parse `3+4i` literals, and the UI does not claim it does.

### Matrices (`math/matrices/Matrix.kt`)
LU decomposition with partial pivoting for determinants; Gauss–Jordan with partial
pivoting for inverses and system solving. Singularity tolerance `1e-10`. A
singular matrix raises `SingularMatrix` instead of returning a garbage inverse;
mismatched shapes raise `DimensionMismatch`. Cell count is capped at 4 096.
Parsing accepts `"1, 2; 3, 4"`. `toRows()` hands a matrix to the solver layer so
the Math Lab uses the authoritative system solver rather than a second one.

### Statistics (`math/statistics/Statistics.kt`)
Population statistics divide by `N`; sample statistics divide by `N − 1` and
require `N ≥ 2`, otherwise `InsufficientSample` is raised — the value is **not**
silently reported as the population figure. An empty dataset raises
`EmptyDataset`. Mode returns all modes (multimodal datasets are reported as such).
Median averages the two middle values for even `N`. Reference: `mean(2,4,6) = 4`.

---

## 8. Presentation of numbers (`core/formatting/NumberFormatter.kt`)

This is presentation only. It never feeds back into the engine, and there is no
parser for its output.

| Rule | Behaviour |
|------|-----------|
| Automatic decimal notation | Plain while the adjusted exponent is in `[−6, 12]`; outside that window, scientific with 12 significant digits |
| Scientific | mantissa in `[1, 10)` |
| Engineering | exponent a multiple of 3, mantissa in `[1, 1000)` |
| Trailing zeros | trimmed **only** when automatic precision is on |
| Rounding | user-selectable `HALF_UP` / `HALF_EVEN` / `HALF_DOWN` / `UP` / `DOWN`; the engine itself is `HALF_EVEN` |
| Grouping | locale-independent thousands grouping, user-toggleable |
| Significant figures | `significantFigures(value, n)` rounds to *n* significant digits |

Verified: grouping on/off; `3.14159` → `3.142` at 3 decimals; `1234567` →
`1.234567e+6`; engineering `1.234567e+6`; sig figs `3.14`; `2.345` → `2.35`
(HALF_UP) vs `2.34` (HALF_EVEN); a very small value switches to scientific.

A defect found by testing and fixed: scientific and engineering mantissas used to
render padded trailing zeros (`1.23456700000e+6`). `renderMantissa(mantissa,
trim)` now trims only under automatic precision, so fixed-precision output still
shows the zeros the user asked for.

---

## 9. Error model (`core/errors/CalcError.kt`)

Sealed hierarchy, each subtype with a stable `code` and a technical `detail`:

`Incomplete`, `Syntax(position)`, `UnknownFunction(name)`,
`UnbalancedParentheses(position)`, `DivisionByZero`, `Domain(operation)`,
`Overflow`, `Underflow`, `InvalidArgument`, `UnsupportedOperation`,
`NonConvergence`, `ResourceLimit(limit)`, `DimensionMismatch`, `SingularMatrix`,
`EmptyDataset`, `InsufficientSample(required, actual)`.

`CalcException` transports it inside the recursive evaluator and is caught at
every public boundary; it never escapes to the UI. `core/ui/ErrorMessages.kt`
maps **every** subtype to a user-facing string exhaustively — adding an error type
without a message is a compile error, and a mapping entry that was missing
(`UnsupportedOperation`) was found by a compiler sweep and fixed.

Display rules: no `NaN`, no stack trace, no raw exception text, no silent zero.
`Incomplete` is not shown as an error while typing; the live preview simply stays
quiet.

---

## 10. What is **not** implemented

Stated explicitly so no claim can be inferred:

- No symbolic integration or differentiation of arbitrary expressions.
- No unrestricted symbolic algebra and no computer algebra system.
- No ordinary or partial differential equation solving.
- No complex-number expression parsing (`3+4i` literals are not accepted by the
  engine; complex arithmetic is a dedicated Math Lab module).
- No arbitrary-precision transcendentals: those are computed in binary64 and
  re-rounded to DECIMAL128, which is documented above and visible in the
  `inspection` line of the Math Lab formatting module.
- No live currency conversion, exchange rates or any networked data. Rates are
  always user-supplied, and no formula asserts anything about any country's
  current tax law.

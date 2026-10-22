# Axiom Calc

**A premium, offline-first, all-in-one mathematics app for Android.**

Axiom Calc is a native Android application written in Kotlin with Jetpack
Compose. Every calculation runs on-device through a single authoritative
expression engine. The app declares **no Android permissions at all** — not even
`INTERNET` — so the process cannot open a network socket, and the offline claim
is enforced by the platform rather than by convention.

- **Owner:** Shohan Khan — `helloiamshohan@gmail.com`
- **Application ID:** `com.shohankhan.axiomcalc`
- **Version:** 1.0.0 (versionCode 1)

---

## What is in the app

Six primary destinations, all fully implemented:

| # | Destination | Icon | What it does |
|---|-------------|------|--------------|
| 1 | **Standard** | Calculator | Full keypad: AC/CE/backspace, sign, %, parentheses, memory (MS/MR/M+/M−/MC), undo/redo, paste, hardware-keyboard input, `Ans` reuse |
| 2 | **Scientific** | Sigma | Compact + expanded panels: trig (DEG/RAD, inverse, hyperbolic), powers/roots, logs, constants (π, e, τ, φ), combinatorics (nCr, nPr, gcd, lcm), factorial |
| 3 | **Formulas** | Percent | Formula Calculator with 13 categories A–M and 77 named forms |
| 4 | **Quick Solve** | Zap | 27 named solver tools with step-by-step working |
| 5 | **Formula Library** | Book | Searchable offline reference — 58 entries — with favourites and recents, deep-linked to the implementing calculator |
| 6 | **Math Lab** | Flask | Matrices, statistics, complex numbers, numerical root finding, number bases, engineering notation |

Secondary destinations: **History** (date grouping, search, time-window and
origin filters, multi-delete), **Saved calculations** (permanent, never touched
by retention), **Settings**, **About**, and **formula detail**.

Complex-number arithmetic lives in Math Lab, not inside the real-number
expression engine. That boundary is deliberate and documented in
[MATHEMATICS_SPEC.md](MATHEMATICS_SPEC.md) — the app does not claim complex
expression parsing it has not implemented.

---

## Verified toolchain

| Component | Version |
|-----------|---------|
| JDK | 17 (Temurin 17.0.9) |
| Gradle | 8.11.1 (wrapper pinned, jar integrity verified) |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| Compose BOM | 2024.10.01 |
| compileSdk / targetSdk | 35 |
| minSdk | 24 (Android 7.0) |

Every version above is a published release coordinate; none is a snapshot or an
invented string. See [RELEASE.md](RELEASE.md) for the full dependency table and
the signing/release procedure.

---

## Building

```bash
./gradlew assembleDebug          # debug APK
./gradlew test                   # local JVM unit tests
./gradlew connectedDebugAndroidTest   # instrumented tests (device/emulator)
./gradlew assembleRelease        # unsigned release APK
```

The Gradle wrapper is committed (`gradlew`, `gradlew.bat`,
`gradle/wrapper/gradle-wrapper.jar`). The first build downloads the Gradle
distribution and dependencies from the network; the **installed app** needs no
network for any function.

### Building without the Android SDK

`verification/run-verification.sh` compiles the Android-free core (the math
engine, error model, formatter and domain layer) together with the real unit
tests from `app/src/test` and executes them on the JVM. It exists for restricted
environments where the Android SDK and Maven repositories are unreachable. It is
**not** a substitute for `./gradlew test`; the same test classes run under real
JUnit 4 there. See [TESTING.md](TESTING.md) for exactly what it covers, what it
excludes, and why.

---

## Tests

183 local unit tests and 75 instrumented tests are committed.

```
bash verification/run-verification.sh
>> compiling 38 Kotlin sources (core=31 tests=5 shim=2)
TOTAL 183   PASSED 183   FAILED 0   ERRORS 0   SKIPPED 0
RESULT: PASS
```

The raw output is committed at
[`verification/reports/unit-tests-latest.txt`](verification/reports/unit-tests-latest.txt).

The instrumented suite has **not** been executed in this environment because no
Android SDK, emulator or device was available. That is recorded as NOT TESTED in
[FEATURE_ACCEPTANCE.md](FEATURE_ACCEPTANCE.md) rather than claimed as passing.

---

## Documentation

| Document | Contents |
|----------|----------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | As-built layering, module boundaries, data flow, dependency injection |
| [MATHEMATICS_SPEC.md](MATHEMATICS_SPEC.md) | Precedence, associativity, precision policy, error model, per-solver guarantees |
| [TESTING.md](TESTING.md) | Test strategy, how to run every suite, what is not covered and why |
| [RELEASE.md](RELEASE.md) | Build, signing, versioning, publishing procedure |
| [SECURITY_AND_PRIVACY.md](SECURITY_AND_PRIVACY.md) | Offline proof, permission audit, data inventory, threat notes |
| [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) | Every third-party dependency and its licence |
| [FEATURE_ACCEPTANCE.md](FEATURE_ACCEPTANCE.md) | Criterion-by-criterion acceptance matrix with honest statuses |

---

## Privacy

No accounts. No analytics. No advertising SDKs. No crash reporting. No network
permission. History and settings are stored in the app's private sandbox and are
never transmitted anywhere, because there is no transport. The only outbound
intent the app can start is a `mailto:` to the owner's address, initiated
explicitly by the user from the About screen.

Details and the audit procedure: [SECURITY_AND_PRIVACY.md](SECURITY_AND_PRIVACY.md).

---

## Known limitations

Stated plainly, because the app is not bug-free and does not claim to be:

- No computer algebra system. Integration, differentiation of arbitrary
  expressions, unrestricted symbolic manipulation and ODE solving are **not**
  implemented and are not offered in the UI.
- Numerical root finding (bisection, Newton–Raphson) is iterative: it reports
  non-convergence explicitly instead of returning a plausible-looking number.
- Matrices, complex numbers and statistics use IEEE-754 `Double`, while the
  expression engine and the financial/percentage forms use `BigDecimal` at
  DECIMAL128. Which is used where is documented in MATHEMATICS_SPEC.md.
- The instrumented test suite and the APK build have not been executed in the
  authoring sandbox (no Android SDK). See FEATURE_ACCEPTANCE.md.

---

## Licence

No licence file has been added by this work; the repository's licensing terms are
the owner's to declare.

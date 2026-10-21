# Axiom Calc — Security and Privacy

This document states what the app does with data, how the offline claim is
enforced rather than asserted, and how to audit both. Where a control could not
be verified in the authoring environment, that is said explicitly.

---

## 1. Data the app holds

| Data | Where | Leaves the device? |
|------|-------|--------------------|
| Calculation history (expression, canonical result, displayed result, origin, timestamp) | Room, `history` table, app-private sandbox | **No** |
| Saved calculations (user-chosen name plus the same fields) | Room, `saved_calculations` | **No** |
| Formula Library favourites | Room, `favorites` | **No** |
| Recently viewed library formulas (capped at 20) | Room, `recent_formulas` | **No** |
| Settings (theme, haptics, angle mode, precision, grouping, notation, retention) | DataStore preferences, app-private sandbox | **No** |
| In-session calculator state (buffer, memory register, `Ans`, undo stack) | Memory only | **No** — not persisted at all |

**Not collected, because nothing collects it:** no account, no email address
entered by the user, no device identifiers, no advertising ID, no location, no
contacts, no camera, no microphone, no files, no clipboard upload, no analytics
events, no crash reports, no usage telemetry, no A/B assignment.

The only address the app knows is the owner's, `helloiamshohan@gmail.com`, which
is a compile-time string resource shown on the About screen. It is never
transmitted; it is only placed in a `mailto:` URI when the user taps it.

---

## 2. The offline guarantee

### 2.1 No permission, therefore no socket

`app/src/main/AndroidManifest.xml` declares **zero** `uses-permission` elements.
`android.permission.INTERNET` is deliberately absent. Without it the Android
kernel denies socket creation for the whole process, so no code path in the app —
including any code path inside a library — can reach the network. This is enforced
by the platform, not by convention or by a code review.

### 2.2 No network code

There is no HTTP client, no WebSocket, no gRPC, no MQTT, no `URLConnection`, no
`OkHttp`, `Retrofit`, `Ktor` or `Volley` dependency, and no Firebase, Play
Services, analytics, advertising or crash-reporting SDK. The dependency list in
[RELEASE.md](RELEASE.md) §2 is the complete set.

### 2.3 No component that could phone home

`dependenciesInfo { includeInApk = false; includeInBundle = false }` disables the
Play-encrypted dependency metadata block. The manifest declares exactly one
activity, one application class, and one provider entry whose only purpose is to
**remove** `androidx.work.WorkManagerInitializer` from `androidx.startup` so no
background component runs before the retention preference is read.

### 2.4 The one outbound intent

The About screen can start `Intent.ACTION_SENDTO` with `data =
mailto:helloiamshohan@gmail.com`. That hands control to a mail app **the user
already has**; Axiom Calc sends nothing itself. The manifest's single `<queries>`
element declares `SENDTO`/`mailto` so package visibility rules on Android 11+ can
resolve an installed mail client. A `<queries>` element is not a permission and
grants no access to any data. If no mail client exists, the failure is caught and
reported in-app instead of crashing.

### 2.5 How to audit it yourself

```bash
# 1. Source manifest: must print nothing
grep -n "uses-permission" app/src/main/AndroidManifest.xml

# 2. Merged release manifest: must print nothing
grep -n "uses-permission" app/build/outputs/logs/manifest-merger-release-report.txt
aapt2 dump permissions app/build/outputs/apk/release/app-release.apk

# 3. Dependency graph: no networking/analytics coordinates
./gradlew app:dependencies --configuration releaseRuntimeClasspath

# 4. Runtime proof: the app cannot open a socket
adb shell dumpsys package com.shohankhan.axiomcalc | grep -i permission
```

On a device, `OfflineAndPrivacyTest` performs steps 1–3 against the **installed
package** and additionally asserts that an attempted socket connection fails
without the permission, and that core calculations produce correct results. That
suite is committed but was **NOT TESTED** in the authoring environment (no device,
no emulator, no Android SDK).

### 2.6 Airplane-mode verification procedure

The functional half of the offline claim, to be run on a real device:

1. Install the build.
2. Enable airplane mode; confirm Wi-Fi and mobile data are both off.
3. Launch the app. It must start normally — no splash error, no retry dialog, no
   spinner waiting on a network.
4. On each of the six primary destinations, perform a real calculation:
   `2+2=` on Standard, `sin(30)` on Scientific (DEG), a percentage form on
   Formulas, a quadratic solve on Quick Solve, a library search, and a matrix
   determinant in Math Lab.
5. Open History and confirm the new rows appear; open Settings and change the
   theme and the retention policy; open About.
6. Reboot the device with airplane mode still on, relaunch, and confirm history,
   saved calculations, favourites and settings all persisted.

Any step that fails, hangs or shows an error is a defect against the offline
requirement. **This procedure has not been executed** — no device was available.

---

## 3. Storage security

- Room and DataStore files live in the app's private internal directory
  (`/data/data/com.shohankhan.axiomcalc/`), which other apps cannot read on a
  non-rooted device.
- No external storage, no shared storage, no cache directory, no exported
  `FileProvider` and no exported component of any kind. The only exported
  component is the launcher activity, which is required for the app to start.
- No secrets exist in the app to protect: there are no API keys, tokens,
  passwords or credentials in the source, resources, build files or Git history.
  Nothing sensitive is committed, and `.gitignore` excludes keystores,
  `*.jks`, `*.keystore`, signing properties and local Gradle properties.
- Data is not encrypted at rest. The stored values are the user's own arithmetic,
  there is no credential or personal-identity data, and encryption at rest would
  add key-management risk without protecting anything an attacker could not
  already read on a rooted device. This is a deliberate, documented decision
  rather than an oversight.

### Backup
`allowBackup="true"` with explicit `@xml/backup_rules` and
`@xml/data_extraction_rules`. History and settings are included so a device
migration keeps them; no exclusion is needed because nothing sensitive is stored.
Backup transport is Android's own and is outside this app's control.

---

## 4. Data retention

The user controls retention in Settings: **1 / 7 / 30 / 90 days / Never**, default
**30 days**.

Enforcement has three independent paths, because a background job cannot be
relied upon:

1. **Read-time filtering** — `RoomHistoryRepository` filters by the policy cutoff
   on every read, so an expired entry cannot be displayed even if no cleanup has
   ever run.
2. **Startup pass** — `AppContainer.enforceRetentionAtStartup()` runs when the
   process starts.
3. **Best-effort WorkManager** — a daily `RetentionWorker` with a
   battery-not-low constraint. If WorkManager never runs (fresh install,
   restricted background execution), paths 1 and 2 still hold.

Retention **never** touches saved calculations, favourites or recents; those are
separate tables and the separation is asserted by `HistoryDatabaseTest`.
Cutoff arithmetic is tested against a fixed injectable clock in
`PersistenceSemanticsTest`, including the exact boundary (an entry at the cutoff
is kept; one millisecond older is expired).

Settings exposes an "Apply now" action that runs cleanup immediately and reports
how many rows were removed. A failure is surfaced to the user rather than
swallowed.

**Deletion is real deletion**: `DELETE FROM history WHERE timestamp < :cutoff`.
Rows are not soft-deleted or archived elsewhere.

---

## 5. Input handling and robustness

The app parses text the user typed or pasted, so hostile input is a real threat
model:

- **No script evaluation.** Input goes through a lexer and a precedence-climbing
  parser into a typed AST. There is no `eval`, no JavaScript engine, no WebView,
  no reflection over user strings and no dynamic code loading.
- **Resource limits** bound the work any input can demand: expression depth 256,
  nodes 4 000, literal digits 60, integer digits 100 000, exponent 100 000,
  factorial 10 000, matrix cells 4 096, factorisation ≤ 10¹⁸ and ≤ 20 M steps,
  buffer 400 tokens. Exceeding one yields a typed error, not a hang, an OOM or a
  crash.
- **No uncaught exception reaches the UI.** `CalcException` is caught at every
  public engine boundary and converted to `CalcResult.Failure`; the ViewModel maps
  that to a message from an exhaustive `CalcError → string` table.
- **No silent wrong answers.** A failure never substitutes `0` or a plausible
  number, and solvers verify residuals before claiming convergence.
- **Clipboard paste is sanitized** to characters the grammar accepts
  (`0-9 + - * / ^ % . ( ) !`); anything else is dropped and the user is told the
  clipboard had no usable numbers.
- **Formatted display text is never parsed back** into the engine. Persisted rows
  store a separate canonical value, and `PersistenceSemanticsTest` asserts that a
  grouped display string is rejected by `CalcNumber.fromCanonical`.

---

## 6. Permissions summary

| Permission | Requested | Why |
|------------|-----------|-----|
| `INTERNET` | **No** | Not needed; its absence is the offline guarantee |
| `ACCESS_NETWORK_STATE` | **No** | Nothing inspects connectivity |
| `VIBRATE` | **No** | Key haptics use `View.performHapticFeedback` via Compose `LocalHapticFeedback`, which needs no permission |
| `WRITE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE` | **No** | All data is internal |
| anything else | **No** | |

Total requested permissions: **0**.

---

## 7. Supply chain

- Every dependency is a published release coordinate from Google's or Maven's
  repository, pinned in `gradle/libs.versions.toml`. No snapshots, no local paths,
  no dynamic versions (`+`), no JitPack or personal forks.
- The Gradle wrapper jar was obtained from the official `gradle/gradle` repository
  at tag `v8.11.1` and its blob SHA-1 was matched against the GitHub contents API
  (`a4b76b9530d66f5e68d973ea569d8e19de379189`, 43,583 bytes).
- The launcher icons and all 27 vector drawables are hand-authored in this
  repository; there are no third-party binary assets.
- No CI secret, keystore, token or credential is stored in the repository.

---

## 8. Residual risk

Reported honestly, not minimised:

| Risk | Severity | Status |
|------|----------|--------|
| The merged release manifest has not been audited on a real build; a transitive dependency could add a permission at merge time | High if it happened | **UNVERIFIED** — the source manifest declares none, `OfflineAndPrivacyTest` checks the installed package, and the audit commands in §2.5 must be run on a real build before release |
| The instrumented suite has never been executed | Medium | **NOT TESTED** — no device, emulator or Android SDK |
| No APK has been built, so installability, launch behaviour and R8/minification effects are unknown | Medium | **BLOCKED** — Gradle distribution unobtainable |
| Compose sources were checked structurally (zero syntax errors) but not compiled against the SDK | Medium | **UNVERIFIED** beyond the structural sweep |
| No coverage measurement | Low | **NOT MEASURED**; no percentage is claimed |
| Data is not encrypted at rest | Low | Accepted by design (§3) |
| WorkManager cleanup may never run on some devices | Low | Mitigated by two independent enforcement paths (§4) |

No known unresolved critical or high-severity **code** defect is open. The
high-severity item above is a verification gap, not a defect: it can only be
closed by building on a machine with the Android SDK.

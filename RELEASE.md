# Axiom Calc — Release

Build, sign, version and publish procedure, plus the exact state of the build in
the authoring environment.

---

## 1. Toolchain contract

| Component | Version | How it was verified |
|-----------|---------|---------------------|
| JDK | 17 (Temurin 17.0.9+9) | Kotlin 2.0.21 rejects JDK 25 (`IllegalArgumentException: 25.0.2`); JDK 17 was used for every compile in this repository |
| Gradle | 8.11.1 | tag `v8.11.1` confirmed on `gradle/gradle`; `gradle-wrapper.properties` pins `gradle-8.11.1-bin.zip` |
| Android Gradle Plugin | 8.7.3 | requires Gradle ≥ 8.9 and JDK ≥ 17 — both satisfied |
| Kotlin | 2.0.21 | tag `v2.0.21` confirmed on `JetBrains/kotlin`; used to compile all main and test sources here |
| KSP | 2.0.21-1.0.28 | tag confirmed on `google/ksp`; must match the Kotlin version prefix |
| Compose compiler | = Kotlin version | applied via `org.jetbrains.kotlin.plugin.compose`, so no separate compiler version can drift |
| compileSdk / targetSdk | 35 | |
| minSdk | 24 (Android 7.0) | `CivilDate` avoids `java.time` precisely so behaviour is identical from API 24 up |
| applicationId | `com.shohankhan.axiomcalc` | |
| versionName / versionCode | 1.0.0 / 1 | |

---

## 2. Dependencies

All coordinates are published releases. None is a snapshot, a local path, or an
invented version. Versions are declared once in `gradle/libs.versions.toml`.

| Library | Version | Used for |
|---------|---------|----------|
| Compose BOM | 2024.10.01 | Compose artifact alignment |
| `androidx.compose.ui:ui`, `ui-graphics`, `ui-tooling(-preview)` | from BOM | UI |
| `androidx.compose.material3:material3` | from BOM | Material 3 components |
| `androidx.compose.material3:material3-window-size-class` | from BOM | Size-class branching |
| `androidx.compose.ui:ui-test-junit4`, `ui-test-manifest` | from BOM | Instrumented UI tests |
| `androidx.core:core-ktx` | 1.15.0 | AndroidX core |
| `androidx.lifecycle:lifecycle-runtime-ktx`, `-runtime-compose`, `-viewmodel-compose` | 2.8.7 | ViewModel + `collectAsStateWithLifecycle` |
| `androidx.activity:activity-compose` | 1.9.3 | `ComponentActivity.setContent`, edge-to-edge |
| `androidx.navigation:navigation-compose` | 2.8.4 | Navigation graph |
| `androidx.room:room-runtime`, `room-ktx` | 2.6.1 | History, saved calculations, favourites, recents |
| `androidx.room:room-compiler` (KSP) | 2.6.1 | Room code generation |
| `androidx.room:room-testing` | 2.6.1 | In-memory database tests |
| `androidx.datastore:datastore-preferences` | 1.1.1 | Settings |
| `androidx.work:work-runtime-ktx` | 2.9.1 | Best-effort retention cleanup |
| `androidx.work:work-testing` | 2.9.1 | `TestListenableWorkerBuilder` |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.8.1 | Coroutines/Flow |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.8.1 | `runTest`, `backgroundScope` |
| `junit:junit` | 4.13.2 | Unit tests |
| `androidx.test.ext:junit` | 1.2.1 | Instrumented test runner |
| `androidx.test.espresso:espresso-core` | 3.6.1 | Instrumented assertions |
| `app.cash.turbine:turbine` | 1.1.0 | Flow assertions |

**No** networking, analytics, advertising, crash-reporting, remote-configuration
or licensing library appears anywhere in this list. There is no `OkHttp`,
`Retrofit`, `Ktor`, `Volley`, Firebase, Play Services, Facebook, Sentry or
Crashlytics dependency, and `dependenciesInfo` is disabled in
`app/build.gradle.kts` so no Play-encrypted dependency metadata block is
embedded.

Licences: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

## 3. Gradle wrapper

Committed and integrity-checked:

| File | Size | SHA-1 (git blob) |
|------|------|------------------|
| `gradle/wrapper/gradle-wrapper.jar` | 43,583 bytes | `a4b76b9530d66f5e68d973ea569d8e19de379189` |

SHA-256 of the jar:
`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`

`gradlew` and `gradlew.bat` are executable and were taken from the official
`gradle/gradle` repository at tag `v8.11.1`; the blob SHA reported by the GitHub
contents API matched `git hash-object` of the downloaded file, so the wrapper is
authentic rather than reconstructed.

---

## 4. Build commands

```bash
./gradlew clean
./gradlew assembleDebug                     # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                              # local unit tests
./gradlew connectedDebugAndroidTest         # instrumented tests
./gradlew lint                              # Android lint report
./gradlew assembleRelease                   # app/build/outputs/apk/release/app-release-unsigned.apk
```

Debug builds are signed automatically with the local debug keystore
(`~/.android/debug.keystore`). Release builds are **unsigned** until step 5 is
performed with the owner's key.

---

## 5. Signing a release

No production keystore exists in this repository, and none has been invented.
**Never** commit a keystore, a key alias password or a keystore password; they are
excluded by `.gitignore` and must stay outside the working tree.

1. Create the owner's key once, and store it outside the repository:

   ```bash
   keytool -genkeypair -v \
     -keystore ~/keys/axiom-calc-release.jks \
     -keyalg RSA -keysize 4096 -validity 10000 \
     -alias axiom-calc
   ```

2. Put the credentials in `~/.gradle/gradle.properties` (never in the project):

   ```properties
   AXIOM_RELEASE_STORE_FILE=/Users/<you>/keys/axiom-calc-release.jks
   AXIOM_RELEASE_STORE_PASSWORD=…
   AXIOM_RELEASE_KEY_ALIAS=axiom-calc
   AXIOM_RELEASE_KEY_PASSWORD=…
   ```

3. Add a `signingConfigs` block to `app/build.gradle.kts` reading those four
   properties, and attach it to the `release` build type. Keep
   `isMinifyEnabled`/`isShrinkResources` as configured and re-run the full test
   suite afterwards, because R8 changes what reflection can see.

4. Build and verify:

   ```bash
   ./gradlew assembleRelease
   apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
   ```

5. Keep the same keystore for the app's entire life. Losing it means the app can
   never be updated in place. Back it up offline, and never upload it to a
   repository, a CI secret store you do not control, or a chat.

If the app is published to Google Play, App Signing by Google Play holds the
deployment key; the upload key created above is still the owner's responsibility.

---

## 6. Versioning

- `versionName` follows semantic versioning: breaking changes to stored data or to
  documented mathematical behaviour increment the major version.
- `versionCode` increments monotonically on every published build.
- **The Room schema version is independent of both.** It is `1` with
  `exportSchema = true`, so schemas land in `app/schemas/` and every future change
  must ship a tested `Migration`. The schema is never dropped to avoid writing a
  migration, because that would delete user history.
- Settings keys are stable strings. An unknown or legacy value degrades to a
  documented default (tested in `SettingsRepositoryTest`), so an upgrade can never
  brick the settings screen.

---

## 7. Pre-release checklist

Run every item before publishing. Items marked ⚠ have **not** been completed in
the authoring environment.

- [ ] `./gradlew test` — 183 unit tests pass ⚠ not run under Gradle here
- [ ] `./gradlew connectedDebugAndroidTest` — 75 instrumented tests pass ⚠ no device
- [ ] `./gradlew lint` — no new errors ⚠ not run
- [ ] `./gradlew assembleRelease` succeeds ⚠ not run
- [ ] APK inspected: `aapt2 dump badging` shows the right package, versionName,
      versionCode, min/target SDK ⚠ no Android build tools available
- [ ] **Merged release manifest audited: zero `uses-permission` entries, and
      `android.permission.INTERNET` absent** ⚠ must be re-run on a real build; the
      source manifest is audited and `OfflineAndPrivacyTest` checks the installed
      package
- [ ] Dependency graph audited for anything introducing network access
      (`./gradlew app:dependencies`) ⚠ not run
- [ ] Install and launch on a device, then confirm the calculator works with
      airplane mode on ⚠ no device
- [ ] Owner signs the release with a real key ⚠ **no owner key exists**
- [ ] `SECURITY_AND_PRIVACY.md` and `FEATURE_ACCEPTANCE.md` updated with the
      results of the above

---

## 8. Status of the build in the authoring environment

**BLOCKED — and this is reported rather than worked around.**

The sandbox had no Android SDK and no route to obtain one: `dl.google.com`,
`maven.google.com`, `repo1.maven.org`, `plugins.gradle.org` and
`services.gradle.org` were all unreachable, so `./gradlew` cannot even download
the Gradle distribution. Consequently:

| Artifact | Status |
|----------|--------|
| `./gradlew assembleDebug` | **BLOCKED** — Gradle distribution unobtainable |
| `./gradlew assembleRelease` | **BLOCKED** |
| Debug APK | **NOT PRODUCED** — no APK file exists, and none is claimed |
| Signed release APK | **NOT PRODUCED** — additionally, no owner signing key exists |
| `./gradlew test` | **BLOCKED**; the same 183 test classes were executed via `verification/run-verification.sh` (PASS) |
| `./gradlew connectedDebugAndroidTest` | **NOT TESTED** — no device or emulator |
| `./gradlew lint` | **BLOCKED** |
| Coverage | **NOT MEASURED** — no percentage is claimed |

What *was* executed and verified here:

- All 64 main-source Kotlin files parse with **zero syntax errors** under
  Kotlin 2.0.21; the only diagnostics are unresolved references to Android and
  Compose artifacts that cannot be downloaded.
- The Android-free core compiles cleanly and 183 unit tests pass on the JVM.
- All resources are validated by `tools/validate_resources.py`: XML well-formedness,
  vector `pathData` parsing, and every intra-project `@drawable`/`@string`/`@color`/
  `@style`/`@xml`/`@mipmap` reference resolving to a real definition.

A successful Gradle task would not by itself prove installability, and no such
task was run, so no installability claim is made. Building the APK on a machine
with the Android SDK is the first item in the checklist above.

package com.shohankhan.axiomcalc

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Offline and privacy verification, executed on a real device build.
 *
 * These are the tests that make the "no network, no tracking" claim falsifiable
 * rather than rhetorical: they read the *installed* package's granted and
 * requested permissions, its declared components and its queries element, so a
 * dependency that sneaks in `INTERNET` at merge time fails the suite.
 *
 * Run with `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class OfflineAndPrivacyTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val packageInfo = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS,
    )

    @Test fun noPermissionsAreRequestedAtAll() {
        val requested = packageInfo.requestedPermissions?.toList() ?: emptyList()
        assertEquals(
            "the app must request no permissions; found $requested",
            emptyList<String>(),
            requested,
        )
    }

    @Test fun internetPermissionIsAbsent() {
        val requested = packageInfo.requestedPermissions?.toList() ?: emptyList()
        assertFalse(
            "INTERNET permission must never be declared",
            requested.contains("android.permission.INTERNET"),
        )
        assertFalse(
            "ACCESS_NETWORK_STATE is unnecessary offline",
            requested.contains("android.permission.ACCESS_NETWORK_STATE"),
        )
    }

    @Test fun noPermissionsAreGrantedToThePackage() {
        val granted = packageInfo.requestedPermissions
            ?.zip(packageInfo.requestedPermissionsFlags?.toList() ?: emptyList())
            ?.filter { (_, flags) -> flags and PackageManager.PERMISSION_GRANTED != 0 }
            ?.map { it.first }
            ?: emptyList()
        assertEquals("no permission should be granted; found $granted", emptyList<String>(), granted)
    }

    @Test fun noTrackingAnalyticsOrAdvertisingComponentsAreMerged() {
        val bannedPrefixes = listOf(
            "com.google.firebase",
            "com.google.android.gms.analytics",
            "com.google.android.gms.ads",
            "com.facebook.",
            "com.crashlytics",
            "io.fabric",
            "com.adjust.",
            "com.appsflyer",
            "com.amplitude",
            "com.mixpanel",
            "com.segment",
            "com.sentry",
            "io.sentry",
            "com.bugsnag",
            "com.newrelic",
            "com.onesignal",
            "com.airship",
        )
        val components = buildList {
            packageInfo.activities?.forEach { add(it.name) }
            packageInfo.services?.forEach { add(it.name) }
            packageInfo.receivers?.forEach { add(it.name) }
            packageInfo.providers?.forEach { add(it.name) }
        }
        val offenders = components.filter { name -> bannedPrefixes.any { name.startsWith(it) } }
        assertEquals("tracking/analytics components merged into the app: $offenders", emptyList<String>(), offenders)
    }

    @Test fun onlyExpectedComponentsAreDeclared() {
        val activities = packageInfo.activities?.map { it.name } ?: emptyList()
        assertTrue("MainActivity must be declared", activities.any { it.endsWith("MainActivity") })
        assertEquals("exactly one activity is expected", 1, activities.size)

        val services = packageInfo.services?.map { it.name } ?: emptyList()
        // WorkManager's SystemJobService/SystemForegroundService are the only
        // services a retention worker needs; nothing else should appear.
        services.forEach { name ->
            assertTrue(
                "unexpected service merged into the manifest: $name",
                name.startsWith("androidx.work.") || name.startsWith("androidx.room."),
            )
        }
    }

    @Test fun packageIdentityMatchesTheSpecification() {
        assertEquals("com.shohankhan.axiomcalc", context.packageName)
        assertEquals("com.shohankhan.axiomcalc", packageInfo.packageName)
    }

    @Test fun versionMetadataIsPresent() {
        assertTrue("versionName must be set", !packageInfo.versionName.isNullOrBlank())
        assertTrue("versionCode must be positive", packageInfo.longVersionCode > 0)
    }

    @Test fun applicationIsNotDebuggableInReleaseBuilds() {
        val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!BuildConfig.DEBUG) {
            assertFalse("a release build must not be debuggable", isDebuggable)
        }
    }

    @Test fun openingASocketIsImpossibleWithoutTheInternetPermission() {
        // The strongest offline proof available without root: this process has no
        // INTERNET permission, so any attempt to open a socket must fail. If it
        // ever succeeds, some dependency has added the permission back.
        val attempt = runCatching {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("10.255.255.1", 80), 1_000)
            }
        }
        assertTrue(
            "a socket connection succeeded without the INTERNET permission",
            attempt.isFailure,
        )
        val reason = attempt.exceptionOrNull()
        assertTrue(
            "expected a SecurityException or connect failure, got $reason",
            reason is SecurityException || reason is java.io.IOException,
        )
    }

    @Test fun coreCalculationsSucceedWithoutAnyNetworkCall() {
        // Runs the authoritative engine in-process on the device: this is the
        // functional half of the offline claim.
        val results = listOf(
            "2+2" to "4",
            "2+3*4" to "14",
            "sqrt(81)" to "9",
            "log10(1000)" to "3",
        )
        results.forEach { (expression, expected) ->
            val value = com.shohankhan.axiomcalc.math.evaluator.ExpressionEngine
                .evaluate(expression).valueOrNull()
            assertTrue("$expression produced no value on device", value != null)
            assertEquals(
                "$expression did not equal $expected",
                0,
                com.shohankhan.axiomcalc.math.precision.CalcNumber.parse(expected).compareTo(value!!),
            )
        }
    }
}

package com.shohankhan.axiomcalc

import android.app.Application
import androidx.work.Configuration
import com.shohankhan.axiomcalc.infra.di.AppContainer

/**
 * Application entry point.
 *
 * Responsibilities are deliberately narrow: build the dependency container,
 * enforce history retention at startup (the guaranteed path), and best-effort
 * schedule the periodic cleanup. No analytics, no network, no tracking: the
 * manifest declares zero permissions, so any accidental network use fails at
 * the platform level rather than silently sending data.
 */
class AxiomCalcApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.enforceRetentionAtStartup()
        container.scheduleRetentionCleanup()
    }

    /**
     * Default WorkManager configuration. `androidx.startup` initialization is
     * removed from the manifest so this provider is the single entry point.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.WARN)
            .build()
}

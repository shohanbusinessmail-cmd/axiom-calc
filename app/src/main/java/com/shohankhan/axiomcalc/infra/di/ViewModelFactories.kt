package com.shohankhan.axiomcalc.infra.di

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shohankhan.axiomcalc.AxiomCalcApplication

/** Resolves the manual dependency container from any context. */
val Context.axiomContainer: AppContainer
    get() = (applicationContext as AxiomCalcApplication).container

/**
 * Creates a ViewModel wired from the manual container.
 *
 * Screens never construct repositories, databases or DataStore instances
 * themselves, which is what keeps the layer boundaries described in
 * ARCHITECTURE.md real in code. The application context is captured (never an
 * Activity) so no ViewModel can leak a window.
 */
@Composable
inline fun <reified VM : ViewModel> axiomViewModel(
    noinline create: (AppContainer, Context) -> VM,
): VM {
    val container = LocalContext.current.axiomContainer
    val appContext = LocalContext.current.applicationContext
    return viewModel(
        factory = viewModelFactory {
            initializer { create(container, appContext) }
        },
    )
}

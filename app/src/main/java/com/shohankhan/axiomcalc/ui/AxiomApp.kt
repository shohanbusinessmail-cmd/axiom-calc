package com.shohankhan.axiomcalc.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.shohankhan.axiomcalc.R
import com.shohankhan.axiomcalc.core.design.AxiomTheme
import com.shohankhan.axiomcalc.domain.model.AppSettings
import com.shohankhan.axiomcalc.domain.model.ThemeMode
import com.shohankhan.axiomcalc.feature.about.AboutScreen
import com.shohankhan.axiomcalc.feature.calculator.CalculatorViewModel
import com.shohankhan.axiomcalc.feature.calculator.ScientificCalculatorScreen
import com.shohankhan.axiomcalc.feature.calculator.StandardCalculatorScreen
import com.shohankhan.axiomcalc.feature.calculator.rememberScientificViewModel
import com.shohankhan.axiomcalc.feature.calculator.rememberStandardViewModel
import com.shohankhan.axiomcalc.feature.formulas.FormulasScreen
import com.shohankhan.axiomcalc.feature.history.HistoryScreen
import com.shohankhan.axiomcalc.feature.library.LibraryScreen
import com.shohankhan.axiomcalc.feature.library.rememberLibraryViewModel
import com.shohankhan.axiomcalc.feature.library.LibraryDetailScreen
import com.shohankhan.axiomcalc.feature.mathlab.LabModuleScreen
import com.shohankhan.axiomcalc.feature.mathlab.MathLabScreen
import com.shohankhan.axiomcalc.feature.quicksolve.QuickSolveScreen
import com.shohankhan.axiomcalc.feature.saved.SavedScreen
import com.shohankhan.axiomcalc.feature.settings.SettingsScreen
import com.shohankhan.axiomcalc.feature.tools.ToolRunnerScreen
import com.shohankhan.axiomcalc.infra.di.axiomViewModel
import com.shohankhan.axiomcalc.navigation.Destination
import com.shohankhan.axiomcalc.navigation.primaryDestinations

/**
 * Root composable: theme, navigation host, top bar and the six-item bottom bar.
 *
 * The bottom bar shows exactly the six primary destinations; History, Saved,
 * Settings and About are reached from the top bar's overflow menu, so no
 * destination is hidden behind an undiscoverable gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AxiomApp(
    navController: NavHostController = rememberNavController(),
    settingsState: AppSettings = rememberSettings(),
) {
    AxiomTheme(themeMode = settingsState.theme) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val snackbarHostState = remember { SnackbarHostState() }
        var menuOpen by remember { mutableStateOf(false) }
        val isPrimary = primaryDestinations.any { it.route == currentRoute }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(titleFor(currentRoute)) },
                    navigationIcon = {
                        if (!isPrimary) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_back),
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate(Destination.History.route) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_history),
                                contentDescription = stringResource(R.string.title_history),
                            )
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.a11y_more_options),
                                modifier = Modifier.semantics {
                                    contentDescription = "More options"
                                },
                            )
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.title_saved)) },
                                    leadingIcon = {
                                        Icon(painterResource(R.drawable.ic_bookmark), contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        navController.navigate(Destination.Saved.route)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.title_settings)) },
                                    leadingIcon = {
                                        Icon(painterResource(R.drawable.ic_settings), contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        navController.navigate(Destination.Settings.route)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.title_about)) },
                                    leadingIcon = {
                                        Icon(painterResource(R.drawable.ic_info), contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        navController.navigate(Destination.About.route)
                                    },
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (isPrimary) {
                    NavigationBar {
                        primaryDestinations.forEach { destination ->
                            val selected = backStackEntry?.destination?.hierarchy
                                ?.any { it.route == destination.route } == true
                            // labelFor() reads a string resource, so it must run
                            // in composition, not inside the semantics{} lambda.
                            val label = labelFor(destination.route)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigateToPrimary(destination.route) },
                                icon = {
                                    Icon(
                                        painter = painterResource(iconFor(destination.route)),
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(label) },
                                alwaysShowLabel = false,
                                // NavigationBarItem is selectable, so the
                                // framework already exposes the selected state to
                                // accessibility services. Spelling it into the
                                // label as well duplicated it for TalkBack and
                                // made the description differ between the selected
                                // and unselected tabs for no reason.
                                modifier = Modifier.semantics {
                                    contentDescription = "Tab $label"
                                },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            AxiomNavHost(navController, innerPadding, snackbarHostState)
        }
    }
}

/** Navigates to a primary tab, keeping a single instance of each on the stack. */
private fun NavHostController.navigateToPrimary(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AxiomNavHost(
    navController: NavHostController,
    innerPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
) {
    NavHost(
        navController = navController,
        startDestination = Destination.Standard.route,
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        composable(Destination.Standard.route) {
            StandardCalculatorScreen(snackbarHostState = snackbarHostState)
        }
        composable(Destination.Scientific.route) {
            ScientificCalculatorScreen(
                snackbarHostState = snackbarHostState,
                onOpenComplexLab = {
                    navController.navigate(
                        Destination.LabModule.create(com.shohankhan.axiomcalc.navigation.LabModuleId.COMPLEX),
                    )
                },
            )
        }
        composable(Destination.Formulas.route) {
            FormulasScreen(onOpenForm = { id -> navController.navigate(Destination.FormRunner.create(id)) })
        }
        composable(Destination.QuickSolve.route) {
            QuickSolveScreen(onOpenTool = { id -> navController.navigate(Destination.SolverTool.create(id)) })
        }
        composable(Destination.Library.route) {
            LibraryScreen(onOpenDetail = { id -> navController.navigate(Destination.FormulaDetail.create(id)) })
        }
        composable(Destination.MathLab.route) {
            MathLabScreen(onOpenModule = { id -> navController.navigate(Destination.LabModule.create(id)) })
        }

        composable(
            Destination.FormRunner.route,
            arguments = Destination.FormRunner.arguments,
        ) { entry ->
            val formId = entry.arguments?.getString(Destination.FormRunner.ARG_FORM_ID).orEmpty()
            ToolRunnerScreen(toolId = formId)
        }
        composable(
            Destination.SolverTool.route,
            arguments = Destination.SolverTool.arguments,
        ) { entry ->
            val toolId = entry.arguments?.getString(Destination.SolverTool.ARG_TOOL_ID).orEmpty()
            ToolRunnerScreen(toolId = toolId)
        }
        composable(
            Destination.LabModule.route,
            arguments = Destination.LabModule.arguments,
        ) { entry ->
            val moduleId = entry.arguments?.getString(Destination.LabModule.ARG_MODULE_ID).orEmpty()
            LabModuleScreen(moduleId = moduleId)
        }
        composable(
            Destination.FormulaDetail.route,
            arguments = Destination.FormulaDetail.arguments,
        ) { entry ->
            val formulaId = entry.arguments?.getString(Destination.FormulaDetail.ARG_FORMULA_ID).orEmpty()
            LibraryDetailScreen(
                formulaId = formulaId,
                onOpenCalculator = { targetId ->
                    val spec = com.shohankhan.axiomcalc.feature.tools.ToolSpec.resolve(targetId)
                    val route = when (spec) {
                        is com.shohankhan.axiomcalc.feature.tools.ToolSpec.Form ->
                            Destination.FormRunner.create(targetId)

                        is com.shohankhan.axiomcalc.feature.tools.ToolSpec.Tool ->
                            Destination.SolverTool.create(targetId)

                        null -> null
                    }
                    if (route != null) navController.navigate(route)
                },
            )
        }

        composable(Destination.History.route) {
            HistoryScreen(
                onReuse = { entry ->
                    navController.popBackStack(Destination.Standard.route, inclusive = false)
                },
            )
        }
        composable(Destination.Saved.route) {
            SavedScreen(
                onReuse = { item ->
                    navController.popBackStack(Destination.Standard.route, inclusive = false)
                },
            )
        }
        composable(Destination.Settings.route) { SettingsScreen() }
        composable(Destination.About.route) { AboutScreen(snackbarHostState = snackbarHostState) }
    }
}

/** Top bar title for the current route. */
@Composable
private fun titleFor(route: String?): String = when (route) {
    Destination.Standard.route -> stringResource(R.string.nav_standard)
    Destination.Scientific.route -> stringResource(R.string.nav_scientific)
    Destination.Formulas.route -> stringResource(R.string.nav_formulas)
    Destination.QuickSolve.route -> stringResource(R.string.nav_quick_solve)
    Destination.Library.route -> stringResource(R.string.nav_library)
    Destination.MathLab.route -> stringResource(R.string.nav_math_lab)
    Destination.History.route -> stringResource(R.string.title_history)
    Destination.Saved.route -> stringResource(R.string.title_saved)
    Destination.Settings.route -> stringResource(R.string.title_settings)
    Destination.About.route -> stringResource(R.string.title_about)
    Destination.FormRunner.route -> stringResource(R.string.nav_formulas)
    Destination.SolverTool.route -> stringResource(R.string.nav_quick_solve)
    Destination.LabModule.route -> stringResource(R.string.nav_math_lab)
    Destination.FormulaDetail.route -> stringResource(R.string.title_formula_detail)
    else -> stringResource(R.string.app_name)
}

@Composable
private fun labelFor(route: String): String = stringResource(
    when (route) {
        Destination.Standard.route -> R.string.nav_standard
        Destination.Scientific.route -> R.string.nav_scientific
        Destination.Formulas.route -> R.string.nav_formulas
        Destination.QuickSolve.route -> R.string.nav_quick_solve
        Destination.Library.route -> R.string.nav_library
        else -> R.string.nav_math_lab
    },
)

private fun iconFor(route: String): Int = when (route) {
    Destination.Standard.route -> R.drawable.ic_nav_standard
    Destination.Scientific.route -> R.drawable.ic_nav_scientific
    Destination.Formulas.route -> R.drawable.ic_nav_formulas
    Destination.QuickSolve.route -> R.drawable.ic_nav_quick_solve
    Destination.Library.route -> R.drawable.ic_nav_library
    else -> R.drawable.ic_nav_math_lab
}

/** Settings Flow lifted into composition once, at the root. */
@Composable
private fun rememberSettings(): AppSettings {
    val viewModel: SettingsObserver = axiomViewModel { container, _ -> SettingsObserver(container.settings) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    return settings
}

/** Tiny observer so the root theme reacts to setting changes. */
class SettingsObserver(
    settingsRepository: com.shohankhan.axiomcalc.domain.repository.SettingsRepository,
) : androidx.lifecycle.ViewModel() {
    // A StateFlow rather than the cold repository Flow: `Flow<T>` has no
    // no-argument collectAsStateWithLifecycle overload, and an eagerly shared
    // StateFlow also avoids a one-frame flash of default settings at the root,
    // which would briefly render the wrong theme.
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings(),
    )
}

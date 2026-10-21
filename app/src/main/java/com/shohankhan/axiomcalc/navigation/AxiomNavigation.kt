package com.shohankhan.axiomcalc.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument

/**
 * Navigation model.
 *
 * Exactly six primary destinations live in the bottom bar; everything else is a
 * secondary destination reached from it. Routes carry their arguments as typed
 * nav args — never by parsing display text — so a deep link or back-navigation
 * always rehydrates the same state.
 */
sealed class Destination(val route: String) {

    data object Standard : Destination("standard")
    data object Scientific : Destination("scientific")
    data object Formulas : Destination("formulas")
    data object QuickSolve : Destination("quick_solve")
    data object Library : Destination("library")
    data object MathLab : Destination("math_lab")

    data object History : Destination("history")
    data object Saved : Destination("saved")
    data object Settings : Destination("settings")
    data object About : Destination("about")

    /** Library formula detail; `formulaId` is the stable library id. */
    data object FormulaDetail : Destination("formula/{formulaId}") {
        const val ARG_FORMULA_ID = "formulaId"
        fun create(formulaId: String) = "formula/$formulaId"
        val arguments = listOf(navArgument(ARG_FORMULA_ID) { type = NavType.StringType })
    }

    /**
     * Formula Calculator runner for one form id. Reached from the Formulas tab,
     * from a library entry's "Open calculator" action, or by deep link.
     */
    data object FormRunner : Destination("form/{formId}") {
        const val ARG_FORM_ID = "formId"
        fun create(formId: String) = "form/$formId"
        val arguments = listOf(navArgument(ARG_FORM_ID) { type = NavType.StringType })
    }

    /** A single Quick Solve tool opened directly. */
    data object SolverTool : Destination("solver/{toolId}") {
        const val ARG_TOOL_ID = "toolId"
        fun create(toolId: String) = "solver/$toolId"
        val arguments = listOf(navArgument(ARG_TOOL_ID) { type = NavType.StringType })
    }

    /** One Math Lab module (matrices / statistics / complex / solver / bases). */
    data object LabModule : Destination("lab/{moduleId}") {
        const val ARG_MODULE_ID = "moduleId"
        fun create(moduleId: String) = "lab/$moduleId"
        val arguments = listOf(navArgument(ARG_MODULE_ID) { type = NavType.StringType })
    }
}

/** The six destinations rendered in the navigation bar, in spec order. */
val primaryDestinations: List<Destination> = listOf(
    Destination.Standard,
    Destination.Scientific,
    Destination.Formulas,
    Destination.QuickSolve,
    Destination.Library,
    Destination.MathLab,
)

/** Math Lab module ids, matching `LabModule` labels in the UI. */
object LabModuleId {
    const val MATRICES = "matrices"
    const val STATISTICS = "statistics"
    const val COMPLEX = "complex"
    const val NUMERIC_SOLVER = "numeric_solver"
    const val BASES = "bases"
    const val FORMATTING = "formatting"
    val ALL = listOf(MATRICES, STATISTICS, COMPLEX, NUMERIC_SOLVER, BASES, FORMATTING)
}

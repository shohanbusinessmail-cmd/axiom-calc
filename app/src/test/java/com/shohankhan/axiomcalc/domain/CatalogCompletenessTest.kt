package com.shohankhan.axiomcalc.domain

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.domain.formula.FormulaCatalog
import com.shohankhan.axiomcalc.domain.formula.FormulaCategory
import com.shohankhan.axiomcalc.domain.library.FormulaLibrary
import com.shohankhan.axiomcalc.domain.solver.QuickSolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Completeness guard: every advertised category, form, solver tool and library
 * entry is executed with a valid sample input set, and the cross-links between
 * the library and the catalog are verified to resolve to real implementations.
 *
 * If a form is added without a sample here, [everyFormHasASample] fails, so the
 * suite cannot silently drift from the catalog.
 */
class CatalogCompletenessTest {

    private val samples: Map<String, Map<String, String>> = mapOf(
        "pct_of" to mapOf("value" to "200", "percent" to "15"),
        "pct_what" to mapOf("part" to "30", "whole" to "200"),
        "pct_increase" to mapOf("from" to "80", "to" to "100"),
        "pct_decrease" to mapOf("from" to "100", "to" to "80"),
        "pct_change" to mapOf("from" to "80", "to" to "100"),
        "pct_points" to mapOf("from" to "18", "to" to "20"),
        "pct_reverse_increase" to mapOf("final" to "110", "percent" to "10"),
        "pct_reverse_decrease" to mapOf("final" to "90", "percent" to "10"),
        "disc_amount" to mapOf("price" to "500", "percent" to "20"),
        "disc_final" to mapOf("price" to "500", "percent" to "20"),
        "disc_percent" to mapOf("original" to "500", "final" to "400"),
        "disc_original" to mapOf("final" to "400", "percent" to "20"),
        "disc_sequential" to mapOf("price" to "100", "d1" to "10", "d2" to "20", "d3" to "0"),
        "disc_effective" to mapOf("d1" to "10", "d2" to "20"),
        "vat_add" to mapOf("base" to "1000", "rate" to "15"),
        "vat_extract" to mapOf("total" to "1150", "rate" to "15"),
        "vat_base_from_tax" to mapOf("tax" to "150", "rate" to "15"),
        "vat_total_from_tax" to mapOf("base" to "1000", "tax" to "150"),
        "profit_basic" to mapOf("cost" to "80", "selling" to "100"),
        "profit_percent" to mapOf("cost" to "80", "selling" to "100"),
        "loss_percent" to mapOf("cost" to "100", "selling" to "80"),
        "markup_percent" to mapOf("cost" to "80", "selling" to "100"),
        "margin_percent" to mapOf("cost" to "80", "selling" to "100"),
        "selling_from_markup" to mapOf("cost" to "80", "markup" to "25"),
        "selling_from_margin" to mapOf("cost" to "80", "margin" to "20"),
        "cost_from_margin" to mapOf("selling" to "100", "margin" to "20"),
        "ratio_simplify" to mapOf("a" to "48", "b" to "18"),
        "ratio_equivalent" to mapOf("a" to "2", "b" to "3", "k" to "4"),
        "ratio_direct" to mapOf("a" to "2", "b" to "3", "c" to "4"),
        "ratio_inverse" to mapOf("a" to "2", "b" to "3", "c" to "4"),
        "ratio_compare" to mapOf("a1" to "2", "b1" to "4", "a2" to "3", "b2" to "6"),
        "avg_mean" to mapOf("values" to "2, 4, 6"),
        "avg_weighted" to mapOf("values" to "2, 4, 6", "weights" to "1, 1, 1"),
        "avg_median" to mapOf("values" to "3, 1, 2"),
        "avg_mode" to mapOf("values" to "1, 2, 2, 3"),
        "avg_range" to mapOf("values" to "1, 5, 3"),
        "int_simple" to mapOf("principal" to "1000", "rate" to "5", "years" to "2"),
        "int_compound" to mapOf("principal" to "1000", "rate" to "10", "years" to "2", "n" to "1"),
        "int_principal" to mapOf("amount" to "1210", "rate" to "10", "years" to "2", "n" to "1"),
        "int_rate" to mapOf("principal" to "1000", "amount" to "1210", "years" to "2", "n" to "1"),
        "int_time" to mapOf("principal" to "1000", "amount" to "1210", "rate" to "10", "n" to "1"),
        "inst_payment" to mapOf("principal" to "10000", "rate" to "12", "years" to "1", "perYear" to "12"),
        "inst_total" to mapOf("principal" to "10000", "rate" to "12", "years" to "1", "perYear" to "12"),
        "geo_square" to mapOf("side" to "3"),
        "geo_rectangle" to mapOf("length" to "4", "width" to "3"),
        "geo_triangle_bh" to mapOf("base" to "4", "height" to "3"),
        "geo_triangle_heron" to mapOf("a" to "3", "b" to "4", "c" to "5"),
        "geo_circle" to mapOf("radius" to "2"),
        "geo_sphere" to mapOf("radius" to "2"),
        "geo_cube" to mapOf("side" to "2"),
        "geo_cuboid" to mapOf("length" to "2", "width" to "3", "height" to "4"),
        "geo_cylinder" to mapOf("radius" to "2", "height" to "5"),
        "geo_cone" to mapOf("radius" to "3", "height" to "4"),
        "geo_trapezoid" to mapOf("a" to "3", "b" to "5", "height" to "4"),
        "geo_ellipse" to mapOf("a" to "3", "b" to "2"),
        "geo_sector" to mapOf("radius" to "5", "angle" to "90"),
        "alg_eval_linear" to mapOf("a" to "2", "b" to "3", "x" to "4"),
        "alg_solve_linear_x" to mapOf("a" to "3", "b" to "5", "c" to "20"),
        "alg_solve_linear_a" to mapOf("x" to "4", "b" to "3", "c" to "20"),
        "alg_poly_eval" to mapOf("c0" to "1", "c1" to "2", "c2" to "3", "c3" to "4", "x" to "2"),
        "frac_simplify" to mapOf("numerator" to "48", "denominator" to "18"),
        "frac_add" to mapOf("n1" to "1", "d1" to "2", "n2" to "1", "d2" to "3"),
        "frac_sub" to mapOf("n1" to "1", "d1" to "2", "n2" to "1", "d2" to "3"),
        "frac_mul" to mapOf("n1" to "1", "d1" to "2", "n2" to "1", "d2" to "3"),
        "frac_div" to mapOf("n1" to "1", "d1" to "2", "n2" to "3", "d2" to "4"),
        "frac_compare" to mapOf("n1" to "1", "d1" to "2", "n2" to "2", "d2" to "4"),
        "frac_mixed" to mapOf("whole" to "2", "numerator" to "1", "denominator" to "2"),
        "frac_decimal" to mapOf("numerator" to "1", "denominator" to "8"),
        "time_add" to mapOf("h1" to "1", "m1" to "30", "s1" to "0", "h2" to "2", "m2" to "45", "s2" to "30"),
        "time_sub" to mapOf("h1" to "2", "m1" to "45", "s1" to "30", "h2" to "1", "m2" to "30", "s2" to "0"),
        "time_convert" to mapOf("hours" to "1", "minutes" to "30", "seconds" to "0"),
        "time_interval" to mapOf("h1" to "9", "m1" to "0", "s1" to "0", "h2" to "17", "m2" to "30", "s2" to "0"),
        "date_diff" to mapOf("start" to "2024-02-28", "end" to "2024-03-01"),
        "date_add" to mapOf("date" to "2024-02-28", "days" to "1"),
        "date_sub" to mapOf("date" to "2024-03-01", "days" to "1"),
        "date_add_months" to mapOf("date" to "2024-01-31", "months" to "1"),
        "date_leap" to mapOf("date" to "2024-02-29"),
    )

    private val toolSamples: Map<String, Map<String, String>> = mapOf(
        "qs_pct_of" to mapOf("value" to "200", "percent" to "15"),
        "qs_reverse_pct" to mapOf("final" to "110", "percent" to "10", "direction" to "increase"),
        "qs_pct_change" to mapOf("from" to "80", "to" to "100"),
        "qs_pct_points" to mapOf("from" to "18", "to" to "20"),
        "qs_successive_discounts" to mapOf("price" to "100", "d1" to "10", "d2" to "20"),
        "qs_ratio_simplify" to mapOf("a" to "48", "b" to "18"),
        "qs_missing_proportion" to mapOf("a" to "2", "b" to "3", "c" to "4"),
        "qs_fraction" to mapOf("n1" to "1", "d1" to "2", "op" to "+", "n2" to "1", "d2" to "3"),
        "qs_gcd" to mapOf("a" to "48", "b" to "18"),
        "qs_lcm" to mapOf("a" to "12", "b" to "18"),
        "qs_prime_factors" to mapOf("n" to "360"),
        "qs_primality" to mapOf("n" to "97"),
        "qs_linear" to mapOf("equation" to "3x + 5 = 20"),
        "qs_simultaneous2" to mapOf("a1" to "1", "b1" to "1", "c1" to "5", "a2" to "1", "b2" to "-1", "c2" to "1"),
        "qs_simultaneous3" to mapOf(
            "a1" to "1", "b1" to "0", "c1" to "0", "d1" to "1",
            "a2" to "0", "b2" to "1", "c2" to "0", "d2" to "2",
            "a3" to "0", "b3" to "0", "c3" to "1", "d3" to "3",
        ),
        "qs_quadratic" to mapOf("a" to "1", "b" to "-5", "c" to "6"),
        "qs_arith_seq" to mapOf("a1" to "2", "d" to "3", "n" to "10"),
        "qs_geom_seq" to mapOf("a1" to "2", "r" to "3", "n" to "5"),
        "qs_sigfigs" to mapOf("value" to "3.14159", "figures" to "3"),
        "qs_round" to mapOf("value" to "2.345", "decimals" to "2", "mode" to "HALF_UP"),
        "qs_scientific" to mapOf("value" to "123456"),
        "qs_mean" to mapOf("values" to "2, 4, 6"),
        "qs_median" to mapOf("values" to "2, 4, 6"),
        "qs_mode" to mapOf("values" to "1, 2, 2, 3"),
        "qs_range" to mapOf("values" to "1, 5, 3"),
        "qs_variance" to mapOf("values" to "2, 4, 6"),
        "qs_stddev" to mapOf("values" to "2, 4, 6"),
    )

    @Test fun everyCategoryHasForms() {
        FormulaCategory.entries.forEach { category ->
            assertTrue(
                "category ${category.title} has no forms",
                FormulaCatalog.byCategory(category).isNotEmpty(),
            )
        }
    }

    @Test fun everyFormHasASample() {
        val missing = FormulaCatalog.all.map { it.id }.filter { it !in samples }
        assertEquals("forms without sample inputs: $missing", emptyList<String>(), missing)
    }

    @Test fun everyFormComputesWithValidInput() {
        val failures = ArrayList<String>()
        FormulaCatalog.all.forEach { form ->
            val input = samples.getValue(form.id)
            try {
                val result = form.compute(input)
                if (result.lines.isEmpty()) failures += "${form.id}: no result lines"
            } catch (e: Exception) {
                failures += "${form.id}: ${e::class.java.simpleName} ${e.message}"
            }
        }
        assertEquals("forms that failed on valid input: $failures", emptyList<String>(), failures)
    }

    @Test fun everyFormDeclaresItsInputsAndFormula() {
        FormulaCatalog.all.forEach { form ->
            assertTrue("${form.id}: no inputs", form.inputs.isNotEmpty())
            assertTrue("${form.id}: no formula text", form.formulaText.isNotBlank())
            assertTrue("${form.id}: no explanation", form.explanation.isNotBlank())
            val ids = form.inputs.map { it.id }.toSet()
            val sample = samples.getValue(form.id)
            assertEquals("${form.id}: sample keys != input ids", ids, sample.keys)
        }
    }

    @Test fun everyFormRejectsGarbageExplicitly() {
        val failures = ArrayList<String>()
        FormulaCatalog.all.forEach { form ->
            val garbage = form.inputs.associate { it.id to "not-a-number-🚫" }
            try {
                form.compute(garbage)
                failures += "${form.id}: accepted garbage input"
            } catch (e: CalcException) {
                // expected: a typed error, never a silent value
            } catch (e: Exception) {
                failures += "${form.id}: untyped ${e::class.java.simpleName}"
            }
        }
        assertEquals("forms with weak validation: $failures", emptyList<String>(), failures)
    }

    @Test fun knownReferenceResultsFromForms() {
        assertEquals("30", FormulaCatalog.byId("pct_of")!!.compute(samples.getValue("pct_of")).lines[0].value)
        assertEquals("400", FormulaCatalog.byId("disc_final")!!.compute(samples.getValue("disc_final")).lines[0].value)
        assertEquals("25", FormulaCatalog.byId("pct_increase")!!.compute(samples.getValue("pct_increase")).lines[0].value)
        assertEquals("8", FormulaCatalog.byId("ratio_simplify")!!.compute(samples.getValue("ratio_simplify")).lines[0].value.split(" : ")[0])
    }

    @Test fun zeroDenominatorsAreRejectedNotSilent() {
        val form = FormulaCatalog.byId("pct_what")!!
        try {
            form.compute(mapOf("part" to "10", "whole" to "0"))
            org.junit.Assert.fail("expected an error for a zero denominator")
        } catch (e: CalcException) {
            assertTrue(e.error is CalcError.DivisionByZero || e.error is CalcError.InvalidArgument)
        }
    }

    @Test fun triangleInequalityIsEnforced() {
        val form = FormulaCatalog.byId("geo_triangle_heron")!!
        try {
            form.compute(mapOf("a" to "1", "b" to "2", "c" to "9"))
            org.junit.Assert.fail("expected a domain error")
        } catch (e: CalcException) {
            assertTrue(e.error is CalcError.Domain)
        }
    }

    // ---- quick solve ---------------------------------------------------------------

    @Test fun everyToolHasASample() {
        val missing = QuickSolve.all.map { it.id }.filter { it !in toolSamples }
        assertEquals("tools without samples: $missing", emptyList<String>(), missing)
    }

    @Test fun everyToolSolvesWithShownWorking() {
        val failures = ArrayList<String>()
        QuickSolve.all.forEach { tool ->
            val input = toolSamples.getValue(tool.id)
            try {
                val result = tool.solve(input)
                if (result.lines.isEmpty()) failures += "${tool.id}: no result lines"
                if (result.steps.size < 3) failures += "${tool.id}: fewer than 3 steps"
                if (result.steps.none { it.startsWith("Relationship:") }) failures += "${tool.id}: no relationship step"
                if (result.steps.none { it.startsWith("Final:") }) failures += "${tool.id}: no final step"
                if (result.steps.none { it.startsWith("Restriction:") }) failures += "${tool.id}: no restriction step"
            } catch (e: Exception) {
                failures += "${tool.id}: ${e::class.java.simpleName} ${e.message}"
            }
        }
        assertEquals("tools that failed: $failures", emptyList<String>(), failures)
    }

    @Test fun quadraticToolMatchesSolver() {
        val r = QuickSolve.byId("qs_quadratic")!!.solve(toolSamples.getValue("qs_quadratic"))
        assertEquals("TWO_DISTINCT_REAL", r.lines[0].value)
        assertEquals("2  ,  3", r.lines[2].value)
    }

    // ---- library ----------------------------------------------------------------------

    @Test fun libraryEntriesAreComplete() {
        FormulaLibrary.all.forEach { f ->
            assertTrue("${f.id}: no expression", f.expression.isNotBlank())
            assertTrue("${f.id}: no variables", f.variables.isNotEmpty())
            assertTrue("${f.id}: no conditions", f.conditions.isNotBlank())
        }
    }

    @Test fun libraryLinksResolveToImplementedForms() {
        val broken = FormulaLibrary.all.mapNotNull { f -> f.formId }
            .filter { FormulaCatalog.byId(it) == null && QuickSolve.byId(it) == null }
        assertEquals("library entries linking to non-existent forms: $broken", emptyList<String>(), broken)
    }

    @Test fun libraryCoversEveryCategory() {
        com.shohankhan.axiomcalc.domain.library.LibraryCategory.entries.forEach { category ->
            assertTrue(
                "library category ${category.title} is empty",
                FormulaLibrary.byCategory(category).isNotEmpty(),
            )
        }
    }

    @Test fun librarySearchFindsByKeywordAndCategory() {
        assertTrue(FormulaLibrary.search("pythagoras").isNotEmpty())
        assertTrue(FormulaLibrary.search("margin").isNotEmpty())
        assertTrue(FormulaLibrary.search("", com.shohankhan.axiomcalc.domain.library.LibraryCategory.TRIGONOMETRY).isNotEmpty())
        assertTrue(FormulaLibrary.search("zzz-not-a-formula").isEmpty())
    }

    @Test fun catalogSearchFindsForms() {
        assertNotNull(FormulaCatalog.search("discount").firstOrNull())
        assertNotNull(FormulaCatalog.search("heron").firstOrNull())
        assertTrue(FormulaCatalog.search("qqqq").isEmpty())
    }
}

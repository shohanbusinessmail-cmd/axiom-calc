package com.shohankhan.axiomcalc.math.constants

import com.shohankhan.axiomcalc.math.precision.CalcNumber
import java.math.BigDecimal

/**
 * Whether a constant's value is defined exactly or is an experimentally
 * measured quantity. The distinction is surfaced in the UI and the Formula
 * Library so the app never implies more precision than physics provides.
 */
enum class ConstantExactness { EXACT, MEASURED }

/**
 * A named constant with its display metadata.
 *
 * @property id          case-insensitive identifier usable in expressions
 * @property symbol      typographic symbol for display
 * @property displayName human readable name
 * @property value       value at the engine's working precision
 * @property unit        SI unit, or null for dimensionless constants
 * @property exactness   whether the value is a definition or a measurement
 * @property note        precision provenance shown in the Formula Library
 */
data class ConstantDefinition(
    val id: String,
    val symbol: String,
    val displayName: String,
    val value: CalcNumber,
    val unit: String?,
    val exactness: ConstantExactness,
    val note: String,
)

/**
 * Centralized, offline registry of every constant the engine understands.
 *
 * Mathematical constants are stored with more digits than the double precision
 * used by transcendental functions, so they never limit the result. Physical
 * constants use the exact SI 2019 values where the SI defines them exactly, and
 * CODATA 2018 recommended values where they are measured.
 */
object ConstantRegistry {

    private fun d(text: String): CalcNumber = CalcNumber.of(BigDecimal(text))

    val PI = ConstantDefinition(
        id = "pi", symbol = "π", displayName = "Pi",
        value = d("3.14159265358979323846264338327950288"),
        unit = null, exactness = ConstantExactness.EXACT,
        note = "Ratio of a circle's circumference to its diameter. Stored to 35 digits; transcendentals evaluate at double precision.",
    )
    val E = ConstantDefinition(
        id = "e", symbol = "e", displayName = "Euler's number",
        value = d("2.71828182845904523536028747135266250"),
        unit = null, exactness = ConstantExactness.EXACT,
        note = "Base of the natural logarithm. Stored to 35 digits.",
    )
    val TAU = ConstantDefinition(
        id = "tau", symbol = "τ", displayName = "Tau",
        value = d("6.28318530717958647692528676655900577"),
        unit = null, exactness = ConstantExactness.EXACT,
        note = "τ = 2π, one full turn in radians.",
    )
    val PHI = ConstantDefinition(
        id = "phi", symbol = "φ", displayName = "Golden ratio",
        value = d("1.61803398874989484820458683436563811"),
        unit = null, exactness = ConstantExactness.EXACT,
        note = "φ = (1 + √5) / 2.",
    )
    val SPEED_OF_LIGHT = ConstantDefinition(
        id = "c", symbol = "c", displayName = "Speed of light in vacuum",
        value = CalcNumber.of(BigDecimal("299792458")),
        unit = "m/s", exactness = ConstantExactness.EXACT,
        note = "Exact by definition of the metre (SI).",
    )
    val AVOGADRO = ConstantDefinition(
        id = "NA", symbol = "Nₐ", displayName = "Avogadro constant",
        value = d("6.02214076e23"),
        unit = "1/mol", exactness = ConstantExactness.EXACT,
        note = "Exact since the 2019 SI redefinition.",
    )
    val PLANCK = ConstantDefinition(
        id = "h", symbol = "h", displayName = "Planck constant",
        value = d("6.62607015e-34"),
        unit = "J·s", exactness = ConstantExactness.EXACT,
        note = "Exact since the 2019 SI redefinition.",
    )
    val BOLTZMANN = ConstantDefinition(
        id = "kB", symbol = "k_B", displayName = "Boltzmann constant",
        value = d("1.380649e-23"),
        unit = "J/K", exactness = ConstantExactness.EXACT,
        note = "Exact since the 2019 SI redefinition.",
    )
    val ELEMENTARY_CHARGE = ConstantDefinition(
        id = "q_e", symbol = "e⁻", displayName = "Elementary charge",
        value = d("1.602176634e-19"),
        unit = "C", exactness = ConstantExactness.EXACT,
        note = "Exact since the 2019 SI redefinition.",
    )
    val STANDARD_GRAVITY = ConstantDefinition(
        id = "g0", symbol = "g₀", displayName = "Standard gravity",
        value = d("9.80665"),
        unit = "m/s²", exactness = ConstantExactness.EXACT,
        note = "Conventional standard acceleration of free fall, defined exactly.",
    )
    val GRAVITATIONAL = ConstantDefinition(
        id = "G", symbol = "G", displayName = "Newtonian constant of gravitation",
        value = d("6.67430e-11"),
        unit = "m³/(kg·s²)", exactness = ConstantExactness.MEASURED,
        note = "CODATA 2018 recommended value, relative standard uncertainty 2.2e-5. Measured, not exact.",
    )

    val all: List<ConstantDefinition> = listOf(
        PI, E, TAU, PHI, SPEED_OF_LIGHT, AVOGADRO, PLANCK,
        BOLTZMANN, ELEMENTARY_CHARGE, STANDARD_GRAVITY, GRAVITATIONAL,
    )

    private val byId: Map<String, ConstantDefinition> =
        all.associateBy { it.id.lowercase() }

    /** Typographic symbols accepted as aliases, e.g. `π` for `pi`. */
    private val bySymbol: Map<String, ConstantDefinition> =
        all.filter { it.symbol != it.id }.associateBy { it.symbol }

    /**
     * Case-insensitive lookup by identifier, falling back to the typographic
     * symbol so the calculator keypad can insert `π`, `τ` and `φ` directly and
     * the engine still evaluates them. Returns null when the name is unknown.
     */
    fun find(id: String): ConstantDefinition? = byId[id.lowercase()] ?: bySymbol[id]
}

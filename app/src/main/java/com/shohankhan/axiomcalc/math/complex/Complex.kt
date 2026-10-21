package com.shohankhan.axiomcalc.math.complex

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Immutable complex number with rectangular and polar views.
 *
 * The engine's expression language is real-valued by design; complex arithmetic
 * is exposed explicitly here and through the Math Lab so that a user always
 * knows when a calculation left the real numbers.
 *
 * Angle convention: [argument] returns the principal value in (-pi, pi],
 * in radians. Conversion to degrees is a display concern.
 */
data class Complex(val re: Double, val im: Double) {

    val isReal: Boolean get() = im == 0.0
    val isImaginary: Boolean get() = re == 0.0 && im != 0.0

    operator fun plus(o: Complex) = Complex(re + o.re, im + o.im)
    operator fun minus(o: Complex) = Complex(re - o.re, im - o.im)
    operator fun times(o: Complex) = Complex(re * o.re - im * o.im, re * o.im + im * o.re)

    operator fun div(o: Complex): Complex {
        val denominator = o.re * o.re + o.im * o.im
        if (denominator == 0.0) {
            throw CalcException(CalcError.DivisionByZero("complex division by zero ($o)"))
        }
        // Smith's algorithm style scaling keeps the intermediate magnitudes sane.
        return Complex(
            (re * o.re + im * o.im) / denominator,
            (im * o.re - re * o.im) / denominator,
        )
    }

    operator fun unaryMinus() = Complex(-re, -im)

    fun conjugate() = Complex(re, -im)

    /** Modulus |z|. */
    fun magnitude(): Double = hypot(re, im)

    /** Principal argument in radians, (-pi, pi]. */
    fun argument(): Double = atan2(im, re)

    /** Polar representation: (magnitude, argument in radians). */
    fun toPolar(): Polar = Polar(magnitude(), argument())

    /** Rectangular display string, e.g. `3 - 4i`. */
    fun toRectangularString(decimals: Int = 6): String {
        val r = trim(re, decimals)
        val i = trim(abs(im), decimals)
        return when {
            im == 0.0 -> r
            re == 0.0 -> "${signOf(im)}${i}i"
            else -> "$r ${signOf(im)} ${i}i"
        }
    }

    override fun toString(): String = toRectangularString()

    private fun signOf(v: Double): String = if (v < 0) "-" else "+"

    private fun trim(v: Double, decimals: Int): String {
        val scaled = String.format("%.${decimals}f", v)
        return if (scaled.contains('.')) scaled.trimEnd('0').trimEnd('.') else scaled
    }

    companion object {
        val ZERO = Complex(0.0, 0.0)
        val ONE = Complex(1.0, 0.0)
        val I = Complex(0.0, 1.0)

        fun ofPolar(magnitude: Double, argumentRadians: Double): Complex =
            Complex(magnitude * cos(argumentRadians), magnitude * sin(argumentRadians))

        /** Real square roots of a real number, returning a complex pair when needed. */
        fun sqrtOfReal(x: Double): Complex =
            if (x >= 0) Complex(kotlin.math.sqrt(x), 0.0) else Complex(0.0, kotlin.math.sqrt(-x))
    }
}

/** Polar view of a complex number: radius r and angle theta in radians. */
data class Polar(val r: Double, val thetaRadians: Double) {
    fun toRectangular(): Complex = Complex.ofPolar(r, thetaRadians)

    fun toString(decimals: Int = 6): String {
        val rr = String.format("%.${decimals}f", r).trimEnd('0').trimEnd('.')
        val th = String.format("%.${decimals}f", thetaRadians).trimEnd('0').trimEnd('.')
        return "${rr} ∠ ${th} rad"
    }
}

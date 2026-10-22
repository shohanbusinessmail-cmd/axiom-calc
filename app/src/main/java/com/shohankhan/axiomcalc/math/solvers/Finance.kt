package com.shohankhan.axiomcalc.math.solvers

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import com.shohankhan.axiomcalc.math.precision.Precision

/**
 * Interest and amortization mathematics.
 *
 * Conventions, documented in the UI and in MATHEMATICS_SPEC.md:
 *  * rates are entered as **percent per year** (e.g. 12 means 12 %/year).
 *  * time is entered in **years** and may be fractional.
 *  * compounding frequency n is the number of compounding events per year.
 *  * simple interest: I = P r t / 100.
 *  * compound interest: A = P (1 + r / (100 n))^(n t).
 *  * installments use the fixed-rate amortization formula
 *        A = P i / (1 - (1 + i)^-N)
 *    with i the periodic rate and N the number of payments; i = 0 degenerates
 *    to A = P / N, which is handled explicitly rather than producing 0/0.
 *
 * These are mathematical results under the stated conventions. They are never
 * presented as a lender's offer or as financial advice.
 */
object Finance {

    private val HUNDRED = CalcNumber.of(100)

    data class SimpleInterest(
        val principal: CalcNumber,
        val interest: CalcNumber,
        val amount: CalcNumber,
    )

    fun simpleInterest(principal: CalcNumber, annualRatePercent: CalcNumber, years: CalcNumber): SimpleInterest {
        val interest = principal * annualRatePercent * years / HUNDRED
        return SimpleInterest(principal, interest, principal + interest)
    }

    fun simplePrincipal(amount: CalcNumber, annualRatePercent: CalcNumber, years: CalcNumber): CalcNumber {
        val factor = CalcNumber.ONE + annualRatePercent * years / HUNDRED
        if (factor.isZero) throw CalcException(CalcError.DivisionByZero("the growth factor is zero"))
        return amount / factor
    }

    fun simpleRate(principal: CalcNumber, amount: CalcNumber, years: CalcNumber): CalcNumber {
        requireNonZero(principal, "principal"); requireNonZero(years, "time")
        return (amount - principal) / principal / years * HUNDRED
    }

    fun simpleTime(principal: CalcNumber, amount: CalcNumber, annualRatePercent: CalcNumber): CalcNumber {
        requireNonZero(principal, "principal"); requireNonZero(annualRatePercent, "rate")
        return (amount - principal) / principal / annualRatePercent * HUNDRED
    }

    data class CompoundInterest(
        val principal: CalcNumber,
        val amount: CalcNumber,
        val interest: CalcNumber,
        val periods: CalcNumber,
        val periodicFactor: CalcNumber,
    )

    /** Growth factor (1 + r/(100 n)); validated to be positive. */
    fun compoundFactor(annualRatePercent: CalcNumber, compoundsPerYear: Int): CalcNumber {
        requirePositiveCompounds(compoundsPerYear)
        val factor = CalcNumber.ONE + annualRatePercent / HUNDRED / CalcNumber.of(compoundsPerYear)
        if (factor.signum <= 0) {
            throw CalcException(CalcError.Domain("compound factor", "1 + r/(100n) must be positive, got $factor"))
        }
        return factor
    }

    fun compoundInterest(
        principal: CalcNumber,
        annualRatePercent: CalcNumber,
        years: CalcNumber,
        compoundsPerYear: Int,
    ): CompoundInterest {
        val factor = compoundFactor(annualRatePercent, compoundsPerYear)
        val periods = CalcNumber.of(compoundsPerYear) * years
        val growth = powDecimal(factor, periods)
        val amount = principal * growth
        return CompoundInterest(principal, amount, amount - principal, periods, factor)
    }

    fun compoundPrincipal(
        amount: CalcNumber,
        annualRatePercent: CalcNumber,
        years: CalcNumber,
        compoundsPerYear: Int,
    ): CalcNumber {
        val growth = powDecimal(compoundFactor(annualRatePercent, compoundsPerYear), CalcNumber.of(compoundsPerYear) * years)
        if (growth.isZero) throw CalcException(CalcError.DivisionByZero("growth factor is zero"))
        return amount / growth
    }

    fun compoundRate(
        principal: CalcNumber,
        amount: CalcNumber,
        years: CalcNumber,
        compoundsPerYear: Int,
    ): CalcNumber {
        requireNonZero(principal, "principal"); requireNonZero(years, "time")
        requirePositiveCompounds(compoundsPerYear)
        val ratio = (amount / principal).toDouble()
        if (ratio <= 0) throw CalcException(CalcError.Domain("compound rate", "amount/principal must be positive"))
        val periods = compoundsPerYear * years.toDouble()
        if (periods == 0.0) throw CalcException(CalcError.DivisionByZero("n * t is zero"))
        val perPeriod = Math.pow(ratio, 1.0 / periods) - 1.0
        return CalcNumber.of(perPeriod * compoundsPerYear * 100.0)
    }

    fun compoundYears(
        principal: CalcNumber,
        amount: CalcNumber,
        annualRatePercent: CalcNumber,
        compoundsPerYear: Int,
    ): CalcNumber {
        requireNonZero(principal, "principal")
        requirePositiveCompounds(compoundsPerYear)
        val factor = compoundFactor(annualRatePercent, compoundsPerYear)
        if (factor == CalcNumber.ONE) throw CalcException(CalcError.DivisionByZero("a zero rate never grows"))
        val ratio = (amount / principal).toDouble()
        if (ratio <= 0) throw CalcException(CalcError.Domain("compound time", "amount/principal must be positive"))
        val years = Math.log(ratio) / (compoundsPerYear * Math.log(factor.toDouble()))
        if (!years.isFinite()) throw CalcException(CalcError.Domain("compound time", "no finite solution"))
        return CalcNumber.of(years)
    }

    // ---- amortization --------------------------------------------------------

    data class Amortization(
        val principal: CalcNumber,
        val periodicPayment: CalcNumber,
        val numberOfPayments: Int,
        val periodicRatePercent: CalcNumber,
        val totalRepayment: CalcNumber,
        val totalInterest: CalcNumber,
        val frequencyPerYear: Int,
    )

    /**
     * Fixed-rate amortization.
     *
     * @param annualRatePercent nominal annual rate in percent
     * @param years             term in years
     * @param paymentsPerYear   payment frequency (12 = monthly, 4 = quarterly, ...)
     */
    fun amortization(
        principal: CalcNumber,
        annualRatePercent: CalcNumber,
        years: CalcNumber,
        paymentsPerYear: Int,
    ): Amortization {
        requirePositiveCompounds(paymentsPerYear)
        if (principal.signum <= 0) throw CalcException(CalcError.InvalidArgument("principal", "principal must be > 0"))
        val periodsCalc = CalcNumber.of(paymentsPerYear) * years
        val nBi = periodsCalc.toBigIntegerOrNull()
            ?: throw CalcException(CalcError.InvalidArgument("term", "payments per year * years must be an integer count"))
        if (nBi.signum() <= 0) throw CalcException(CalcError.InvalidArgument("term", "number of payments must be > 0"))
        if (nBi.bitLength() > 31) throw CalcException(CalcError.ResourceLimit("payments", "too many payments"))
        val n = nBi.toInt()
        val periodicRate = annualRatePercent / CalcNumber.of(paymentsPerYear)   // percent per period
        val i = periodicRate / HUNDRED                                            // decimal per period
        val payment = if (i.isZero) {
            principal / CalcNumber.of(n)
        } else {
            val onePlusI = CalcNumber.ONE + i
            val growth = powDecimal(onePlusI, CalcNumber.of(n))
            // A = P * i * growth / (growth - 1)
            principal * i * growth / (growth - CalcNumber.ONE)
        }
        val total = payment * CalcNumber.of(n)
        return Amortization(principal, payment, n, periodicRate, total, total - principal, paymentsPerYear)
    }

    /**
     * Amortization schedule rows, capped so the UI can never be asked to render
     * an unbounded list.
     */
    fun schedule(amortization: Amortization, maxRows: Int = 360): List<ScheduleRow> {
        val i = amortization.periodicRatePercent / HUNDRED
        var balance = amortization.principal
        val rows = ArrayList<ScheduleRow>()
        val limit = minOf(maxRows, amortization.numberOfPayments)
        for (period in 1..limit) {
            val interestPart = balance * i
            var principalPart = amortization.periodicPayment - interestPart
            if (period == amortization.numberOfPayments) principalPart = balance
            balance = balance - principalPart
            rows += ScheduleRow(period, amortization.periodicPayment, interestPart, principalPart, maxOf(balance, CalcNumber.ZERO))
            if (balance.signum <= 0 && period == amortization.numberOfPayments) break
        }
        return rows
    }

    data class ScheduleRow(
        val period: Int,
        val payment: CalcNumber,
        val interestPart: CalcNumber,
        val principalPart: CalcNumber,
        val remainingBalance: CalcNumber,
    )

    // ---- internals -----------------------------------------------------------

    /** Exact integer powers stay decimal; fractional powers go through double. */
    private fun powDecimal(base: CalcNumber, exponent: CalcNumber): CalcNumber {
        val bi = exponent.toBigIntegerOrNull()
        if (bi != null && bi.bitLength() <= 31) {
            val e = bi.toInt()
            if (e >= 0) return CalcNumber.of(base.value.pow(e, Precision.MC))
            if (base.isZero) throw CalcException(CalcError.DivisionByZero("zero base with negative exponent"))
            return CalcNumber.of(base.value.pow(-e, Precision.MC).let { java.math.BigDecimal.ONE.divide(it, Precision.MC) })
        }
        val b = base.toDouble()
        val e = exponent.toDouble()
        val r = Math.pow(b, e)
        if (r.isInfinite()) throw CalcException(CalcError.Overflow("growth factor overflow"))
        if (r == 0.0) throw CalcException(CalcError.Underflow("growth factor underflow"))
        return CalcNumber.fromTranscendental(r)
    }

    private fun requireNonZero(v: CalcNumber, name: String): CalcNumber {
        if (v.isZero) throw CalcException(CalcError.DivisionByZero("$name is zero"))
        return v
    }

    private fun requirePositiveCompounds(n: Int) {
        if (n <= 0) throw CalcException(CalcError.InvalidArgument("frequency", "frequency per year must be > 0, got $n"))
        if (n > 3650) throw CalcException(CalcError.ResourceLimit("frequency", "frequency $n per year is implausible"))
    }
}

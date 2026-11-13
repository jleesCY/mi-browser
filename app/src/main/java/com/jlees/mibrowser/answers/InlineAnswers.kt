// Inline answers: what's typed in the Pill answered on the spot, on the device, like a search engine's answer box:
// math ("2^10", "20% of 150", "sqrt(2)"), unit conversions ("5 km in mi", "98.6 f to c"), number bases ("255 in
// hex"), the time and date (here or in a city) and coin flips and dice. Plain functions; tested in AnswersTest.
// Nothing here goes online (no exchange rates or weather).
package com.jlees.mibrowser.answers

import java.math.BigDecimal
import java.math.MathContext
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.*
import kotlin.random.Random

/**
 * An answer: [question] is shown small above it ("5 kilometers ="), [answer] below it ("1,048,576"). Tapping it
 * copies [copy]: the plain value ("1048576"), ready to paste into a calculator or a form.
 */
data class InlineAnswer(val question: String, val answer: String, val copy: String = answer)

/**
 * Whether the answer shown for [shownFor] stays while [typed] has none of its own: typing on from it or back over it
 * ("2^1" → "2^10" passes through "2^"), so the Pill doesn't shrink and grow back between keystrokes.
 */
fun keepsAnswer(shownFor: String, typed: String): Boolean {
    val a = shownFor.trim(); val b = typed.trim()
    return a.isNotEmpty() && b.isNotEmpty() && (b.startsWith(a) || a.startsWith(b))
}

/** "1048576.5" → "1,048,576.5": thousands grouped in a plain number (scientific ones are left as they are). */
fun groupThousands(n: String): String {
    if ('e' in n) return n
    val neg = n.startsWith("-")
    val (int, frac) = n.removePrefix("-").split('.').let { it[0] to it.getOrNull(1) }
    val grouped = int.reversed().chunked(3).joinToString(",").reversed()
    return (if (neg) "-" else "") + grouped + (frac?.let { ".$it" } ?: "")
}

/** The answer to [text], or null when it isn't something mi can answer (most searches). */
fun inlineAnswer(text: String, now: ZonedDateTime = ZonedDateTime.now(), random: Random = Random.Default): InlineAnswer? {
    val q = text.trim()
    if (q.isEmpty() || q.length > 120 || "://" in q) return null
    val lower = q.lowercase(Locale.ROOT)
    return clockAnswer(lower, now) ?: chanceAnswer(lower, random) ?: baseAnswer(lower) ?: unitAnswer(lower) ?: mathAnswer(q)
}

/**
 * The engine's own answer among its [suggestions] (Google sends "= 3.10686 miles"), as an answer to [typed]; only when
 * the suggestions are for what's typed now ([forQuery]), never the last text's.
 */
fun engineAnswer(typed: String, suggestions: List<String>, forQuery: String): InlineAnswer? {
    if (forQuery != typed.trim()) return null
    val a = suggestions.firstOrNull { it.startsWith("=") } ?: return null
    return InlineAnswer("${typed.trim()} =", a.removePrefix("=").trim())
}

// ---- numbers ----

/** [d] rounded to [digits] significant digits, without trailing zeros; scientific ("1.5e20") when very large or small. */
fun formatNumber(d: Double, digits: Int = 10): String {
    if (d == 0.0) return "0"
    val a = abs(d)
    if (a >= 1e15 || a < 1e-6) {
        val (m, e) = String.format(Locale.ROOT, "%.${digits - 1}e", d).split('e')
        return m.trimEnd('0').trimEnd('.') + "e" + e.toInt()
    }
    return BigDecimal(d).round(MathContext(digits)).stripTrailingZeros().toPlainString()
}

// ---- math ----

private fun mathAnswer(q: String): InlineAnswer? {
    // only expressions: something to work out, and a number (or π) in it, so a lone "e" or a year isn't answered
    if (q.none { it.isDigit() } && !q.contains("pi", ignoreCase = true) && 'π' !in q) return null
    val r = Calc(normalize(q)).run() ?: return null
    val n = formatNumber(r)
    return InlineAnswer("$q =", groupThousands(n), n)
}

/** Written math as the parser reads it: "3 x 4" and "÷", "1,000", "20% of 150". */
private fun normalize(q: String) = q.lowercase(Locale.ROOT)
    .replace(Regex("""(?<=\d),(?=\d{3}(\D|$))"""), "")
    .replace(Regex("""(?<=[\d)])\s*[x×]\s*(?=[\d(])"""), "*")
    .replace('÷', '/').replace("**", "^").replace('−', '-')
    .replace(Regex("""%\s*of\s+"""), "%*")
    .replace(" mod ", "%")

/**
 * A recursive-descent calculator: + - * / ^ (right-associative), % (modulo between numbers, else a percent), !, °,
 * parentheses, implicit multiplication before a bracket or a name ("2pi", "3(4+1)"), and the usual functions
 * (radians, as most calculators). [run] is null unless the whole text is one expression with something to work out.
 */
private class Calc(val s: String) {
    var i = 0
    var worked = false // an operator, function or constant was used: "42" alone isn't a sum

    fun run(): Double? = runCatching {
        val v = expr()
        skip()
        if (i < s.length || !worked || !v.isFinite()) null else v
    }.getOrNull()

    fun skip() { while (i < s.length && s[i] == ' ') i++ }
    fun peek(): Char? { skip(); return s.getOrNull(i) }
    fun eat(c: Char): Boolean { if (peek() == c) { i++; return true }; return false }

    fun expr(): Double {
        var v = term()
        while (true) v = when {
            eat('+') -> { worked = true; v + term() }
            eat('-') -> { worked = true; v - term() }
            else -> return v
        }
    }

    fun term(): Double {
        var v = unary()
        while (true) {
            val c = peek()
            v = when {
                c == '*' -> { i++; worked = true; v * unary() }
                c == '/' -> { i++; worked = true; v / unary() }
                c == '%' && operandAfter(i + 1) -> { i++; worked = true; v.mod(unary()) }
                c == '(' || (c != null && c.isLetter()) || c == 'π' -> { worked = true; v * unary() }
                else -> return v
            }
        }
    }

    /** Whether a number or bracket follows at [from] (so % there is modulo, not a percent). */
    fun operandAfter(from: Int): Boolean {
        var j = from
        while (j < s.length && s[j] == ' ') j++
        val c = s.getOrNull(j) ?: return false
        return c.isDigit() || c == '.' || c == '('
    }

    fun unary(): Double = when {
        eat('-') -> -unary()
        eat('+') -> unary()
        else -> power()
    }

    fun power(): Double {
        val b = postfix()
        return if (eat('^')) { worked = true; b.pow(unary()) } else b
    }

    fun postfix(): Double {
        var v = primary()
        while (true) v = when {
            eat('!') -> { worked = true; factorial(v) }
            peek() == '%' && !operandAfter(i + 1) -> { i++; worked = true; v / 100 }
            eat('°') -> { worked = true; v * PI / 180 }
            else -> return v
        }
    }

    fun primary(): Double {
        val c = peek() ?: error("end")
        if (eat('(')) { val v = expr(); eat(')'); return v } // a missing ")" at the end is forgiven
        if (c.isDigit() || c == '.') {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (i < s.length && s[i] == 'e' && s.getOrNull(i + 1)?.let { it.isDigit() || it == '-' } == true) { // 1e5
                i += 2; while (i < s.length && s[i].isDigit()) i++
            }
            return s.substring(start, i).toDouble()
        }
        if (c == 'π') { i++; worked = true; return PI }
        val start = i
        while (i < s.length && s[i].isLetter()) i++
        val name = s.substring(start, i)
        worked = true
        return when (name) {
            "pi" -> PI
            "e" -> E
            else -> {
                val f = FUNCTIONS[name] ?: error("unknown $name")
                f(unary())
            }
        }
    }

    fun factorial(n: Double): Double {
        if (n < 0 || n != floor(n) || n > 170) return Double.NaN
        return (2..n.toInt()).fold(1.0) { a, k -> a * k }
    }

    companion object {
        val FUNCTIONS: Map<String, (Double) -> Double> = mapOf(
            "sqrt" to ::sqrt, "cbrt" to ::cbrt, "abs" to ::abs, "exp" to ::exp,
            "sin" to ::sin, "cos" to ::cos, "tan" to ::tan, "asin" to ::asin, "acos" to ::acos, "atan" to ::atan,
            "arcsin" to ::asin, "arccos" to ::acos, "arctan" to ::atan, "sinh" to ::sinh, "cosh" to ::cosh, "tanh" to ::tanh,
            "ln" to ::ln, "log" to ::log10, "round" to { x -> round(x) }, "floor" to ::floor, "ceil" to ::ceil,
        )
    }
}

// ---- units ----

private enum class Dim { LENGTH, MASS, VOLUME, AREA, SPEED, TIME, DATA, ENERGY, ANGLE, TEMPERATURE }

/** A unit of measure: [factor] of its dimension's base unit (temperature converts by formula). [one]/[many] are shown. */
private class Measure(val dim: Dim, val factor: Double, val one: String, val many: String, val names: List<String>)

private val UNITS: List<Measure> = buildList {
    fun u(dim: Dim, factor: Double, one: String, many: String, vararg names: String) = add(Measure(dim, factor, one, many, names.toList() + one + many))
    // length (meters)
    u(Dim.LENGTH, 1e-9, "nanometer", "nanometers", "nm", "nanometre", "nanometres")
    u(Dim.LENGTH, 1e-6, "micrometer", "micrometers", "µm", "um", "micron", "microns")
    u(Dim.LENGTH, 0.001, "millimeter", "millimeters", "mm", "millimetre", "millimetres")
    u(Dim.LENGTH, 0.01, "centimeter", "centimeters", "cm", "centimetre", "centimetres")
    u(Dim.LENGTH, 1.0, "meter", "meters", "m", "metre", "metres")
    u(Dim.LENGTH, 1000.0, "kilometer", "kilometers", "km", "kilometre", "kilometres", "kms")
    u(Dim.LENGTH, 0.0254, "inch", "inches", "in", "\"")
    u(Dim.LENGTH, 0.3048, "foot", "feet", "ft", "'")
    u(Dim.LENGTH, 0.9144, "yard", "yards", "yd", "yds")
    u(Dim.LENGTH, 1609.344, "mile", "miles", "mi")
    u(Dim.LENGTH, 1852.0, "nautical mile", "nautical miles", "nmi")
    // mass (kilograms)
    u(Dim.MASS, 1e-6, "milligram", "milligrams", "mg")
    u(Dim.MASS, 0.001, "gram", "grams", "g", "gr", "gramme", "grammes")
    u(Dim.MASS, 1.0, "kilogram", "kilograms", "kg", "kgs", "kilo", "kilos")
    u(Dim.MASS, 1000.0, "metric ton", "metric tons", "t", "tonne", "tonnes")
    u(Dim.MASS, 0.028349523125, "ounce", "ounces", "oz")
    u(Dim.MASS, 0.45359237, "pound", "pounds", "lb", "lbs")
    u(Dim.MASS, 6.35029318, "stone", "stone", "st", "stones")
    // volume (liters; US measures)
    u(Dim.VOLUME, 0.001, "milliliter", "milliliters", "ml", "millilitre", "millilitres")
    u(Dim.VOLUME, 0.01, "centiliter", "centiliters", "cl")
    u(Dim.VOLUME, 1.0, "liter", "liters", "l", "litre", "litres")
    u(Dim.VOLUME, 1000.0, "cubic meter", "cubic meters", "m3", "m³")
    u(Dim.VOLUME, 0.00492892159375, "teaspoon", "teaspoons", "tsp")
    u(Dim.VOLUME, 0.01478676478125, "tablespoon", "tablespoons", "tbsp")
    u(Dim.VOLUME, 0.0295735295625, "fluid ounce", "fluid ounces", "fl oz", "floz")
    u(Dim.VOLUME, 0.2365882365, "cup", "cups")
    u(Dim.VOLUME, 0.473176473, "pint", "pints", "pt")
    u(Dim.VOLUME, 0.946352946, "quart", "quarts", "qt")
    u(Dim.VOLUME, 3.785411784, "gallon", "gallons", "gal")
    // area (square meters)
    u(Dim.AREA, 1e-4, "square centimeter", "square centimeters", "cm2", "cm²", "sq cm")
    u(Dim.AREA, 1.0, "square meter", "square meters", "m2", "m²", "sq m")
    u(Dim.AREA, 1e6, "square kilometer", "square kilometers", "km2", "km²", "sq km")
    u(Dim.AREA, 1e4, "hectare", "hectares", "ha")
    u(Dim.AREA, 4046.8564224, "acre", "acres", "ac")
    u(Dim.AREA, 0.09290304, "square foot", "square feet", "ft2", "ft²", "sq ft")
    u(Dim.AREA, 0.00064516, "square inch", "square inches", "in2", "in²", "sq in")
    u(Dim.AREA, 0.83612736, "square yard", "square yards", "yd2", "yd²", "sq yd")
    u(Dim.AREA, 2589988.110336, "square mile", "square miles", "mi2", "mi²", "sq mi")
    // speed (meters per second)
    u(Dim.SPEED, 1.0, "meter per second", "meters per second", "m/s", "mps")
    u(Dim.SPEED, 1 / 3.6, "kilometer per hour", "kilometers per hour", "km/h", "kmh", "kph")
    u(Dim.SPEED, 0.44704, "mile per hour", "miles per hour", "mph")
    u(Dim.SPEED, 1852.0 / 3600, "knot", "knots", "kn", "kt")
    u(Dim.SPEED, 0.3048, "foot per second", "feet per second", "ft/s", "fps")
    // time (seconds)
    u(Dim.TIME, 0.001, "millisecond", "milliseconds", "ms")
    u(Dim.TIME, 1.0, "second", "seconds", "s", "sec", "secs")
    u(Dim.TIME, 60.0, "minute", "minutes", "min", "mins")
    u(Dim.TIME, 3600.0, "hour", "hours", "h", "hr", "hrs")
    u(Dim.TIME, 86400.0, "day", "days", "d")
    u(Dim.TIME, 604800.0, "week", "weeks", "wk", "wks")
    u(Dim.TIME, 2629746.0, "month", "months")
    u(Dim.TIME, 31556952.0, "year", "years", "yr", "yrs")
    // data (bytes)
    u(Dim.DATA, 0.125, "bit", "bits")
    u(Dim.DATA, 1.0, "byte", "bytes", "b")
    u(Dim.DATA, 1e3, "kilobyte", "kilobytes", "kb")
    u(Dim.DATA, 1e6, "megabyte", "megabytes", "mb")
    u(Dim.DATA, 1e9, "gigabyte", "gigabytes", "gb")
    u(Dim.DATA, 1e12, "terabyte", "terabytes", "tb")
    u(Dim.DATA, 1024.0, "kibibyte", "kibibytes", "kib")
    u(Dim.DATA, 1048576.0, "mebibyte", "mebibytes", "mib")
    u(Dim.DATA, 1073741824.0, "gibibyte", "gibibytes", "gib")
    u(Dim.DATA, 1099511627776.0, "tebibyte", "tebibytes", "tib")
    // energy (joules)
    u(Dim.ENERGY, 1.0, "joule", "joules", "j")
    u(Dim.ENERGY, 1000.0, "kilojoule", "kilojoules", "kj")
    u(Dim.ENERGY, 4.184, "calorie", "calories", "cal")
    u(Dim.ENERGY, 4184.0, "kilocalorie", "kilocalories", "kcal")
    u(Dim.ENERGY, 3600.0, "watt-hour", "watt-hours", "wh")
    u(Dim.ENERGY, 3.6e6, "kilowatt-hour", "kilowatt-hours", "kwh")
    // angle (radians)
    u(Dim.ANGLE, PI / 180, "degree", "degrees", "deg", "°")
    u(Dim.ANGLE, 1.0, "radian", "radians", "rad")
    // temperature (factor unused)
    u(Dim.TEMPERATURE, 0.0, "degree Celsius", "degrees Celsius", "c", "°c", "celsius", "centigrade")
    u(Dim.TEMPERATURE, 1.0, "degree Fahrenheit", "degrees Fahrenheit", "f", "°f", "fahrenheit")
    u(Dim.TEMPERATURE, 2.0, "kelvin", "kelvin", "k", "kelvins")
}

private val UNIT_NAMES: Map<String, Measure> = UNITS.flatMap { u -> u.names.map { it.lowercase(Locale.ROOT) to u } }.toMap()

private fun unitNamed(name: String): Measure? = name.trim().removePrefix("a ").removePrefix("an ").let {
    UNIT_NAMES[it] ?: UNIT_NAMES[it.removePrefix("degrees ").removePrefix("degree ")]
}

/** "5 km in mi", "98.6 f to c", "km to miles" (one, when no amount is given). */
private val CONVERSION = Regex("""^(-?[\d.]+(?:e-?\d+)?)?\s*(.+?)\s+(?:in|to|into|as|=)\s+(.+?)\??$""")

private fun unitAnswer(q: String): InlineAnswer? {
    val m = CONVERSION.find(q.replace(Regex("""(?<=\d),(?=\d{3})"""), "")) ?: return null
    val amount = m.groupValues[1].ifEmpty { "1" }.toDoubleOrNull() ?: return null
    val from = unitNamed(m.groupValues[2]) ?: return null
    val to = unitNamed(m.groupValues[3]) ?: return null
    if (from.dim != to.dim) return null
    val result = if (from.dim == Dim.TEMPERATURE) {
        val c = when (from.factor) { 0.0 -> amount; 1.0 -> (amount - 32) * 5 / 9; else -> amount - 273.15 }
        when (to.factor) { 0.0 -> c; 1.0 -> c * 9 / 5 + 32; else -> c + 273.15 }
    } else amount * from.factor / to.factor
    // the amount asked about as typed (up to 15 digits, all a number here keeps); the result rounded to 6, as a search engine shows it
    fun shown(v: Double, u: Measure, digits: Int) = formatNumber(v, digits).let { "${groupThousands(it)} ${if (it == "1") u.one else u.many}" }
    return InlineAnswer("${shown(amount, from, 15)} =", shown(result, to, 6), formatNumber(result, 6))
}

// ---- number bases ----

private val BASES = mapOf("hex" to 16, "hexadecimal" to 16, "binary" to 2, "bin" to 2, "octal" to 8, "oct" to 8, "decimal" to 10, "dec" to 10)
private val BASE_CONVERSION = Regex("""^(0x[0-9a-f]+|0b[01]+|0o[0-7]+|\d+)\s+(?:in|to|into)\s+([a-z]+)$""")

private fun baseAnswer(q: String): InlineAnswer? {
    val m = BASE_CONVERSION.find(q) ?: return null
    val to = BASES[m.groupValues[2]] ?: return null
    val src = m.groupValues[1]
    val n = when {
        src.startsWith("0x") -> src.drop(2).toLongOrNull(16)
        src.startsWith("0b") -> src.drop(2).toLongOrNull(2)
        src.startsWith("0o") -> src.drop(2).toLongOrNull(8)
        else -> src.toLongOrNull()
    } ?: return null
    val out = when (to) { 16 -> "0x" + n.toString(16).uppercase(); 2 -> "0b" + n.toString(2); 8 -> "0o" + n.toString(8); else -> n.toString() }
    return InlineAnswer("$src =", out)
}

// ---- time and date ----

/** Names people use for a place that isn't the last part of its time zone's id. */
private val PLACES = mapOf(
    "nyc" to "America/New_York", "new york city" to "America/New_York", "sf" to "America/Los_Angeles",
    "san francisco" to "America/Los_Angeles", "la" to "America/Los_Angeles", "seattle" to "America/Los_Angeles",
    "california" to "America/Los_Angeles", "texas" to "America/Chicago", "dallas" to "America/Chicago",
    "houston" to "America/Chicago", "boston" to "America/New_York", "miami" to "America/New_York",
    "washington" to "America/New_York", "dc" to "America/New_York", "atlanta" to "America/New_York",
    "uk" to "Europe/London", "england" to "Europe/London", "france" to "Europe/Paris", "germany" to "Europe/Berlin",
    "spain" to "Europe/Madrid", "italy" to "Europe/Rome", "japan" to "Asia/Tokyo", "china" to "Asia/Shanghai",
    "beijing" to "Asia/Shanghai", "india" to "Asia/Kolkata", "delhi" to "Asia/Kolkata", "new delhi" to "Asia/Kolkata",
    "mumbai" to "Asia/Kolkata", "bangalore" to "Asia/Kolkata", "korea" to "Asia/Seoul", "australia" to "Australia/Sydney",
    "utc" to "UTC", "gmt" to "GMT", "pst" to "America/Los_Angeles", "pdt" to "America/Los_Angeles",
    "est" to "America/New_York", "edt" to "America/New_York", "cst" to "America/Chicago", "cdt" to "America/Chicago",
    "mst" to "America/Denver", "mdt" to "America/Denver",
)

private val TIME_IN = Regex("""^(?:what(?:'s| is) the )?(?:current )?time (?:in|at) (.+?)\??$""")
private val TIME_HERE = setOf("time", "current time", "what time is it", "what's the time", "what is the time", "time now")
private val DATE_HERE = setOf("date", "today", "today's date", "todays date", "what's the date", "what is the date", "what day is it", "what's today's date", "what is today's date")

private fun zoneFor(place: String): ZoneId? {
    val p = place.trim().removeSuffix(" right now").removeSuffix(" now")
    PLACES[p]?.let { return ZoneId.of(it) }
    return ZoneId.getAvailableZoneIds().firstOrNull { it.substringAfterLast('/').replace('_', ' ').equals(p, ignoreCase = true) }?.let(ZoneId::of)
}

private fun clockAnswer(q: String, now: ZonedDateTime): InlineAnswer? {
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val day = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
    if (q in TIME_HERE) return InlineAnswer("Local time · ${now.format(day)}", now.format(time))
    if (q in DATE_HERE) return InlineAnswer("Today", now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())))
    val m = TIME_IN.find(q) ?: return null
    val zone = zoneFor(m.groupValues[1]) ?: return null
    val there = now.withZoneSameInstant(zone)
    val place = m.groupValues[1].trim().split(' ').joinToString(" ") { w -> if (w.length <= 3) w.uppercase() else w.replaceFirstChar { it.titlecase() } }
    return InlineAnswer("Time in $place · ${there.format(day)}", there.format(time))
}

// ---- chance ----

private val DIE = Regex("""^(?:roll|throw) (?:a |an |one )?(?:die|dice|d(\d+))$""")

private fun chanceAnswer(q: String, random: Random): InlineAnswer? {
    if (q in setOf("flip a coin", "coin flip", "toss a coin", "flip coin", "heads or tails")) {
        return InlineAnswer("Flip a coin", if (random.nextBoolean()) "Heads" else "Tails")
    }
    val m = DIE.find(q) ?: return null
    val sides = m.groupValues[1].ifEmpty { "6" }.toIntOrNull()?.takeIf { it in 2..1000 } ?: return null
    return InlineAnswer("Roll a ${if (sides == 6) "die" else "d$sides"}", (random.nextInt(sides) + 1).toString())
}

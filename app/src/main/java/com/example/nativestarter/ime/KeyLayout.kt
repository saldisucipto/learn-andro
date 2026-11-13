package com.example.nativestarter.ime

/** Which character board is shown below the scanner bar. */
enum class InputMode { ABC, NUM, SYM }

/**
 * Special keys are addressed by name so the row builders can stay declarative.
 * Everything else is a literal label that is committed verbatim.
 */
object SpecialKeys {
    const val SHIFT = "SHIFT"
    const val BACKSPACE = "BACKSPACE"
    const val SPACE = "SPACE"
    const val ENTER = "ENTER"
    const val MODE_ABC = "MODE_ABC"
    const val MODE_NUM = "MODE_NUM"
    const val MODE_SYM = "MODE_SYM"
    const val HIDE = "HIDE"
    const val SCAN = "SCAN"
    const val AUTO = "AUTO"
    const val CLEAR = "CLEAR"
    const val SPACE_KEY = " "
}

/**
 * Key rows are built from these tables so the composable only renders and
 * forwards taps; the actual commit logic lives in the IME service.
 */
object KeyLayout {

    val qwertyShifted = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
    val qwertyLower = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")

    val homeShifted = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
    val homeLower = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")

    val bottomShifted = listOf("Z", "X", "C", "V", "B", "N", "M")
    val bottomLower = listOf("z", "x", "c", "v", "b", "n", "m")

    /** m-BCA style: 1-9 grid, then a symbol / 0 / decimal footer row. */
    val numGrid = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")
    val numFooter = listOf(",", "0", ".")

    /** Secondary symbol board, mirrors the ABC grid weight. */
    val symRow1 = listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\"")
    val symRow2 = listOf(".", ",", "?", "!", "'", "#", "%", "+", "=")
    val symRow3 = listOf("*", "~", "<", ">", "[", "]", "{", "}")

    /**
     * Long-press alternates. Holding a key pops this list above it; releasing
     * over the first entry commits that character instead of the base one.
     *
     * Mirrors the stock AOSP keyboard layout so muscle memory carries over.
     */
    val longPress: Map<String, List<String>> = mapOf(
        "a" to listOf("à", "á", "â", "ã", "ä", "å", "æ", "@"),
        "c" to listOf("ç", "ć", "č"),
        "e" to listOf("è", "é", "ê", "ë", "ę", "€"),
        "i" to listOf("ì", "í", "î", "ï", "į"),
        "n" to listOf("ñ", "ń"),
        "o" to listOf("ò", "ó", "ô", "õ", "ö", "ø", "œ", "°"),
        "s" to listOf("ś", "š", "ß", "$"),
        "u" to listOf("ù", "ú", "û", "ü", "ų"),
        "y" to listOf("ý", "ÿ", "¥"),
        "z" to listOf("ž", "ź", "ż"),
        "l" to listOf("ł", "£"),
        "d" to listOf("ð", "ď"),
        "g" to listOf("ğ", "ĝ"),
        "t" to listOf("ţ", "ť", "™"),
        "r" to listOf("ř", "®"),
        "q" to listOf("1", "~"),
        "w" to listOf("2", "€"),
        "p" to listOf("þ", "%"),
        "j" to listOf("ł"),
        "k" to listOf("ķ"),
        "x" to listOf("×", "†"),
        "v" to listOf("√"),
        "b" to listOf("β", "þ"),
        "m" to listOf("μ"),
        "." to listOf("…", ".com", ".net", ".org", ".id", ".co"),
        "," to listOf(";", "!", "?"),
        "?" to listOf("¿"),
        "!" to listOf("¡"),
        "'" to listOf("‘", "’", "‚", "‛"),
        "\"" to listOf("“", "”", "„"),
        "-" to listOf("–", "—", "·"),
        "/" to listOf("\\", "|"),
        "$" to listOf("€", "£", "¥", "₿", "₩", "₹"),
        "(" to listOf("["),
        ")" to listOf("]"),
        "@" to listOf("@gmail.com", "@yahoo.com", "@outlook.com"),
        "*" to listOf("×", "•"),
        "0" to listOf("°", "º", "∞"),
        "1" to listOf("¹", "½"),
        "2" to listOf("²", "⅓"),
        "3" to listOf("³", "¼"),
        "4" to listOf("⁴", "¾"),
        "5" to listOf("⁵", "⅕"),
        "6" to listOf("⁶"),
        "7" to listOf("⁷"),
        "8" to listOf("⁸"),
        "9" to listOf("⁹"),
        "%" to listOf("‰", "°")
    )

    /** Alternates for [key], case-insensitive, or an empty list. */
    fun alternates(key: String): List<String> =
        longPress[key.lowercase()] ?: longPress[key] ?: emptyList()
}

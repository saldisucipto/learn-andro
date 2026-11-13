package com.example.nativestarter.ime

/**
 * Lightweight on-device typing brain: dictionary, next-word model, autocorrect
 * and typo tolerance.
 *
 * No ML model, no network, no asset files. Everything is a deterministic
 * structure so it stays fast enough to run on every keystroke and behaves the
 * same on every device.
 *
 * Usage contract:
 *  - [suggest] is called on every cursor change and returns up to 3 candidates
 *  - [shouldAutocorrect] decides whether a word should be silently fixed
 *  - [learn] feeds the engine what the user actually typed
 *  - [nextWord] drives the prediction chip after a commit
 */
class TextEngine {

    /** word -> usage weight. Starts from the seed list, grows as the user types. */
    private val wordWeights = HashMap<String, Int>(4096)

    /** word -> words that statistically follow it. */
    private val transitions = HashMap<String, MutableMap<String, Int>>(2048)

    /** Seeded so suggestions work on first boot, in priority order. */
    private val seedWords = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know", "take",
        "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
        "than", "then", "now", "look", "only", "come", "its", "over", "think", "also",
        "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
        "is", "was", "are", "been", "has", "had", "were", "said", "did", "got",
        "am", "my", "your", "his", "her", "its", "our", "their", "hello", "hi",
        "hey", "thanks", "thank", "please", "sorry", "yes", "yeah", "no", "okay", "ok",
        "sure", "great", "nice", "good", "bad", "love", "hate", "need", "help", "send",
        "call", "text", "email", "message", "phone", "number", "address", "name", "today",
        "tomorrow", "yesterday", "morning", "night", "week", "month", "year", "hour", "minute",
        "where", "there", "here", "every", "never", "always", "again", "still",
        "already", "before", "after", "right", "left", "down", "over", "under",
        "through", "between", "without", "during", "while", "something", "someone",
        "anything", "everything", "nothing", "everyone", "nobody", "maybe", "really",
        "because", "however", "although", "therefore", "instead", "perhaps", "probably",
        "actually", "finally", "usually", "sometimes", "often", "rarely", "quickly",
        "slowly", "carefully", "easily", "together", "around", "across", "along"
    )

    /** word -> index in [seedWords], used only to break scoring ties stably. */
    private val seedIndexByName: Map<String, Int> =
        HashMap<String, Int>(seedWords.size * 2).apply {
            seedWords.forEachIndexed { index, word -> if (!containsKey(word)) put(word, index) }
        }

    /** word -> what usually follows it. Seeded for the most common restart points. */
    private val seedTransitions = mapOf(
        "i" to listOf("am", "have", "will", "can", "want", "need", "think", "know", "like"),
        "you" to listOf("are", "have", "can", "will", "should", "want", "need", "know"),
        "we" to listOf("are", "have", "can", "will", "should", "need", "want"),
        "they" to listOf("are", "have", "can", "will", "should"),
        "he" to listOf("is", "has", "can", "will", "was"),
        "she" to listOf("is", "has", "can", "will", "was"),
        "it" to listOf("is", "was", "has", "will", "can", "does"),
        "the" to listOf("same", "best", "first", "only", "other", "next", "last", "way", "time"),
        "this" to listOf("is", "was", "will", "can", "should", "has"),
        "that" to listOf("is", "was", "will", "can", "should", "has"),
        "what" to listOf("is", "are", "was", "do", "does", "will", "can", "if"),
        "where" to listOf("is", "are", "was", "do", "does", "will", "can"),
        "how" to listOf("are", "is", "do", "does", "much", "many", "long", "can"),
        "please" to listOf("send", "call", "let", "help", "check", "confirm"),
        "thank" to listOf("you", "god"),
        "good" to listOf("morning", "night", "luck", "job", "idea", "day", "evening", "afternoon"),
        "see" to listOf("you", "it", "that", "them", "us"),
        "let" to listOf("me", "us", "them", "him", "her", "it"),
        "can" to listOf("you", "i", "we", "be", "do", "get", "see"),
        "will" to listOf("be", "do", "send", "call", "check", "help"),
        "is" to listOf("it", "that", "this", "the", "there", "not"),
        "are" to listOf("you", "we", "they", "there", "not")
    )

    /** Letters a finger typically mis-hits, used for typo tolerance scoring. */
    private val neighbours = mapOf(
        'a' to "qwszx", 'b' to "vghn", 'c' to "xdfv", 'd' to "swaefc",
        'e' to "wsdr", 'f' to "drgvc", 'g' to "ftyhbv", 'h' to "gyujnb",
        'i' to "ujko", 'j' to "huiknm", 'k' to "jiolm", 'l' to "kmo",
        'm' to "njk", 'n' to "bhjm", 'o' to "iklp", 'p' to "ol",
        'q' to "was", 'r' to "edft", 's' to "awedxz", 't' to "rfgy",
        'u' to "yhji", 'v' to "cfgb", 'w' to "qase", 'x' to "zsdc",
        'y' to "tghu", 'z' to "asx"
    )

    init {
        seedWords.forEach { word ->
            wordWeights[word] = (wordWeights[word] ?: 0) + baseWeight(word)
        }
        seedTransitions.forEach { (from, tos) ->
            val target = transitions.getOrPut(from) { HashMap() }
            tos.forEach { to -> target[to] = (target[to] ?: 0) + 1 }
        }
    }

    private fun baseWeight(word: String): Int = when (word.length) {
        0, 1 -> 14
        2 -> 12
        3 -> 10
        else -> 8
    }

    /**
     * Candidate completions and corrections for [prefix].
     *
     * Returns at most [limit] strings, already capitalised to match the prefix
     * casing. Ordering is fully deterministic:
     *  1. higher weight first
     *  2. shorter completion first (less to type)
     *  3. seed declaration order (stable across runs and process restarts)
     *  4. alphabetical
     */
    fun suggest(prefix: String, limit: Int = 3): List<String> {
        /* Keep the raw prefix: it is the only source of the user's casing intent.
           Lowercasing happens for matching only, never for case restoration. */
        val typed = prefix.trimStart()
        val cleaned = typed.lowercase()
        if (cleaned.isEmpty()) return emptyList()

        val scored = ArrayList<Pair<String, Int>>(64)

        for ((word, weight) in wordWeights) {
            if (word == cleaned) continue
            if (word.startsWith(cleaned)) {
                /* Prefix match — preferred, distance 0. */
                scored.add(word to weight * 10 - (word.length - cleaned.length))
            } else if (cleaned.length >= 3) {
                /*
                 * Typo tolerance, graded so the most likely mistake ranks first:
                 *   neighbour-key substitution (3) > transposition (2)
                 *   > length mismatch, i.e. a dropped/extra letter (1)
                 * Ranking a dropped letter above a wrong finger produced
                 * "yhe" -> "he" instead of "the".
                 */
                when (typoScore(word, cleaned)) {
                    TYPO_SUBSTITUTION -> scored.add(word to weight * 7)
                    TYPO_TRANSPOSITION -> scored.add(word to weight * 5)
                    TYPO_LENGTH -> scored.add(word to weight * 3)
                }
            }
        }

        if (scored.isEmpty()) return emptyList()

        return scored
            .sortedWith(
                compareByDescending<Pair<String, Int>> { it.second }
                    .thenBy { it.first.length }
                    .thenBy { seedIndex(it.first) }
                    .thenBy { it.first }
            )
            .take(limit)
            .map { matchCase(typed, it.first) }
    }

    /** Position of [word] in the seed list, or [Int.MAX_VALUE] when learned later. */
    private fun seedIndex(word: String): Int = seedIndexByName[word] ?: Int.MAX_VALUE

    /**
     * True when the word behind the cursor is likely mistyped and should be
     * replaced in place. Deliberately conservative: only fires on a stronger
     * candidate, never on short words, never on an exact dictionary hit.
     */
    fun shouldAutocorrect(word: String, candidates: List<String>): String? {
        val cleaned = word.trim().lowercase()
        if (cleaned.length < 3) return null
        if (wordWeights.containsKey(cleaned)) return null
        val best = candidates.firstOrNull() ?: return null
        val bestLower = best.lowercase()
        if (bestLower == cleaned) return null
        if (!withinOneEdit(bestLower, cleaned) && !bestLower.startsWith(cleaned)) return null
        return best
    }

    /**
     * Top prediction after committing [word], or null when nothing fits.
     *
     * Ties are broken toward the order the seed list declares, then
     * alphabetically, so the same input always yields the same prediction.
     * Relying on [HashMap] iteration order here produced unstable results.
     */
    fun nextWord(word: String): String? {
        val cleaned = word.trim().lowercase().trimEnd('.', ',', '!', '?')
        if (cleaned.isEmpty()) return null
        val options = transitions[cleaned] ?: return null
        if (options.isEmpty()) return null

        val seed = seedTransitions[cleaned]

        return options.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { entry ->
                        val index = seed?.indexOf(entry.key)?.takeIf { it >= 0 }
                        index ?: Int.MAX_VALUE
                    }
                    .thenBy { it.key }
            )
            .firstOrNull()
            ?.key
    }

    /** Records what the user committed, so suggestions adapt to their habits. */
    fun learn(word: String, previous: String?) {
        val cleaned = word.trim().lowercase().trimEnd('.', ',', '!', '?', '"', '\'')
        if (cleaned.length < 2) return
        if (cleaned.any { !it.isLetter() && it != '\'' }) return

        wordWeights[cleaned] = (wordWeights[cleaned] ?: 0) + 6

        if (previous != null) {
            val prev = previous.trim().lowercase()
            if (prev.isNotEmpty() && prev.any { it.isLetter() }) {
                val target = transitions.getOrPut(prev) { HashMap() }
                target[cleaned] = (target[cleaned] ?: 0) + 3
            }
        }
    }

    /** Dictionary hit check, used to decide whether to underline a word. */
    fun isKnown(word: String): Boolean =
        wordWeights.containsKey(word.trim().lowercase())

    private fun matchCase(typed: String, candidate: String): String = when {
        typed.isEmpty() -> candidate
        typed.all { it.isUpperCase() } -> candidate.uppercase()
        typed.first().isUpperCase() -> candidate.replaceFirstChar { it.uppercase() }
        else -> candidate
    }

    /**
     * Classifies a one-edit difference between [a] and [b].
     *
     * Returns [TYPO_NONE] when the pair is not a plausible single-finger error,
     * otherwise the most likely mistake class. Adjacent-key substitution beats
     * transposition, which beats a dropped or extra letter — a finger landing one
     * key over is the single most common typing error.
     */
    private fun typoScore(a: String, b: String): Int {
        if (a == b) return TYPO_NONE
        val diff = a.length - b.length
        if (diff > 1 || diff < -1) return TYPO_NONE

        if (a.length == b.length) {
            val mismatches = a.indices.filter { a[it] != b[it] }
            return when {
                mismatches.size == 1 -> {
                    val at = mismatches[0]
                    if (isAdjacent(a[at], b[at])) TYPO_SUBSTITUTION else TYPO_LENGTH
                }
                mismatches.size == 2 && isTransposition(a, b) -> TYPO_TRANSPOSITION
                else -> TYPO_NONE
            }
        }

        /* Length differs by exactly one: a single insertion or deletion. */
        val longer = if (a.length > b.length) a else b
        val shorter = if (a.length > b.length) b else a
        var i = 0
        var j = 0
        var skipped = false
        while (i < longer.length && j < shorter.length) {
            if (longer[i] == shorter[j]) {
                i++; j++
            } else {
                if (skipped) return TYPO_NONE
                skipped = true
                i++
            }
        }
        return TYPO_LENGTH
    }

    /** True when [a] and [b] are one plausible single-finger edit apart. */
    private fun withinOneEdit(a: String, b: String): Boolean = typoScore(a, b) != TYPO_NONE

    private fun isTransposition(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        val diffs = a.indices.filter { a[it] != b[it] }
        if (diffs.size != 2) return false
        val (x, y) = diffs
        return y == x + 1 && a[x] == b[y] && a[y] == b[x]
    }

    private fun isAdjacent(a: Char, b: Char): Boolean {
        val lower = b.lowercaseChar()
        val pool = neighbours[a.lowercaseChar()] ?: return false
        return pool.contains(lower)
    }

    private companion object {
        /** Not a plausible single-finger typo. */
        const val TYPO_NONE = 0

        /** A dropped or extra letter. */
        const val TYPO_LENGTH = 1

        /** Two adjacent characters swapped. */
        const val TYPO_TRANSPOSITION = 2

        /** A finger landed on a neighbouring key. Most likely of all. */
        const val TYPO_SUBSTITUTION = 3
    }
}

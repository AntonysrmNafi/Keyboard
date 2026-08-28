package com.privacykeyboard.app

// Simplified Avro-style phonetic engine. Converts a buffered English key sequence
// into Bangla Unicode text. Covers common consonants, vowels and matra signs.
// Type a comma inside a word to force an explicit hasant (conjunct) between two consonants.
// This is a simplified rule set, not a full replica of every Avro grammar rule,
// so a few uncommon conjuncts may still need the manual comma.
object PhoneticEngine {

    private const val HASANT = "্"
    private val BANGLA_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    // Triple: typed pattern, standalone vowel letter, matra (vowel sign) form
    private val vowelRules = listOf(
        Triple("OU", "ঔ", "ৌ"),
        Triple("OI", "ঐ", "ৈ"),
        Triple("rri", "ঋ", "ৃ"),
        Triple("O", "ও", "ো"),
        Triple("I", "ঈ", "ী"),
        Triple("U", "ঊ", "ূ"),
        Triple("A", "আ", "া"),
        Triple("i", "ই", "ি"),
        Triple("u", "উ", "ু"),
        Triple("e", "এ", "ে"),
        Triple("a", "আ", "া"),
        Triple("o", "অ", "")
    ).sortedByDescending { it.first.length }

    // Pair: typed pattern, base consonant letter
    private val consonantRules = listOf(
        "kh" to "খ", "k" to "ক", "gh" to "ঘ", "g" to "গ", "Ng" to "ঙ", "ng" to "ং",
        "Ch" to "ছ", "ch" to "চ", "jh" to "ঝ", "j" to "জ",
        "Th" to "ঠ", "T" to "ট", "Dh" to "ঢ", "D" to "ড", "N" to "ণ",
        "th" to "থ", "t" to "ত", "dh" to "ধ", "d" to "দ", "n" to "ন",
        "ph" to "ফ", "f" to "ফ", "p" to "প",
        "bh" to "ভ", "b" to "ব", "m" to "ম",
        "z" to "য", "Z" to "য", "R" to "ড়", "r" to "র", "l" to "ল",
        "sh" to "শ", "S" to "ষ", "s" to "স", "h" to "হ",
        "y" to "য়", "v" to "ভ", "x" to "ক্স", "w" to "ও"
    ).sortedByDescending { it.first.length }

    fun transliterate(input: String): String {
        if (input.isEmpty()) return ""
        val output = StringBuilder()
        var i = 0
        var prevWasConsonant = false

        while (i < input.length) {
            if (input[i] == ',' && prevWasConsonant) {
                output.append(HASANT)
                i++
                continue
            }

            val vowelMatch = vowelRules.firstOrNull { input.startsWith(it.first, i) }
            if (vowelMatch != null) {
                output.append(if (prevWasConsonant) vowelMatch.third else vowelMatch.second)
                i += vowelMatch.first.length
                prevWasConsonant = false
                continue
            }

            val consMatch = consonantRules.firstOrNull { input.startsWith(it.first, i) }
            if (consMatch != null) {
                output.append(consMatch.second)
                i += consMatch.first.length
                prevWasConsonant = true
                continue
            }

            val c = input[i]
            if (c in '0'..'9') {
                output.append(BANGLA_DIGITS[c - '0'])
            } else {
                output.append(c)
            }
            i++
            prevWasConsonant = false
        }
        return output.toString()
    }
}

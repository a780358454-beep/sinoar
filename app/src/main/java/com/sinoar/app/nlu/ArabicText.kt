package com.sinoar.app.nlu

/** Arabic text normalisation used before any rule-based understanding. */
object ArabicText {

    private val DIACRITICS = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
    private val SPACES = Regex("\\s+")

    fun normalize(input: String): String {
        var s = DIACRITICS.replace(input, "")
        s = s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        s = s.replace('ى', 'ي').replace('ة', 'ه').replace('ـ', '')
        s = s.replace('،', ' ').replace('؟', ' ').replace('!', ' ').replace('.', ' ')

        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                when (ch) {
                    in '\u0660'..'\u0669' -> ('0' + (ch - '\u0660'))
                    in '\u06F0'..'\u06F9' -> ('0' + (ch - '\u06F0'))
                    else -> ch
                }
            )
        }
        return SPACES.replace(sb.toString().lowercase(), " ").trim()
    }

    /** Removes common command wrappers like "يا سنوار". */
    fun stripWake(text: String): String =
        text.replace(Regex("^(يا )?سنوار[ ،]*"), "").trim()
}

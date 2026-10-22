package com.sinoar.app.nlu

import java.util.Calendar

sealed class Intent {
    data class Call(val target: String) : Intent()
    data class Alarm(val hour: Int, val minute: Int, val label: String) : Intent()
    data class Play(val query: String, val video: Boolean) : Intent()
    data class Search(val query: String) : Intent()
    object Unknown : Intent()
}

/**
 * Deterministic, rule-based Arabic command parser.
 *
 * Runs entirely on device and never guesses: ambiguous input becomes Unknown,
 * which the UI surfaces instead of executing something wrong.
 */
object IntentParser {

    private val CALL = Regex("(?:اتصل|كلم|رن|هاتف)\\s*(?:ب|علي|على|ل)?\\s*(.+)")
    private val ALARM = Regex("(?:نبهني|ذكرني|منبه|تذكير|اضبط منبه)\\s*(.*)")
    private val PLAY = Regex("(?:شغل|ابحث عن|افتح|شغلي)\\s*(.+)")
    private val AFTER_MIN = Regex("بعد\\s+(\\d+)\\s*(?:دقيقه|دقايق|دقائق)")
    private val AFTER_HOUR = Regex("بعد\\s+(\\d+)\\s*(?:ساعه|ساعات)")
    private val AT_TIME = Regex("(?:الساعه|الساعة)\\s+(\\d{1,2})(?::(\\d{1,2}))?")

    private val NUMBER_WORDS = mapOf(
        "واحد" to 1, "اثنين" to 2, "ثلاثه" to 3, "اربعه" to 4, "خمسه" to 5,
        "سته" to 6, "سبعه" to 7, "ثمانيه" to 8, "تسعه" to 9, "عشره" to 10,
        "احدعشر" to 11, "اثناعشر" to 12,
    )

    private val STOP = setOf("لي", "عن", "ملف", "صوت", "صوتي", "تسجيل", "اغنيه", "فيديو", "من")

    fun parse(raw: String, now: Calendar = Calendar.getInstance()): Intent {
        val text = ArabicText.stripWake(ArabicText.normalize(raw))
        if (text.isBlank()) return Intent.Unknown

        CALL.find(text)?.let { match ->
            val target = match.groupValues[1].trim().trimStart('ب').trim()
            if (target.isNotBlank()) return Intent.Call(target)
        }

        if (ALARM.containsMatchIn(text) || text.contains("منبه")) {
            parseAlarm(text, now)?.let { return it }
            return Intent.Unknown
        }

        PLAY.find(text)?.let { match ->
            val query = cleanQuery(match.groupValues[1])
            if (query.isNotBlank()) {
                return Intent.Play(query, video = text.contains("فيديو"))
            }
        }

        if (text.contains("ابحث")) {
            val query = cleanQuery(text.substringAfter("ابحث"))
            if (query.isNotBlank()) return Intent.Search(query)
        }

        return Intent.Unknown
    }

    private fun parseAlarm(text: String, now: Calendar): Intent.Alarm? {
        AFTER_MIN.find(text)?.let {
            val minutes = it.groupValues[1].toIntOrNull() ?: return null
            val target = (now.clone() as Calendar).apply { add(Calendar.MINUTE, minutes) }
            return Intent.Alarm(target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE), "تذكير")
        }

        AFTER_HOUR.find(text)?.let {
            val hours = it.groupValues[1].toIntOrNull() ?: return null
            val target = (now.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, hours) }
            return Intent.Alarm(target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE), "تذكير")
        }

        if (text.contains("نص ساعه")) {
            val target = (now.clone() as Calendar).apply { add(Calendar.MINUTE, 30) }
            return Intent.Alarm(target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE), "تذكير")
        }

        val match = AT_TIME.find(text)
        if (match != null) {
            val hour = match.groupValues[1].toIntOrNull()
            val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            if (hour != null && hour in 0..23 && minute in 0..59) {
                return Intent.Alarm(hour, minute, "منبه")
            }
        }

        for ((word, value) in NUMBER_WORDS) {
            if (text.contains(word)) {
                return Intent.Alarm(value, 0, "منبه")
            }
        }
        return null
    }

    private fun cleanQuery(raw: String): String =
        raw.split(" ").filter { it.isNotBlank() && it !in STOP }.joinToString(" ").trim()
}

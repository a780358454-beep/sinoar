package com.sinoar.app.actions

import android.content.Context
import com.sinoar.app.nlu.Intent
import java.util.Locale

sealed class PlannedAction {
    abstract val title: String
    abstract val detail: String

    data class Call(val contact: Contact) : PlannedAction() {
        override val title: String = "اتصال"
        override val detail: String = "${contact.name} — ${contact.number}"
    }

    data class Alarm(val hour: Int, val minute: Int, val label: String) : PlannedAction() {
        override val title: String = "منبه"
        override val detail: String = String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    data class Play(val item: MediaItem) : PlannedAction() {
        override val title: String = "تشغيل"
        override val detail: String = item.title
    }
}

/** Turns a parsed intent into a concrete, confirmable action. */
object Planner {

    fun plan(context: Context, intent: Intent): Pair<PlannedAction?, String?> = when (intent) {
        is Intent.Call -> {
            val contact = ContactResolver.find(context, intent.target)
            if (contact == null) {
                null to "لم أجد جهة اتصال باسم «${intent.target}»"
            } else {
                PlannedAction.Call(contact) to null
            }
        }

        is Intent.Alarm -> PlannedAction.Alarm(intent.hour, intent.minute, intent.label) to null

        is Intent.Play -> {
            val items = MediaFinder.search(context, intent.query)
            if (items.isEmpty()) {
                null to "لم أجد ملفًا يطابق «${intent.query}»"
            } else {
                PlannedAction.Play(items.first()) to null
            }
        }

        is Intent.Search -> {
            val items = MediaFinder.search(context, intent.query)
            if (items.isEmpty()) {
                null to "لم أجد نتائج لـ «${intent.query}»"
            } else {
                PlannedAction.Play(items.first()) to null
            }
        }

        Intent.Unknown -> null to "لم أفهم الأمر — جرّب: «اتصل بأحمد»، «ذكرني بعد 10 دقائق»، «شغل تسجيل المحاضرة»"
    }
}

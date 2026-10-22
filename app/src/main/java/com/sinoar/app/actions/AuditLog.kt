package com.sinoar.app.actions

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Append-only audit trail. Each line carries the hash of the previous line,
 * so silently editing or deleting an entry breaks the chain.
 */
object AuditLog {

    fun append(context: Context, action: String, detail: String) {
        runCatching {
            val file = File(context.filesDir, "audit.jsonl")
            val previous = file.takeIf { it.exists() }?.readLines()?.lastOrNull()
            val prevHash = previous?.let { sha256(it) } ?: "genesis"
            val entry = JSONObject()
                .put("ts", System.currentTimeMillis())
                .put("action", action)
                .put("detail", detail)
                .put("prev", prevHash)
            file.appendText(entry.toString() + "\n")
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}

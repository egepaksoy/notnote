package com.notnote.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Notları JSON olarak dışa / içe aktarır (Menü → Yedekle / Geri yükle). */
object Backup {

    fun toJson(notes: List<Note>): String {
        val arr = JSONArray()
        notes.forEach { n ->
            arr.put(JSONObject().apply {
                put("text", n.text)
                put("createdAt", n.createdAt)
                put("done", n.done)
                put("pinned", n.pinned)
                put("isCommand", n.isCommand)
                put("status", n.status.name)
                n.detail?.let { put("detail", it) }
                n.targetName?.let { put("targetName", it) }
                n.targetNumber?.let { put("targetNumber", it) }
            })
        }
        return JSONObject().put("app", "notnote").put("version", 1).put("notes", arr).toString(2)
    }

    fun fromJson(json: String): List<Note> {
        val arr = JSONObject(json).getJSONArray("notes")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val status = runCatching { NoteStatus.valueOf(o.optString("status", "NONE")) }
                .getOrDefault(NoteStatus.NONE)
            Note(
                text = o.getString("text"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                done = o.optBoolean("done", false),
                pinned = o.optBoolean("pinned", false),
                isCommand = o.optBoolean("isCommand", false),
                // Planlanmış işler yedekten geri gelince tekrar çalışmasın.
                status = if (status == NoteStatus.SCHEDULED || status == NoteStatus.RUNNING) NoteStatus.CANCELLED else status,
                detail = o.optString("detail").ifEmpty { null },
                targetName = o.optString("targetName").ifEmpty { null },
                targetNumber = o.optString("targetNumber").ifEmpty { null },
            )
        }
    }
}

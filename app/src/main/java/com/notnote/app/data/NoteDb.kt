package com.notnote.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class NoteDb(context: Context) : SQLiteOpenHelper(context, "notnote.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                done INTEGER NOT NULL DEFAULT 0,
                is_command INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'NONE',
                detail TEXT,
                scheduled_at INTEGER,
                target_name TEXT,
                target_number TEXT,
                pinned INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
    }

    fun all(): List<Note> =
        readableDatabase.query("notes", null, null, null, null, null, "pinned DESC, created_at DESC, id DESC")
            .use { c -> buildList { while (c.moveToNext()) add(c.toNote()) } }

    fun get(id: Long): Note? =
        readableDatabase.query("notes", null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { c -> if (c.moveToFirst()) c.toNote() else null }

    fun insert(note: Note): Long =
        writableDatabase.insertOrThrow("notes", null, note.toValues(includeId = note.id != 0L))

    fun update(note: Note) {
        writableDatabase.update("notes", note.toValues(includeId = false), "id = ?", arrayOf(note.id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete("notes", "id = ?", arrayOf(id.toString()))
    }

    private fun Note.toValues(includeId: Boolean) = ContentValues().apply {
        if (includeId) put("id", id)
        put("text", text)
        put("created_at", createdAt)
        put("done", if (done) 1 else 0)
        put("pinned", if (pinned) 1 else 0)
        put("is_command", if (isCommand) 1 else 0)
        put("status", status.name)
        put("detail", detail)
        put("scheduled_at", scheduledAt)
        put("target_name", targetName)
        put("target_number", targetNumber)
    }

    private fun Cursor.toNote(): Note {
        fun str(col: String) = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getString(it) }
        fun long(col: String) = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getLong(it) }
        return Note(
            id = long("id")!!,
            text = str("text").orEmpty(),
            createdAt = long("created_at") ?: 0L,
            done = long("done") == 1L,
            pinned = long("pinned") == 1L,
            isCommand = long("is_command") == 1L,
            status = runCatching { NoteStatus.valueOf(str("status") ?: "NONE") }.getOrDefault(NoteStatus.NONE),
            detail = str("detail"),
            scheduledAt = long("scheduled_at"),
            targetName = str("target_name"),
            targetNumber = str("target_number"),
        )
    }
}

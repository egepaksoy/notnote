package com.notnote.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notların tek kaynağı. Veri küçük olduğu için işlemler senkron yapılır;
 * her değişiklikten sonra liste yeniden okunur ve arayüz kendini günceller.
 */
class NoteRepository(private val db: NoteDb) {

    private val lock = Any()
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    init {
        // Uygulama kapanırken yarım kalan komutları "hata" olarak işaretle.
        db.all().filter { it.status == NoteStatus.RUNNING }.forEach {
            db.update(it.copy(status = NoteStatus.FAILED, detail = "yarım kaldı"))
        }
        reload()
    }

    private fun reload() {
        _notes.value = db.all()
    }

    fun get(id: Long): Note? = synchronized(lock) { db.get(id) }

    fun add(note: Note): Note = synchronized(lock) {
        val id = db.insert(note)
        reload()
        note.copy(id = id)
    }

    fun update(note: Note) = synchronized(lock) {
        db.update(note)
        reload()
    }

    fun delete(id: Long) = synchronized(lock) {
        db.delete(id)
        reload()
    }

    fun setStatus(id: Long, status: NoteStatus, detail: String? = null) = synchronized(lock) {
        val note = db.get(id) ?: return
        db.update(note.copy(status = status, detail = detail))
        reload()
    }

    /** Yedekten gelen notları ekler; aynı metin ve tarihe sahip olanları atlar. */
    fun merge(incoming: List<Note>): Int = synchronized(lock) {
        val existing = db.all().map { it.createdAt to it.text }.toHashSet()
        var added = 0
        incoming.filter { (it.createdAt to it.text) !in existing }.forEach {
            db.insert(it.copy(id = 0))
            added++
        }
        reload()
        added
    }
}

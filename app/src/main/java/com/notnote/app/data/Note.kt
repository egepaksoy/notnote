package com.notnote.app.data

enum class NoteStatus { NONE, RUNNING, DONE, FAILED, SCHEDULED, CANCELLED }

data class Note(
    val id: Long = 0,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val done: Boolean = false,
    val pinned: Boolean = false,
    val isCommand: Boolean = false,
    val status: NoteStatus = NoteStatus.NONE,
    val detail: String? = null,
    val scheduledAt: Long? = null,
    /** Komutun hedefi (rehberdeki kişi adı / numara); planlanmış gönderimlerde tekrar kullanılır. */
    val targetName: String? = null,
    val targetNumber: String? = null,
)

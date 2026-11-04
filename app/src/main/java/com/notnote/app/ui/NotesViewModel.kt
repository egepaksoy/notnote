package com.notnote.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notnote.app.NotnoteApp
import com.notnote.app.automation.Scheduler
import com.notnote.app.command.AppEntry
import com.notnote.app.command.Apps
import com.notnote.app.command.CommandParser
import com.notnote.app.command.CommandType
import com.notnote.app.command.Contact
import com.notnote.app.command.Contacts
import com.notnote.app.command.ExecResult
import com.notnote.app.command.ParseResult
import com.notnote.app.command.ParsedCommand
import com.notnote.app.command.TargetKind
import com.notnote.app.command.matchRank
import com.notnote.app.command.normalize
import com.notnote.app.data.Backup
import com.notnote.app.data.Note
import com.notnote.app.data.NoteStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Suggestion(
    val title: String,
    val subtitle: String? = null,
    val newText: String = "",
    val opensPermissions: Boolean = false,
)

enum class Screen { Notes, Permissions, Help }

data class ContactChoice(val noteId: Long, val command: ParsedCommand, val options: List<Contact>)

sealed interface UiEvent {
    data class Message(val text: String, val undo: Note? = null) : UiEvent
    data object OpenPermissions : UiEvent
}

class NotesViewModel(app: Application) : AndroidViewModel(app) {

    private val notnote = app as NotnoteApp
    private val repo = notnote.repository
    private val executor = notnote.executor

    val notes: StateFlow<List<Note>> = repo.notes

    var input by mutableStateOf(TextFieldValue(""))
        private set
    var suggestions by mutableStateOf<List<Suggestion>>(emptyList())
        private set
    var choice by mutableStateOf<ContactChoice?>(null)
        private set
    var focusRequests by mutableIntStateOf(0)
        private set

    /** Açık olan ekran (dışarıdan gelen istekler ana ekrana döndürebilsin diye burada). */
    var screen by mutableStateOf(Screen.Notes)

    /** Widget'tan dokunulan not: ana ekran bunu düzenleme penceresinde açar. */
    var openNoteId by mutableStateOf<Long?>(null)

    /** null = arama kapalı. */
    var searchQuery by mutableStateOf<String?>(null)

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var contacts: List<Contact>? = null
    private var apps: List<AppEntry>? = null
    private var loading = false

    // --- Yazı kutusu -------------------------------------------------------

    fun onInputChange(value: TextFieldValue) {
        // Enter = kaydet (bazı klavyeler "Gönder" yerine satır sonu yollar).
        if (value.text.contains('\n')) {
            setInput(value.text.replace("\n", " ").trim())
            submit()
            return
        }
        input = value
        updateSuggestions()
    }

    fun onResume() {
        // İzinler / kurulu uygulamalar değişmiş olabilir.
        contacts = null
        apps = null
        updateSuggestions()
    }

    fun requestFocus() {
        screen = Screen.Notes
        focusRequests++
    }

    fun openNote(id: Long) {
        screen = Screen.Notes
        searchQuery = null
        openNoteId = id
    }

    /** Başka bir uygulamadan "Paylaş → notnote" ile gelen metin. */
    fun addShared(text: String) {
        if (text.isBlank()) return
        screen = Screen.Notes
        repo.add(Note(text = text))
        message("Not eklendi")
    }

    /** Sesle yazma sonucu: yazı kutusuna eklenir, istersen düzeltip gönderirsin. */
    fun onVoiceResult(text: String) {
        if (text.isBlank()) return
        val current = input.text.trimEnd()
        setInput(if (current.isEmpty()) text else "$current $text")
        requestFocus()
    }

    fun togglePin(note: Note) = repo.update(note.copy(pinned = !note.pinned))

    fun applySuggestion(s: Suggestion) {
        if (s.opensPermissions) {
            _events.tryEmit(UiEvent.OpenPermissions)
            return
        }
        setInput(s.newText)
    }

    private fun setInput(text: String) {
        input = TextFieldValue(text, TextRange(text.length))
        updateSuggestions()
    }

    fun submit() {
        val text = input.text.trim()
        if (text.isEmpty()) return
        when (val r = CommandParser.parse(text)) {
            ParseResult.NotCommand -> {
                repo.add(Note(text = text))
                setInput("")
            }
            is ParseResult.Error -> message(r.message)
            is ParseResult.Ok -> {
                setInput("")
                val note = repo.add(Note(text = text, isCommand = true, status = NoteStatus.RUNNING))
                handle(note.id, r.command, executor.execute(note.id, r.command))
            }
        }
    }

    private fun handle(noteId: Long, cmd: ParsedCommand, result: ExecResult) {
        when (result) {
            is ExecResult.NeedsChoice -> choice = ContactChoice(noteId, cmd, result.options)
            is ExecResult.Failed -> message(result.detail)
            else -> Unit
        }
    }

    fun choose(contact: Contact) {
        val c = choice ?: return
        choice = null
        handle(c.noteId, c.command, executor.execute(c.noteId, c.command, contact))
    }

    fun dismissChoice() {
        val c = choice ?: return
        choice = null
        repo.setStatus(c.noteId, NoteStatus.CANCELLED, "kişi seçilmedi")
    }

    // --- Not işlemleri -----------------------------------------------------

    fun edit(note: Note, newText: String) {
        val t = newText.trim()
        if (t.isEmpty()) delete(note) else repo.update(note.copy(text = t))
    }

    fun delete(note: Note) {
        if (note.status == NoteStatus.SCHEDULED) Scheduler.cancel(notnote, note.id)
        repo.delete(note.id)
        _events.tryEmit(UiEvent.Message("Not silindi", undo = note))
    }

    fun undoDelete(note: Note) {
        repo.add(note)
        val at = note.scheduledAt
        if (note.status == NoteStatus.SCHEDULED && at != null && at > System.currentTimeMillis()) {
            Scheduler.schedule(notnote, note.id, at)
        }
    }

    fun cancelScheduled(note: Note) {
        Scheduler.cancel(notnote, note.id)
        repo.setStatus(note.id, NoteStatus.CANCELLED, "iptal edildi")
    }

    /** Komut notunu yazı kutusuna geri koyar: düzenleyip tekrar gönderilebilir. */
    fun reuse(note: Note) {
        setInput(note.text)
        requestFocus()
    }

    /** Bildirimden gelen "dokun, gönder" isteği. */
    fun runStoredNote(id: Long) {
        val note = repo.get(id) ?: return
        if (note.status == NoteStatus.RUNNING || note.status == NoteStatus.SCHEDULED) {
            Scheduler.cancel(notnote, id)
            executor.runStored(note, fromBackground = false)
        }
    }

    // --- Yedek -------------------------------------------------------------

    fun exportJson(): String = Backup.toJson(repo.notes.value)

    fun importJson(json: String) {
        val added = runCatching { repo.merge(Backup.fromJson(json)) }
        message(added.fold({ "$it not geri yüklendi" }, { "Yedek dosyası okunamadı" }))
    }

    fun message(text: String) {
        _events.tryEmit(UiEvent.Message(text))
    }

    // --- Öneriler ----------------------------------------------------------

    private fun updateSuggestions() {
        suggestions = computeSuggestions(input.text)
    }

    private fun computeSuggestions(text: String): List<Suggestion> {
        if (!text.startsWith("@")) return emptyList()
        if (text.none { it.isWhitespace() }) {
            val typed = normalize(text.drop(1))
            return CommandType.entries
                .filter { t -> t.keys.any { normalize(it).startsWith(typed) } }
                .map { Suggestion("@${it.primaryKey}", it.label, "@${it.primaryKey} ") }
        }

        val (key, rest) = CommandParser.splitHead(text) ?: return emptyList()
        val type = CommandType.find(key) ?: return emptyList()

        return when (type.target) {
            TargetKind.CONTACT -> {
                var prefix = ""
                var query = rest
                if (type.acceptsTime) {
                    val (time, after) = CommandParser.splitTime(rest)
                    if (time != null) {
                        prefix = rest.substringBefore(' ') + " "
                        query = after
                    }
                }
                if (query.isBlank() || query.contains(':')) return emptyList()
                if (!Contacts.hasPermission(notnote)) {
                    return listOf(Suggestion("Rehber izni ver", "kişileri önermek için", opensPermissions = true))
                }
                val all = contacts ?: return emptyList<Suggestion>().also { loadLists() }
                all.mapNotNull { c -> matchRank(c.name, query)?.let { it to c } }
                    .sortedWith(compareBy({ it.first }, { normalize(it.second.name) }))
                    .take(8)
                    .map { (_, c) ->
                        val newText = if (type == CommandType.CALL) "@$key ${c.name}" else "@$key $prefix${c.name}: "
                        Suggestion(c.name, c.number, newText)
                    }
            }
            TargetKind.APP -> {
                if (rest.isBlank()) return emptyList()
                val all = apps ?: return emptyList<Suggestion>().also { loadLists() }
                all.mapNotNull { a -> matchRank(a.label, rest)?.let { it to a } }
                    .sortedWith(compareBy({ it.first }, { it.second.label.length }))
                    .take(8)
                    .map { (_, a) -> Suggestion(a.label, null, "@$key ${a.label}") }
            }
            TargetKind.NONE -> emptyList()
        }
    }

    private fun loadLists() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            val (c, a) = withContext(Dispatchers.IO) { Contacts.load(notnote) to Apps.load(notnote) }
            contacts = c
            apps = a
            loading = false
            updateSuggestions()
        }
    }
}

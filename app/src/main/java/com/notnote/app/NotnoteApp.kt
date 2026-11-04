package com.notnote.app

import android.app.Application
import com.notnote.app.automation.Notifications
import com.notnote.app.command.CommandExecutor
import com.notnote.app.data.NoteDb
import com.notnote.app.data.NoteRepository
import com.notnote.app.widget.NotesWidget
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class NotnoteApp : Application() {

    lateinit var repository: NoteRepository
        private set
    lateinit var executor: CommandExecutor
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = NoteRepository(NoteDb(this))
        executor = CommandExecutor(this, repository)
        Notifications.createChannels(this)
        // Notlar her değiştiğinde ana ekran widget'ını yenile.
        MainScope().launch {
            repository.notes.collect { NotesWidget.updateAll(this@NotnoteApp, it) }
        }
    }

    companion object {
        lateinit var instance: NotnoteApp
            private set
    }
}

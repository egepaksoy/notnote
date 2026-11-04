package com.notnote.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.notnote.app.ui.HelpScreen
import com.notnote.app.ui.NotesScreen
import com.notnote.app.ui.NotesViewModel
import com.notnote.app.ui.NotnoteTheme
import com.notnote.app.ui.PermissionsScreen
import com.notnote.app.ui.Screen

class MainActivity : ComponentActivity() {

    private val vm: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            NotnoteTheme {
                BackHandler(enabled = vm.screen != Screen.Notes) { vm.screen = Screen.Notes }
                LifecycleResumeEffect(Unit) {
                    vm.onResume()
                    onPauseOrDispose { }
                }
                when (vm.screen) {
                    Screen.Notes -> NotesScreen(
                        vm = vm,
                        onOpenPermissions = { vm.screen = Screen.Permissions },
                        onOpenHelp = { vm.screen = Screen.Help },
                    )
                    Screen.Permissions -> PermissionsScreen(onBack = { vm.screen = Screen.Notes })
                    Screen.Help -> HelpScreen(onBack = { vm.screen = Screen.Notes })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.action == Intent.ACTION_SEND) {
            val text = listOfNotNull(
                intent.getStringExtra(Intent.EXTRA_SUBJECT),
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            ).joinToString(" — ").trim()
            intent.action = null
            vm.addShared(text)
        }
        if (intent.getBooleanExtra(EXTRA_FOCUS_INPUT, false)) vm.requestFocus()
        val openId = intent.getLongExtra(EXTRA_OPEN_NOTE, -1L)
        if (openId > 0) {
            intent.removeExtra(EXTRA_OPEN_NOTE)
            vm.openNote(openId)
        }
        val runId = intent.getLongExtra(EXTRA_RUN_NOTE, -1L)
        if (runId > 0) {
            intent.removeExtra(EXTRA_RUN_NOTE)
            vm.runStoredNote(runId)
        }
    }

    companion object {
        const val EXTRA_FOCUS_INPUT = "focus_input"
        const val EXTRA_RUN_NOTE = "run_note"
        const val EXTRA_OPEN_NOTE = "open_note"
    }
}

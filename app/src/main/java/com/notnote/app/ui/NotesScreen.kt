package com.notnote.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notnote.app.BuildConfig
import com.notnote.app.command.CommandParser
import com.notnote.app.command.ParseResult
import com.notnote.app.command.normalize
import com.notnote.app.data.Note
import com.notnote.app.data.NoteStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun NotesScreen(
    vm: NotesViewModel,
    onOpenPermissions: () -> Unit,
    onOpenHelp: () -> Unit,
) {
    val ctx = LocalContext.current
    val notes by vm.notes.collectAsState()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Note?>(null) }
    val listState = rememberLazyListState()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(vm.exportJson().toByteArray()) }
        }.isSuccess
        vm.message(if (ok) "Yedek kaydedildi" else "Yedek kaydedilemedi")
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
            .onSuccess { vm.importJson(it) }
            .onFailure { vm.message("Dosya okunamadı") }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is UiEvent.OpenPermissions -> onOpenPermissions()
                is UiEvent.Message -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val r = snackbar.showSnackbar(
                        message = e.text,
                        actionLabel = if (e.undo != null) "Geri al" else null,
                        duration = SnackbarDuration.Short,
                    )
                    if (r == SnackbarResult.ActionPerformed && e.undo != null) vm.undoDelete(e.undo)
                }
            }
        }
    }

    // Yeni not eklenince listenin başına kaydır.
    LaunchedEffect(notes.maxOfOrNull { it.id }) { if (notes.isNotEmpty()) listState.animateScrollToItem(0) }

    // Widget'tan dokunulan notu aç.
    LaunchedEffect(vm.openNoteId, notes) {
        val id = vm.openNoteId ?: return@LaunchedEffect
        notes.firstOrNull { it.id == id }?.let { editing = it }
        vm.openNoteId = null
    }

    BackHandler(enabled = vm.searchQuery != null) { vm.searchQuery = null }

    val query = vm.searchQuery.orEmpty()
    val shown = if (query.isBlank()) notes else notes.filter { normalize(it.text).contains(normalize(query)) }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(vm::onVoiceResult)
    }
    val startVoice = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Konuş, not alayım")
        runCatching { speech.launch(intent) }.onFailure { vm.message("Bu telefonda sesle yazma bulunamadı") }
        Unit
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = C.Raised, drawerContentColor = C.Text) {
                Text(
                    "notnote",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 28.dp, top = 28.dp, bottom = 20.dp),
                )
                DrawerItem("Ara", NIcons.Search) { scope.launch { drawer.close() }; vm.searchQuery = "" }
                DrawerItem("İzinler", NIcons.Lock) { scope.launch { drawer.close() }; onOpenPermissions() }
                DrawerItem("Komutlar", NIcons.At) { scope.launch { drawer.close() }; onOpenHelp() }
                DrawerItem("Yedekle", NIcons.Save) {
                    scope.launch { drawer.close() }
                    val day = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
                    exportLauncher.launch("notnote-yedek-$day.json")
                }
                DrawerItem("Yedekten geri yükle", NIcons.Restore) {
                    scope.launch { drawer.close() }
                    importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "sürüm ${BuildConfig.VERSION_NAME}",
                    color = C.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(28.dp),
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(C.Bg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            if (vm.searchQuery != null) {
                SearchBar(query, onChange = { vm.searchQuery = it }, onClose = { vm.searchQuery = null })
            } else {
                TopBar(onMenu = { scope.launch { drawer.open() } })
            }

            Box(Modifier.weight(1f)) {
                if (vm.searchQuery != null && query.isNotBlank() && shown.isEmpty()) {
                    Text(
                        "\"$query\" geçen not yok",
                        color = C.Muted,
                        fontSize = 15.sp,
                        modifier = Modifier.align(Alignment.Center).padding(40.dp),
                    )
                } else if (notes.isEmpty()) {
                    Text(
                        "Henüz not yok.\nKomut için @ ile başla: @whatsapp Ahmet: selam",
                        color = C.Muted,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.align(Alignment.Center).padding(40.dp),
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    items(shown, key = { it.id }) { note ->
                        Column(Modifier.animateItem()) {
                            SwipeRow(onSwipeLeft = { vm.delete(note) }) {
                                NoteRow(
                                    note,
                                    onClick = { editing = note },
                                    onPin = { vm.togglePin(note) },
                                    onCopy = {
                                        ctx.getSystemService(ClipboardManager::class.java)
                                            .setPrimaryClip(ClipData.newPlainText("not", note.text))
                                    },
                                    onShare = {
                                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                            .putExtra(Intent.EXTRA_TEXT, note.text)
                                        ctx.startActivity(Intent.createChooser(send, null))
                                    },
                                    onDelete = { vm.delete(note) },
                                    onRemind = { pickDateTime(ctx, note.scheduledAt) { vm.setReminder(note, it) } },
                                    onClearReminder = { vm.clearReminder(note) },
                                )
                            }
                            HorizontalDivider(color = C.Divider, thickness = 1.dp)
                        }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter)) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = C.SendBg,
                        contentColor = C.Text,
                        actionColor = C.Accent,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }

            AnimatedVisibility(vm.suggestions.isNotEmpty()) {
                SuggestionBar(vm.suggestions, vm::applySuggestion)
            }
            InputBar(vm, onVoice = startVoice)
        }
    }

    editing?.let { note ->
        val fresh = notes.firstOrNull { it.id == note.id } ?: note
        if (fresh.isCommand) {
            CommandDialog(
                note = fresh,
                onDismiss = { editing = null },
                onReuse = { vm.reuse(fresh); editing = null },
                onCancel = { vm.cancelScheduled(fresh); editing = null },
                onDelete = { vm.delete(fresh); editing = null },
            )
        } else {
            EditDialog(
                note = fresh,
                onDismiss = { editing = null },
                onSave = { vm.edit(fresh, it); editing = null },
                onDelete = { vm.delete(fresh); editing = null },
            )
        }
    }

    vm.choice?.let { choice ->
        AlertDialog(
            onDismissRequest = vm::dismissChoice,
            containerColor = C.Raised,
            title = { Text("Hangisi?", color = C.Text) },
            text = {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(choice.options) { c ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { vm.choose(c) }
                                .padding(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            Text(c.name, color = C.Text, fontSize = 17.sp)
                            Text(c.number, color = C.Muted, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::dismissChoice) { Text("Vazgeç", color = C.Muted) } },
        )
    }
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, fontSize = 16.sp) },
        icon = { Icon(icon, null, tint = C.Muted) },
        selected = false,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun TopBar(onMenu: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(C.Raised)
                .clickable(onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) {
            Icon(NIcons.Menu, "Menü", tint = C.Text, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            "notnote",
            color = C.Text,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.weight(1f),
        )
        // Sadece görüntü: bir işlevi yok.
        Icon(NIcons.Checklist, null, tint = C.Muted, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(C.Raised)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NIcons.Search, null, tint = C.Muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Notlarda ara…", color = C.Muted, fontSize = 18.sp)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = C.Text, fontSize = 18.sp),
                cursorBrush = SolidColor(C.Accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Icon(
            NIcons.Close, "Aramayı kapat", tint = C.Text,
            modifier = Modifier.size(24.dp).clip(CircleShape).clickable(onClick = onClose),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    note: Note,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRemind: () -> Unit,
    onClearReminder: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val hasReminder = !note.isCommand && note.status == NoteStatus.SCHEDULED
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 4.dp, vertical = 20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = buildAnnotatedString {
                        val t = note.text
                        if (t.startsWith("@")) {
                            val end = t.indexOfFirst { it.isWhitespace() }.let { if (it < 0) t.length else it }
                            withStyle(SpanStyle(color = C.Accent)) { append(t.substring(0, end)) }
                            append(t.substring(end))
                        } else {
                            append(t)
                        }
                    },
                    color = C.Text,
                    fontSize = 18.sp,
                    lineHeight = 27.sp,
                )
                if (note.isCommand) {
                    Text(
                        statusLine(note),
                        color = statusColor(note.status),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else if (hasReminder) {
                    Text(
                        "⏰ ${formatWhen(note.scheduledAt)} hatırlatılacak",
                        color = C.Wait,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (note.pinned) {
                Icon(NIcons.Pin, "Sabit", tint = C.Wait, modifier = Modifier.padding(start = 8.dp, top = 3.dp).size(18.dp))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = C.SendBg) {
            MenuItem(if (note.pinned) "Sabitlemeyi kaldır" else "Üste sabitle") { menu = false; onPin() }
            if (!note.isCommand) {
                MenuItem(if (hasReminder) "Hatırlatıcıyı değiştir" else "Hatırlatıcı ekle") { menu = false; onRemind() }
                if (hasReminder) MenuItem("Hatırlatıcıyı kaldır") { menu = false; onClearReminder() }
            }
            MenuItem("Kopyala") { menu = false; onCopy() }
            MenuItem("Paylaş") { menu = false; onShare() }
            MenuItem("Sil", C.Fail) { menu = false; onDelete() }
        }
    }
}

@Composable
private fun MenuItem(label: String, color: Color = C.Text, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label, color = color, fontSize = 16.sp) }, onClick = onClick)
}

private fun statusColor(s: NoteStatus) = when (s) {
    NoteStatus.DONE -> C.Ok
    NoteStatus.FAILED -> C.Fail
    NoteStatus.RUNNING, NoteStatus.SCHEDULED -> C.Wait
    else -> C.Muted
}

private fun statusLine(note: Note): String {
    val type = (CommandParser.parse(note.text) as? ParseResult.Ok)?.command?.type
    val state = when (note.status) {
        NoteStatus.RUNNING -> "⏳ ${note.detail ?: "çalışıyor…"}"
        NoteStatus.DONE -> "✓ ${note.detail ?: "tamam"}"
        NoteStatus.FAILED -> "✗ ${note.detail ?: "hata"}"
        NoteStatus.SCHEDULED -> "⏰ ${formatWhen(note.scheduledAt)} planlandı"
        NoteStatus.CANCELLED -> note.detail ?: "iptal edildi"
        NoteStatus.NONE -> null
    }
    return listOfNotNull(type?.label, note.targetName, state).joinToString(" · ")
}

/** Önce gün, sonra saat sorar; ikisi de seçilirse zamanı verir. Varsayılan: kurulu zaman ya da bir saat sonrası. */
private fun pickDateTime(ctx: Context, initial: Long?, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply {
        timeInMillis = initial ?: (System.currentTimeMillis() + 60 * 60 * 1000L)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val theme = android.R.style.Theme_DeviceDefault_Dialog_Alert
    DatePickerDialog(ctx, theme, { _, y, m, d ->
        cal.set(y, m, d)
        TimePickerDialog(ctx, theme, { _, h, min ->
            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, min)
            onPicked(cal.timeInMillis)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
        datePicker.minDate = System.currentTimeMillis() - 1000
    }.show()
}

private fun formatWhen(at: Long?): String {
    if (at == null) return ""
    val target = Calendar.getInstance().apply { timeInMillis = at }
    val today = Calendar.getInstance()
    val hm = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(at))
    val dayDiff = target.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR) +
        365 * (target.get(Calendar.YEAR) - today.get(Calendar.YEAR))
    return when (dayDiff) {
        0 -> hm
        1 -> "yarın $hm"
        else -> SimpleDateFormat("dd.MM HH:mm", Locale.ROOT).format(Date(at))
    }
}

/** Sola kaydır: sil. */
@Composable
private fun SwipeRow(onSwipeLeft: () -> Unit, content: @Composable () -> Unit) {
    // Kaydırma konumu doğrudan güncellenir; her harekette ayrı iş başlatmak
    // bırakma animasyonunu iptal edip kartı yarıda bırakıyordu.
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width }) {
        if (offset != 0f) {
            Row(
                Modifier
                    .matchParentSize()
                    .background(C.Fail.copy(alpha = 0.18f))
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Text("Sil", color = C.Fail, fontSize = 15.sp)
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .background(C.Bg)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { d ->
                        offset = (offset + d).coerceIn(-width.toFloat(), 0f)
                    },
                    onDragStopped = { velocity ->
                        // Yeterince uzağa çekildiyse ya da hızlıca fırlatıldıysa sil.
                        val delete = offset < -width * 0.3f || velocity < -1500f
                        val target = if (delete) -width.toFloat() else 0f
                        animate(offset, target) { v, _ -> offset = v }
                        if (delete) onSwipeLeft()
                    },
                )
        ) { content() }
    }
}

@Composable
private fun SuggestionBar(items: List<Suggestion>, onPick: (Suggestion) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        items(items) { s ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.Raised)
                    .border(1.dp, C.InputBorder, RoundedCornerShape(14.dp))
                    .clickable { onPick(s) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(s.title, color = C.Text, fontSize = 15.sp)
                s.subtitle?.let { Text(it, color = C.Muted, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun InputBar(vm: NotesViewModel, onVoice: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(vm.focusRequests) {
        if (vm.focusRequests > 0) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }
    val hasText = vm.input.text.isNotBlank()
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(C.InputBg)
            .border(1.dp, C.InputBorder, RoundedCornerShape(32.dp))
            .padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
            if (vm.input.text.isEmpty()) {
                Text("Aklına geleni yaz…", color = C.Muted, fontSize = 17.sp)
            }
            BasicTextField(
                value = vm.input,
                onValueChange = vm::onInputChange,
                textStyle = TextStyle(color = C.Text, fontSize = 17.sp, lineHeight = 24.sp),
                cursorBrush = SolidColor(C.Accent),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { vm.submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(C.SendBg)
                .clickable { if (hasText) vm.submit() else onVoice() },
            contentAlignment = Alignment.Center,
        ) {
            // Boşken mikrofon (sesle yaz), yazı varken gönder oku.
            if (hasText) Icon(NIcons.ArrowUp, "Gönder", tint = C.Text, modifier = Modifier.size(24.dp))
            else Icon(NIcons.Mic, "Sesle yaz", tint = C.Muted, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun EditDialog(note: Note, onDismiss: () -> Unit, onSave: (String) -> Unit, onDelete: () -> Unit) {
    var text by remember(note.id) { mutableStateOf(note.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Raised,
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = C.Text,
                    unfocusedTextColor = C.Text,
                    focusedBorderColor = C.Accent,
                    unfocusedBorderColor = C.InputBorder,
                    cursorColor = C.Accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Kaydet", color = C.Accent) } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Sil", color = C.Fail) }
                TextButton(onClick = onDismiss) { Text("Vazgeç", color = C.Muted) }
            }
        },
    )
}

@Composable
private fun CommandDialog(
    note: Note,
    onDismiss: () -> Unit,
    onReuse: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Raised,
        title = { Text(note.text, color = C.Text, fontSize = 17.sp, lineHeight = 24.sp) },
        text = { Text(statusLine(note), color = statusColor(note.status), fontSize = 14.sp) },
        confirmButton = {
            if (note.status == NoteStatus.SCHEDULED) {
                TextButton(onClick = onCancel) { Text("Planı iptal et", color = C.Wait) }
            } else {
                TextButton(onClick = onReuse) { Text("Düzenle / tekrar", color = C.Accent) }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Sil", color = C.Fail) }
                TextButton(onClick = onDismiss) { Text("Kapat", color = C.Muted) }
            }
        },
    )
}

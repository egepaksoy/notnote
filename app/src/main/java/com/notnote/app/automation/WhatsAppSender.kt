package com.notnote.app.automation

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.notnote.app.MainActivity
import com.notnote.app.NotnoteApp
import com.notnote.app.data.NoteStatus

/**
 * WhatsApp tam otomatik gönderimin durumu.
 *
 * notnote bir mesaj göndermeden hemen önce [arm] çağırır. Erişilebilirlik servisi
 * ancak bu "bekleyen gönderim" varken ve sadece WhatsApp ekranında bir şey yapar:
 * yazı kutusunda bizim mesajımızı görünce Gönder'e basar. [TIMEOUT_MS] içinde
 * olmazsa vazgeçer ve notu "gönderilemedi" olarak işaretler.
 */
object WhatsAppSender {
    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    private const val TIMEOUT_MS = 20_000L

    data class Pending(
        val noteId: Long,
        val pkg: String,
        val text: String,
        val returnToApp: Boolean,
        val armedAt: Long,
    )

    @Volatile
    var pending: Pending? = null
        private set

    private val handler = Handler(Looper.getMainLooper())

    fun isServiceEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(ctx, WhatsAppSendService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun arm(noteId: Long, pkg: String, text: String, returnToApp: Boolean) {
        val p = Pending(noteId, pkg, text, returnToApp, System.currentTimeMillis())
        pending = p
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (pending == p) {
                pending = null
                repo().setStatus(p.noteId, NoteStatus.FAILED, "Gönder tuşu bulunamadı — mesaj WhatsApp'ta bekliyor")
            }
        }, TIMEOUT_MS)
    }

    fun disarm() {
        pending = null
        handler.removeCallbacksAndMessages(null)
    }

    /** Servis her WhatsApp ekran değişikliğinde bunu çağırır. */
    fun onWhatsAppWindow(service: AccessibilityService, root: AccessibilityNodeInfo, pkg: String) {
        val p = pending ?: return
        if (pkg != p.pkg) return

        // Yazı kutusunda bizim mesajımız yoksa (henüz yüklenmedi / başka sohbet) bekle.
        val entry = root.findAccessibilityNodeInfosByViewId("$pkg:id/entry").firstOrNull()
        if (entry != null && !sameText(entry.text?.toString(), p.text)) return

        val send = findSendButton(root, pkg) ?: return
        val clickable = generateSequence(send) { it.parent }.firstOrNull { it.isClickable } ?: return

        disarm()
        if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            repo().setStatus(p.noteId, NoteStatus.DONE, "gönderildi")
            handler.postDelayed({ leaveWhatsApp(service, p) }, 500)
        } else {
            repo().setStatus(p.noteId, NoteStatus.FAILED, "Gönder tuşuna basılamadı")
        }
    }

    private fun leaveWhatsApp(service: AccessibilityService, p: Pending) {
        if (p.returnToApp) {
            val intent = Intent(service, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            runCatching { service.startActivity(intent) }
                .onFailure { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        } else {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
    }

    private fun findSendButton(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        root.findAccessibilityNodeInfosByViewId("$pkg:id/send").firstOrNull { it.isVisibleToUser }?.let { return it }
        // Yedek: WhatsApp kimliği değiştirirse "Gönder"/"Send" açıklamalı düğmeyi ara.
        val labels = setOf("gönder", "send")
        fun walk(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
            if (node == null || depth > 40) return null
            val desc = node.contentDescription?.toString()?.trim()?.lowercase()
            if (desc != null && desc in labels && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)?.let { return it }
            return null
        }
        return walk(root, 0)
    }

    private fun sameText(a: String?, b: String): Boolean {
        fun clean(s: String) = s.replace(Regex("\\s+"), " ").trim()
        val x = clean(a ?: return false)
        val y = clean(b)
        return x == y || (y.length > 30 && x.startsWith(y.take(30)))
    }

    private fun repo() = NotnoteApp.instance.repository
}

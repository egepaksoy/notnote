package com.notnote.app.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** Sadece WhatsApp ekranlarını dinler (bkz. res/xml/accessibility_config.xml). */
class WhatsAppSendService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (WhatsAppSender.pending == null) return
        val pkg = event.packageName?.toString() ?: return
        val root = rootInActiveWindow ?: return
        WhatsAppSender.onWhatsAppWindow(this, root, pkg)
    }

    override fun onInterrupt() = Unit
}

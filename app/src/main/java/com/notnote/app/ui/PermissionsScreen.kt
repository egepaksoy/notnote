package com.notnote.app.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.notnote.app.automation.WhatsAppSender

private data class PermRow(
    val title: String,
    val why: String,
    val granted: Boolean?,
    val onClick: () -> Unit,
    val actionLabel: String = "Ver",
)

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val activity = LocalActivity.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val prefs = remember { ctx.getSharedPreferences("permissions", Context.MODE_PRIVATE) }

    fun runtime(perm: String) {
        val askedBefore = prefs.getBoolean(perm, false)
        val canAsk = activity?.shouldShowRequestPermissionRationale(perm) == true
        if (askedBefore && !canAsk) {
            // Android artık sormuyor: uygulama ayarlarına gönder.
            openAppDetails(ctx)
        } else {
            prefs.edit { putBoolean(perm, true) }
            launcher.launch(perm)
        }
    }

    fun has(perm: String) = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    val rows = remember(tick) {
        val power = ctx.getSystemService(PowerManager::class.java)
        val alarms = ctx.getSystemService(AlarmManager::class.java)
        listOf(
            PermRow(
                "Rehber", "@whatsapp, @sms ve @ara için kişileri isimle bulur.",
                has(Manifest.permission.READ_CONTACTS),
                { runtime(Manifest.permission.READ_CONTACTS) },
            ),
            PermRow(
                "SMS gönderme", "@sms mesajlarını doğrudan gönderir.",
                has(Manifest.permission.SEND_SMS),
                { runtime(Manifest.permission.SEND_SMS) },
            ),
            PermRow(
                "Telefon araması", "@ara ile direkt arama yapar. İzin yoksa sadece numara açılır.",
                has(Manifest.permission.CALL_PHONE),
                { runtime(Manifest.permission.CALL_PHONE) },
            ),
            PermRow(
                "Bildirimler", "@hatırlat ve planlanmış mesajlar için.",
                has(Manifest.permission.POST_NOTIFICATIONS),
                { runtime(Manifest.permission.POST_NOTIFICATIONS) },
            ),
            PermRow(
                "WhatsApp otomatik gönderim",
                "Erişilebilirlik → Yüklü uygulamalar → \"notnote WhatsApp gönderici\" → Aç. " +
                    "Kapalıysa WhatsApp mesajı yazılı açılır, Gönder'e sen basarsın.",
                WhatsAppSender.isServiceEnabled(ctx),
                { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                "Aç",
            ),
            PermRow(
                "Kısıtlanmış ayarlar",
                "Erişilebilirlik'te notnote gri/kilitli görünürse: buraya dokun → sağ üstteki ⋮ → " +
                    "\"Kısıtlanmış ayarlara izin ver\". Sonra Erişilebilirlik'e geri dön.",
                null,
                { openAppDetails(ctx) },
                "Uygulama bilgisi",
            ),
            PermRow(
                "Tam zamanlı alarm", "Hatırlatıcılar ve planlanmış mesajlar dakikası dakikasına çalışsın.",
                alarms.canScheduleExactAlarms(),
                {
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                    )
                },
            ),
            PermRow(
                "Pil kısıtlaması yok",
                "Samsung arka plandaki uygulamaları uyutabilir. Ayrıca: Ayarlar → Pil → Arka plan kullanım sınırları → " +
                    "\"Hiç uyku moduna geçmeyen uygulamalar\" listesine notnote'u ekle.",
                power.isIgnoringBatteryOptimizations(ctx.packageName),
                {
                    @Suppress("BatteryLife")
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                    )
                },
            ),
        )
    }

    SubScreen("İzinler", onBack) {
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(rows) { row -> PermissionCard(row) }
        }
    }
}

@Composable
private fun PermissionCard(row: PermRow) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.Raised)
            .clickable(onClick = row.onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.title, color = C.Text, fontSize = 17.sp)
            Text(row.why, color = C.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(12.dp))
        when (row.granted) {
            true -> Text("✓ Açık", color = C.Ok, fontSize = 14.sp)
            else -> Text(
                row.actionLabel,
                color = C.Bg,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (row.granted == false) C.Accent else C.Muted)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private fun openAppDetails(ctx: Context) {
    ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    )
}

package com.notnote.app.command

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone as CPhone

data class Contact(val name: String, val number: String)

data class AppEntry(val label: String, val packageName: String)

object Contacts {

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Rehberdeki herkes; her kişi için tek numara (cep telefonu öncelikli). */
    fun load(ctx: Context): List<Contact> {
        if (!hasPermission(ctx)) return emptyList()
        data class Row(val name: String, val number: String, val mobile: Boolean, val primary: Boolean)
        val rows = mutableListOf<Row>()
        ctx.contentResolver.query(
            CPhone.CONTENT_URI,
            arrayOf(CPhone.DISPLAY_NAME, CPhone.NUMBER, CPhone.TYPE, CPhone.IS_SUPER_PRIMARY),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0)?.trim().orEmpty()
                val number = c.getString(1)?.trim().orEmpty()
                if (name.isEmpty() || number.isEmpty()) continue
                rows += Row(name, number, c.getInt(2) == CPhone.TYPE_MOBILE, c.getInt(3) == 1)
            }
        }
        return rows.groupBy { it.name }
            .map { (name, list) ->
                val best = list.sortedWith(compareByDescending<Row> { it.primary }.thenByDescending { it.mobile }).first()
                Contact(name, best.number)
            }
            .sortedBy { normalize(it.name) }
    }

    /** En iyi eşleşme grubunu döndürür: tam eşleşme varsa sadece onlar, yoksa kelime başı, yoksa içinde geçenler. */
    fun search(all: List<Contact>, query: String): List<Contact> {
        val ranked = all.mapNotNull { c -> matchRank(c.name, query)?.let { it to c } }
        val best = ranked.minOfOrNull { it.first } ?: return emptyList()
        return ranked.filter { it.first == best }.map { it.second }
            .distinctBy { Phone.toInternational(it.number) }
    }
}

object Apps {

    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != ctx.packageName }
            .distinctBy { it.packageName }
            .sortedBy { normalize(it.label) }
    }

    fun search(all: List<AppEntry>, query: String): List<AppEntry> {
        val ranked = all.mapNotNull { a -> matchRank(a.label, query)?.let { it to a } }
        val best = ranked.minOfOrNull { it.first } ?: return emptyList()
        return ranked.filter { it.first == best }.map { it.second }.sortedBy { it.label.length }
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    fun firstInstalled(ctx: Context, vararg pkgs: String): String? = pkgs.firstOrNull { isInstalled(ctx, it) }
}

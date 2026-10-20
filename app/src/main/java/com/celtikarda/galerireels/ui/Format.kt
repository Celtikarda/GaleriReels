package com.celtikarda.galerireels.ui

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val tr: Locale = Locale.forLanguageTag("tr-TR")
private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", tr)

fun localDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

fun formatDate(millis: Long): String = dateFormat.format(localDate(millis))

private val dateTimeFormat = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", tr)

fun formatDateTime(millis: Long): String =
    dateTimeFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** "dün", "3 ay önce", "tam 5 yıl önce bugün" gibi */
fun relativeDate(millis: Long, today: LocalDate = LocalDate.now()): String {
    val date = localDate(millis)
    if (date.isAfter(today)) return ""
    val p = Period.between(date, today)
    return when {
        p.years > 0 && p.months == 0 && p.days == 0 -> "tam ${p.years} yıl önce bugün"
        p.years > 0 -> "${p.years} yıl önce"
        p.months > 0 -> "${p.months} ay önce"
        p.days > 1 -> "${p.days} gün önce"
        p.days == 1 -> "dün"
        else -> "bugün"
    }
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatCount(n: Int): String = String.format(tr, "%,d", n)

/** 15492676 → "14,8 MB" */
fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(tr, "%.1f GB", mb / 1024) else String.format(tr, "%.1f MB", mb)
}

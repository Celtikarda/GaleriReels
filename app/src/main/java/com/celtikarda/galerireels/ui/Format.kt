package com.celtikarda.galerireels.ui

import com.celtikarda.galerireels.R
import com.celtikarda.galerireels.Strings
import com.celtikarda.galerireels.plural
import com.celtikarda.galerireels.str
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Biçimler seçili dile göre: "9 Kasım 2026" / "November 9, 2026", "1.234" / "1,234", "14,8 MB" / "14.8 MB"
private fun formatter(patternRes: Int) = DateTimeFormatter.ofPattern(str(patternRes), Strings.locale)

fun localDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

fun formatDate(millis: Long): String = formatter(R.string.pattern_date).format(localDate(millis))

fun formatMonth(month: YearMonth): String =
    formatter(R.string.pattern_month).format(month).replaceFirstChar { it.titlecase(Strings.locale) }

fun formatDateTime(millis: Long): String =
    formatter(R.string.pattern_date_time).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** "dün", "3 ay önce", "tam 5 yıl önce bugün" gibi */
fun relativeDate(millis: Long, today: LocalDate = LocalDate.now()): String {
    val date = localDate(millis)
    if (date.isAfter(today)) return ""
    val p = Period.between(date, today)
    return when {
        p.years > 0 && p.months == 0 && p.days == 0 -> plural(R.plurals.rel_exact_years, p.years, p.years)
        p.years > 0 -> plural(R.plurals.rel_years, p.years, p.years)
        p.months > 0 -> plural(R.plurals.rel_months, p.months, p.months)
        p.days > 1 -> plural(R.plurals.rel_days, p.days, p.days)
        p.days == 1 -> str(R.string.yesterday)
        else -> str(R.string.today_lower)
    }
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Toplam izleme süresi: "2 sa 15 dk", "14 dk", "45 sn" */
fun formatWatchTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 && m > 0 -> str(R.string.time_h_m, h, m)
        h > 0 -> str(R.string.time_h, h)
        m > 0 -> str(R.string.time_m, m)
        else -> str(R.string.time_s, s)
    }
}

fun formatCount(n: Int): String = String.format(Strings.locale, "%,d", n)

/** 15492676 → "14,8 MB" */
fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(Strings.locale, "%.1f GB", mb / 1024) else String.format(Strings.locale, "%.1f MB", mb)
}

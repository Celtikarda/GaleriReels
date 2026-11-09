package com.celtikarda.galerireels

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Uygulama dili: telefonun dili (varsayılan), Türkçe ya da İngilizce.
 *
 * - Metinler `res/values` (İngilizce, varsayılan) ve `res/values-tr` (Türkçe) dosyalarında.
 *   Telefonu Türkçe olan Türkçe, diğer bütün diller İngilizce görür.
 * - Android 13+: seçim sistemin uygulama dili ayarına yazılır (Ayarlar › Uygulamalar › Dil ile aynı yer).
 * - Android 12 ve öncesi: seçim SharedPreferences'ta; Activity / Application / Service
 *   `attachBaseContext`'te [wrap] ile o dile çevrilir.
 */
object AppLanguage {
    /** "" = telefonun dili */
    val options = listOf("", "tr", "en")

    private const val PREFS = "app_language"
    private const val KEY = "tag"

    fun current(context: Context): String =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales
                ?.takeIf { !it.isEmpty }?.get(0)?.language.orEmpty()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        }

    fun set(activity: Activity, tag: String) {
        if (tag == current(activity)) return
        if (Build.VERSION.SDK_INT >= 33) {
            // Sistem Activity'yi kendisi yeniden oluşturur
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
            activity.recreate()
        }
    }

    /** Android 12 ve öncesinde seçilen dili uygular; 13+ ve "telefonun dili" seçiliyken aynen döner */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}

/**
 * Ekranda gösterilen metinlerin okunduğu kaynaklar. Uygulama açılınca uygulamanınkiyle,
 * Activity açılınca onun (seçili dildeki) kaynaklarıyla doldurulur. Dil değişince Activity
 * yeniden oluşturulduğu için burası da güncellenir.
 */
object Strings {
    /** Metinlerin kaynağı; birim testlerinde strings.xml'i doğrudan okuyan bir kaynakla değiştirilir */
    interface Source {
        val locale: Locale
        fun get(id: Int, args: Array<out Any>): String
        fun plural(id: Int, count: Int, args: Array<out Any>): String
    }

    lateinit var source: Source

    fun use(res: Resources) {
        source = object : Source {
            override val locale: Locale get() = res.configuration.locales[0]
            override fun get(id: Int, args: Array<out Any>) =
                if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
            override fun plural(id: Int, count: Int, args: Array<out Any>) = res.getQuantityString(id, count, *args)
        }
    }

    val locale: Locale get() = source.locale
}

fun str(@StringRes id: Int, vararg args: Any): String = Strings.source.get(id, args)

/** Çoğul: İngilizcede "1 video" / "2 videos"; Türkçede ikisi aynı */
fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String = Strings.source.plural(id, count, args)

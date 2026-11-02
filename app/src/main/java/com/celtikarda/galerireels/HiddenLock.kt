package com.celtikarda.galerireels

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Gizlenen klasörlerin PIN kilidi.
 *
 * PIN'in kendisi saklanmaz; rastgele tuzla SHA-256 özeti uygulamanın kendi ayarlarında durur
 * (seçilen veri klasörüne yazılmaz, böylece başka bir uygulama okuyamaz). 5 yanlış denemeden
 * sonra 30 saniye beklenir. PIN unutulursa telefonun kendi kilidi / parmak iziyle açılabilir.
 */
class HiddenLock(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hidden_lock", Context.MODE_PRIVATE)

    var hasPin by mutableStateOf(prefs.getString(KEY_HASH, null) != null)
        private set
    /** Bu oturumda kilit açıldı mı; uygulama arka plana gidince tekrar kilitlenir */
    var unlocked by mutableStateOf(false)
        private set

    private var wrongCount = 0
    private var blockedUntil = 0L

    /** Bekleme süresi bitene kadar kalan saniye (0: denenebilir) */
    fun waitSeconds(): Int {
        val left = blockedUntil - SystemClock.elapsedRealtime()
        return if (left > 0) ((left + 999) / 1000).toInt() else 0
    }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        prefs.edit {
            putString(KEY_SALT, salt)
            putString(KEY_HASH, hash(salt, pin))
        }
        hasPin = true
        unlocked = true
    }

    fun removePin() {
        prefs.edit {
            remove(KEY_SALT)
            remove(KEY_HASH)
        }
        hasPin = false
        unlocked = false
    }

    /** Doğruysa kilidi açar; yanlışsa sayar ve gerekirse bekletir */
    fun tryUnlock(pin: String): Boolean {
        if (waitSeconds() > 0) return false
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val expected = prefs.getString(KEY_HASH, null) ?: return false
        val ok = MessageDigest.isEqual(hash(salt, pin).toByteArray(), expected.toByteArray())
        if (ok) {
            wrongCount = 0
            unlocked = true
        } else if (++wrongCount >= MAX_WRONG) {
            wrongCount = 0
            blockedUntil = SystemClock.elapsedRealtime() + BLOCK_MS
        }
        return ok
    }

    /** Telefonun kendi kilidi / parmak izi doğrulandı */
    fun unlockWithDevice() {
        wrongCount = 0
        blockedUntil = 0L
        unlocked = true
    }

    fun lock() {
        unlocked = false
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray(Charsets.UTF_8)).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    companion object {
        const val PIN_LENGTH = 4
        private const val MAX_WRONG = 5
        private const val BLOCK_MS = 30_000L
        private const val KEY_SALT = "salt"
        private const val KEY_HASH = "hash"
    }
}

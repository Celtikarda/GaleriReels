package com.celtikarda.galerireels

import android.os.SystemClock
import android.util.Log

/**
 * Bir videonun açılış süresini logcat'e yazar: sayfa oturduğu andan ilk karenin ekrana
 * gelmesine kadar. `adb logcat -s GaleriAcilis` ile izlenir.
 */
object OpenTimer {
    private var id = -1L
    private var start = 0L

    /** Aynı video için tekrar çağrılırsa (ör. VLC'ye geçince) ilk başlangıç zamanı korunur. */
    fun start(id: Long) {
        if (id == this.id) return
        this.id = id
        start = SystemClock.uptimeMillis()
    }

    fun firstFrame(id: Long, engine: String) {
        if (id != this.id || start == 0L) return
        Log.i("GaleriAcilis", "video=$id motor=$engine süre=${SystemClock.uptimeMillis() - start} ms")
        start = 0L
    }
}

package com.celtikarda.galerireels.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.celtikarda.galerireels.HiddenLock
import com.celtikarda.galerireels.R
import kotlinx.coroutines.delay

enum class PinMode { SET, UNLOCK }

/**
 * Gizlenen klasörler için PIN ekranı.
 * - [PinMode.SET]: yeni PIN iki kez girilir
 * - [PinMode.UNLOCK]: PIN ya da telefonun kilidi / parmak izi ile açılır
 * Başarılı olunca [onDone], vazgeçince [onDismiss].
 */
@Composable
fun PinDialog(lock: HiddenLock, mode: PinMode, onDone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var entered by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var shakeTick by remember { mutableIntStateOf(0) }
    var wait by remember { mutableIntStateOf(lock.waitSeconds()) }

    // Bekleme süresini saniye saniye göster
    LaunchedEffect(shakeTick, wait > 0) {
        while (true) {
            wait = lock.waitSeconds()
            if (wait == 0) break
            delay(250)
        }
    }

    val deviceUnlock = rememberDeviceUnlock(context) {
        lock.unlockWithDevice()
        onDone()
    }

    fun submit(pin: String) {
        when (mode) {
            PinMode.SET -> {
                val firstPin = first
                if (firstPin == null) {
                    first = pin
                    message = null
                } else if (firstPin == pin) {
                    lock.setPin(pin)
                    onDone()
                } else {
                    first = null
                    message = "PIN'ler aynı değil, baştan gir"
                    shakeTick++
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            PinMode.UNLOCK -> {
                if (lock.tryUnlock(pin)) {
                    onDone()
                } else {
                    wait = lock.waitSeconds()
                    message = if (wait > 0) null else "Yanlış PIN"
                    shakeTick++
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
        entered = ""
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(appColors.card)
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = appColors.textPrimary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    mode == PinMode.UNLOCK -> "Gizlenen klasörler"
                    first == null -> "Yeni PIN (${HiddenLock.PIN_LENGTH} hane)"
                    else -> "PIN'i tekrar gir"
                },
                color = appColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    wait > 0 -> "Çok fazla yanlış deneme. $wait sn bekle"
                    message != null -> message!!
                    mode == PinMode.UNLOCK -> "Görmek için PIN'i gir"
                    else -> "Gizlenen klasörleri görmek için sorulacak"
                },
                color = if (wait > 0 || message != null) Color(0xFFED4956) else appColors.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            PinDots(filled = entered.length, shakeTick = shakeTick)
            Spacer(Modifier.height(18.dp))

            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    row.forEach { key ->
                        when (key) {
                            "bio" -> if (mode == PinMode.UNLOCK && deviceUnlock != null) {
                                KeyButton(onClick = deviceUnlock) {
                                    Icon(painterResource(R.drawable.ic_fingerprint), contentDescription = "Telefonun kilidiyle aç", tint = appColors.textPrimary)
                                }
                            } else {
                                Spacer(Modifier.size(64.dp))
                            }
                            "del" -> KeyButton(onClick = { entered = entered.dropLast(1) }) {
                                Icon(painterResource(R.drawable.ic_backspace), contentDescription = "Sil", tint = appColors.textPrimary)
                            }
                            else -> KeyButton(onClick = {
                                if (wait == 0 && entered.length < HiddenLock.PIN_LENGTH) {
                                    entered += key
                                    if (entered.length == HiddenLock.PIN_LENGTH) submit(entered)
                                }
                            }) {
                                Text(key, color = appColors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        }
    }
}

@Composable
private fun PinDots(filled: Int, shakeTick: Int) {
    // Yanlış girişte noktalar sağa sola sallanır
    val offset = remember { Animatable(0f) }
    LaunchedEffect(shakeTick) {
        if (shakeTick > 0) {
            for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) offset.animateTo(x, tween(45))
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.graphicsLayer { translationX = offset.value * density },
    ) {
        repeat(HiddenLock.PIN_LENGTH) { i ->
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .then(
                        if (i < filled) Modifier.background(appColors.textPrimary)
                        else Modifier.border(1.5.dp, appColors.textSecondary, CircleShape)
                    )
            )
        }
    }
}

@Composable
private fun KeyButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(appColors.textPrimary.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Telefonun kendi kilidiyle (parmak izi, yüz, desen, PIN) doğrulama. Telefonda kilit yoksa null.
 * Android 11+ BiometricPrompt, daha eskilerde KeyguardManager ekranı.
 */
@Composable
private fun rememberDeviceUnlock(context: Context, onSuccess: () -> Unit): (() -> Unit)? {
    val keyguard = remember { context.getSystemService(KeyguardManager::class.java) }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) onSuccess()
    }
    if (keyguard == null || !keyguard.isDeviceSecure) return null
    val activity = context.findActivity() ?: return null
    return {
        if (Build.VERSION.SDK_INT >= 30) {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle("Gizlenen klasörler")
                .setSubtitle("Telefonunun kilidiyle doğrula")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
            prompt.authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                },
            )
        } else {
            @Suppress("DEPRECATION")
            keyguard.createConfirmDeviceCredentialIntent("Gizlenen klasörler", "Telefonunun kilidiyle doğrula")
                ?.let { legacyLauncher.launch(it) }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

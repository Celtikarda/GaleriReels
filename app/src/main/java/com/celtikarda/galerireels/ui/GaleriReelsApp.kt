package com.celtikarda.galerireels.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.celtikarda.galerireels.DataFolder
import com.celtikarda.galerireels.GalleryViewModel
import com.celtikarda.galerireels.MediaAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GaleriReelsApp(openVideoId: Long? = null, openCleanupIds: List<Long>? = null, vm: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
    var access by rememberSaveable { mutableStateOf(MediaAccess.current(context)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    // İzin penceresinden her dönüşte artar; "daha fazla seç" sonrası yeniden yüklemeyi tetikler
    var reloadKey by rememberSaveable { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        askedOnce = true
        access = MediaAccess.current(context)
        reloadKey++
    }
    val requestAccess = { launcher.launch(MediaAccess.permissions) }

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAchievements by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Android'in klasör seçme penceresi; seçilen klasöre beğeni ve izlenmeler yazılır
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        scope.launch {
            vm.stats.useFolder(tree)
                .onSuccess { restored ->
                    val text = if (restored > 0) "Eski verilerin geri geldi ($restored video)" else "Veriler artık bu klasöre kaydediliyor"
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                }
                .onFailure { Toast.makeText(context, "Bu klasör kullanılamadı, başka bir klasör seç", Toast.LENGTH_LONG).show() }
        }
    }
    val pickFolder: () -> Unit = {
        scope.launch {
            val start = withContext(Dispatchers.IO) { DataFolder.prepareSuggested(context) }
            folderPicker.launch(start)
        }
    }

    // Kullanıcı ayarlardan izni değiştirip dönmüş olabilir
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access = MediaAccess.current(context)
        // Favoriler galeri uygulamasından değişmiş olabilir; gün de değişmiş olabilir
        vm.refreshFavorites()
        if (vm.stats.loaded) {
            vm.stats.markActiveToday()
            vm.checkAchievements()
        }
    }
    // Uygulama arka plana geçerken bekleyen beğeni / izlenme kaydını hemen yaz
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.stats.flush()
    }

    LaunchedEffect(access, reloadKey) {
        if (access != MediaAccess.NONE) vm.load(access to reloadKey)
    }

    LaunchedEffect(openCleanupIds, vm.loaded) {
        if (openCleanupIds != null && vm.loaded) vm.openCleanup(firstIds = openCleanupIds)
    }

    LaunchedEffect(openVideoId) {
        if (openVideoId != null) vm.openVideo(openVideoId)
    }

    val openAlbum = vm.openAlbum
    BackHandler(enabled = openAlbum != null) { vm.close() }
    BackHandler(enabled = openAlbum == null && showSettings) { showSettings = false }
    BackHandler(enabled = openAlbum == null && showAchievements) { showAchievements = false }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            access == MediaAccess.NONE -> PermissionScreen(
                deniedBefore = askedOnce,
                onRequest = requestAccess,
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                },
            )
            !vm.loaded -> LoadingScreen()
            vm.stats.folder == null && !vm.stats.folderPromptDismissed -> FolderSetupScreen(
                onPick = pickFolder,
                onLater = vm.stats::dismissFolderPrompt,
            )
            vm.videoCount == 0 -> MessageScreen(
                title = "Video bulunamadı",
                body = if (access == MediaAccess.PARTIAL) "Sadece seçtiğin videoları görebiliyorum." else "Galeride hiç video yok gibi görünüyor.",
                buttonText = if (access == MediaAccess.PARTIAL) "Video seç" else null,
                onButton = requestAccess,
            )
            openAlbum != null -> key(openAlbum.id) {
                ReelsScreen(vm = vm, album = openAlbum, onBack = vm::close)
            }
            showAchievements -> AchievementsScreen(vm = vm, onBack = { showAchievements = false })
            showSettings -> SettingsScreen(vm = vm, onPickFolder = pickFolder, onBack = { showSettings = false })
            else -> AlbumsScreen(
                vm = vm,
                partialAccess = access == MediaAccess.PARTIAL,
                onSelectMore = requestAccess,
                onOpenSettings = { showSettings = true },
                onOpenAchievements = { showAchievements = true },
            )
        }
        // Kaydırılan sayfalarda (ana ekran, ayarlar, başarımlar) durum çubuğunun arkası
        if (vm.loaded && openAlbum == null) StatusBarScrim(Modifier.align(Alignment.TopCenter))

        // Yeni başarım açılınca üstte birkaç saniye kutlama
        val unlocked = vm.newlyUnlocked
        var shownUnlocked by remember { mutableStateOf(unlocked) }
        if (unlocked.isNotEmpty()) shownUnlocked = unlocked
        LaunchedEffect(unlocked) {
            if (unlocked.isNotEmpty()) {
                delay(4000)
                vm.consumeUnlocked()
            }
        }
        AnimatedVisibility(
            visible = unlocked.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp),
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut(),
        ) {
            AchievementBanner(
                unlocked = shownUnlocked,
                onClick = {
                    vm.consumeUnlocked()
                    vm.close()
                    showSettings = false
                    showAchievements = true
                },
            )
        }
    }
}

/** Videolar galeriden okunurken (çok videoda bir iki saniye sürebiliyor) */
@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLogo(size = 96.dp)
        Spacer(Modifier.height(18.dp))
        Text("Galeri Reels", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Videoların hazırlanıyor…", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f), strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PermissionScreen(deniedBefore: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    MessageScreen(
        title = "Videoların, reels gibi",
        body = if (deniedBefore) {
            "İzin verilmedi. Pencere tekrar açılmazsa izni Ayarlar > İzinler > Fotoğraflar ve videolar kısmından açabilirsin."
        } else {
            "Telefonundaki videoları dikey kaydırarak izlemek için galeri erişimi gerekiyor. Uygulamanın internet izni yok, hiçbir şey telefonundan çıkmaz."
        },
        buttonText = "Galeriye izin ver",
        onButton = onRequest,
        secondaryText = if (deniedBefore) "Ayarları aç" else null,
        onSecondary = onOpenSettings,
    )
}

/** İlk açılışta: beğeni ve izlenmelerin uygulama silinse de kalması için klasör seçtirir */
@Composable
private fun FolderSetupScreen(onPick: () -> Unit, onLater: () -> Unit) {
    MessageScreen(
        title = "Verilerini koruyalım",
        body = "Beğenilerin ve izlenme sayıların telefonunda bir klasörde saklanacak; uygulamayı silip " +
            "yeniden kursan bile kaybolmazlar.\n\nAçılan pencerede Documents › GaleriReels klasörü açık " +
            "olacak: \"Bu klasörü kullan\" ve ardından \"İzin ver\"e bas. Uygulama sadece bu klasörü görür. " +
            "Daha önce kurduysan da aynısını yap, eski verilerin geri gelir.",
        buttonText = "Klasör seç",
        onButton = onPick,
        secondaryText = "Şimdi değil",
        onSecondary = onLater,
    )
}

@Composable
private fun MessageScreen(
    title: String,
    body: String,
    buttonText: String?,
    onButton: () -> Unit,
    secondaryText: String? = null,
    onSecondary: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLogo(size = 72.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(body, color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp, textAlign = TextAlign.Center)
        if (buttonText != null) {
            Spacer(Modifier.height(28.dp))
            Button(onClick = onButton) { Text(buttonText) }
        }
        if (secondaryText != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSecondary) { Text(secondaryText) }
        }
    }
}

package com.celtikarda.galerireels.ui

import com.celtikarda.galerireels.R
import com.celtikarda.galerireels.str
import android.app.Activity
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.celtikarda.galerireels.AppUpdate
import com.celtikarda.galerireels.DataFolder
import com.celtikarda.galerireels.GalleryViewModel
import com.celtikarda.galerireels.MediaAccess
import com.celtikarda.galerireels.MediaMode
import com.celtikarda.galerireels.ThemeMode
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
    var showRecap by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Android'in klasör seçme penceresi; seçilen klasöre beğeni ve izlenmeler yazılır
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        scope.launch {
            vm.useFolder(tree)
                .onSuccess { restored ->
                    val text = if (restored > 0) str(R.string.data_restored, restored) else str(R.string.data_saving_here)
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                }
                .onFailure { Toast.makeText(context, str(R.string.folder_unusable), Toast.LENGTH_LONG).show() }
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

    // Yeni sürüm var mı; açılışı yavaşlatmasın diye videolar yüklendikten sonra
    LaunchedEffect(vm.loaded) {
        if (vm.loaded) vm.updates.check()
    }

    // Uygulama arka plana gidince gizlenen klasörler tekrar kilitlenir
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.hiddenLock.lock() }

    val openAlbum = vm.openAlbum
    BackHandler(enabled = openAlbum != null) { vm.close() }
    BackHandler(enabled = openAlbum == null && showSettings) { showSettings = false }
    BackHandler(enabled = openAlbum == null && showAchievements) { showAchievements = false }
    BackHandler(enabled = openAlbum == null && showRecap) { showRecap = false }

    val systemDark = isSystemInDarkTheme()
    val isDark = when (vm.stats.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val palette = if (isDark) DarkAppColors else LightAppColors
    val colorScheme = if (isDark) {
        darkColorScheme(primary = InstaBlue, onPrimary = Color.White, surface = palette.card, onSurface = palette.textPrimary)
    } else {
        lightColorScheme(primary = InstaBlue, onPrimary = Color.White, surface = palette.card, onSurface = palette.textPrimary)
    }

    val view = LocalView.current
    val lightBars = !isDark && openAlbum == null
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = lightBars
        controller.isAppearanceLightNavigationBars = lightBars
    }

    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(LocalAppColors provides palette) {
            Box(Modifier.fillMaxSize().background(if (openAlbum != null) Color.Black else palette.background)) {
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
                        onLater = vm::dismissFolderPrompt,
                    )
                    !vm.stats.mediaModeAsked -> MediaModeSetupScreen(
                        videoCount = vm.allVideosCount,
                        photoCount = vm.allPhotosCount,
                        onSelect = vm::setMediaMode,
                    )
                    vm.totalVideoCount == 0 -> {
                        val mode = vm.stats.mediaMode
                        val hasOther = vm.totalAnyMediaCount > 0
                        MessageScreen(
                            title = str(when (mode) { MediaMode.VIDEOS -> R.string.no_videos_found; MediaMode.PHOTOS -> R.string.no_photos_found; MediaMode.ALL -> R.string.no_media_found }),
                            body = when {
                                access == MediaAccess.PARTIAL -> str(R.string.only_selected)
                                hasOther -> str(if (mode == MediaMode.PHOTOS) R.string.no_photos_other_mode else R.string.no_videos_other_mode)
                                else -> str(R.string.gallery_empty)
                            },
                            buttonText = when {
                                access == MediaAccess.PARTIAL -> str(R.string.select_media)
                                hasOther -> str(R.string.show_all_media)
                                else -> null
                            },
                            onButton = {
                                if (access == MediaAccess.PARTIAL) requestAccess()
                                else vm.setMediaMode(MediaMode.ALL)
                            },
                        )
                    }
                    openAlbum != null -> key(openAlbum.id) {
                        ReelsScreen(vm = vm, album = openAlbum, onBack = vm::close)
                    }
                    showRecap -> RecapScreen(vm = vm, onBack = { showRecap = false })
                    showAchievements -> AchievementsScreen(vm = vm, onBack = { showAchievements = false })
                    showSettings -> SettingsScreen(vm = vm, onPickFolder = pickFolder, onBack = { showSettings = false })
                    else -> AlbumsScreen(
                        vm = vm,
                        partialAccess = access == MediaAccess.PARTIAL,
                        onSelectMore = requestAccess,
                        onOpenSettings = { showSettings = true },
                        onOpenAchievements = { showAchievements = true },
                        onOpenRecap = { showRecap = true },
                    )
                }
                // Video izlerken ve ilk kurulum ekranlarında araya girmesin; ana ekrana gelince gösterilir
                val update = vm.updates.available
                val onHome = vm.loaded && openAlbum == null && vm.stats.mediaModeAsked &&
                    (vm.stats.folder != null || vm.stats.folderPromptDismissed)
                if (update != null && vm.updates.showPrompt && onHome) {
                    UpdateDialog(update = update, onDismiss = vm.updates::dismissPrompt)
                }
                // Kaydırılan sayfalarda (ana ekran, ayarlar, başarımlar, özet) durum çubuğunun arkası
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
                            showRecap = false
                            showAchievements = true
                        },
                    )
                }
            }
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
        Text("Galeri Reels", color = appColors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(str(R.string.preparing), color = appColors.textSecondary, fontSize = 15.sp)
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(color = appColors.textSecondary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PermissionScreen(deniedBefore: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    MessageScreen(
        title = str(R.string.onboarding_title),
        body = if (deniedBefore) {
            str(R.string.perm_denied)
        } else {
            str(R.string.perm_explain)
        },
        buttonText = str(R.string.allow_gallery),
        onButton = onRequest,
        secondaryText = if (deniedBefore) str(R.string.open_settings) else null,
        onSecondary = onOpenSettings,
    )
}

/** İlk açılışta: beğeni ve izlenmelerin uygulama silinse de kalması için klasör seçtirir */
@Composable
private fun FolderSetupScreen(onPick: () -> Unit, onLater: () -> Unit) {
    MessageScreen(
        title = str(R.string.protect_data_title),
        body = str(R.string.protect_data_body),
        buttonText = str(R.string.choose_folder),
        onButton = onPick,
        secondaryText = str(R.string.not_now),
        onSecondary = onLater,
    )
}

/** İlk açılışta: kullanıcının videoları mı, fotoğrafları mı yoksa ikisini birden mi gezmek istediğini sorar */
@Composable
private fun MediaModeSetupScreen(videoCount: Int, photoCount: Int, onSelect: (MediaMode) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLogo(size = 72.dp)
        Spacer(Modifier.height(20.dp))
        Text(str(R.string.what_browse), color = appColors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            str(R.string.mode_explain),
            color = appColors.textSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = { onSelect(MediaMode.VIDEOS) }) {
            Text(str(R.string.only_videos_n, formatCount(videoCount)))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { onSelect(MediaMode.PHOTOS) }) {
            Text(str(R.string.only_photos_n, formatCount(photoCount)))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { onSelect(MediaMode.ALL) }) {
            Text(str(R.string.all_n, formatCount(videoCount + photoCount)))
        }
    }
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
        Text(title, color = appColors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(body, color = appColors.textSecondary, fontSize = 16.sp, textAlign = TextAlign.Center)
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

/** GitHub'da yeni sürüm çıkınca bir kez gösterilir; "Sonra" denirse Ayarlar › Uygulama'da durur */
@Composable
fun UpdateDialog(update: AppUpdate, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = appColors.card,
        titleContentColor = appColors.textPrimary,
        textContentColor = appColors.textSecondary,
        title = { Text(str(R.string.new_version, update.version), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(update.title, color = appColors.textPrimary, fontWeight = FontWeight.SemiBold)
                if (update.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(plainNotes(update.notes), fontSize = 14.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    str(R.string.update_install_hint),
                    fontSize = 13.sp,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(str(R.string.later), color = appColors.textSecondary) }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)))
                }
            ) {
                Text(str(R.string.download), color = InstaBlue, fontWeight = FontWeight.Bold)
            }
        },
    )
}

/** GitHub sürüm notlarındaki Markdown işaretlerini (## başlık, **kalın**, `kod`) düz yazıya çevirir */
private fun plainNotes(markdown: String): String = markdown
    .lines()
    .joinToString("\n") { line -> if (line.startsWith("#")) line.trimStart('#', ' ') else line }
    .replace("**", "")
    .replace("`", "")
    .trim()

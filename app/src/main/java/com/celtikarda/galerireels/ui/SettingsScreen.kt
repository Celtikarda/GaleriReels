package com.celtikarda.galerireels.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celtikarda.galerireels.DataFolder
import com.celtikarda.galerireels.GalleryViewModel
import com.celtikarda.galerireels.R

private val WarningOrange = Color(0xFFFFB020)

@Composable
fun SettingsScreen(vm: GalleryViewModel, onPickFolder: () -> Unit, onBack: () -> Unit) {
    val stats = vm.stats
    val folder = stats.folder
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Geri", tint = Color.White)
            }
            Text("Ayarlar", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        SectionTitle("Beğeniler ve izlenmeler")
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    tint = if (folder != null) InstaBlue else WarningOrange,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Kayıt klasörü", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    Text(
                        folder?.displayPath ?: "Seçilmedi",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (folder != null) {
                if (stats.lastFolderSave > 0) InfoLine("Son kayıt", formatDateTime(stats.lastFolderSave))
                InfoLine("Dosyalar", "${DataFolder.DATA_FILE}, BENİ OKU.txt")
            }
            InfoLine(
                "Kayıtlı",
                "${formatCount(stats.totalLikes)} beğeni · ${formatCount(stats.totalViews)} izlenme " +
                    "(${formatCount(stats.watchedVideos)} video)",
            )

            val warning = stats.folderError
                ?: if (folder == null) "Klasör seçilmedi: uygulamayı silersen beğenilerin ve izlenmelerin kaybolur." else null
            if (warning != null) {
                Spacer(Modifier.height(10.dp))
                Text(warning, color = WarningOrange, fontSize = 14.sp)
            }

            Spacer(Modifier.height(14.dp))
            if (folder == null) {
                Button(onClick = onPickFolder) { Text("Klasör seç") }
            } else {
                OutlinedButton(onClick = onPickFolder) { Text("Klasörü değiştir") }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Uygulamayı silip yeniden kurarsan aynı klasörü (Documents › GaleriReels) seçmen " +
                    "yeterli; beğenilerin ve izlenmelerin geri gelir.",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
            )
        }

        SectionTitle("Uygulama")
        Card {
            InfoLine("Sürüm", version ?: "")
            InfoLine("Kaynak kodu", "github.com/Celtikarda/GaleriReels")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1C1E))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.width(96.dp))
        Text(value, color = Color.White, fontSize = 14.sp)
    }
}

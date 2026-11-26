# Makara Aşama 1 — Temel (renk, font, tek tema) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bütün uygulama Makara renklerine ve Inter fontuna geçer; açık tema ve Instagram renkleri/gradyanları (oynatıcı hariç) kalkar.

**Architecture:** `ui/Colors.kt` tek bir Makara paletine indirgenir; eski renk adları (`InstaBlue`, `LikeRed`…) Makara değerlerine yönlenen geçici takma ad olarak kalır, böylece `ReelsScreen.kt`'ye dokunmadan oynatıcı da yeni renge geçer. Yeni `ui/Type.kt` gömülü fontları ve Material `Typography`'yi verir; `GaleriReelsApp` ve `MainActivity` tek `ColorScheme` + `Typography` kullanır.

**Tech Stack:** Kotlin, Jetpack Compose Material3, JUnit 4 (JVM unit test), Gradle.

**Spec:** `docs/superpowers/specs/2026-11-26-v2-makara-kimlik-design.md` · Token kaynağı: `DESIGN.md`

## Global Constraints

- Çalışma yeri: worktree `C:\Users\Arda\AndroidStudioProjects\GaleriReels-kimlik`, dal `ozellik/v2-kimlik`. Ana klasöre (`GaleriReels`) geçme.
- **`app/src/main/java/com/celtikarda/galerireels/ui/ReelsScreen.kt` bu aşamada hiç değişmez** (yandaki oturum çalışıyor).
- Renk değerleri DESIGN.md'den birebir: background #120F0D, surface #1E1915, surface-high #2B241F, outline #3A302A, primary #FFB547, on-primary #120F0D, on-background #F5EDE3, on-surface-soft #D8CCBE, on-surface-muted #A89B8C, error #B3261E, on-error #FFFFFF.
- Tek koyu tema; açık tema seçeneği arayüzden kalkar. Kayıtlı `themeMode` verisi ve yedek dosyasındaki alanı silinmez (eski yedekler hatasız açılmalı).
- Fontlar gömülü, OFL lisanslı: Inter (400/500/600), Big Shoulders Display (600/800). İnternet gerekmez.
- Bulanıklık/cam efekti eklenmez. Okunabilirlik için video üstü karartma gradyanları kalabilir; dekoratif gradyanlar kalkar.
- Commit mesajı sonu: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Push ve sürüm (tag/release) yalnızca Arda onaylarsa.
- Telefon testi kuralları: her adb girdisinden önce arama durumu + uygulamanın önde olduğu kontrol edilir; telefonun sesine dokunulmaz; veri yedeği alınıp sonra iki kopya da geri yüklenir.

## Review Focus

- Daha önce **Aydınlık** veya **Sistem** teması seçmiş kullanıcı → uygulama koyu Makara ile açılmalı, çökmemeli (Task 3 testi).
- Material bileşenleri (Button, OutlinedButton, Switch, Slider, AlertDialog) → kehribar üstünde koyu yazı, okunur kontrast (Task 3 `makaraColorScheme` testi).
- Durum ve gezinme çubuğu simgeleri her ekranda (kurulum ekranları dahil) açık renk ve görünür (Task 5 telefon kontrolü).
- Gri ikincil metinler kart üstünde okunur kalmalı (Task 1 kontrast testi: muted/surface ≥ 4.5).
- `ReelsScreen.kt` diff'i boş (Task 5 adım 1).

---

### Task 1: Makara paleti

**Files:**
- Modify: `app/src/main/java/com/celtikarda/galerireels/ui/Colors.kt` (tamamı)
- Test: `app/src/test/java/com/celtikarda/galerireels/ui/MakaraColorsTest.kt`

**Interfaces:**
- Produces:
  - `object Makara { val Black; val Surface; val SurfaceHigh; val Outline; val Amber; val OnAmber; val Cream; val CreamSoft; val Muted; val Error; val OnError }` (hepsi `Color`, değerler Global Constraints'teki sırayla).
  - `val MakaraAppColors: AppColors` — `background=Black, card=Surface, cardElevated=SurfaceHigh, textPrimary=Cream, textSecondary=CreamSoft, textMuted=Muted, divider=Outline, accentGold=Amber, achievementDoneBg=SurfaceHigh, statusBarScrim=Black.copy(alpha=0.92f)`.
  - `AppColors.isDark` şimdilik kalır (`MakaraAppColors.isDark = true`); `DarkAppColors` ve `LightAppColors` geçici olarak `= MakaraAppColors` olur (kod derlenmeye devam etsin); üçü Task 3'te silinir. `LocalAppColors` varsayılanı `MakaraAppColors`.
  - Geçici takma adlar (KDoc: "Aşama 4'te kaldırılacak, ReelsScreen için"): `InstaBlue = Makara.Amber`, `LikeRed = Makara.Amber`, `FavoriteYellow = Makara.Amber`, `CleanupGreen = Makara.Amber`, `DeleteRed = Makara.Error`, `StoryRing = listOf(Makara.Amber, Color(0xFFFFD08A), Makara.Amber)`.

- [ ] **Step 1: Write the failing test** `MakaraColorsTest`
  - `tokens match DESIGN md`: `assertEquals(0xFF120F0D.toInt(), Makara.Black.toArgb())` … her token için (11 assert). `toArgb()` yerine JVM'de `(color.value shr 32).toInt()` kullan.
  - `text pairs pass WCAG AA`: test içi `contrast(a: Color, b: Color): Double` (sRGB doğrusallaştırma, `(L1+0.05)/(L2+0.05)`); şu çiftlerin her biri `>= 4.5`: OnAmber/Amber, Cream/Black, Cream/Surface, CreamSoft/Black, Muted/Black, Muted/Surface, OnError/Error.
  - `legacy aliases point to Makara`: `assertEquals(Makara.Amber, InstaBlue)`, `assertEquals(Makara.Error, DeleteRed)`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*MakaraColorsTest*"` → FAIL (`Makara` yok).
- [ ] **Step 3: Implement** Colors.kt'yi yukarıdaki arayüzle yeniden yaz.
- [ ] **Step 4: Run** aynı komut → PASS; `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `git add app/src && git commit -m "Makara palette with legacy color aliases"`

### Task 2: Gömülü fontlar ve Typography

**Files:**
- Create: `app/src/main/res/font/inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`, `big_shoulders_display_semibold.ttf`, `big_shoulders_display_extrabold.ttf`
- Create: `app/src/main/assets/licenses/OFL-Inter.txt`, `OFL-BigShouldersDisplay.txt`
- Create: `app/src/main/java/com/celtikarda/galerireels/ui/Type.kt`
- Test: `app/src/test/java/com/celtikarda/galerireels/ui/MakaraTypeTest.kt`

**Interfaces:**
- Produces: `object MakaraFonts { val Body: FontFamily; val Display: FontFamily }`; `val MakaraTypography: Typography` (Material3 varsayılan ölçüler, 15 stilin hepsinde `fontFamily = MakaraFonts.Body`).

- [ ] **Step 1:** Fontları Google Fonts kaynağından (github.com/google/fonts, `ofl/inter`, `ofl/bigshouldersdisplay`) statik TTF olarak indir; repo yalnızca değişken font veriyorsa, aynı OFL kaynağının resmi statik sürümünü (rsms/inter releases, Big Shoulders GitHub releases) kullan. Dosya adları küçük harf + alt çizgi. Her ailenin `OFL.txt`'sini assets/licenses'a koy. İndirilen dosyaları önce boş bir klasöre al, sadece .ttf ve OFL.txt'yi kopyala.
- [ ] **Step 2: Write the failing test** `MakaraTypeTest`: `all material styles use body font` — `MakaraTypography`'nin 15 stilinin (`displayLarge` … `labelSmall`) `fontFamily`'si `MakaraFonts.Body`'ye eşit.
- [ ] **Step 3: Run** `./gradlew :app:testDebugUnitTest --tests "*MakaraTypeTest*"` → FAIL.
- [ ] **Step 4: Implement** Type.kt: `Body = FontFamily(Font(R.font.inter_regular, W400), Font(R.font.inter_medium, W500), Font(R.font.inter_semibold, W600))`; `Display` aynı şekilde W600/W800; `MakaraTypography = Typography().let { t -> t.copy(displayLarge = t.displayLarge.copy(fontFamily = Body), …) }`.
- [ ] **Step 5: Run** → PASS; `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 6: Commit** `git add app/src && git commit -m "Embed Inter and Big Shoulders Display fonts"`

### Task 3: Tek tema bağlantısı ve tema seçeneğinin kaldırılması

**Files:**
- Modify: `app/src/main/java/com/celtikarda/galerireels/ui/GaleriReelsApp.kt:155-178`
- Modify: `app/src/main/java/com/celtikarda/galerireels/MainActivity.kt:62`
- Modify: `app/src/main/java/com/celtikarda/galerireels/ui/AlbumsScreen.kt:116-135` (açık/koyu düğmesi)
- Modify: `app/src/main/java/com/celtikarda/galerireels/ui/SettingsScreen.kt:133-152` (Görünüm bölümü)
- Modify: `app/src/main/java/com/celtikarda/galerireels/GalleryViewModel.kt:90-97` (`setThemeMode`, `toggleDarkLight` kalkar)
- Test: `app/src/test/java/com/celtikarda/galerireels/ui/MakaraThemeTest.kt`

**Interfaces:**
- Consumes: `Makara`, `MakaraAppColors` (Task 1), `MakaraTypography` (Task 2).
- Create: `app/src/main/java/com/celtikarda/galerireels/ui/Theme.kt`
- Produces: `fun makaraColorScheme(): ColorScheme` (Theme.kt) — `darkColorScheme(primary=Amber, onPrimary=OnAmber, background=Black, onBackground=Cream, surface=Surface, onSurface=Cream, surfaceVariant=SurfaceHigh, onSurfaceVariant=CreamSoft, outline=Outline, error=Error, onError=OnError)`; `@Composable fun MakaraTheme(content: @Composable () -> Unit)` = `MaterialTheme(makaraColorScheme(), typography = MakaraTypography) { CompositionLocalProvider(LocalAppColors provides MakaraAppColors, content) }`.

- [ ] **Step 1: Write the failing test** `MakaraThemeTest`:
  - `scheme text pairs readable`: `makaraColorScheme()` için onPrimary/primary, onSurface/surface, onBackground/background, onError/error kontrastı ≥ 4.5 (Task 1'deki `contrast` yardımcısını test kaynak setinde ortak bir `ContrastTestUtil.kt`'ye taşı).
  - `saved light theme still opens dark`: `ThemeMode.entries.forEach { mode -> assertSame(MakaraAppColors, paletteFor(mode)) }` — `internal fun paletteFor(mode: ThemeMode): AppColors = MakaraAppColors` Theme.kt'de (eski ayarı okuyan tek nokta).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*MakaraThemeTest*"` → FAIL.
- [ ] **Step 3: Implement** Theme.kt; GaleriReelsApp'te `isSystemInDarkTheme`/`palette`/`colorScheme` bloğu yerine `MakaraTheme { … }`; durum/gezinme çubukları her zaman `isAppearanceLight*Bars = false`; MainActivity `MaterialTheme(colorScheme = darkColorScheme())` → `MakaraTheme`. AlbumsScreen başlığındaki tema düğmesi ve SettingsScreen "Görünüm" bölümü silinir; `appColors.isDark` kullanan kalan satırlar kaldırılır; Colors.kt'den `isDark`, `DarkAppColors`, `LightAppColors` silinir. `ThemeMode` enum, `StatsStore.themeMode` ve yedek alanı **kalır**. Kullanılmayan string'ler (`switch_light`, `switch_dark`, `theme_explain`, `selected_theme`, `appearance`) her iki dilden silinir; `theme_dark/light/system` enum kullandığı için kalır.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest` → tüm testler PASS; `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `git add app/src && git commit -m "Single Makara dark theme; remove theme picker"`

### Task 4: Dekoratif gradyanların kaldırılması (oynatıcı hariç)

**Files:**
- Modify: `app/src/main/java/com/celtikarda/galerireels/ui/AlbumsScreen.kt:393` (RecapBannerCard gradyanı → `appColors.card` zemin, başlık/simge `Makara.Amber`), `:582` (MemoryTile `sweepGradient(StoryRing)` kenarlığı → `1.5.dp` düz `Makara.Amber`)
- Modify: `app/src/main/java/com/celtikarda/galerireels/ui/RecapScreen.kt:66-71,198` (`RecapGradient` silinir → `appColors.card` zemin, kenarlık `Makara.Outline`)
- Kalır: AlbumsScreen `:595`, `:627` (küçük resim altı karartma — okunabilirlik).

- [ ] **Step 1:** Değişiklikleri yap; `grep -n "Gradient" app/src/main/java/com/celtikarda/galerireels/ui/AlbumsScreen.kt app/src/main/java/com/celtikarda/galerireels/ui/RecapScreen.kt` → yalnızca `:595` ve `:627` satırlarındaki `verticalGradient` kalmalı.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest :app:assembleDebug` → PASS / BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** `git commit -am "Remove decorative gradients outside the player"`

### Task 5: Doğrulama — çakışma, telefon, akıcılık

- [ ] **Step 1:** `git diff main --stat -- app/src/main/java/com/celtikarda/galerireels/ui/ReelsScreen.kt` → boş çıktı.
- [ ] **Step 2:** Arda'nın vivo'sunda: önce `files/galerireels-veri.json` yedeği; debug build kur; ana ekran, Ayarlar, Aylık özet, Başarımlar, kurulum ekranı ve oynatıcıdan ekran görüntüsü al. Kontrol: InstaBlue/mor gradyan yok; düğmelerde kehribar üstünde koyu yazı; durum çubuğu simgeleri görünür; Ayarlar'da "Görünüm" bölümü yok.
- [ ] **Step 3:** Release build + `cmd package compile -m speed-profile -f`, ana ekranda kaydırma ve 10 video geçişi; `dumpsys gfxinfo` janky % ve 99. persentil, `main` dalının release build'iyle aynı senaryoya göre karşılaştır. Kabul: janky % artışı ≤ 1 puan.
- [ ] **Step 4:** Yedeği iki kopyaya da geri yükle; sonuçları ve ekran görüntülerini Arda'ya göster. Sürüm/push için Arda'nın onayını iste.

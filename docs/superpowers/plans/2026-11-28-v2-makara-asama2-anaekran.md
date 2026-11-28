# Makara Aşama 2 — Ana ekran Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ana ekran Makara düzenine geçer (Kaldığın yer, Bu ay, ay makarası, En çok sarılanlar, Kutular, yıl şeridi) ve altta yüzen 4 sekmeli menü gelir (Makara · Akış · Sevdiklerim · Sen).

**Architecture:** Hesaplar (ay gruplama, mozaik boyutu, yıl listesi, kare numarası) Android'siz saf Kotlin dosyası `HomeSections.kt`'de, JVM testli. `GalleryViewModel` yeni "aç" yardımcıları alır (aynı `source`/`items`/`openAlbum` deseni). Görsel parçalar `ui/MakaraParts.kt`'de; `AlbumsScreen.kt` yalnızca sırayı dizer. Sekme durumu `GaleriReelsApp`'te; Sevdiklerim ve Sen ayrı küçük ekranlar.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Coil 3, JUnit 4 (JVM), Gradle.

**Spec:** `docs/superpowers/specs/2026-11-26-v2-makara-kimlik-design.md` · Token ve düzen kaynağı: `DESIGN.md` (Layout, Shapes, Components) · Görsel referans: tasarım panosu D "Karma"

## Global Constraints

- Dal: `ozellik/v2-anaekran` (main'den, `C:\Users\Arda\AndroidStudioProjects\GaleriReels`). `ReelsScreen.kt` bu aşamada değişmez (aşama 4).
- **Commit tarihi:** 2026-11-27'de commit atılmaz; kod yazılır, commit'ler 2026-11-28'de (görev başına bir commit). Tarih sahteciliği yok (`--date` / `GIT_COMMITTER_DATE` kullanılmaz).
- Renk/yazı/köşe yalnızca DESIGN.md token'larından (`Makara.*`, `MakaraType`); yeni sabit renk yok.
- Kenar boşluğu 20 dp, içerik ortalı; dokunma alanı ≥ 44 dp; sayfa sonunda 110 dp boşluk (menünün altında içerik kalmasın).
- Film delikleri (12×8 dp, 2 dp köşe, zemin rengi) **yalnızca** Kaldığın yer kartında ve ay makarası şeridinde.
- Sayılar Big Shoulders, sıfır dolgulu 4 hane ("0214"); 9999'u geçen sayı dolgusuz yazılır.
- Bulanıklık/cam, gradyan zemin, emoji yok. Tek gölge: yüzen menü.
- Bütün yeni yazılar `res/values/strings.xml` (İngilizce) + `res/values-tr/strings.xml`; koddan `str()` / `plural()`.
- Gizli klasörlerin videoları hiçbir yeni bölümde görünmez (`visibleVideos` kullan, `all` değil).
- Akıcılık: Arda'nın vivo'sunda release build, ana ekran kaydırma janky ≤ 1.9.4 + 1 puan.
- Commit mesajı sonu: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Push / sürüm yalnızca Arda onaylarsa.
- Telefon testi: her adb girdisinden önce arama durumu + uygulama önde mi; telefon sesine dokunulmaz; veri yedeği alınır, sonra geri yüklenir ve md5 karşılaştırılır.

## Review Focus

- **Hiç izleme geçmişi olmayan yeni kullanıcı** → Kaldığın yer, Son izlediklerin, Bu ay, En çok sarılanlar gizli; ekran boş kalmaz, ay makarası + Kutular görünür (Task 1 `mostWatched_emptyWhenNoViews`, Task 5 telefon).
- **Tek yıllık kütüphane** → yıl şeridi hiç görünmez (Task 1 `yearsOf_singleYearHidden`).
- **Fotoğraf modu (FOTO)** → Kaldığın yer yok (fotoğraf kaldığı yer tutmaz), ay makarası ve mozaik fotoğraflarla çalışır, süre etiketi gösterilmez (Task 1 `monthReels_photosHaveNoDuration`, Task 4 parça).
- **Kaldığın yer kaydı silinmiş videoya ait** (dosya silindi / klasör gizlendi) → kart gösterilmez, çökme yok (Task 2 `latestResume_skipsMissingVideo`).
- **Büyük yazı ayarı (fontScale 1.6)** → menü simgeleri ve kutu adları kesilip üst üste binmez (Task 5 telefon adımı).

---

### Task 1: Ana ekran hesapları

**Files:**
- Create: `app/src/main/java/com/celtikarda/galerireels/HomeSections.kt`
- Test: `app/src/test/java/com/celtikarda/galerireels/HomeSectionsTest.kt`

**Interfaces:**
- Produces:
  - `data class MonthReel(val month: YearMonth, val items: List<VideoItem>)`
  - `fun monthReels(videos: List<VideoItem>, zone: ZoneId = ZoneId.systemDefault(), limit: Int = 12): List<MonthReel>` — `dateMillis`'e göre ay grupları, en yeni ay önce, ay içi en yeni önce, boş ay yok.
  - `enum class TileSize(val cols: Int, val rows: Int) { BIG(2,2), TALL(1,2), WIDE(2,1), SMALL(1,1) }`
  - `data class MosaicTile(val video: VideoItem, val views: Int, val size: TileSize) { val loud: Boolean get() = views >= 10 }`
  - `fun mostWatched(videos: List<VideoItem>, viewsOf: (VideoItem) -> Int, limit: Int = 8): List<MosaicTile>` — views > 0 olanlar, çoktan aza; 1. BIG, 2. TALL, 3. WIDE, gerisi SMALL.
  - `fun yearsOf(videos: List<VideoItem>, zone: ZoneId = ZoneId.systemDefault()): List<Int>` — yeniden eskiye; tek yıl varsa `emptyList()`.
  - `fun frameNumber(n: Int): String` — `"%04d"`.

- [ ] **Step 1: Write the failing tests** (`HomeSectionsTest`, `VideoItem`'ları test yardımcısıyla kur; `dateMillis` UTC, `zone = ZoneOffset.UTC`)
  - `monthReels_groupsNewestFirst`: 2026-11-03, 2026-11-20, 2026-09-01 → `[2026-11 (20,03 sırası), 2026-09]`
  - `monthReels_respectsLimit`: 15 farklı ay, limit 12 → 12 eleman, ilki en yeni
  - `monthReels_photosHaveNoDuration`: foto `durationMs == 0` gruplanır (süre etiketi UI'da gizlenecek, burada yalnızca gruba girmesi)
  - `mostWatched_sizesByRank`: views [24, 15, 11, 9, 7] → sizes `[BIG, TALL, WIDE, SMALL, SMALL]`; `[0].loud == true`, `[3].loud == false`
  - `mostWatched_emptyWhenNoViews`: hepsi 0 → `emptyList()`
  - `yearsOf_singleYearHidden`: yalnızca 2026 → `emptyList()`; 2026+2024 → `[2026, 2024]`
  - `frameNumber_pads`: 7 → `"0007"`, 214 → `"0214"`, 12345 → `"12345"`
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*HomeSectionsTest*"` → FAIL (çözülmeyen referanslar).
- [ ] **Step 3: Implement** yukarıdaki imzaları `HomeSections.kt`'de (Android import yok).
- [ ] **Step 4: Run** aynı komut → PASS (7 test).
- [ ] **Step 5: Commit (11-28)** `feat: home section helpers (month reels, mosaic, years)`

### Task 2: Kaldığın yer + yeni "aç" yardımcıları

**Files:**
- Modify: `app/src/main/java/com/celtikarda/galerireels/ResumeStore.kt` (okuma yardımcısı)
- Modify: `app/src/main/java/com/celtikarda/galerireels/GalleryViewModel.kt` (Son izlenenler bölümünün altı)
- Modify: `app/src/main/java/com/celtikarda/galerireels/VideoItem.kt:77-83` (yeni Album sabitleri)
- Test: `app/src/test/java/com/celtikarda/galerireels/ResumeLatestTest.kt`

**Interfaces:**
- Consumes: Task 1 `monthReels`, `mostWatched`, `MonthReel`.
- Produces:
  - `ResumeStore.entries(): Map<String, Pair<Long, Long>>` (key → konum ms, kaydedildiği an)
  - `internal fun latestResume(entries: Map<String, Pair<Long, Long>>, byKey: Map<String, VideoItem>): Pair<VideoItem, Long>?` (`ResumeStore.kt`, saf) — en son kaydedilen ve `byKey`'de bulunan
  - `GalleryViewModel.resume: ResumeStore` (tek örnek; `ReelsPlayback` kendi örneğini kullanmaya devam eder, aynı SharedPreferences)
  - `fun resumeCandidate(): Pair<VideoItem, Long>?` — `visibleVideos` ile; FOTO modunda null
  - `fun openResume()` — adayı tek başına + "Son izlediklerin" arkasında açar (`Album.RECENT`)
  - `fun monthReels(): List<MonthReel>` · `fun openMonth(reel: MonthReel, first: VideoItem)` (`Album.MONTH_REEL`, ad `formatMonth`)
  - `fun mostWatched(): List<MosaicTile>` · `fun openMostWatched(first: VideoItem)` (`Album.MOST_WATCHED`)
  - `fun years(): List<Int>` · `fun openYear(year: Int)` — Tüm videolar, o yılın ilk videosundan başlar
  - `Album.MONTH_REEL = "__month_reel__"`, `Album.MOST_WATCHED = "__most_watched__"`

- [ ] **Step 1: Write the failing tests** (`ResumeLatestTest`)
  - `latestResume_picksNewestSaved`: a(pos 30 000, at 100), b(pos 90 000, at 200) → b, 90 000
  - `latestResume_skipsMissingVideo`: en yenisi `byKey`'de yok → bir öncekini döndürür; hiçbiri yoksa null
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*ResumeLatestTest*"` → FAIL.
- [ ] **Step 3: Implement** `entries()` (bozuk değerleri atla), `latestResume`, VM yardımcıları; `open*` fonksiyonları mevcut `openRecent` desenini izler (`source`, `items = listOf(first) + rest`, `openAlbum = Album(...)`).
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest` → tüm suite PASS; `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit (11-28)** `feat: resume card data and open helpers for home sections`

### Task 3: Makara parçaları (Compose)

**Files:**
- Create: `app/src/main/java/com/celtikarda/galerireels/ui/MakaraParts.kt`
- Create: `app/src/main/java/com/celtikarda/galerireels/ui/Shape.kt` (DESIGN.md `rounded`: frame 2, sm 4, DEFAULT 6, tile 8, control 18, bar 24 → `object MakaraShape`)
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-tr/strings.xml`
- Test: `app/src/test/java/com/celtikarda/galerireels/ui/MakaraShapeTest.kt`

**Interfaces:**
- Consumes: Task 1 tipleri, `Makara`, `MakaraType`, `VideoThumb`/Coil (mevcut kare yükleme).
- Produces (hepsi `@Composable internal`):
  - `SectionTitle(text: String, action: String? = null, onAction: (() -> Unit)? = null)` — display, büyük harf
  - `Perforations(count: Int)` — yalnızca aşağıdaki iki parçada çağrılır
  - `ResumeCard(video: VideoItem, positionMs: Long, onClick: () -> Unit)` — etiket "KALDIĞIN YER · KARE 0214" (kare = konum saniyesi), üst/alt delik, 4 dp kehribar ilerleme, ad + "Devam et"
  - `ThisMonthTile(watchTimeMs: Long, videoCount: Int, onClick: () -> Unit, modifier: Modifier)` — kehribar zemin, "BU AY", `formatWatchTime` + video sayısı
  - `CleanupTile(bytes: Long, onClick: () -> Unit, modifier: Modifier)` — kare kutu, "1,2 GB boşaltılabilir"
  - `MonthChips(reels: List<MonthReel>, selected: Int, onSelect: (Int) -> Unit)` — chip-month 56 dp, 3 harf ay + "48 kare"
  - `FilmStrip(title: String, items: List<VideoItem>, onPlay: () -> Unit, onItem: (VideoItem) -> Unit)` — delikli şerit, sol üst numara, sağ alt süre (foto: yok), "Oynat"
  - `MostWatchedMosaic(tiles: List<MosaicTile>, onItem: (VideoItem) -> Unit)` — 3 sütun, `TileSize` hücreleri; `loud` → "×24" counter-display kehribar, değilse counter krem
  - `Canister(album: Album, mode: MediaMode, onClick: () -> Unit, onLongClick: (() -> Unit)?)` — oval kapak + 4 dp kenar, ad + sıfır dolgulu sayı
  - Yeni dizgiler: `resume_label` ("CONTINUE · FRAME %1$s" / "KALDIĞIN YER · KARE %1$s"), `resume_action`, `this_month`, `freeable_space`, `month_reel` ("%1$s REEL" / "%1$s MAKARASI"), `play`, `most_watched`, `most_watched_hint`, `canisters` ("BOXES"/"KUTULAR"), `n_frames` (plural), sekme adları `tab_reel`, `tab_flow`, `tab_loved`, `tab_you` (yalnızca contentDescription)
- [ ] **Step 1: Write the failing test** `MakaraShapeTest`: `MakaraShape.Tile` köşesi 8 dp, `Bar` 24 dp, `Frame` 2 dp (dp değerlerini sabitlerden oku: `MakaraShape.TILE_DP == 8` vb.).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*MakaraShapeTest*"` → FAIL.
- [ ] **Step 3: Implement** `Shape.kt` + parçalar + dizgiler. `AudioAndStringsTest` iki dilde aynı anahtar setini zaten denetler.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest` → PASS; `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit (11-28)** `feat: Makara home components and shape tokens`

### Task 4: Yeni ana ekran düzeni + yıl şeridi

**Files:**
- Modify: `app/src/main/java/com/celtikarda/galerireels/ui/AlbumsScreen.kt:84-343` (gövde baştan dizilir; `CleanupCard`, `RecapBannerCard`, `MemoryTile`, `RecentTile`, `AlbumTile` artık kullanılmayanlar silinir)
- Create: `app/src/main/java/com/celtikarda/galerireels/ui/YearRail.kt`

**Interfaces:**
- Consumes: Task 2 VM yardımcıları, Task 3 parçaları.
- Produces: `AlbumsScreen(vm, partialAccess, onSelectMore, onOpenSettings, onOpenRecap, contentBottomPadding: Dp)` — `onOpenAchievements` parametresi kalkar (Sen sekmesine gider). `YearRail(years: List<Int>, activeYear: Int?, visible: Boolean, onPick: (Int) -> Unit, modifier: Modifier)`.

- [ ] **Step 1: Düzen** DESIGN.md Layout sırasıyla: başlık (logo "GALERİREELS", ara yok → yalnızca ayarlar simgesi) → medya seçici → uyarı şeridi (sınırlı erişim / yedeklenmiyor; ince kehribar kenarlı, dokununca eski davranış) → `ResumeCard` (aday varsa) → Son izlediklerin küçük kare sırası → `ThisMonthTile` (+ `CleanupTile` yalnızca `MediaActions.trashSupported` ve boşaltılacak bayt > 0; yoksa Bu ay tam genişlik) → `MonthChips` + `FilmStrip`: o gün `vm.memories()` doluysa ilk çip "BUGÜN" ve şerit "TARİHTE BUGÜN MAKARASI" → `MostWatchedMosaic` (boşsa bölüm yok) → "KUTULAR": Tüm videolar, Favoriler, Beğendiklerin, sonra klasörler (uzun basınca gizle menüsü + gizli klasör ⋮ davranışı aynen) → sürüm yazısı → `contentBottomPadding` (110 dp).
- [ ] **Step 2: Temizlik kartının akıllı satırları** (kopyalar, kısa videolar, ekran görüntüleri, WhatsApp, bulanık): `CleanupTile`'a dokununca küçük bir alt sayfa (`ModalBottomSheet`) eski seçenekleri listeler; davranış `vm.openCleanup` / `openDuplicateCleanup` / `openSmartCleanup` ile aynı.
- [ ] **Step 3: Yıl şeridi** sağ kenarda yüzer, yer kaplamaz; `LazyGridState.isScrollInProgress` true iken görünür, durduktan 1 500 ms sonra kaybolur (fade 150 ms); aktif yıl = ekrandaki ilk görünen kutunun/şeridin yılı değil, basit kural: ay makarasında seçili ayın yılı; dokununca `vm.openYear(year)`. `yearsOf` boşsa hiç çizilmez.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest` → PASS; `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit (11-28)** `feat: Makara home layout with year rail`

### Task 5: Yüzen alt menü + Sevdiklerim + Sen

**Files:**
- Create: `app/src/main/java/com/celtikarda/galerireels/ui/BottomBar.kt` (`enum class HomeTab { REEL, FLOW, LOVED, YOU }`, `FloatingBottomBar(current: HomeTab, onSelect: (HomeTab) -> Unit, modifier: Modifier)`)
- Create: `app/src/main/java/com/celtikarda/galerireels/ui/LovedScreen.kt`, `app/src/main/java/com/celtikarda/galerireels/ui/YouScreen.kt`
- Modify: `app/src/main/java/com/celtikarda/galerireels/ui/GaleriReelsApp.kt` (sekme durumu, `rememberSaveable`)
- Modify: `app/src/main/res/drawable/` (4 sekme simgesi, vektör; `ic_light_mode.xml` ve `ic_dark_mode.xml` silinir)
- Test: `app/src/test/java/com/celtikarda/galerireels/ui/HomeTabTest.kt`

**Interfaces:**
- Consumes: Task 2-4.
- Produces: `fun HomeTab.opensPlayer(): Boolean` (yalnızca FLOW true) — FLOW seçilince sekme değişmez, `vm.open(Album ALL)` çağrılır, geri gelince REEL'de kalınır.

- [ ] **Step 1: Write the failing test** `HomeTabTest`: `HomeTab.FLOW.opensPlayer() == true`, diğer üçü false; `HomeTab.entries.size == 4`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "*HomeTabTest*"` → FAIL.
- [ ] **Step 3: Implement**
  - Menü: nav-bar 64 dp, 24 dp köşe, surface zemin, tek gölge; altta 16 dp + `navigationBarsPadding`; aktif sekme 64×48 dp kehribar kutu, simge on-primary; pasif muted; yazı yok, contentDescription var; ≥ 44 dp dokunma. Oynatıcı, Ayarlar, Aylık özet, Başarımlar açıkken menü gizli.
  - Sevdiklerim: başlık "SEVDİKLERİM" → Favoriler ve Beğendiklerin iki `Canister` → "En çok beğendiklerin" `FilmStrip` (likes > 0, çoktan aza, ilk 20; boşsa kısa boş durum yazısı).
  - Sen: başlık "SEN" → `ThisMonthTile` (dokununca Aylık özet) → aktif gün ve beğeni sayısı satırı → "Başarımlar" kutusu: açılan/toplam sayaç, dokununca `AchievementsScreen` → "Aylık özet" kutusu. Ana ekran başlığındaki özet ve kupa simgeleri kalkar.
  - Geri tuşu: LOVED/YOU'dayken REEL'e döner; REEL'de uygulamadan çıkar (mevcut davranış).
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest` → PASS; `./gradlew :app:assembleRelease` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit (11-28)** `feat: floating bottom bar with Loved and You tabs`

### Task 6: Ertelenen küçükler + telefon kontrolü

**Files:**
- Modify: `ui/Type.kt` / kullanım yerleri (`FontWeight.Bold` → `SemiBold`; Inter 700 dosyası yok), `ui/Theme.kt` (`makaraColorScheme` bir kez `val`, iç içe `MakaraTheme` kalkar), `ui/Colors.kt` (`GoldYellow` silinir), `ui/PinDialog.kt` + `ui/TrimDialog.kt` (`0xFFED4956` → `Makara.Error`), `0xFF1C1C1E`/`0xFF2C2C2E` → `Makara.Surface`/`SurfaceHigh`, `AlbumsScreen` KDoc yerine, Başarım rozetlerindeki emoji → `counter` yazılı sayı rozeti.
- Kontrol listesi: `.superpowers/sdd/2026-11-26-v2-makara-asama1-temel/progress.md` "deferred" satırları (aşama 4'e ait LikeRed hariç).

- [ ] **Step 1: Implement** yukarıdakiler; `rg "0xFF(ED4956|1C1C1E|2C2C2E)|GoldYellow|FontWeight.Bold" app/src/main` → yalnızca `ReelsScreen.kt` satırları kalır.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest` → PASS; `./gradlew :app:assembleRelease` → BUILD SUCCESSFUL.
- [ ] **Step 3: Telefon** (kurallar Global Constraints'te): veri yedeği + md5 → release APK kur → ekran görüntüleri: ana ekran üst/orta/alt, yıl şeridi kaydırırken, Sevdiklerim, Sen, FOTO modu, fontScale 1.6 → `dumpsys gfxinfo` ana ekran kaydırma 3 tur, 1.9.4 ile karşılaştır → 1.9.4'ü geri kur, veri md5 aynı, `stay_on` 0.
- [ ] **Step 4: Commit (11-28)** `chore: phase-1 leftovers (weights, palette, badges)`

---

**Plan sonrası:** bütün dal için son inceleme → Arda'ya ekran görüntüleri → onayla main'e birleştir; sürüm (1+2 birlikte "2.0" önerisi) Arda'nın kararı.

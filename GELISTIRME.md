# Geliştirici rehberi

Bu dosya projeye sonradan bakacak kişi (ya da yapay zekâ) için: nasıl derlenir, nasıl imzalanır,
nasıl yayınlanır, kod nerede ne yapar ve nelere dikkat edilmeli. Kullanıcıya yönelik tanıtım ve
yol haritası [README](README.md)'de.

## Derleme

- Android Studio'nun içindeki JDK ile: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug`
- Sürüm (imzalı) APK: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
- Sadece `arm64-v8a` (libVLC her mimari için ~25 MB). `jniLibs.useLegacyPackaging = true`:
  VLC kütüphanesi APK içinde sıkıştırılır, APK ~35 MB.

## İmza (önemli)

- Debug ve release APK'lar **aynı anahtarla** imzalanır; böylece biri diğerinin üstüne kurulur
  ve telefondaki veriler (beğeni, izlenme) silinmez.
- Anahtar ve şifresi **repoda değildir**: proje kökündeki `keystore.properties` dosyasından okunur
  (`.gitignore`'da). Biçimi:

  ```properties
  storeFile=C:/…/galerireels.jks
  storePassword=…
  keyAlias=galerireels
  keyPassword=…
  ```

- Anahtar dosyası proje sahibinin bilgisayarında ve telefonunda `Documents/GaleriReels İmza`
  klasöründedir. **Kaybolursa** yeni sürümler eskisinin üstüne kurulamaz; kullanıcı uygulamayı
  silip yeniden kurmak zorunda kalır (veriler seçtiği klasörden geri gelir, bkz. Veriler).
- `keystore.properties` yoksa release APK imzasız çıkar (GitHub'dan indirip derleyen için).

## Sürüm çıkarma

1. `app/build.gradle.kts`: `versionCode` +1, `versionName` (yeni özellik → 0.x, düzeltme → 0.x.y)
2. README'deki yol haritasını güncelle
3. Commit, `git tag -a v0.x -m "v0.x"`, `git push origin main v0.x`
4. `./gradlew assembleRelease`, APK'yı `GaleriReels-v0.x.apk` adıyla kopyala
5. `gh release create v0.x GaleriReels-v0.x.apk --title "…" --notes-file notlar.md --latest`

Commit e-postası: hesap e-postayı gizli tutuyor, repoda `user.email` GitHub'ın
`…@users.noreply.github.com` adresine ayarlı (gmail'li commit'i GitHub reddediyor).

## Kod haritası (`app/src/main/java/com/celtikarda/galerireels/`)

| Dosya | Ne yapar |
|---|---|
| `MediaRepository` | Galeriden (MediaStore) video ve fotoğrafları okur: boyut, dönüklük, favori, klasör |
| `VideoItem` | Bir video veya fotoğraf (`isPhoto`), `MediaMode` ve `ThemeMode`; `key` = dosya adı + boyut |
| `GalleryViewModel` | Klasörler, açık liste, beğeni/favori/temizlik/aylık özet işlemleri |
| `StatsStore` | Beğeni, izlenme, günlük geçmiş, temizlik kaydı; JSON olarak iki yere yazar |
| `MonthlyRecap` | Aylık özet verisi ve 1080×1920 paylaşılabilir özet kartı (`RecapCardShare`) |
| `Memories` | Geçmiş yıllarda bugünlerde (±3 gün) çekilen videoları bulur ("Tarihte bugün") |
| `DuplicateFinder` | 64-bit algısal görüntü özeti (dHash) ve Hamming mesafesiyle benzer/kopya video tespiti |
| `BackgroundPlayService` | Arka planda oynatma ön plan servisi ve bildirim çubuğu kontrolleri (oynat/durdur/sonraki) |
| `Achievements` | 8 başarım rozeti ve açılma koşulları |
| `ResumeStore` | Uzun videolarda kaldığın yer (SharedPreferences, en fazla 300 video) |
| `VideoFrames` | Sarma önizlemesindeki kareler (`SeekPreview`) ve kareyi fotoğraf olarak kaydetme |
| `DataFolder` | Kullanıcının seçtiği klasör (SAF): `Documents/GaleriReels` |
| `ReelsPlayback` | Oynatma: 3 ExoPlayer (önceki/ekrandaki/sonraki hazır bekler) + 1 VLC; %200 ses yükseltme; izlenme sayımı |
| `DecoderCheck` | Video ExoPlayer ile mi VLC ile mi oynatılacak (dosya başlığı + telefonun çözücü sınırları) |
| `VlcEngine` | libVLC sarmalayıcı; bütün başlat/durdur çağrıları ayrı iş parçacığında |
| `MediaActions` | Favori / çöp kutusu / kalıcı silme istekleri (Android 11+, onay penceresi) |
| `VideoThumb` | Küçük resim; sistemin resmi simsiyahsa videonun içinden kare alır |
| `ui/ReelsScreen` | Reels ekranı (temizlik modu da burada: `Album.CLEANUP`) |
| `ui/RecapScreen`, `ui/AchievementsScreen` | Aylık özet ve başarımlar ekranları |
| `ui/AlbumsScreen`, `ui/SettingsScreen`, `ui/GaleriReelsApp` | Ana ekran, ayarlar, ekran akışı |

## Veriler

- Beğeni, izlenme, günlük izleme geçmişi, temizlenen bayt ve çöp kutusu listesi tek bir JSON'da
  (`galerireels-veri.json`, `format: 2`). İki kopya: uygulamanın içinde (hızlı açılış) ve
  kullanıcının seçtiği klasörde (uygulama silinse de kalsın diye). Uygulama yeniden kurulup aynı
  klasör seçilince veriler birleştirilerek geri gelir.
- Klasör seçme penceresi doğrudan `Documents/GaleriReels`'te açılır (önceden MediaStore ile
  oluşturulur), izin **sadece o klasöre** alınır. Bütün `Documents`'a izin alınmamalı: orada
  imza anahtarı da duruyor.
- Favoriler telefonun galerisiyle ortak (MediaStore `IS_FAVORITE`); uygulamada ayrı tutulmaz.
- Temizlikte silinenler Android çöp kutusuna gider (`createTrashRequest`), 30 gün sonra Android siler.

## Telefonda test

- Debug sürümde test kapıları:
  - `adb shell am start -S -n com.celtikarda.galerireels/.MainActivity --el open_video_id <id>`
  - `… --ela open_cleanup_ids <id>,<id>` (temizliği bu videolarla başlatır)
- Açılış süreleri logcat'te `GaleriAcilis` etiketiyle. vivo `logcat -d`'de satır yutuyor;
  logcat'i arkada akıtıp dosyaya yazın.
- **Silme ve favori testlerini asla kullanıcının gerçek videolarıyla yapmayın.** Küçük bir videoyu
  `/sdcard/Movies/GRTest/` içine kopyalayıp `MEDIA_SCANNER_SCAN_FILE` yayınıyla galeriye ekleyin,
  testten sonra silin. Her dokunmadan önce ekran görüntüsüne bakın; bir keresinde körlemesine
  yapılan kaydırma kullanıcının 2,4 GB'lık videosunu işaretledi (onay penceresinde reddedildi).
- Test sırasında açık kalan videolar izlenme olarak sayılır; testten sonra kayıttan çıkarın.

## Bilinen tuzaklar

- **VLC yazı tipi taraması:** VLC altyazı için telefondaki bütün yazı tiplerini tarıyor (vivo'da
  342 dosya); önbellek yokken yarım dakika sürüyor ve video açılmıyordu. `--no-spu
  --text-renderer=none` ile kapalı; açmayın.
- **VLC ana iş parçacığında durdurulmamalı:** `MediaPlayer.stop()` VLC'nin iş parçacığını bekliyor,
  ana iş parçacığında uygulamayı donduruyordu (ANR). `VlcEngine` hepsini `vlc-control`'da yapıyor.
- **Donanım çözücü sınırı:** Bu telefonda (Snapdragon 685) H.264/HEVC/VP9 en fazla 1920x1088;
  AV1 donanımı yok. 1440x1600@60 ve bazı AV1 webm'ler VLC'ye gider.
- **Kaydırıcı (Pager) ekrandaki videoyu anahtarıyla yerinde tutar:** listeye eleman ekleyince
  ekrandaki bir sonraki sayfaya kayar; bir kare bekleyip `scrollToPage` gerekir (Geri al'da var).
- ANR raporları: `adb shell dumpsys dropbox --print data_app_anr` (bütün iş parçacıklarının
  yığını içinde). Root olmadığı için `debuggerd` çalışmaz.

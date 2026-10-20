# Galeri Reels

Telefonun galerisindeki videoları Instagram Reels gibi, kaydırarak izlemeye yarayan Android uygulaması.

- Galerideki klasörleri gösterir, bir klasör seçince videoları tam ekran, alt alta kaydırarak oynatır
- Çift dokunarak beğenme, paylaşma, sesi kapatma, karışık / yeniden eskiye sıralama
- Her videonun beğenisi ve kaç kez izlendiği kaydedilir; kayıt seçilen bir klasörde de
  (Documents › GaleriReels) tutulur, uygulama silinip yeniden kurulsa da geri gelir
- Telefonun donanımının açamadığı büyük videolar (ör. 1440x1600@60) VLC motoruyla oynatılır

Sadece Android (64-bit ARM, Android 8.0+). APK'yı [Releases](../../releases) sayfasından indirebilirsiniz.

## Yol haritası

Telefonlarımızda yüzlerce video birikiyor ve çoğuna bir daha hiç bakmıyoruz. Galeri Reels bu
videoları yeniden önüne getiriyor; sıradaki adımlar onları **temizlemeyi** ve **hatırlamayı**
kolaylaştırmak.

### Yapılanlar
- [x] **0.2** İlk sürüm: klasörler, Reels gibi kaydırma, beğenme, paylaşma
- [x] **0.3** Videolar doğru boyutta ve çok daha hızlı açılıyor (kaydırınca ~10 ms)
- [x] **0.4** Kalıcı beğeniler ve izlenme sayısı; uygulama silinse de geri gelir
- [x] **0.4.1** Bazı videolarda donma ve açılmama düzeltildi

### Sıradakiler
- [ ] **0.5 Favoriler** · Üç nokta menüsünden "Favorilere ekle"; favoriler telefonun kendi
  galerisindeki favorilerle aynı liste olur.
  *Bu sürümden itibaren her videoyu hangi gün izlediğin de kaydedilmeye başlar (aylık özet için,
  aşağıya bakın). Bu bilgi sadece senin telefonunda ve seçtiğin klasörde durur; uygulamanın
  internet izni yoktur.*
- [ ] **0.6 Temizlik modu** · Videoları kaydırırken artık istemediğini tek hareketle sil, uygulama
  ne kadar yer açtığını göstersin ("Bugün 2,3 GB yer açtın"). Silinen videolar önce çöp kutusuna
  gider, yanlışlıkla silineni geri alabilirsin. Hiç izlemediğin ya da en çok yer kaplayan
  videoları öne çıkarır.
- [ ] **0.7 Yeni görünüm** · Daha küçük ve düzenli klasörler, parlak videolarda da okunan
  yazılar, siyah çıkan küçük resimlerin düzeltilmesi.
- [ ] **0.8 Aylık özet** · "Bu ay en çok izlediğin videolar", toplam izlenme, en sevdiğin klasör;
  arkadaşlarınla paylaşabileceğin bir kart olarak (Spotify Wrapped gibi).
- [ ] **Açılış ekranı ve yeni uygulama simgesi**

### Fikir havuzu
Henüz bir sürüme konmamış fikirler; sırası gelince yukarıya taşınır.

- **Başarımlar** · "100 video izledin", "1 GB yer açtın", "7 gün üst üste açtın", "Bütün
  klasörleri gezdin" gibi rozetler. Uygulamayı tekrar açmak için güzel bir sebep olur; temizlik
  modu ve 0.5'te başlayan günlük izleme kaydıyla birlikte çalışır.
- **Arka planda oynatma** · Uygulamadan çıkınca ya da ekran kapanınca videonun sesi devam etsin.
  Android bunun için bildirim çubuğunda bir oynatıcı göstermeyi zorunlu tutuyor (oynat /
  durdur / sonraki).
- **Ses yükseltme** · Sesi kısık videolar için %100'ün üstüne çıkabilen ses ayarı (en fazla
  %200; daha fazlası hoparlörde cızırtı yapar).

### Değerlendiriliyor
- **iPhone sürümü** · İstek var ama Apple geliştirici üyeliği (yıllık ücretli) ve kodun iki
  platforma göre yeniden düzenlenmesi gerekiyor. Android sürümü olgunlaşınca karar verilecek.

Fikir ya da hata bildirmek için [Issues](../../issues) sayfasını kullanabilirsiniz.

---

Kotlin + Jetpack Compose, Media3 ExoPlayer, libVLC, Coil. [Claude Code](https://claude.com/claude-code) ile geliştiriliyor.

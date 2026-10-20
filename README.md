# Galeri Reels

Telefonun galerisindeki videoları Instagram Reels gibi, kaydırarak izlemeye yarayan Android uygulaması.

- Galerideki klasörleri gösterir, bir klasör seçince videoları tam ekran, alt alta kaydırarak oynatır
- Çift dokunarak beğenme, paylaşma, sesi kapatma, karışık / yeniden eskiye sıralama
- Her videonun beğenisi ve kaç kez izlendiği kaydedilir; kayıt seçilen bir klasörde de
  (Documents › GaleriReels) tutulur, uygulama silinip yeniden kurulsa da geri gelir
- Favoriler (telefonun galerisiyle ortak) ve Temizlik modu: istemediğin videoları sola kaydırıp sil, ne kadar yer açtığını gör
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
- [x] **0.5 + 0.6** (birlikte yayınlandı)
  - **Favoriler:** Üç nokta menüsünden "Favorilere ekle"; telefonun kendi galerisindeki
    favorilerle aynı liste. Menüde ayrıca "Video bilgisi" (boyut, çözünürlük, izlenme...).
  - **Temizlik modu:** Ana ekrandaki Temizlik kartından başlar. Önce hiç izlemediğin ve en çok
    yer kaplayan videolar gelir; silmek istediğini sola kaydır, sonra tek onayla sil.
    Beğendiklerin ve favorilerin listeye hiç girmez. Silinenler Android'in çöp kutusuna gider:
    30 gün içinde Ayarlar'dan geri getirebilir ya da hemen kalıcı olarak silebilirsin.
  - Her videoyu hangi gün izlediğin de kaydedilmeye başladı (aylık özet ve başarımlar için).
    Bu bilgi sadece senin telefonunda ve seçtiğin klasörde durur; uygulamanın internet izni yoktur.
- [x] **0.7 Yeni görünüm** · Klasörler 3 sütun, kare kapaklı ve adları altta (telefonun
  galerisi gibi); parlak videolarda da okunan yazılar; kararmayla başlayan videolarda simsiyah
  çıkan küçük resimler artık videonun içinden bir kareyle gösteriliyor.
- [x] **0.8 Başarımlar ve yeni görünüm**
  - **Başarımlar:** Ana ekrandaki kupadan; izledikçe, temizledikçe, beğendikçe ve uygulamayı her
    gün açtıkça açılan 16 rozet ("İzleyici", "Temizlikçi", "7 gün üst üste"...). Yenisi açılınca
    üstte kısa bir kutlama çıkar.
  - **Yeni simge ve açılış ekranı**
  - **Son izlediklerin:** Ana ekranda en son izlediğin videolar.
  - **Sarma:** Alttaki çizgiyi parmakla sürükleyerek videoyu ileri / geri sar.
  - Beğenince ve silinecekler listesine atınca hafif titreşim; ayarlarda bölüm simgeleri.

### Sıradakiler
- [ ] **0.9 Aylık özet** · "Bu ay en çok izlediğin videolar", toplam izlenme, en sevdiğin klasör;
  arkadaşlarınla paylaşabileceğin bir kart olarak (Spotify Wrapped gibi).

### Fikir havuzu
Henüz bir sürüme konmamış fikirler; sırası gelince yukarıya taşınır.

- **Arka planda oynatma** · Uygulamadan çıkınca ya da ekran kapanınca videonun sesi devam etsin.
  Android bunun için bildirim çubuğunda bir oynatıcı göstermeyi zorunlu tutuyor (oynat /
  durdur / sonraki).
- **Ses yükseltme** · Sesi kısık videolar için %100'ün üstüne çıkabilen ses ayarı (en fazla
  %200; daha fazlası hoparlörde cızırtı yapar).

### Değerlendiriliyor
- **iPhone sürümü** · İstek var ama Apple geliştirici üyeliği (yıllık ücretli) ve kodun iki
  platforma göre yeniden düzenlenmesi gerekiyor. Android sürümü olgunlaşınca karar verilecek.

Fikir ya da hata bildirmek için [Issues](../../issues) sayfasını kullanabilirsiniz.

Geliştirmek isteyenler için: [GELISTIRME.md](GELISTIRME.md) (derleme, imza, sürüm çıkarma, kod haritası).

---

Kotlin + Jetpack Compose, Media3 ExoPlayer, libVLC, Coil. [Claude Code](https://claude.com/claude-code) ile geliştiriliyor.

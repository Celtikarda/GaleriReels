# Galeri Reels

Telefonun galerisindeki videoları ve fotoğrafları Instagram Reels gibi, kaydırarak gezmeye ve temizlemeye yarayan Android uygulaması.

- Galerideki klasörleri gösterir; sadece **Videolar**, sadece **Fotoğraflar** ya da **Tümü** modunda tam ekran, alt alta kaydırarak oynatır/gösterir
- Çift dokunarak beğenme, sağ/sol kenarlara basılı tutarak 2x hızlı oynatma, ortaya basılı tutarak net görünüm (arayüzü gizleme), paylaşma, sesi kapatma, kısık videolar için %200 ses yükseltme, karışık / yeniden eskiye sıralama
- Her içeriğin beğenisi ve kaç kez izlendiği/bakıldığı kaydedilir; kayıt seçilen bir klasörde de
  (Documents › GaleriReels) tutulur, uygulama silinip yeniden kurulsa da geri gelir
- Favoriler (telefonun galerisiyle ortak), Tarihte bugün anıları ve Temizlik modu: istemediğin video ve fotoğrafları sola kaydırıp sil, algısal görüntü özeti (dHash) ile kopya/benzer videoları ve seri çekim fotoğrafları yakala, ne kadar yer açtığını gör
- Aylık özet: bu ay en çok izlediğin videoları, izleme süreni ve en sevdiğin klasörü Spotify Wrapped tarzı kart olarak gör ve paylaş
- Arka planda oynatma: ayarlar açıldığında ekran kapansa da ses devam eder ve bildirim çubuğundan oynat / durdur / sonraki kontrol edilir
- Telefonun donanımının açamadığı büyük videolar (ör. 1440x1600@60) VLC motoruyla oynatılır
- Ayarlar ve video hata ekranından sıfır internet izniyle (tarayıcı üzerinden cihaz/çözücü bilgilerini hazır doldurarak) sorun bildirme ve öneri gönderme

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
- [x] **0.9 Aylık özet ve ses yükseltme**
  - **Aylık özet:** Ana ekrandaki özet kartından ya da üstteki grafik simgesinden; "Bu ay en çok
    izlediğin videolar", toplam izlenme, izleme süresi, açılan yer ve en sevdiğin klasör. Tek
    dokunuşla 1080×1920 hikâye kartı (PNG) olarak arkadaşlarınla paylaşabilir, listedeki videolara
    dokunarak doğrudan izleyebilirsin.
  - **Ses yükseltme (%200):** Üç nokta menüsünden sesi kısık videolar için %200'e kadar ses
    güçlendirme (ExoPlayer `LoudnessEnhancer` ve VLC ses çarpanı ile).
  - Başarımlar ve ayarlarda güncel günlük seri (`currentStreak`) gösterimi.
- [x] **1.0 Kopya video avcısı, Tarihte bugün, 2x hız ve arka planda oynatma**
  - **Benzer / kopya video tespiti (`DuplicateFinder`):** Videoların ortasından alınan karelerden
    64-bit algısal görüntü özeti (dHash) hesaplanır ve Hamming mesafesiyle WhatsApp'tan tekrar gelen
    ya da sıkıştırılmış kopyalar yakalanır. Temizlik kartından tek dokunuşla incelenir.
  - **Tarihte bugün (`Memories`):** Geçmiş yıllarda bugünlerde (±3 gün) çekilmiş videolar varsa ana
    ekranda özel "Tarihte bugün" anı kartı açılır.
  - **Basılı tutarak 2x hızlı oynatma:** Reels ekranında parmağını basılı tuttuğun sürece video 2x
    hızda oynar, bırakınca 1x'e döner (ExoPlayer + VLC).
  - **Arka planda oynatma (`BackgroundPlayService`):** Ayarlar'dan açıldığında uygulamadan çıkınca
    ya da ekran kapanınca ses devam eder; bildirim çubuğundan oynat / durdur / sonraki kontrol edilir.
  - **Klasör gizleme / hariç tutma:** Ana ekranda herhangi bir klasöre basılı tutarak gizle; gizlenen
    klasörler "Tüm videolar", "Tarihte bugün" ve "Temizlik" önerilerine girmez, Ayarlar'dan istediğin
    zaman izlenebilir veya tekrar görünür yapılabilir.
- [x] **1.1 Fotoğraf modu, kenardan 2x hız / ortada net görünüm ve sorun bildirimi**
  - **Fotoğraf modu (`MediaMode`):** Uygulama ilk açıldığında **Sadece videolar**, **Sadece fotoğraflar**
    ya da **Tümü (Fotoğraf + Video)** seçimi sorulur; ana ekrandaki sekmeden veya Ayarlar'dan her an
    değiştirilebilir. Fotoğraflar da Reels gibi tam ekran dikey kaydırılır, çift dokunuşla beğenilir ve
    Temizlik modunda sola kaydırılarak silinebilir.
  - **Benzer / seri çekim fotoğraf tespiti (`DuplicateFinder`):** 64-bit algısal görüntü özeti (dHash)
    ile yakın zamanda çekilmiş seri (burst) fotoğraflar veya WhatsApp kopyaları yakalanıp "Kopyaları incele"
    altında sunulur.
  - **YouTube tarzı sağ/sol kenardan 2x hız & ortada net görünüm:** Videonun sol veya sağ kenarına (%25)
    basılı tutunca 2x hızlı oynatır; orta kısmına (%50) basılı tutunca üst/alt bilgileri ve butonları
    gizleyerek videoyu/fotoğrafı net gösterir.
  - **Sorun bildir / Öneri gönder:** Ayarlar ekranına ve açılmayan videoların hata kutusuna eklendi;
    internet izni istemeden cihaz modeli, Android sürümü ve video çözücü bilgilerini hazır doldurarak
    GitHub Issue sayfasını açar.

### Değerlendiriliyor
- **iPhone sürümü** · İstek var ama Apple geliştirici üyeliği (yıllık ücretli) ve kodun iki
  platforma göre yeniden düzenlenmesi gerekiyor. Android sürümü olgunlaşınca karar verilecek.

Fikir ya da hata bildirmek için [Issues](../../issues) sayfasını kullanabilirsiniz.

Geliştirmek isteyenler için: [GELISTIRME.md](GELISTIRME.md) (derleme, imza, sürüm çıkarma, kod haritası).

---

Kotlin + Jetpack Compose, Media3 ExoPlayer, libVLC, Coil. [Claude Code](https://claude.com/claude-code) ile geliştiriliyor.

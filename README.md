# Galeri Reels

Telefonun galerisindeki videoları ve fotoğrafları Instagram Reels gibi, kaydırarak gezmeye ve temizlemeye yarayan Android uygulaması.

- Galerideki klasörleri gösterir; sadece **Videolar**, sadece **Fotoğraflar** ya da **Tümü** modunda tam ekran, alt alta kaydırarak oynatır/gösterir
- Çift dokunarak beğenme (kaç kez beğendiğin sayılır), sağ/sol kenarlara basılı tutarak 2x hızlı oynatma, ortaya basılı tutarak net görünüm (arayüzü gizleme), yatay tam ekran (Landscape) izleme, paylaşma, sesi kapatma, kısık videolar için %200 ses yükseltme, karışık / yeniden eskiye sıralama
- Her içeriğin beğenisi ve kaç kez izlendiği/bakıldığı kaydedilir; kayıt seçilen bir klasörde de
  (Documents › GaleriReels) tutulur, uygulama silinip yeniden kurulsa da geri gelir
- Favoriler (telefonun galerisiyle ortak), Tarihte bugün anıları ve Temizlik modu: istemediğin video ve fotoğrafları sola kaydırıp sil, algısal görüntü özeti (dHash) ile kopya/benzer videoları ve seri çekim fotoğrafları yakala, ne kadar yer açtığını gör
- Aylık özet: bu ay en çok izlediğin videoları, izleme süreni ve en sevdiğin klasörü Spotify Wrapped tarzı kart olarak gör ve paylaş
- Arka planda oynatma: ayarlar açıldığında ekran kapansa da ses devam eder ve bildirim çubuğundan oynat / durdur / sonraki kontrol edilir
- Telefonun donanımının açamadığı büyük videolar (ör. 1440x1600@60) VLC motoruyla oynatılır
- **Gece (koyu), Aydınlık (açık) ve Sistem teması:** Ana ekrandaki güneş/ay simgesiyle ya da Ayarlar'dan tek dokunuşla tema geçişi (Reels oynatıcı ekranı her zaman sinema siyahı kalır)
- Ayarlar ve video hata ekranından (tarayıcı üzerinden cihaz/çözücü bilgilerini hazır doldurarak) sorun bildirme ve öneri gönderme
- **Güncelleme bildirimi:** Yeni sürüm çıkınca uygulama haber verir, tek dokunuşla APK'yı indirirsin. Uygulama internete sadece bunun için, GitHub'daki son sürüm numarasını okumak üzere bağlanır; videoların ve verilerin telefonundan çıkmaz. Ayarlar'dan kapatılabilir.

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
    Bu bilgi sadece senin telefonunda ve seçtiğin klasörde durur, hiçbir yere gönderilmez.
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
- [x] **1.1 Fotoğraf modu, Gece/Aydınlık tema, kenardan 2x hız / ortada net görünüm ve sorun bildirimi**
  - **Fotoğraf modu (`MediaMode`):** Uygulama ilk açıldığında **Sadece videolar**, **Sadece fotoğraflar**
    ya da **Tümü (Fotoğraf + Video)** seçimi sorulur; ana ekrandaki sekmeden veya Ayarlar'dan her an
    değiştirilebilir. Fotoğraflar da Reels gibi tam ekran dikey kaydırılır, çift dokunuşla beğenilir ve
    Temizlik modunda sola kaydırılarak silinebilir.
  - **Gece / Aydınlık / Sistem teması (`ThemeMode`):** Ana ekrandaki güneş/ay simgesiyle ya da **Ayarlar › Görünüm**
    bölümünden Gece (koyu), Aydınlık (açık) ve Sistem teması arasında geçiş yapılabilir; Reels oynatıcı
    ekranı videoların ve fotoğrafların net görünmesi için her zaman sinema siyahı kalır.
  - **Benzer / seri çekim fotoğraf tespiti (`DuplicateFinder`):** 64-bit algısal görüntü özeti (dHash)
    ile yakın zamanda çekilmiş seri (burst) fotoğraflar veya WhatsApp kopyaları yakalanıp "Kopyaları incele"
    altında sunulur.
  - **YouTube tarzı sağ/sol kenardan 2x hız & ortada net görünüm:** Videonun sol veya sağ kenarına (%25)
    basılı tutunca 2x hızlı oynatır; orta kısmına (%50) basılı tutunca üst/alt bilgileri ve butonları
    gizleyerek videoyu/fotoğrafı net gösterir.
  - **Sorun bildir / Öneri gönder:** Ayarlar ekranına ve açılmayan videoların hata kutusuna eklendi;
    internet izni istemeden cihaz modeli, Android sürümü ve video çözücü bilgilerini hazır doldurarak
    GitHub Issue sayfasını açar.

- [x] **1.2 Yatay tam ekran izleme ve 2x hızlı oynatma akıcılığı**
  - **Yatay tam ekran (Landscape) modu:** Sağdaki işlem sütunundan, üst çubuktan ya da üç nokta
    menüsünden tek dokunuşla ekranı yataya çevirip sistem çubuklarını gizleyerek tam ekran izleme;
    geri tuşuyla veya **Küçült** butonuyla oynatma kesilmeden dikey Reels akışına dönülür.
  - **Yüksek kaliteli videolarda pürüzsüz 2x hızlı oynatma:** H.264/HEVC/AV1 referans-dışı karelerin
    (B-kare / ara katman) çözücüye girmeden elenmesi, `BUFFER_FLAG_DECODE_ONLY` desteği, değişken
    kare hızlı (VFR) telefon videolarında donanım çözücü saat hızı (`operating-rate`) yükseltmesi,
    sadece ekrandaki oynatıcıya hız uygulanması ve VLC `avcodec-fast` / `skip-loop-filter`
    optimizasyonlarıyla 60 fps ve yüksek çözünürlüklü videolarda 2x takılmaları giderildi.
  - Sıfırdan kurulumda klasör seçilmeden önce başarım bildirimi çıkması düzeltildi.

- [x] **1.3 Güncelleme bildirimi, beğeni sayacı ve sadeleşen tam ekran**
  - **Yeni sürüm bildirimi (`UpdateChecker`):** Uygulama en fazla 12 saatte bir GitHub'daki son sürüme
    bakar; yenisi varsa ana ekranda sürüm notlarıyla bir pencere açılır ve **İndir** ile APK iner.
    Eski sürümün üstüne kurulur, veriler silinmez. "Sonra" denirse aynı sürüm için tekrar sormaz;
    **Ayarlar › Uygulama › Güncellemeler**'den görülebilir, elle kontrol edilebilir ya da tamamen kapatılabilir.
    Bunun için uygulamaya internet izni eklendi; sadece sürüm numarası okunur, telefondan hiçbir şey gönderilmez.
  - **Beğeni sayacı:** Kalbin altındaki sayı artık videoyu kaç kez beğendiğini gösterir. Uygulama
    arkadan kapatılınca sayı kalır ama kalp boşalır; tekrar beğenince sayı bir artar. Aynı açılışta kalbe
    yeniden basmak o beğeniyi geri alır. Eski beğeniler 1 olarak aktarılır; Beğendiklerin klasörü ve
    temizlikte beğenilenlerin korunması aynen sürer.
  - **Tam ekran butonu tek yerde:** Sağdaki işlem sütunundaki ve üç nokta menüsündeki tekrar eden
    "Tam ekran" kaldırıldı; tam ekrana geçmek ve çıkmak için üst çubuktaki buton kullanılır.

- [x] **1.3.1 Beğenileri geri alma**
  - Kalbe **basılı tutunca** açılan menüden ya da üç nokta menüsünden, önceki açılışlarda verilmiş olsa da
    **bir beğeniyi geri alabilir** veya **tüm beğenileri kaldırabilirsin** (onay sorar). Beğenisi kalmayan
    video Beğendiklerin'den çıkar ve Temizlik'te artık korunmaz.
  - Temizlik kartındaki "… bulundu" yazısı ile "Kopyaları incele" arasına boşluk eklendi.

- [x] **1.4 Oynatıcı araçları**
  - **Kaldığın yerden devam:** 1 dakikadan uzun videolar bir dahaki açılışta bıraktığın yerden başlar
    (ilk ve son 5 saniyede bırakılanlar baştan).
  - **Sarma önizlemesi:** Alttaki çizgiyi sürüklerken o andaki kare küçük bir resim olarak görünür.
  - **Oynatma hızı:** Üç nokta menüsünden 0,5x – 2x; uzun basılı tutunca 2x yine çalışır.
  - **Bitince:** Videonun başa sarması ya da sıradaki videoya geçmesi seçilebilir (hatırlanır).
  - **A-B tekrarı:** Menüden önce A'yı, sonra B'yi işaretle; video bu aralığı tekrarlar.
  - **Kare kare:** Video durdurulunca ortada "‹ 1 kare" / "1 kare ›" butonları çıkar.
  - **Kareyi fotoğraf olarak kaydet:** Ekrandaki kare tam çözünürlükte Resimler/GaleriReels'e kaydedilir (Android 10+).
  - **Yatay tam ekranda ±10 sn:** Sol / sağ kenara çift dokununca 10 saniye geri / ileri; art arda
    dokundukça toplanır. Ortaya çift dokunmak yine beğenir.
  - **Başarımlar sadeleşti:** 16 rozet 8'e indi; sadece farklı videolar izlemek, bütün klasörleri gezmek,
    beğeni, favori ve yer açmak sayılıyor. Uygulamayı her gün açma ve aynı videoyu tekrar izleme rozetleri kaldırıldı.

### Değerlendiriliyor
- **iPhone sürümü** · İstek var ama Apple geliştirici üyeliği (yıllık ücretli) ve kodun iki
  platforma göre yeniden düzenlenmesi gerekiyor. Android sürümü olgunlaşınca karar verilecek.

Fikir ya da hata bildirmek için [Issues](../../issues) sayfasını kullanabilirsiniz.

Geliştirmek isteyenler için: [GELISTIRME.md](GELISTIRME.md) (derleme, imza, sürüm çıkarma, kod haritası).

---

Kotlin + Jetpack Compose, Media3 ExoPlayer, libVLC, Coil. [Claude Code](https://claude.com/claude-code) ile geliştiriliyor.

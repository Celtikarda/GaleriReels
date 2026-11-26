# GaleriReels 2.0 — "Makara" görsel kimliği

Tarih: 2026-11-26 · Durum: Arda onayladı (2026-11-26) · Görsel kaynak: Design tuvali (D · Makara + Mozaik, D · Oynatıcı, D · Tanıtım) · Token kaynağı: `DESIGN.md`

## Neden

Arda: "diğer uygulamalara çok benzedik, biraz da kendi elimizden bi şi gelsin, patenti bizim olsun." Bugünkü arayüz Instagram gradyanı, iOS grileri ve `InstaBlue` kullanıyor. 2.0'ın amacı: modern, göze hoş, insanın kaydırdıkça kaydırası gelen ve **ilk bakışta "bu GaleriReels" dedirten** kendine özgü bir kimlik. Hiçbir uygulamanın kopyası olmayacak.

## Hedefler

- Uygulamanın adından doğan tanınır bir kimlik: galeri = film makarası (kare sayaçları, film şeridi, film kutusu klasörler, kehribar).
- "Çok izlediğin büyür" (En çok sarılanlar) — başka galeride olmayan bir özellik.
- Ana ekranda kaydırma isteği: kaldığın yer → makara → en çok sarılanlar → kutular.
- Kısa tanıtım (3 kart, geçilebilir).
- Bugünkü bütün özellikler yeni düzende yerini bulur; hiçbiri kaybolmaz.

## Hedef olmayanlar

- Oynatma motoru, ViewModel, veri depoları (StatsStore, LikesStore, ResumeStore…) değişmez. Bu bir **kabuk** değişikliğidir.
- Açık tema 2.0'da yok (sonra istenirse eklenir).
- Yeni özellik eklenmez; sadece yıl şeridi, En çok sarılanlar ve tanıtım mevcut veriden üretilir.

## Kesinlikle olmaması gerekenler

- Başka bir uygulamaya benzeme (Instagram gradyanı, mavi, Google Fotoğraflar ızgarası).
- Akıcılık kaybı: Arda'nın vivo V2441'inde release build ile ölçülen jank artmamalı. Bulanıklık/cam efekti yok.
- Film motifinin her yere yayılması (kitsch). Film delikleri yalnızca Kaldığın yer kartında ve makara şeridinde.

## Kısıtlar

- Jetpack Compose; mevcut `ui/Colors.kt` (`appColors`, `InstaBlue`, `LikeRed`…) tüm ekranlarda kullanılıyor.
- Fontlar gömülü olmalı (internetsiz çalışır): Big Shoulders Display, Inter (ikisi de OFL).
- Türkçe büyük harf (`Locale("tr")`), uygulama İngilizce de destekliyor (`ozellik/ingilizce` dalı).
- Yandaki oturum `main` ve `ozellik/v2-preload-manager` üzerinde oynatıcı içiyle çalışıyor; bu iş `ozellik/v2-kimlik` dalında, ayrı worktree'de yapılır. ReelsScreen.kt'de çakışma riski var → oynatıcı aşaması en sona, diğer dal birleştikten sonra.

## Yaklaşımlar

| Yön | Fikir | Neden seçilmedi / seçildi |
|---|---|---|
| A · Makara | Film makarası, sıcak siyah + kehribar | Kimliği verdi; tek başına "çok izlenen" fikri yoktu |
| B · Mozaik | Bento, siyah + fosfor yeşili, çok izlenen büyür | Fikri güçlü ama görünüşü genel "modern uygulama"; isimle bağı yok |
| C · Zaman Tüneli | Açık tema, dikey zaman çizgisi, yıl şeridi | Video uygulamasında açık zemin parlıyor |
| **D · Makara + Mozaik** ✓ | A'nın görünüşü + B'nin "çok izlenen büyür"ü + C'nin yıl şeridi + B'nin yüzen alt menüsü | Arda seçti |

## Kararlaşanlar

- Arda'nın kararları: D yönü; kopya yok; kısa tanıtım olacak; yüzen alt menü; oynatıcı ilerleme çubuğu ince olacak.
- Arda "sence?" diye sordu, benim kararım: tek koyu tema; mozaik köşesi 8 px; alt menü yazısız simge (tanıtım açıklar); Aylık özet + Başarımlar "Sen" sekmesine taşınır.
- Özellik → yer eşlemesi (DESIGN.md > Layout'ta da var):
  - Medya seçici → başlık altı VİDEO · FOTO · HEPSİ
  - Sınırlı erişim / yedeklenmiyor → ince kehribar kenarlı uyarı şeridi
  - Kaldığın yer (ResumeStore) → film delikli kart; Son izlediklerin → altında küçük kare sırası
  - Tarihte bugün (Memories) → o gün anı varsa "Tarihte bugün makarası", yoksa ay makarası
  - Görüntülenme sayıları (StatsStore.viewsOf) → En çok sarılanlar
  - Tüm videolar / Favoriler / Beğendiklerin → Kutular ilk sıra; klasörler + gizli klasör (PIN) davranışı aynı
  - Temizlik kartı → tek satır, yalnızca boşaltılacak yer varsa
  - Açık/koyu düğmesi → kalkar
  - Ayarlar, kurulum, güncelleme, başarım kutlaması → aynı yerde, yeni token'larla
  - Oynatıcı: sayaç başlığı, sağ sütun (Beğen/Favori/Hız/Kilit), ⋮ (kırp, uyku, PiP, sil), ince parçalı ilerleme, sararken film şeridi önizlemesi

## Aşamalar (her biri ayrı sürüm, her birinde vivo ölçümü)

1. **Temel**: `DESIGN.md` token'ları → `Colors.kt` / `Type.kt` / `Shape.kt`; fontlar; açık tema ve InstaBlue/gradyan kaldırılır. Bütün ekranlar yeni renge geçer.
2. **Ana ekran**: AlbumsScreen yeni düzen, yüzen alt menü (Makara · Akış · Sevdiklerim · Sen), yıl şeridi, En çok sarılanlar, Sen sekmesi.
3. **Tanıtım**: 3 kart + Ayarlar'da "Tanıtımı tekrar göster".
4. **Oynatıcı**: ReelsScreen kabuğu (preload dalı birleştikten sonra).

## Yapımcıya kalanlar

Animasyon süreleri, menü geçişleri, boş durum metinleri, İngilizce metinler, yıl şeridinin az videolu kütüphanede gizlenmesi (ör. tek yıl varsa gösterme), sararken şeridin büyüme animasyonu — DESIGN.md'ye uygun olduğu sürece yapımcının kararı.

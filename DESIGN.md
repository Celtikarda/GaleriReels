---
version: alpha
name: GaleriReels — Makara
description: GaleriReels 2.0 görsel kimliği. Galeri bir film makarasıdır; videolar karedir, klasörler kutudur.
colors:
  primary: "#FFB547"
  on-primary: "#120F0D"
  background: "#120F0D"
  on-background: "#F5EDE3"
  surface: "#1E1915"
  on-surface: "#F5EDE3"
  surface-high: "#2B241F"
  outline: "#3A302A"
  on-surface-soft: "#D8CCBE"
  on-surface-muted: "#A89B8C"
  scrim: "#120F0D"
  error: "#B3261E"
  on-error: "#FFFFFF"
typography:
  display:
    fontFamily: Big Shoulders Display
    fontSize: 30px
    fontWeight: "800"
    lineHeight: 32px
    letterSpacing: 0.01em
  display-lg:
    fontFamily: Big Shoulders Display
    fontSize: 40px
    fontWeight: "800"
    lineHeight: 41px
    letterSpacing: 0.01em
  counter:
    fontFamily: Big Shoulders Display
    fontSize: 20px
    fontWeight: "800"
    lineHeight: 20px
  counter-sm:
    fontFamily: Big Shoulders Display
    fontSize: 13px
    fontWeight: "600"
    lineHeight: 14px
  title:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: "600"
    lineHeight: 20px
  body:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: "400"
    lineHeight: 22px
  body-sm:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: "400"
    lineHeight: 18px
  label:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: "600"
    lineHeight: 18px
  overline:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: "600"
    lineHeight: 16px
    letterSpacing: 0.16em
rounded:
  frame: 2px
  sm: 4px
  DEFAULT: 6px
  tile: 8px
  control: 18px
  bar: 24px
  full: 9999px
spacing:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 20px
  xl: 28px
  margin: 20px
  touch: 44px
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    height: 44px
    padding: 16px
  button-danger:
    backgroundColor: "{colors.error}"
    textColor: "{colors.on-error}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    height: 44px
    padding: 16px
  screen:
    backgroundColor: "{colors.background}"
    textColor: "{colors.on-background}"
    typography: "{typography.body}"
  card-resume:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.DEFAULT}"
    padding: 10px
  film-strip:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    typography: "{typography.counter-sm}"
    rounded: "{rounded.DEFAULT}"
  perforation:
    backgroundColor: "{colors.background}"
    rounded: "{rounded.frame}"
    width: 12px
    height: 8px
  section-title:
    textColor: "{colors.on-background}"
    typography: "{typography.display}"
  overline:
    textColor: "{colors.on-surface-muted}"
    typography: "{typography.overline}"
  chip-month:
    backgroundColor: "{colors.background}"
    textColor: "{colors.on-background}"
    typography: "{typography.counter}"
    rounded: "{rounded.DEFAULT}"
    height: 56px
  chip-month-active:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.counter}"
    rounded: "{rounded.DEFAULT}"
    height: 56px
  chip-month-outline:
    backgroundColor: "{colors.outline}"
  tile-mosaic:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    typography: "{typography.counter}"
    rounded: "{rounded.tile}"
  canister:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    typography: "{typography.title}"
    rounded: "{rounded.DEFAULT}"
    padding: 10px
  canister-rim:
    backgroundColor: "{colors.surface-high}"
  nav-bar:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface-muted}"
    rounded: "{rounded.bar}"
    height: 64px
  nav-item-active:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.control}"
    width: 64px
    height: 48px
  year-rail:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface-muted}"
    typography: "{typography.counter-sm}"
    rounded: "{rounded.full}"
    width: 38px
  player-action:
    backgroundColor: "{colors.scrim}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.control}"
    size: 52px
  player-meta:
    textColor: "{colors.on-surface-soft}"
    typography: "{typography.body-sm}"
  seek-bar:
    backgroundColor: "{colors.primary}"
    rounded: "{rounded.frame}"
    height: 10px
  tutorial-card:
    backgroundColor: "{colors.background}"
    textColor: "{colors.on-background}"
    typography: "{typography.display-lg}"
---

## Overview

GaleriReels'in kimliği **Makara**: uygulamanın adındaki "reel" kelimesinden doğar. Galeri bir film makarasıdır — videolar **kare**, klasörler film **kutusu**, kullanıcının izleme alışkanlığı makaranın şeklini belirler ("çok izlediğin büyür"). Hedef duygu: sıcak, sinemaya özgü, sakin ama canlı; insan aşağı kaydırdıkça bir sonraki makarayı görmek istesin.

Bu bir başka uygulamanın kopyası değildir: Instagram gradyanı, iOS grileri ve "InstaBlue" tamamen kalkar. Tek, koyu tema vardır (açık tema 2.0'da yok). Kaynak taslak: Design tuvalindeki "D · Makara + Mozaik", "D · Oynatıcı", "D · Tanıtım".

## Colors

- **Makara siyahı** (`background` #120F0D): her ekranın zemini. Saf siyah değil, sıcak; videoyu öne çıkarır, gece gözü yormaz.
- **Kehribar** (`primary` #FFB547): imza renk. Yalnızca *şu an* ve *eylem* için: seçili sekme, seçili ay, oynat/devam düğmesi, ilerleme, kare numaraları, çok izlenen sayacı. Bir ekranda en fazla 3–4 kehribar öğe olsun; her şey kehribar olursa hiçbir şey öne çıkmaz.
- **Kare zemini** (`surface` #1E1915): kartlar, film şeridi gövdesi, alt menü, yıl şeridi.
- **Kutu kenarı** (`surface-high` #2B241F) ve **çizgi** (`outline` #3A302A): ayırıcılar, seçili olmayan ay çerçevesi, film kutusu kenarı.
- **Metin** (`on-background` #F5EDE3 krem beyaz), **yumuşak metin** (`on-surface-soft` #D8CCBE, video üstü bilgi), **sönük metin** (`on-surface-muted` #A89B8C, tarih, sayı, üst etiket).
- **Perde** (`scrim` #120F0D): video üstündeki düğmelerin arkası; %55 opaklıkla kullanılır, üst ve alt kenar karartması da bu renkten saydama geçer.
- **Sil** (`error` #B3261E): yalnızca silme onayı ve silme modu.

## Typography

- **Big Shoulders Display** (dar, uzun, afiş harfi): bölüm başlıkları BÜYÜK HARFLE ("KASIM MAKARASI", "KUTULAR"), kare sayaçları (0214), ay adları (KAS), sayılar (×24, '26). Kimliğin sesi budur.
- **Inter**: geri kalan her şey — video adı, açıklama, düğme, ayarlar.
- Türkçe büyük harfe dikkat: "i" → "İ" (GALERİREELS, KASIM, EKİ). Kotlin'de `uppercase(Locale("tr"))`.
- Sayaçlar dört hanelidir ve sıfırla doldurulur: 0001, 0214, 1284.
- Fontlar uygulamaya gömülür (res/font), internet gerekmez.

## Layout

- Kenar boşluğu iki yanda 20 dp; içerik her zaman ortalı. Yıl şeridi içeriğe yer ayırmaz: yalnızca kaydırırken (ve kaydırma bittikten ~1,5 sn sonrasına kadar) içeriğin üstünde belirir, sonra kaybolur.
- Ana ekran sırası: başlık (logo, ara, ayarlar) → medya seçici (VİDEO · FOTO · HEPSİ) → gerekirse uyarı şeridi → **Kaldığın yer** → son izlediklerin sırası → **Bu ay** kutusu (kehribar zemin; izleme süresi + video sayısı; dokununca Aylık özet) ve yanında temizlik kutusu (yalnızca boşaltılacak yer varsa, yoksa Bu ay tam genişlik) → ay sayacı → **ay / tarihte bugün makarası** → **En çok sarılanlar** → **Kutular** (önce Tüm videolar, Favoriler, Beğendiklerin; sonra klasörler).
- Alt menü yüzer: Makara · Akış · Sevdiklerim · Sen. Sayfa sonunda 110 dp boşluk bırak ki içerik menünün altında kalmasın.
- "Sen" sekmesi: aylık özet, başarımlar, izleme süresi.
- Dokunma alanı en az 44 dp.

## Elevation & Depth

Derinlik gölgeyle değil **katmanla** verilir: zemin → kare zemini → kehribar. Tek gölge yüzen alt menüdedir (aşağı doğru yumuşak, koyu). Bulanıklık (blur) ve cam efekti yok — eski telefonlarda akıcılığı bozar.

## Shapes

- Film kareleri neredeyse köşelidir (2 px); kartlar 6 px; mozaik kutuları 8 px; düğme kutuları 18 px; alt menü 24 px; hap düğmeler tam yuvarlak.
- **Film delikleri** (12×8 dp, 2 px köşe, zemin renginde) yalnızca iki yerde: "Kaldığın yer" kartı ve ay makarası şeridi. Başka hiçbir yerde kullanılmaz; tekrarlanırsa retro/kitsch olur.
- Film kutusu (klasör): oval kapak + 4 dp kenar.

## Components

- **Kaldığın yer kartı**: üst ve alt sırada film delikleri, ortada videonun karesi, altında 4 dp kehribar ilerleme; altında video adı + "Devam et". Sayaç üst etikette: "KALDIĞIN YER · KARE 0214".
- **Makara şeridi**: yatay kayar, her karenin sol üstünde kehribar numara, sağ altında süre.
- **En çok sarılanlar**: 3 sütunlu yoğun ızgara; kutu boyutu izlenme sayısına göre (en çok izlenen 2×2, sonra 1×2 / 2×1, gerisi 1×1). 10+ izlenende "×24" büyük ve kehribar.
- **Yıl şeridi**: sağ kenarda yüzen dikey hap ('26 '25 …), %92 opak kare zemini + gölge; yalnızca kaydırırken görünür; aktif yıl kehribar; dokununca Tüm videolar o yıldan açılır.
- **Oynatıcı**: üstte "0214 / 1284" + klasör adı; sağda Beğen (aktifken kehribar dolu), Favori, Hız (1×), Kilit; ⋮ menüsünde kırp, uyku, PiP, sil. Altta "12. kez izliyorsun" ve "Sıradaki ↑ 0215".
- **İlerleme çubuğu**: dinlenirken 10 dp yüksekliğinde 12 parçalı ince şerit (izlenen kısım kehribar, kalan %28 krem); 3 dp kehribar oynatma çizgisi. Parmakla sarılırken film şeridine büyür ve kare önizlemesi gösterir, bırakınca küçülür.
- **Tanıtım**: ilk açılışta 3 kart (0001 kaydır, 0002 kenar hareketleri ve kilit, 0003 çok izlediğin büyür), her zaman "Geç"; son kartta "Başla". Ayarlar'dan "Tanıtımı tekrar göster".

## Do's and Don'ts

- Do: kehribarı az kullan; sayıları Big Shoulders ile, sıfır dolgulu yaz.
- Do: her yeni ekranı önce bu dosyaya göre tasarla; görünüş değişirse önce bu dosyayı güncelle.
- Do: akıcılığı Arda'nın vivo'sunda release build ile ölç.
- Don't: gradyan zemin, cam/bulanıklık, emoji, Instagram/Google Fotoğraflar'a benzeyen öğe.
- Don't: film deliğini kart, ayar veya menüde kullanma.
- Don't: açık tema renkleri ekleme (2.0 tek koyu tema).

# Reels içinden silme modu — plan

Normal kaydırırken silinecek bir video çıkınca Temizlik'e gidip onu yeniden aramak gerekmesin.

## Tasarım (seçenek A)
- Reels üst barına 🗑 düğmesi. Basınca **aynı videoda kalınır**, o klasörde silme modu açılır.
- Mod açıkken bugünkü Temizlik davranışı: sola kaydır = işaretle, alttaki `CleanupBar` ile geri al
  veya tek onayla sil.
- Mod kapatılırken işaretli video varsa "Silinsin mi?" sorulur.
- Mod içinde filtre çipleri: **10 sn'den kısa**, **hiç izlenmemiş**, **en çok yer kaplayan**.
- Beğenilen / favori videolar klasör modunda görünür ama işaretlenirken uyarı çıkar.
- Ana ekrandaki Temizlik kartı aynen kalır.

## Bugünkü kod (başlangıç noktası)
- `ReelsScreen.kt`: `cleanup = album.id == Album.CLEANUP`; `onDelete` sadece `cleanup` iken dolu,
  `CleanupBar` da öyle. → Bu koşul `cleanup || deleteMode` olacak.
- `GalleryViewModel.openCleanup()`: ayrı `Album.CLEANUP` akışı (önce izlenmemiş, sonra en büyük).

## Yapılacaklar
- [x] Plan (bu dosya)
- [ ] `deleteMode` durumu + üst bar 🗑 düğmesi; mod açıkken kaydırıp işaretleme ve `CleanupBar`
  normal klasörde çalışır
- [ ] Moddan çıkarken "Silinsin mi?" onayı
- [ ] Beğeni / favori işaretlenirken uyarı
- [ ] Filtre çipleri (10 sn'den kısa, hiç izlenmemiş, en çok yer kaplayan)
- [ ] Telefonda test, README güncelle, sürüm yayınla

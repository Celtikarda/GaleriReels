
# libVLC'nin yerel kodu (libvlcjni.so) Java sınıflarını, alanlarını ve metotlarını isimle arıyor
# (ör. IMedia$Track); R8 bunları silince ya da adını değiştirince VLC açılırken uygulama kapanıyor
-keep class org.videolan.** { *; }
-keep interface org.videolan.** { *; }

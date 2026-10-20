package com.celtikarda.galerireels

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import kotlin.concurrent.thread

class GaleriApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // Çözücü listesini ilk okumak ~250 ms sürüyor; ilk video açılırken beklemesin
        thread(name = "decoder-warmup") { DecoderCheck.warmUp() }
    }

    // Coil, VideoThumb modelinden videoların küçük resmini çizebilsin
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(VideoThumbFetcher.Factory())
                add(VideoThumbKeyer())
            }
            .crossfade(true)
            .build()
}

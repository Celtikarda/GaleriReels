package com.celtikarda.galerireels

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade

class GaleriApp : Application(), SingletonImageLoader.Factory {

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

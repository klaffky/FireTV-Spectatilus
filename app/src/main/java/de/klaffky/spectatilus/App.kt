package de.klaffky.spectatilus

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import de.klaffky.spectatilus.data.JellyfinApi
import de.klaffky.spectatilus.data.LiveTvStore
import de.klaffky.spectatilus.data.Session

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
        LiveTvStore.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(JellyfinApi.http)
        .crossfade(true)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(150L * 1024 * 1024).build() }
        .build()
}

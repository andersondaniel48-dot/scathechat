package dev.daniel.scathechat

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import dev.daniel.scathechat.data.Repo
import dev.daniel.scathechat.service.Notifier

class ScatheApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        Notifier.createChannels(this)
        Repo.init(this)
        if (CrashGuard.safeMode) {
            // crashing on launch: skip everything optional and go straight for the latest fix
            dev.daniel.scathechat.net.Updater.fixNow(this)
            return
        }
        dev.daniel.scathechat.net.Push.ensureInit(this)
        dev.daniel.scathechat.net.Updater.startAuto(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }
        .crossfade(120)
        .build()
}

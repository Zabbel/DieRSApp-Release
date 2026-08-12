package com.zabbel.diersapp

import android.app.Application
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
// import coil3.gif.AnimatedImageDecoder // Temporär auskommentiert bis Sync
// import coil3.gif.GifDecoder // Temporär auskommentiert bis Sync
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DieRSApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        try {
            // Loading the SQLCipher native library
            System.loadLibrary("sqlcipher")
            Log.d("DieRSApp", "SQLCipher native library loaded successfully via System.loadLibrary")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("DieRSApp", "Failed to load SQLCipher native library", e)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                // Sobald das Projekt synchronisiert ist, kannst du die GIF-Decoder hier hinzufügen:
                // if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .build()
    }
}

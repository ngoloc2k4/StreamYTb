package com.example

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import org.conscrypt.Conscrypt
import java.security.Security

class StreamApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        setupSecurityProvider()
    }

    /**
     * Installs Conscrypt as the primary security provider to guarantee
     * modern TLS 1.2 / 1.3 support on Android 6.0+ (API 23+) devices.
     */
    private fun setupSecurityProvider() {
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        } catch (e: Throwable) {
            // Fallback to default system security provider if Conscrypt fails to load
            e.printStackTrace()
        }
    }

    /**
     * Configures Coil ImageLoader optimized for low-memory devices:
     * - Bitmap Config: RGB_565 (saves 50% RAM compared to ARGB_8888)
     * - Memory cache capped at 20% of available RAM
     * - 50MB disk cache
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .bitmapConfig(Bitmap.Config.RGB_565)
            .crossfade(true)
            .build()
    }
}

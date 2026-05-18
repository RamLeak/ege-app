package com.daniel.ege100

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Глобальный Coil ImageLoader с поддержкой SVG.
 *
 * `coil-svg` подключён в Stage 1, но `SvgDecoder.Factory()` нужно явно
 * зарегистрировать в ImageLoader'е приложения — Coil использует ленивую
 * инициализацию глобального лоадера, без этого SVG не декодируется.
 *
 * Кеши: в памяти 25% от runtime maxMemory (формул много, лёгкие), на диске
 * 100 MB в `cacheDir/svg_cache` — этого хватает на навигацию по нескольким
 * сотням задач без повторного декодирования.
 */
class EgeApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("svg_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}

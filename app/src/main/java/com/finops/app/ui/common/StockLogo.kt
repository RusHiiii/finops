package com.finops.app.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.finops.app.domain.model.AssetType
import com.finops.app.domain.model.Symbol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Suffixe de place façon Yahoo attendu par Financial Modeling Prep (place US : aucun). */
private val EXCHANGE_SUFFIX = mapOf(
    "EPA" to ".PA", "AMS" to ".AS", "ETR" to ".DE", "FRA" to ".F", "EBR" to ".BR", "ELI" to ".LS",
    "BIT" to ".MI", "BME" to ".MC", "SWX" to ".SW", "LON" to ".L", "TSE" to ".TO", "STO" to ".ST",
    "CPH" to ".CO", "HEL" to ".HE", "VIE" to ".VI",
)
private val US_EXCHANGES = setOf("NASDAQ", "NYSE", "NYSEARCA", "NYSEAMERICAN", "BATS", "OTCMKTS")

/** URL du logo d'une action (Financial Modeling Prep, public) ; null pour un ETF, un type inconnu ou une place non gérée. */
fun stockLogoUrl(symbol: Symbol, type: AssetType?): String? {
    if (type != AssetType.STOCK) return null
    val suffix = if (symbol.exchange in US_EXCHANGES) "" else EXCHANGE_SUFFIX[symbol.exchange] ?: return null
    return "https://financialmodelingprep.com/image-stock/${symbol.ticker.replace('.', '-')}$suffix.png"
}

/**
 * Chargement des logos : cache mémoire, puis disque (cacheDir/logos, 30 jours), puis réseau.
 * Un logo introuvable est mémorisé (fichier vide) pour ne pas le redemander.
 */
private object LogoLoader {
    private const val MAX_AGE_DAYS = 30L
    private val memory = LruCache<String, Bitmap>(64)
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun cached(url: String): Bitmap? = memory.get(url)

    suspend fun load(url: String, cacheDir: File): Bitmap? {
        memory.get(url)?.let { return it }
        if (url in missing) return null
        return locks.getOrPut(url) { Mutex() }.withLock {
            memory.get(url) ?: withContext(Dispatchers.IO) { fromDisk(url, cacheDir) ?: fromNetwork(url, cacheDir) }
                ?.also { memory.put(url, it) }
        }
    }

    private fun file(url: String, cacheDir: File) = File(File(cacheDir, "logos").apply { mkdirs() }, url.hashCode().toUInt().toString() + ".png")

    private fun fromDisk(url: String, cacheDir: File): Bitmap? {
        val f = file(url, cacheDir)
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > TimeUnit.DAYS.toMillis(MAX_AGE_DAYS)) return null
        if (f.length() == 0L) { missing += url; return null }
        return BitmapFactory.decodeFile(f.path)
    }

    private fun fromNetwork(url: String, cacheDir: File): Bitmap? = runCatching {
        val c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 8_000; readTimeout = 8_000 }
        try {
            val f = file(url, cacheDir)
            when (c.responseCode) {
                200 -> {
                    val bytes = c.inputStream.use { it.readBytes() }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { f.writeBytes(bytes) }
                }
                404 -> { f.writeBytes(ByteArray(0)); missing += url; null }
                else -> null
            }
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}

/** Logo à [url] (null tant qu'il n'est pas chargé ou s'il n'existe pas). */
@Composable
fun rememberLogo(url: String?): ImageBitmap? {
    if (url == null) return null
    val cacheDir = LocalContext.current.cacheDir
    val bitmap by produceState(LogoLoader.cached(url)?.asImageBitmap(), url) {
        if (value == null) value = LogoLoader.load(url, cacheDir)?.asImageBitmap()
    }
    return bitmap
}

package kr.co.reevl.ontheway.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kr.co.reevl.ontheway.data.CarBrand
import kr.co.reevl.ontheway.format.ReevlFormat
import kr.co.reevl.ontheway.ui.theme.CarTheme
import kr.co.reevl.ontheway.ui.theme.LocalMotion
import okhttp3.OkHttpClient
import okhttp3.Request
import kr.co.reevl.design.car.ReevlCarTheme.Size as CarSize

/**
 * Brand avatar — port of components/common/AptAvatar.tsx:10-16 (+ AvatarRotator :24-37) and Avatar.tsx:9-37.
 *  · Logo ONLY from the server's builder-gated `brandUrl(s)` (A7 `brand.logoUrls`); no name-based guessing (memory
 *    reevl-brand-avatar-gate: 296 wrong 롯데캐슬 logos when names were matched).
 *  · Logo: white circle, `object-cover` (Avatar.tsx `style={{background:'#fff'}}`), skeleton while loading.
 *  · No logo / load failed: `color` at 14 % behind the first letter in `color`, letter = diameter × 0.42, weight 800.
 *  · JV (several logos): dissolve in place every 6 s, 600 ms (AptAvatar.tsx AvatarRotator) — still while driving.
 * Without an API answer (offline, office stops) the initial circle is computed locally exactly like the web:
 * initial = `(name || '아').trim()[0]`, colour = `avatarColor(cid || name || 'x')`.
 */
@Composable
fun AptAvatar(name: String, cid: String?, brand: CarBrand?, size: Dp, modifier: Modifier = Modifier) {
    val initial = brand?.avatar?.initials?.takeIf { it.isNotBlank() } ?: (name.trim().ifEmpty { "아" }.take(1))
    val color = parseHex(brand?.avatar?.color) ?: parseHex(ReevlFormat.avatarColor(cid?.ifEmpty { null } ?: name.ifEmpty { "x" }))!!
    val urls = brand?.logoUrls?.ifEmpty { listOfNotNull(brand.logoUrl) } ?: emptyList()
    if (urls.size <= 1) {
        AvatarCircle(urls.firstOrNull(), initial, color, size, brand?.avatar?.fontScale ?: 0.42f, modifier)
    } else {
        var step by remember(urls) { mutableIntStateOf(0) }
        val motion = LocalMotion.current
        LaunchedEffect(urls, motion) { while (motion) { delay(6_000); step++ } }
        Box(modifier.size(size)) {
            urls.forEachIndexed { i, u ->
                val a by animateFloatAsState(if (i == step % urls.size) 1f else 0f, tween(600), label = "jv")
                AvatarCircle(u, initial, color, size, 0.42f, Modifier.alpha(a))
            }
        }
    }
}

@Composable
private fun AvatarCircle(url: String?, initial: String, color: Color, size: Dp, fontScale: Float, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val state by produceState<LogoState>(if (url == null) LogoState.None else LogoLoader.cached(url)?.let { LogoState.Ready(it) } ?: LogoState.Loading, url) {
        // The state survives a new url (produceState's holder is not keyed), so always resolve THIS url — a reused avatar slot
        // (compare page ‹ ›, recycled rows) must not keep the previous complex's logo.
        value = when {
            url == null -> LogoState.None
            else -> LogoLoader.cached(url)?.let { LogoState.Ready(it) }
                ?: run { value = LogoState.Loading; LogoLoader.load(ctx, url)?.let { LogoState.Ready(it) } ?: LogoState.Failed }
        }
    }
    val px = with(LocalDensity.current) { size.toPx() }
    when (val s = state) {
        // Car deviation: a 2 dp `--line-strong` ring. The web draws the white logo circle on the grey `--app` page; in the
        // car the rows are white `--bg` cards, where a ring-less white circle disappears (car-ui-spec §2 AptAvatar).
        is LogoState.Ready -> Image(s.bitmap, null, contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape).background(Color.White).border(CarSize.Stroke.thin, CarTheme.colors.lineStrong, CircleShape))
        LogoState.Loading -> Box(modifier.size(size).clip(CircleShape).background(CarTheme.colors.chip))
        else -> Box(modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(initial, color = color, fontWeight = FontWeight.ExtraBold, style = CarTheme.type.title.copy(
                fontSize = with(LocalDensity.current) { (px * fontScale).toSp() }, lineHeight = with(LocalDensity.current) { (px * fontScale * 1.2f).toSp() },
                color = color, letterSpacing = 0.sp))
        }
    }
}

private sealed interface LogoState {
    data object None : LogoState
    data object Loading : LogoState
    data object Failed : LogoState
    data class Ready(val bitmap: ImageBitmap) : LogoState
}

fun parseHex(hex: String?): Color? {
    val h = hex?.removePrefix("#") ?: return null
    if (h.length != 6) return null
    return runCatching { Color(0xFF000000 or h.toLong(16)) }.getOrNull()
}

/**
 * Logo files from the Reevl app origin (`/brands/<브랜드>.png`, no Referer/cookie/token — A7 curl proof). Memory LRU +
 * disk copy under cacheDir (logos are immutable, served with `max-age=31536000, immutable`).
 */
object LogoLoader {
    private val mem = LruCache<String, ImageBitmap>(64)
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    }

    fun cached(url: String): ImageBitmap? = mem.get(url)

    suspend fun load(ctx: Context, url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        mem.get(url)?.let { return@withContext it }
        val f = File(File(ctx.cacheDir, "logos").apply { mkdirs() }, sha1(url) + ".png")
        val bytes = runCatching {
            if (f.exists()) f.readBytes() else client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.bytes()?.also { f.writeBytes(it) }
            }
        }.getOrNull() ?: return@withContext null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.also { mem.put(url, it) }
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

package kr.co.reevl.ontheway.data

import java.io.File

/** The last 저장한 집 list this car saw. Memory first, disk behind it. Cleared on link, logout and 401. */
interface FavCache {
    fun get(): Stamped<FavsResponse>?
    fun put(value: FavsResponse, at: Long)
    fun clear()
}

class MemoryFavCache : FavCache {
    private var v: Stamped<FavsResponse>? = null
    @Synchronized override fun get() = v
    @Synchronized override fun put(value: FavsResponse, at: Long) { v = Stamped(at, value) }
    @Synchronized override fun clear() { v = null }
}

/** One JSON file under [dir] (app-private, no backup). A corrupt file is ignored and deleted. */
class DiskFavCache(private val dir: File) : FavCache {
    private val mem = MemoryFavCache()
    private val ser = Stamped.serializer(FavsResponse.serializer())
    private val file get() = File(dir, "favs.json")

    @Synchronized override fun get(): Stamped<FavsResponse>? = mem.get() ?: file.takeIf { it.exists() }?.let { f ->
        runCatching { Api.json.decodeFromString(ser, f.readText()) }.getOrElse { f.delete(); null }
    }?.also { mem.put(it.value, it.syncedAt) }

    @Synchronized override fun put(value: FavsResponse, at: Long) {
        mem.put(value, at)
        dir.mkdirs()
        val tmp = File(dir, "favs.json.tmp")
        tmp.writeText(Api.json.encodeToString(ser, Stamped(at, value)))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    @Synchronized override fun clear() { mem.clear(); file.delete() }
}

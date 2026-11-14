package kr.co.reevl.ontheway.data

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kr.co.reevl.ontheway.search.ComplexPick
import kr.co.reevl.ontheway.search.PickSource

/**
 * 비교 담기 (≤ 3, car limit — web COMPARE_MAX is larger). Kept on the device only, like the web's compare tray (localStorage),
 * so it survives restarts; cleared with the session (logout / new link). Coordinates come from the server row the user picked
 * (nearby / search / saved), so `안내` from a compare column goes through the same CoordsGate as anywhere else.
 */
@Serializable
data class CompareItem(
    val cid: String,
    val name: String,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val brand: CarBrand? = null,
) {
    fun pick() = ComplexPick(cid, name, address, false, lat, lng, null, brand, source = PickSource.COMPARE)

    companion object {
        fun of(p: ComplexPick) = CompareItem(p.cid, p.displayName, p.address, p.lat, p.lng, p.brand)
    }
}

interface CompareStore {
    fun load(): List<CompareItem>
    fun save(items: List<CompareItem>)
}

class MemoryCompareStore : CompareStore {
    private var items: List<CompareItem> = emptyList()
    override fun load() = items
    override fun save(items: List<CompareItem>) { this.items = items }
}

class DiskCompareStore(private val file: File) : CompareStore {
    private val ser = ListSerializer(CompareItem.serializer())
    @Synchronized override fun load(): List<CompareItem> =
        runCatching { if (file.exists()) Api.json.decodeFromString(ser, file.readText()) else emptyList() }.getOrElse { file.delete(); emptyList() }

    @Synchronized override fun save(items: List<CompareItem>) {
        if (items.isEmpty()) { file.delete(); return }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(Api.json.encodeToString(ser, items))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}

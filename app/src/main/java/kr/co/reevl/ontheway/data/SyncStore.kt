package kr.co.reevl.ontheway.data

import java.io.File
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/**
 * 휴대폰 ↔ 차 동기화 (2026-11-10): the last 최근 본 단지 / 내 메모 / 저장한 조건 this car saw, plus the complexes opened in this car
 * that the server has not taken yet ([pending]). Memory first, disk behind it (app-private, no backup). Cleared on link, logout and
 * 401 — like [FavCache]: it all belongs to the account.
 */
interface SyncStore {
    val recent: Slot<Stamped<RecentResponse>>
    val memos: Slot<Stamped<MemosResponse>>
    val screeners: Slot<Stamped<ScreenersResponse>>
    /** Opened in this car, not yet on the server (newest first, ≤ [kr.co.reevl.ontheway.search.Recent.KEEP]). Full facts for the list. */
    val pending: Slot<List<RecentItem>>
    fun clear() { recent.clear(); memos.clear(); screeners.clear(); pending.clear() }

    interface Slot<T> {
        fun get(): T?
        fun put(value: T)
        fun clear()
    }
}

class MemorySyncStore : SyncStore {
    private class Mem<T> : SyncStore.Slot<T> {
        private var v: T? = null
        @Synchronized override fun get() = v
        @Synchronized override fun put(value: T) { v = value }
        @Synchronized override fun clear() { v = null }
    }
    override val recent: SyncStore.Slot<Stamped<RecentResponse>> = Mem()
    override val memos: SyncStore.Slot<Stamped<MemosResponse>> = Mem()
    override val screeners: SyncStore.Slot<Stamped<ScreenersResponse>> = Mem()
    override val pending: SyncStore.Slot<List<RecentItem>> = Mem()
}

/** One JSON file per slot under [dir]. A corrupt file is ignored and deleted (same rule as [DiskFavCache]). */
class DiskSyncStore(private val dir: File) : SyncStore {
    private inner class FileSlot<T>(private val name: String, private val ser: KSerializer<T>) : SyncStore.Slot<T> {
        private var mem: T? = null
        private val file get() = File(dir, "$name.json")
        @Synchronized override fun get(): T? = mem ?: file.takeIf { it.exists() }?.let { f ->
            runCatching { Api.json.decodeFromString(ser, f.readText()) }.getOrElse { f.delete(); null }
        }?.also { mem = it }
        @Synchronized override fun put(value: T) {
            mem = value
            dir.mkdirs()
            val tmp = File(dir, "$name.json.tmp")
            tmp.writeText(Api.json.encodeToString(ser, value))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
        @Synchronized override fun clear() { mem = null; file.delete() }
    }
    override val recent: SyncStore.Slot<Stamped<RecentResponse>> = FileSlot("recent", Stamped.serializer(RecentResponse.serializer()))
    override val memos: SyncStore.Slot<Stamped<MemosResponse>> = FileSlot("memos", Stamped.serializer(MemosResponse.serializer()))
    override val screeners: SyncStore.Slot<Stamped<ScreenersResponse>> = FileSlot("screeners", Stamped.serializer(ScreenersResponse.serializer()))
    override val pending: SyncStore.Slot<List<RecentItem>> = FileSlot("recent-pending", ListSerializer(RecentItem.serializer()))
}

package kr.co.reevl.ontheway

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.co.reevl.ontheway.navi.NaviAdapter
import kr.co.reevl.ontheway.navi.NaviAdapterFactory
import kr.co.reevl.ontheway.navi.NaviEvent
import kr.co.reevl.ontheway.navi.NaviLog
import kr.co.reevl.ontheway.spike.SpikeStops

/**
 * NaviHelper spike: one button that connects and routes to one hard-coded, verified Reevl coordinate.
 * `adb shell am start -n kr.co.reevl.ontheway/.SpikeActivity --ez autorun true` runs it without a tap.
 */
class SpikeActivity : ComponentActivity() {

    private val log by lazy {
        // Copy of every line in the app sandbox: `adb shell run-as kr.co.reevl.ontheway cat files/otw-navi.log`
        NaviLog(adapter = NaviAdapterFactory.NAME, file = java.io.File(filesDir, "otw-navi.log"))
    }
    private val adapter: NaviAdapter by lazy { NaviAdapterFactory.create(applicationContext, log) }

    private var status by mutableStateOf("대기 중 · adapter=${NaviAdapterFactory.NAME}")
    private val lines = mutableStateListOf<String>()
    private var running: Job? = null

    private var permissionWaiter: CompletableDeferred<Map<String, Boolean>>? = null
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            permissionWaiter?.complete(result)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SpikeScreen(
                    status = status,
                    lines = lines,
                    onGo = { runSpike() },
                )
            }
        }
        if (intent?.getBooleanExtra(EXTRA_AUTORUN, false) == true) runSpike()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_AUTORUN, false)) runSpike()
    }

    private fun refresh() {
        val snapshot = synchronized(log.history) { log.history.toList() }
        lines.clear()
        lines.addAll(snapshot.takeLast(40))
    }

    private fun runSpike() {
        if (running?.isActive == true) return
        val cancelFirst = intent?.getBooleanExtra(EXTRA_CANCEL_FIRST, false) == true
        running = lifecycleScope.launch {
            // SDK callbacks land in log.history from binder threads; keep the screen in sync.
            val ticker = launch {
                while (true) {
                    delay(1_000)
                    refresh()
                }
            }
            ensureNaviPermissions()
            status = "연결 중…"
            refresh()
            val connected = adapter.connect()
            refresh()
            if (connected.isFailure) {
                status = "연결 실패: ${connected.exceptionOrNull()}"
                ticker.cancel()
                return@launch
            }
            if (cancelFirst) {
                NaviAdapterFactory.cancelRoute(adapter)
                delay(3_000)
            }
            val stop = SpikeStops.FIRST
            status = "안내 요청: ${stop.name} (${stop.lat}, ${stop.lng})"
            adapter.requestRoute(stop).collect { ev ->
                status = when (ev) {
                    NaviEvent.Requested -> "요청 보냄 → 콜백 대기"
                    NaviEvent.RoutingCompleted -> "RoutingCompleted (신호 + 목적지 재조회 일치)"
                    is NaviEvent.Failed -> "실패 ${ev.code}: ${ev.message}"
                }
                refresh()
            }
            val dest = adapter.destination()
            status += "\n목적지 조회: " + (dest?.let { "${it.name} (${it.lat}, ${it.lng})" } ?: "없음")
            NaviAdapterFactory.probe(adapter)
            delay(2_000)
            refresh()
            ticker.cancel()
        }
    }

    private suspend fun ensureNaviPermissions() {
        val state = NAVI_PERMISSIONS.associateWith { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        log.sdk("permission", msg = "before " + state.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" })
        val missing = state.filterValues { !it }.keys
        if (missing.isEmpty()) return
        val waiter = CompletableDeferred<Map<String, Boolean>>()
        permissionWaiter = waiter
        permissionLauncher.launch(missing.toTypedArray())
        val result = waiter.await()
        log.sdk("permission", msg = "requested " + result.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" })
    }

    override fun onDestroy() {
        adapter.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AUTORUN = "autorun"
        /** Spike-only: cancelRoute() before requesting, so the run starts with no active destination. */
        const val EXTRA_CANCEL_FIRST = "cancel_first"
        val NAVI_PERMISSIONS = listOf(
            "pleos.car.permission.NAVI_ROUTE",
            "pleos.car.permission.NAVI_ROUTE_SEARCH",
        )
    }
}

@Composable
private fun SpikeScreen(status: String, lines: List<String>, onGo: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1116))
            .padding(32.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text("REEVL · NaviHelper spike", color = Color.White, fontSize = 28.sp)
        Text(
            "${SpikeStops.FIRST.name} · ${SpikeStops.FIRST.address} · build=${BuildConfig.NAVI_ADAPTER}",
            color = Color(0xFFB0B8C4),
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGo, modifier = Modifier.height(72.dp)) {
            Text("첫 장소로 안내", fontSize = 26.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text(status, color = Color(0xFF7FD1FF), fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(lines) { line ->
                Text(line, color = Color(0xFFD7DCE3), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

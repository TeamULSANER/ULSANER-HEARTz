package com.example.pleossetup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = viewModelFactory {
            initializer {
                // ViewModel 생성 시 1회만 초기화한다(재구성·회전 시 재초기화 없음).
                ProbeViewModel(PleosVehicleProbeRepository(application)).also { it.initialize() }
            }
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val vm: ProbeViewModel = viewModel(factory = factory)
                    LifecycleStartEffect(vm) {
                        vm.onScreenVisible(true)
                        onStopOrDispose { vm.onScreenVisible(false) }
                    }
                    ProbeScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun ProbeScreen(vm: ProbeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("PleosSetupProbe", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "Vehicle SDK ${state.sdkVersion ?: "unknown"} · Vehicle Service " +
                    (state.serviceVersion ?: "unknown") + " · Door Position / ${state.targetArea}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Actions(vm)
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        InitCard(state)
                        CapabilityCard(state)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CurrentCard(state)
                        LiveCard(state)
                    }
                }
            } else {
                InitCard(state)
                CapabilityCard(state)
                CurrentCard(state)
                LiveCard(state)
            }
            Text(
                "Control request / Observed state: 이 단계에서는 구현하지 않음 (read·구독 검증 전용).",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(vm: ProbeViewModel) {
    val big = Modifier.heightIn(min = 64.dp)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = vm::reconnect, modifier = big) { Text("Reconnect", fontSize = 20.sp) }
        Button(onClick = vm::checkCapability, modifier = big) { Text("Check support", fontSize = 20.sp) }
        Button(onClick = vm::readCurrent, modifier = big) { Text("Read value", fontSize = 20.sp) }
        Button(onClick = vm::startUpdates, modifier = big) { Text("Start updates", fontSize = 20.sp) }
        OutlinedButton(onClick = vm::stopUpdates, modifier = big) { Text("Stop updates", fontSize = 20.sp) }
    }
}

@Composable
private fun InfoCard(title: String, lines: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            lines.forEach { Text(it, fontSize = 18.sp, fontFamily = FontFamily.Monospace) }
        }
    }
}

@Composable
private fun InitCard(state: ProbeUiState) = InfoCard(
    "Initialization",
    when (val s = state.initialization) {
        InitState.NotInitialized -> listOf("NOT_INITIALIZED")
        is InitState.Returned -> listOf(
            "initialize() RETURNED #${s.attempt} at ${s.atMillis.fmt()}",
            "(반환 = 연결 완료 아님. 실제 read·callback으로 확인)",
        )
        is InitState.Failed -> listOf("FAILED: ${s.detail}", "at ${s.atMillis.fmt()}")
    },
)

@Composable
private fun CapabilityCard(state: ProbeUiState) = InfoCard(
    "Capability",
    when (val c = state.capability) {
        is Probe.Ok -> buildList {
            add("SUPPORTED access=${c.value.accessType} at ${c.atMillis.fmt()}")
            if (c.value.areaBounds.isEmpty()) add("areas: (none reported)")
            c.value.areaBounds.forEach { (area, range) -> add("area $area range ${range.first}..${range.last}") }
            if (state.targetArea !in c.value.areaBounds) add("target ${state.targetArea}: NOT LISTED")
        }
        else -> c.describe()
    },
)

@Composable
private fun CurrentCard(state: ProbeUiState) = InfoCard(
    "Current value (get)",
    state.currentValue.describe { "value=${it ?: "null (unavailable)"}" },
)

@Composable
private fun LiveCard(state: ProbeUiState) {
    val live = state.live
    val sub = when (val s = live.subscription) {
        Subscription.Off -> "OFF"
        Subscription.PausedWhileHidden -> "PAUSED (screen hidden, unregistered)"
        is Subscription.Registered -> "REGISTERED ${s.area} at ${s.atMillis.fmt()} (register 반환)"
        is Subscription.Failed -> "FAILED ${s.code}: ${s.detail}"
    }
    InfoCard(
        "Live updates (callback)",
        listOf(
            "subscription: $sub",
            "callbacks (this session): ${live.callbackCount}",
            "callbacks (total): ${live.totalCallbackCount}",
            "register/unregister: ${live.registerCount}/${live.unregisterCount}",
        ) + live.lastValue.describe { "last callback value=${it ?: "null (unavailable)"}" } +
            listOfNotNull(live.lastError?.let { "last error: $it" }),
    )
}

private fun <T> Probe<T>.describe(ok: (T) -> String = { it.toString() }): List<String> = when (this) {
    Probe.NotRun -> listOf("NOT_RUN")
    Probe.Pending -> listOf("PENDING…")
    is Probe.Ok -> listOf("${ok(value)} at ${atMillis.fmt()}")
    is Probe.Unsupported -> listOf("UNSUPPORTED: $detail", "at ${atMillis.fmt()}")
    is Probe.PermissionDenied -> listOf("PERMISSION_DENIED: $detail", "at ${atMillis.fmt()}")
    is Probe.Failed -> listOf("FAILED $code: $detail", "at ${atMillis.fmt()}")
    is Probe.TimedOut -> listOf("TIMEOUT (${timeoutMillis}ms, no response)", "at ${atMillis.fmt()}")
}

private fun Long.fmt(): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(this))

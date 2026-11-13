package ai.heartz.app.ui.startup

import ai.heartz.app.AppGraph
import ai.heartz.app.data.SessionState
import ai.heartz.app.ui.HeartzRoot
import ai.heartz.app.ui.components.HeartzWordmark
import ai.heartz.app.ui.components.PrimaryButton
import ai.heartz.app.ui.drive.DriveViewModel
import ai.heartz.app.ui.library.LibraryViewModel
import ai.heartz.app.ui.studio.StudioViewModel
import ai.heartz.app.ui.theme.Hz
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HeartzLaunch(graph: AppGraph, drive: DriveViewModel, library: LibraryViewModel, studio: StudioViewModel) {
    var splashFinished by rememberSaveable { mutableStateOf(false) }
    val session by graph.repository.session.collectAsStateWithLifecycle()
    val sync by graph.repository.sync.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { delay(1_400); splashFinished = true }

    if (splashFinished && session is SessionState.SignedOut) {
        Column(Modifier.fillMaxSize().background(Hz.Bg).padding(40.dp), verticalArrangement = Arrangement.Center) {
            Text("프로필을 불러오지 못했어요", style = MaterialTheme.typography.headlineMedium)
            Text(sync.error ?: "잠시 후 다시 시도해 주세요", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(28.dp))
            PrimaryButton("다시 시도", { graph.appScope.launch { graph.repository.bootstrap() } })
        }
        return
    }

    when (startupDestination(splashFinished, session is SessionState.SignedIn)) {
        StartupDestination.SPLASH -> BrandSplash()
        StartupDestination.MAIN -> HeartzRoot(graph, drive, library, studio)
    }
}

@Composable
fun BrandSplash() {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF080E0B)), contentAlignment = Alignment.Center) {
        HeartzWordmark(size = (maxWidth.value * 0.16f).coerceIn(52f, 150f).sp, onDark = true)
    }
}

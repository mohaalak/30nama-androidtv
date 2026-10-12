package com.nama30.tv.ui

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.nama30.tv.data.Api
import com.nama30.tv.data.Prefs
import com.nama30.tv.player.PlayerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        Api.setup(cacheDir)
        // debug hook: adb shell am start -n com.nama30.tv/.ui.MainActivity --el details <postId> [--ez play true]
        val dbg = if (com.nama30.tv.BuildConfig.DEBUG) intent.getLongExtra("details", 0L) else 0L
        val dbgPlay = intent.getBooleanExtra("play", false)
        setContent { AppTheme { Root(onExit = { finishAndRemoveTask() }, debugDetails = dbg, debugPlay = dbgPlay) } }
    }
}

@Composable
private fun Root(onExit: () -> Unit, debugDetails: Long = 0, debugPlay: Boolean = false) {
    var booted by remember { mutableStateOf(false) }
    var bootError by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var confirmExit by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        bootError = false
        try {
            Api.init()
            if (Prefs.loggedIn) Api.refreshUser()
            if (Nav.stack.isEmpty()) Nav.reset(if (Prefs.loggedIn || Prefs.guest) Screen.Home else Screen.Login)
            if (debugDetails > 0) Nav.go(Screen.Details(debugDetails, debugPlay))
            booted = true
        } catch (e: Exception) { bootError = true }
    }
    // session dropped by the server ("login required"): back to the QR screen
    LaunchedEffect(Prefs.loggedIn) { if (booted && !Prefs.loggedIn && !Prefs.guest && Nav.top != Screen.Login) Nav.reset(Screen.Login) }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        when {
            bootError -> ErrorView { attempt++ }
            !booted -> Splash()
            else -> {
                val holder = rememberSaveableStateHolder()
                val top = Nav.top
                val playing = Nav.player != null
                if (top != null && !playing) {
                    // keyed by stack depth + screen so going Back restores scroll positions;
                    // and by language so remembered strings and loaded data are rebuilt after a switch
                    val k = "${Prefs.lang}:${Nav.stack.size}:$top"
                    key(k) { holder.SaveableStateProvider(k) { ScreenContent(top) } }
                    if (top != Screen.Login) key(Prefs.lang) { Rail(Modifier.align(Alignment.CenterStart)) }
                }
                Nav.player?.let { PlayerScreen(it) }
                BackHandler(enabled = !playing) {
                    when {
                        Nav.back() -> {}
                        top != Screen.Home && top != Screen.Login -> Nav.reset(Screen.Home)
                        else -> confirmExit = true
                    }
                }
            }
        }
        Toast()
        if (confirmExit) ConfirmDialog(t("exitTitle"), t("exitBody"), t("exit"), onOk = onExit) { confirmExit = false }
    }
}

@Composable
private fun ScreenContent(s: Screen) {
    when (s) {
        Screen.Login -> LoginScreen()
        Screen.Home -> HomeScreen()
        is Screen.Details -> DetailsScreen(s.id, s.autoplay)
        Screen.Search -> SearchScreen()
        Screen.Browse -> BrowseScreen()
        is Screen.Archive -> ArchiveScreen(s.slug, s.name)
        is Screen.Settings -> SettingsScreen(s.section)
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                Text("30", style = T.Hero.copy(color = C.Accent))
                Text("nama", style = T.Hero)
            }
            Spacer(Modifier.height(24.dp))
            Spinner(28.dp)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, ok: String, danger: Boolean = false, onOk: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val fr = remember { FocusRequester() }
        Column(Modifier.background(C.Surface, R10).padding(28.dp).width(380.dp)) {
            Text(title, style = T.H2.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            Text(body, style = T.Body.copy(color = C.Text2))
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Btn(t("cancel"), modifier = Modifier.focusRequester(fr), onClick = onDismiss)
                Btn(ok, primary = !danger, onClick = { onDismiss(); onOk() })
            }
        }
        AutoFocus(fr)
    }
}

package com.nama30.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import com.nama30.tv.BuildConfig
import com.nama30.tv.data.Api
import com.nama30.tv.data.Prefs
import kotlinx.coroutines.launch

private val SECTIONS = listOf("account", "language", "playback", "about")
/** Set when a language row is picked, so the rebuilt Settings screen refocuses the language list. */
private var focusLangOnce = false

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(section: String) {
    var sec by rememberSaveable { mutableStateOf(section) }
    var confirmLogout by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val secFr = remember { FocusRequester() }
    val paneFr = remember { FocusRequester() }
    var paneFocus by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = paneFocus) { runCatching { secFr.requestFocus() } }

    Column(Modifier.fillMaxSize().background(C.Bg).padding(start = START_PAD.dp, top = 40.dp)) {
        Text(t("settings"), style = T.H1)
        Spacer(Modifier.height(24.dp))
        Row {
            Column(Modifier.width(220.dp).focusRestorer { secFr }.focusGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SECTIONS.forEach { s -> Row0(t(s), selected = s == sec, modifier = if (s == sec) Modifier.focusRequester(secFr) else Modifier, onFocus = { sec = s }) { runCatching { paneFr.requestFocus() } } }
            }
            Spacer(Modifier.width(40.dp))
            Column(Modifier.width(520.dp).onFocusChanged { paneFocus = it.hasFocus }.focusGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (sec) {
                    "account" -> {
                        val u = Prefs.user
                        Info(t("account"), u?.name ?: if (Prefs.loggedIn) "user" else t("guestAcc"))
                        if (Prefs.loggedIn) {
                            Info(t("userType"), u?.type ?: "-")
                            Info(t("expires"), u?.expire ?: t("unknown"))
                            Row0(t("logout"), danger = true, modifier = Modifier.focusRequester(paneFr)) { confirmLogout = true }
                        } else Row0(t("login"), modifier = Modifier.focusRequester(paneFr)) { Nav.reset(Screen.Login) }
                    }
                    "language" -> listOf("en" to "English", "fa" to "فارسی").forEach { (code, label) ->
                        val cur = Prefs.lang == code
                        // the screen is rebuilt on a language change; land back on the chosen row
                        Row0(label, selected = cur, trailing = if (cur) "✓" else null, modifier = if (cur) Modifier.focusRequester(paneFr) else Modifier) {
                            if (!cur) { focusLangOnce = true; Nav.replace(Screen.Settings("language")); setLanguage(code) }
                        }
                    }
                    "playback" -> {
                        var sub by remember { mutableStateOf(Prefs.defaultSub) }
                        var size by remember { mutableIntStateOf(Prefs.subSize) }
                        var auto by remember { mutableStateOf(Prefs.autoplay) }
                        Opt(t("defSubs"), listOf("off" to t("off"), "fa" to t("persian"), "en" to t("english")), sub, Modifier.focusRequester(paneFr)) { sub = it; Prefs.defaultSub = it }
                        Opt(t("subSize"), listOf(38 to t("small"), 46 to t("medium"), 54 to t("large")), size) { size = it; Prefs.subSize = it }
                        Text(t("sample"), style = T.Body.copy(fontSize = (size / 2).sp, fontWeight = FontWeight.Medium), modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp))
                        Opt(t("autoplay"), listOf(true to t("on"), false to t("off")), auto) { auto = it; Prefs.autoplay = it }
                    }
                    "about" -> {
                        Info("30nama TV", BuildConfig.VERSION_NAME)
                        Info(t("unofficial"), "")
                        Info(t("apiHost"), (Api.host ?: "").removePrefix("https://").substringBefore("/api"))
                    }
                }
            }
        }
    }
    AutoFocus(if (focusLangOnce) paneFr.also { focusLangOnce = false } else secFr)
    if (confirmLogout) ConfirmDialog(t("logoutTitle"), t("logoutBody"), t("logout"), danger = true, onOk = {
        scope.launch { Api.logout(); Prefs.changeGuest(false); Nav.reset(Screen.Login) }
    }) { confirmLogout = false }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Row0(label: String, selected: Boolean = false, danger: Boolean = false, trailing: String? = null, modifier: Modifier = Modifier,
                 onFocus: () -> Unit = {}, onClick: () -> Unit) {
    ListItem(
        selected = selected, onClick = onClick,
        headlineContent = { Text(label, style = T.Btn.copy(color = Color.Unspecified)) },
        trailingContent = trailing?.let { { Text(it, style = T.Btn.copy(color = Color.Unspecified)) } },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent, contentColor = if (danger) C.Accent else C.Text2,
            selectedContainerColor = C.Surface, selectedContentColor = C.Text1,
            focusedContainerColor = C.Text1, focusedContentColor = C.Bg, focusedSelectedContainerColor = C.Text1, focusedSelectedContentColor = C.Bg,
        ),
        shape = ListItemDefaults.shape(R6), scale = ListItemDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.height(40.dp).onFocusChanged { if (it.isFocused) onFocus() },
    )
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.Btn.copy(color = C.Text2), modifier = Modifier.weight(1f))
        Text(value, style = T.Btn)
    }
}

/** Option row: OK or Left/Right cycles through values. */
@Composable
private fun <V> Opt(label: String, values: List<Pair<V, String>>, cur: V, modifier: Modifier = Modifier, set: (V) -> Unit) {
    val i = values.indexOfFirst { it.first == cur }.coerceAtLeast(0)
    fun cycle(d: Int) = set(values[(i + d + values.size) % values.size].first)
    Row0(label, trailing = "‹  ${values[i].second}  ›", modifier = modifier.onPreviewKeyEvent {
        if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (it.key) { Key.DirectionLeft -> { cycle(if (rtl) 1 else -1); true }; Key.DirectionRight -> { cycle(if (rtl) -1 else 1); true }; else -> false }
    }) { cycle(1) }
}

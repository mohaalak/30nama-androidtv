package com.nama30.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import com.nama30.tv.R

private data class RailItem(val key: String, val icon: ImageVector, val go: () -> Unit)

private val ITEMS = listOf(
    RailItem("search", Icons.Outlined.Search) { Nav.reset(Screen.Search) },
    RailItem("home", Icons.Outlined.Home) { Nav.reset(Screen.Home) },
    RailItem("movies", Icons.Outlined.Movie) { Nav.reset(Screen.Archive("movies", t("movies"))) },
    RailItem("series", Icons.Outlined.Tv) { Nav.reset(Screen.Archive("series", t("series"))) },
    RailItem("anime", Icons.Outlined.AutoAwesome) { Nav.reset(Screen.Archive("anime", t("anime"))) },
    RailItem("archive", Icons.Outlined.GridView) { Nav.reset(Screen.Browse) },
    RailItem("settings", Icons.Outlined.Tune) { Nav.reset(Screen.Settings("language")) },
    RailItem("account", Icons.Outlined.Person) { Nav.reset(Screen.Settings("account")) },
)

/** Collapsed icon rail on the start edge; expands with labels while it holds focus. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun Rail(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val cur = Nav.railKey()
    val curFr = remember { FocusRequester() }
    Box(modifier.fillMaxHeight()) {
        AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxHeight().width(320.dp).background(Brush.horizontalGradient(listOf(C.Bg, C.Bg.copy(alpha = .92f), Color.Transparent).let { if (rtl) it.reversed() else it })))
        }
        Column(
            Modifier.fillMaxHeight().width(if (open) 220.dp else 56.dp).padding(top = 22.dp, bottom = 22.dp)
                .onFocusChanged { open = it.hasFocus }
                .focusRestorer { if (ITEMS.any { it.key == cur }) curFr else FocusRequester.Default }
                .focusGroup(),
        ) {
            Box(Modifier.padding(start = 14.dp).size(28.dp)) {
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.banner_icon), null)
            }
            Spacer(Modifier.height(90.dp))
            ITEMS.forEachIndexed { i, it ->
                if (it.key == "account") Spacer(Modifier.weight(1f))
                val selected = it.key == cur
                var f by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp).height(36.dp).fillMaxWidth()
                        .background(if (f) C.Text1 else Color.Transparent, R10)
                        .let { m -> if (selected) m.focusRequester(curFr) else m }
                        .onFocusChanged { s -> f = s.isFocused }
                        .clickable { if (!selected || Nav.stack.size > 1) it.go() }
                        .padding(start = 12.dp),
                ) {
                    val col = when { f -> C.Bg; selected -> C.Accent; else -> C.Text2 }
                    Icon(it.icon, null, tint = col, modifier = Modifier.size(20.dp))
                    if (open) { Spacer(Modifier.width(14.dp)); Text(t(it.key), style = T.Btn.copy(color = col, fontWeight = FontWeight.Medium), maxLines = 1) }
                }
            }
        }
    }
}

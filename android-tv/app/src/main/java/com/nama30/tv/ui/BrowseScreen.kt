package com.nama30.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nama30.tv.data.Api
import com.nama30.tv.data.Genre
import com.nama30.tv.data.Menu
import com.nama30.tv.data.Post

@OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun BrowseScreen() {
    var menu by remember { mutableStateOf<Menu?>(null) }
    var error by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) { error = false; menu = try { Api.menu() } catch (e: Exception) { error = true; null } }
    val m = menu
    if (error) { ErrorView { attempt++ }; return }
    if (m == null) { Loading(); return }
    val fm = rememberFocusMem()
    val first = remember { FocusRequester() }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4), modifier = Modifier.fillMaxSize().background(C.Bg),
        contentPadding = PaddingValues(start = START_PAD.dp, end = 40.dp, top = 36.dp, bottom = 60.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Text(t("browse"), style = T.H1) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.focusRestorer().focusGroup()) {
                items(m.categories, key = { it.slug }) { c ->
                    var mod = Modifier.memFocus(fm, "c${c.slug}")
                    if (c == m.categories.first()) mod = mod.focusRequester(first)
                    Pill(c.name, modifier = mod) { Nav.go(Screen.Archive(c.slug, c.name)) }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Text(t("genres"), style = T.H2, modifier = Modifier.padding(top = 10.dp)) }
        items(m.genres, key = { "g" + it.slug }) { g -> GenreTile(g, Modifier.memFocus(fm, "g${g.slug}")) { Nav.go(Screen.Archive(g.slug, g.name)) } }
    }
    if (fm.pending == null) AutoFocus(first)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GenreTile(g: Genre, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = modifier.height(100.dp), shape = CardDefaults.shape(R10),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, C.Text1), shape = R10)),
        colors = CardDefaults.colors(containerColor = C.Surface),
    ) {
        Box(Modifier.fillMaxSize()) {
            Img(g.image, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.3f to Color.Transparent, 1f to Color.Black.copy(alpha = .85f))))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(g.name, style = T.H2)
                g.count?.let { Text("$it", style = T.Small.copy(color = C.Text2)) }
            }
        }
    }
}

/** Archives opened from the rail: their title is a UI string, so it follows the language. */
private val RAIL_SLUGS = setOf("movies", "series", "anime")
private val SORTS = listOf("date" to "newest", "favorite" to "popular", "update" to "updated")

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ArchiveScreen(slug: String, name: String) {
    var sort by rememberSaveable { mutableStateOf("date") }
    val posts = remember(sort) { mutableStateListOf<Post>() }
    var page by rememberSaveable(sort) { mutableIntStateOf(0) }
    var pages by remember(sort) { mutableIntStateOf(1) }
    var total by remember(sort) { mutableIntStateOf(0) }
    var loading by remember(sort) { mutableStateOf(false) }
    var error by remember(sort) { mutableStateOf(false) }
    var want by remember(sort) { mutableIntStateOf(1) }
    val fm = rememberFocusMem()
    val first = remember { FocusRequester() }
    val sortFr = remember { FocusRequester() }

    // load pages up to `want`; `page` is saved, so Back reloads as many pages as were open
    var loaded by remember(sort) { mutableIntStateOf(0) }
    LaunchedEffect(sort, want) {
        val target = maxOf(want, page)
        loading = true
        while (loaded < target && loaded < pages) {
            try {
                val r = Api.archive(slug, loaded + 1, sort)
                val seen = posts.map { it.id }.toHashSet()
                posts += r.posts.filter { seen.add(it.id) }
                pages = r.pages; total = r.total; loaded++; page = maxOf(page, loaded)
                if (r.posts.isEmpty()) break
            } catch (e: Exception) { if (posts.isEmpty()) error = true; break }
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(C.Bg).padding(start = START_PAD.dp, top = 36.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (slug in RAIL_SLUGS) t(slug) else name, style = T.H1)
            if (total > 0) Text("   ${"%,d".format(total)} ${t("results")}", style = T.Small.copy(color = C.Text3), modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.focusRestorer().focusGroup()) {
            SORTS.forEach { (k, label) -> Pill(t(label), selected = sort == k, modifier = if (sort == k) Modifier.focusRequester(sortFr) else Modifier) { if (sort != k) sort = k } }
        }
        Spacer(Modifier.height(16.dp))
        when {
            error -> ErrorView { error = false; want++ }
            posts.isEmpty() -> Loading()
            else -> PosterGrid(posts, cols = 7, fm = fm, first = first, onNearEnd = { if (!loading && page < pages) want = page + 1 })
        }
    }
    if (fm.pending == null) AutoFocus(if (posts.isEmpty()) sortFr else first, posts.isEmpty())
}

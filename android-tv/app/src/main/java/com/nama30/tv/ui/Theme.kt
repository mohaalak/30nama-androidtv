package com.nama30.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.nama30.tv.R
import com.nama30.tv.data.Prefs

object C {
    val Bg = Color(0xFF0E1016)
    val Surface = Color(0xFF1A1D25)
    val Surface2 = Color(0xFF262A34)
    val Text1 = Color(0xFFF3EFE7)
    val Text2 = Color(0xFFB9B6B0)
    val Text3 = Color(0xFF7D7B78)
    val Accent = Color(0xFFEF4A55)
    val Green = Color(0xFF4CC38A)
}

val Vazir = FontFamily(
    Font(R.font.vazir_regular, FontWeight.Normal),
    Font(R.font.vazir_medium, FontWeight.Medium),
    Font(R.font.vazir_bold, FontWeight.Bold),
    Font(R.font.vazir_extrabold, FontWeight.ExtraBold),
)

private fun st(size: Int, w: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = Vazir, fontSize = size.sp, fontWeight = w, color = C.Text1)

object T {
    val Hero = st(30, FontWeight.ExtraBold)
    val H1 = st(24, FontWeight.Bold)
    val H2 = st(16, FontWeight.Bold)
    val Body = st(13)
    val Small = st(11)
    val Eyebrow = st(11, FontWeight.Bold).copy(color = C.Accent)
    val Btn = st(13, FontWeight.Medium)
}

/** UI string in the current language. */
fun t(k: String) = Strings.t(Prefs.lang, k)
val rtl get() = Prefs.lang == "fa"

/** Switches the UI language. Cached server data was fetched with the old c-language header, so it is dropped too. */
fun setLanguage(v: String) {
    if (v == Prefs.lang) return
    Prefs.changeLang(v)
    com.nama30.tv.data.Api.dropLangCaches()
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Text1, onPrimary = C.Bg, background = C.Bg, surface = C.Surface,
            onSurface = C.Text1, surfaceVariant = C.Surface2, onSurfaceVariant = C.Text2, border = C.Text1,
        ),
        typography = Typography(bodyMedium = T.Body, bodyLarge = T.Body, labelLarge = T.Btn, titleMedium = T.H2),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr, content = content)
    }
}

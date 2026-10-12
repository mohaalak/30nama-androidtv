package com.nama30.tv.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.nama30.tv.data.Api
import com.nama30.tv.data.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private fun qrBitmap(text: String): ImageBitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.RGB_565)
    for (y in 0 until m.height) for (x in 0 until m.width) bmp.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    return bmp.asImageBitmap()
}

@Composable
fun LoginScreen() {
    var gen by remember { mutableIntStateOf(0) }
    var code by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var state by remember { mutableStateOf("wait") }   // wait | expired | error | done
    var backdrop by remember { mutableStateOf<String?>(null) }
    val guestFr = remember { FocusRequester() }
    val newFr = remember { FocusRequester() }

    LaunchedEffect(Unit) { backdrop = runCatching { Api.home().heroes.firstOrNull { it.cover != null }?.cover }.getOrNull() }
    LaunchedEffect(gen) {
        state = "wait"; code = null; qr = null
        val (c, url) = try { Api.qrCode() } catch (e: Exception) { state = "error"; newFr.requestFocus(); return@LaunchedEffect }
        code = c; qr = qrBitmap(url)
        val start = System.currentTimeMillis()
        while (isActive) {
            delay(5000)
            if (System.currentTimeMillis() - start > 60_000) { state = "expired"; runCatching { newFr.requestFocus() }; return@LaunchedEffect }
            if (Api.qrPoll(c)) {
                state = "done"; Prefs.changeGuest(false)
                Nav.showToast("${t("welcome")} ${Prefs.user?.name ?: ""}")
                delay(400); Nav.reset(Screen.Home); return@LaunchedEffect
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Img(backdrop, Modifier.fillMaxSize().alpha(.35f))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(C.Bg, C.Bg.copy(alpha = .7f), C.Bg.copy(alpha = .4f)).let { if (rtl) it.reversed() else it })))
        Column(Modifier.align(Alignment.CenterStart).padding(start = START_PAD.dp)) {
            Text(t("signIn"), style = T.Small.copy(color = C.Text3, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            Text(t("scanTitle"), style = T.H1.copy(fontSize = 28.sp))
            Spacer(Modifier.height(20.dp))
            listOf("step1", "step2", "step3").forEachIndexed { i, k ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                    Box(Modifier.size(20.dp).background(C.Accent, CircleShape), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = T.Small.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(Modifier.width(10.dp)); Text(t(k), style = T.Body.copy(color = C.Text2, fontSize = 14.sp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(t("orVisit"), style = T.Body.copy(color = C.Text2))
            Spacer(Modifier.height(6.dp))
            Text(code ?: " ", style = T.H1.copy(letterSpacing = 4.sp))
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn(t("guest"), primary = true, modifier = Modifier.focusRequester(guestFr)) { Prefs.changeGuest(true); Nav.reset(Screen.Home) }
                Btn(t("refresh"), modifier = Modifier.focusRequester(newFr)) { gen++ }
            }
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(230.dp).background(Color.White, R10).padding(14.dp), contentAlignment = Alignment.Center) {
                when {
                    state == "done" -> Icon(Icons.Default.CheckCircle, null, tint = C.Green, modifier = Modifier.size(96.dp))
                    qr != null -> Image(qr!!, null, Modifier.fillMaxSize().alpha(if (state == "expired") .15f else 1f), filterQuality = FilterQuality.None)
                    state == "error" -> Text(t("error"), style = T.Body.copy(color = C.Bg))
                    else -> Spinner()
                }
                if (state == "expired") Text(t("expired"), style = T.H2.copy(color = C.Bg))
            }
            Spacer(Modifier.height(14.dp))
            if (state == "wait") Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.dot().background(C.Accent)); Spacer(Modifier.width(8.dp))
                Text(t("waiting"), style = T.Small.copy(color = C.Text2))
            }
        }
    }
    AutoFocus(guestFr)
}

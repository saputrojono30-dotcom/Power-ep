package id.dlms.dsp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.dlms.dsp.DspViewModel
import id.dlms.dsp.R
import id.dlms.dsp.dsp.Meters
import id.dlms.dsp.dsp.gKey

private val CH_NAMES = listOf("IN 1", "IN 2", "OUT 1", "OUT 2", "OUT 3", "OUT 4")
private fun chColor(i: Int) = if (i < 2) IN_COLOR[i] else OUT_COLOR[i - 2]

@Composable
fun MainScreen(vm: DspViewModel, meters: Meters) {
    val st by vm.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }      // 0 KANAL, 1 SINYAL, 2 SISTEM
    var ch by rememberSaveable { mutableStateOf(2) }       // 0..1 input, 2..5 output
    var dlg by remember { mutableStateOf("") }

    val ctx = remember(st.params, st.rvok) { Ctx(st.params, st.rvok) { k, v, f -> vm.set(k, v, f) } }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1224), BG)))
    ) {
        TopBar(meters, onPreset = { dlg = "preset" }, onSecurity = { dlg = "security" },
            onRefresh = vm::refresh, onDisconnect = vm::disconnect)

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
            when (tab) {
                0 -> KanalPage(st.params, meters, ctx, ch, { ch = it }, vm)
                1 -> SinyalPage(st.params, ctx, ch, { ch = it }, vm, onCopy = { dlg = "copy" })
                else -> SistemPage(st.params, ctx, vm, onPreset = { dlg = "preset" }, onSecurity = { dlg = "security" })
            }
        }

        BottomBar(tab) { tab = it }
    }

    when (dlg) {
        "preset" -> PresetDialog(st.slots, { w, n -> vm.preset(w, n) }) { dlg = "" }
        "copy" -> {
            if (ch >= 2) CopyDialog(ch - 2, { vm.copyOut(ch - 2, it) }) { dlg = "" }
        }
        "security" -> SecurityDialog(vm.settings.value.ssid, vm::validateWifi, vm::changeWifi, vm::resetWifiOnBoard) { dlg = "" }
    }
}

// ------------------------------------------------------------------ bar atas / bawah
@Composable
private fun TopBar(m: Meters, onPreset: () -> Unit, onSecurity: () -> Unit, onRefresh: () -> Unit, onDisconnect: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(painterResource(R.drawable.nj_logo), "NJ Audio", Modifier.size(38.dp))
        Text("Dlms by Jony", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.background(Color(0xFF0E7A3A), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)) {
            Text("● ONLINE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text("DSP ${m.dsp}%", color = if (m.dsp >= 95) RED else if (m.dsp >= 80) AMBER else DIM,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        PillButton("PRESET", Color(0xFF2A5FA8), onPreset)
        PillButton("PUTUSKAN", Color(0xFFC62828), onDisconnect)
        Box {
            Text("⋮", color = TXT, fontSize = 24.sp, modifier = Modifier.clickable { menu = true }.padding(horizontal = 8.dp))
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Baca ulang dari papan") }, onClick = { menu = false; onRefresh() })
                DropdownMenuItem(text = { Text("Keamanan WiFi (password)…") }, onClick = { menu = false; onSecurity() })
            }
        }
    }
}

@Composable
private fun PillButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.height(30.dp).background(color, RoundedCornerShape(8.dp)).border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit) {
    val names = listOf("KANAL", "SINYAL", "SISTEM")
    Row(Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEachIndexed { i, n ->
            val sel = i == tab
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(if (sel) Color(0xFF1B3A6B) else PANEL, RoundedCornerShape(8.dp))
                    .border(1.dp, if (sel) SEL else GRID, RoundedCornerShape(8.dp))
                    .clickable { onTab(i) },
                contentAlignment = Alignment.Center
            ) { Text(n, color = if (sel) Color.White else DIM, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
        }
    }
}

// ------------------------------------------------------------------ halaman KANAL
@Composable
private fun KanalPage(p: Map<String, Double>, m: Meters, c: Ctx, ch: Int, onCh: (Int) -> Unit, vm: DspViewModel) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // ---- enam fader
        Row(Modifier.weight(0.56f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until 6) {
                val isIn = i < 2
                val key = if (isIn) "i${i + 1}.trim" else "o${i - 1}.gain"
                val v = p[key] ?: 0.0
                val on: Boolean; val btn: String
                if (isIn) { on = (p["i${i + 1}.gon"] ?: 0.0) != 0.0; btn = "GATE" }
                else { on = (p["o${i - 1}.mute"] ?: 1.0) == 0.0; btn = if (on) "ON" else "OFF" }
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .background(PANEL, RoundedCornerShape(10.dp))
                        .border(1.dp, if (ch == i) chColor(i) else GRID, RoundedCornerShape(10.dp))
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(String.format("%+.1f", v), color = chColor(i), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onCh(i) })
                    Fader(
                        v, if (isIn) IN_SCALE else OUT_SCALE, chColor(i),
                        m.level[i], if (i >= 2) m.gr[i - 2] else 0f,
                        { nv, fin -> c.set(key, nv, fin) },
                        Modifier.weight(1f).fillMaxWidth()
                    )
                    Box(
                        Modifier.padding(horizontal = 4.dp).fillMaxWidth().height(26.dp)
                            .background(if (on) Color(0xFF0E7A3A) else Color(0xFFC62828), RoundedCornerShape(6.dp))
                            .clickable {
                                if (isIn) c.set("i${i + 1}.gon", if (on) 0.0 else 1.0, true)
                                else c.set("o${i - 1}.mute", if (on) 1.0 else 0.0, true)
                            },
                        contentAlignment = Alignment.Center
                    ) { Text(btn, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    Text(CH_NAMES[i], color = DIM, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp).clickable { onCh(i) })
                }
            }
        }

        // ---- routing + grafik crossover
        Column(Modifier.weight(0.44f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RoutingDiagram(p, (ch - 2).coerceAtLeast(0), { onCh(it + 2) }, Modifier.fillMaxWidth().weight(0.46f))
            Text("MAGNITUDE XOVER", color = DIM, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            ResponsePlot(
                p, if (ch >= 2) Active(true, ch - 2) else Active(false, ch), { k, v, f -> vm.set(k, v, f) },
                Modifier.fillMaxWidth().weight(0.54f), fill = true, showChips = false, showHint = false
            )
        }
    }
}

// ------------------------------------------------------------------ halaman SINYAL
@Composable
private fun SinyalPage(p: Map<String, Double>, c: Ctx, ch: Int, onCh: (Int) -> Unit, vm: DspViewModel, onCopy: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(0) }
    val isOut = ch >= 2
    val pages = if (isOut) outPages else inPages
    val summaries = if (isOut) outSummaries(c, ch - 2) else inSummaries(c, ch)
    val pg = page.coerceIn(0, pages.size - 1)

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(0.5f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 6) {
                    val sel = i == ch
                    Box(
                        Modifier.weight(1f).height(30.dp)
                            .background(if (sel) chColor(i).copy(alpha = 0.2f) else PANEL, RoundedCornerShape(8.dp))
                            .border(1.dp, if (sel) chColor(i) else GRID, RoundedCornerShape(8.dp))
                            .clickable { onCh(i); page = 0 },
                        contentAlignment = Alignment.Center
                    ) { Text(CH_NAMES[i], color = if (sel) chColor(i) else DIM, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
            ResponsePlot(
                p, if (isOut) Active(true, ch - 2) else Active(false, ch), { k, v, f -> vm.set(k, v, f) },
                Modifier.fillMaxWidth().weight(1f), fill = true, showChips = true, showHint = false
            )
        }
        Column(
            Modifier.weight(0.5f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChipRow(pages, summaries, pg) { page = it }
            if (isOut) OutPanel(c, ch - 2, pg) { vm.rvApplyAll(ch - 2) } else InPanel(c, ch, pg)
            if (isOut) BigButton("Salin OUT ${ch - 1} ke kanal lain…", onCopy, Modifier.fillMaxWidth(), Color(0xFF2A5FA8))
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ------------------------------------------------------------------ halaman SISTEM
@Composable
private fun SistemPage(p: Map<String, Double>, c: Ctx, vm: DspViewModel, onPreset: () -> Unit, onSecurity: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(0) }
    val rvOn = (p[gKey("rv")] ?: 1.0) != 0.0
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier.weight(0.5f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChipRow(gPages, gSummaries(c), page.coerceIn(0, gPages.size - 1)) { page = it }
            GlobalPanel(c, page.coerceIn(0, gPages.size - 1))
            Spacer(Modifier.height(8.dp))
        }
        Column(
            Modifier.weight(0.5f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Group("REVERB MASTER") {
                Text(if (rvOn) "Reverb aktif. Matikan untuk memutus semua reverb sekaligus." else "Semua reverb dimatikan.",
                    color = DIM, fontSize = 12.sp)
                BigButton(if (rvOn) "Matikan reverb" else "Hidupkan reverb", { vm.toggleRvMaster() }, Modifier.fillMaxWidth(),
                    if (rvOn) Color(0xFF7A2E2C) else Color(0xFFB3402C), enabled = c.rvok)
                if (!c.rvok) Text("Memori reverb di papan tidak cukup — reverb dinonaktifkan.", color = RED, fontSize = 12.sp)
            }
            Group("PRESET") {
                Text("Simpan atau muat 8 slot setelan di dalam papan.", color = DIM, fontSize = 12.sp)
                BigButton("Buka preset…", onPreset, Modifier.fillMaxWidth(), Color(0xFF2A5FA8))
            }
            Group("WIFI & KEAMANAN") {
                Text("Ganti nama dan password WiFi papan.", color = DIM, fontSize = 12.sp)
                BigButton("Keamanan WiFi…", onSecurity, Modifier.fillMaxWidth(), Color(0xFF2A5FA8))
                BigButton("Baca ulang dari papan", { vm.refresh() }, Modifier.fillMaxWidth(), GRID)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

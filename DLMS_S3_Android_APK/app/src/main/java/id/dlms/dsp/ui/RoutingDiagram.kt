package id.dlms.dsp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.dlms.dsp.dsp.N_PEQ

/**
 * Diagram aliran sinyal: IN A / IN B di kiri, empat baris output di kanan.
 * Garis mengikuti pilihan sumber tiap output (IN 1 / IN 2 / MIX); kotak rantai menyala bila blok aktif.
 * Ketuk sebuah baris untuk memilih output itu.
 */
@Composable
fun RoutingDiagram(p: Map<String, Double>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val onSelectNow by rememberUpdatedState(onSelect)
    Canvas(
        modifier
            .background(Color(0xFF0A1226), RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    onSelectNow((off.y / (size.height / 4f)).toInt().coerceIn(0, 3))
                }
            }
    ) {
        val w = size.width; val h = size.height
        val rowH = h / 4f
        fun yRow(o: Int) = rowH * (o + 0.5f)
        val inW = 34.dp.toPx(); val inH = 20.dp.toPx()
        val yA = h * 0.25f; val yB = h * 0.75f
        val x0 = w * 0.34f
        val gap = 3.dp.toPx()
        val labels = listOf("HPF", "PEQ", "LPF", "LIM", "")
        val bw = (w - x0 - 6.dp.toPx() - gap * 4) / 5f
        val bh = (rowH - 8.dp.toPx()).coerceAtMost(22.dp.toPx())

        val paint = android.graphics.Paint().apply {
            textSize = 8.sp.toPx(); isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        fun text(t: String, cx: Float, cy: Float, c: Int) {
            paint.color = c
            drawContext.canvas.nativeCanvas.drawText(t, cx, cy + paint.textSize * 0.35f, paint)
        }

        for (o in 0 until 4) {
            val pre = "o${o + 1}."
            val col = OUT_COLOR[o]
            val muted = (p[pre + "mute"] ?: 1.0) != 0.0
            val src = (p[pre + "src"] ?: 0.0).toInt()
            val yr = yRow(o)

            if (o == selected) {
                drawRoundRect(col.copy(alpha = 0.10f), Offset(2.dp.toPx(), o * rowH + 2.dp.toPx()),
                    Size(w - 4.dp.toPx(), rowH - 4.dp.toPx()), CornerRadius(6.dp.toPx()))
                drawRoundRect(col.copy(alpha = 0.6f), Offset(2.dp.toPx(), o * rowH + 2.dp.toPx()),
                    Size(w - 4.dp.toPx(), rowH - 4.dp.toPx()), CornerRadius(6.dp.toPx()), style = Stroke(1.2.dp.toPx()))
            }
            val lineC = if (muted) GRID else col
            fun link(fromY: Float, wd: Float) {
                val path = Path().apply {
                    moveTo(inW, fromY)
                    val mx = (inW + x0) / 2f
                    cubicTo(mx, fromY, mx, yr, x0 - 2.dp.toPx(), yr)
                }
                drawPath(path, lineC, style = Stroke(wd))
            }
            when (src) {
                0 -> link(yA, 1.8.dp.toPx())
                1 -> link(yB, 1.8.dp.toPx())
                else -> { link(yA, 1.2.dp.toPx()); link(yB, 1.2.dp.toPx()) }
            }

            val on = booleanArrayOf(
                (p[pre + "hpt"] ?: 0.0) != 0.0,
                (1..N_PEQ).any { (p[pre + "p$it.t"] ?: 0.0) != 0.0 },
                (p[pre + "lpt"] ?: 0.0) != 0.0,
                (p[pre + "lim"] ?: 0.0) != 0.0,
                true)
            for (k in 0 until 5) {
                val bx = x0 + k * (bw + gap)
                val lit = on[k]
                val last = k == 4
                val fillC = when {
                    last -> if (muted) Color(0xFF7A2E2C) else col.copy(alpha = 0.85f)
                    lit -> col.copy(alpha = 0.30f)
                    else -> Color(0xFF16223C)
                }
                drawRoundRect(fillC, Offset(bx, yr - bh / 2), Size(bw, bh), CornerRadius(4.dp.toPx()))
                drawRoundRect(if (lit) col else GRID, Offset(bx, yr - bh / 2), Size(bw, bh), CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()))
                text(if (last) "O${o + 1}" else labels[k], bx + bw / 2, yr,
                    if (last) android.graphics.Color.BLACK else if (lit) android.graphics.Color.WHITE else 0xFF7C8CA8.toInt())
            }
        }

        // kotak input digambar terakhir supaya menutup pangkal garis
        for ((y, name, c) in listOf(Triple(yA, "IN A", IN_COLOR[0]), Triple(yB, "IN B", IN_COLOR[1]))) {
            drawRoundRect(c.copy(alpha = 0.9f), Offset(2.dp.toPx(), y - inH / 2), Size(inW - 2.dp.toPx(), inH), CornerRadius(4.dp.toPx()))
            text(name, (inW + 2.dp.toPx()) / 2f, y, android.graphics.Color.BLACK)
        }
    }
}

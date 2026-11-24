package id.dlms.dsp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Skala fader: pemetaan nilai(dB) <-> posisi 0..1 (0 = bawah) dan tanda skala yang digambar. */
class FaderScale(
    val min: Double, val max: Double, val step: Double,
    val toT: (Double) -> Float, val fromT: (Float) -> Double,
    val ticks: List<Pair<Double, String>>,
)

/** Gain output: -60..+12 dB. Separuh atas gerak fader dipakai untuk -12..+12 (daerah kerja), sisanya memampatkan -60..-12. */
val OUT_SCALE = FaderScale(
    -60.0, 12.0, 0.5,
    toT = { db -> if (db >= -12.0) (0.25 + 0.75 * (db + 12.0) / 24.0).toFloat() else (0.25 * (db + 60.0) / 48.0).toFloat() },
    fromT = { t -> if (t >= 0.25f) -12.0 + (t - 0.25) / 0.75 * 24.0 else -60.0 + t / 0.25 * 48.0 },
    ticks = listOf(12.0 to "+12", 6.0 to "+6", 0.0 to "0", -6.0 to "-6", -12.0 to "-12", -30.0 to "-30", -60.0 to "-60"),
)

/** Trim input: -20..+20 dB linear. */
val IN_SCALE = FaderScale(
    -20.0, 20.0, 0.5,
    toT = { db -> ((db + 20.0) / 40.0).toFloat() },
    fromT = { t -> -20.0 + t * 40.0 },
    ticks = listOf(20.0 to "+20", 10.0 to "+10", 0.0 to "0", -10.0 to "-10", -20.0 to "-20"),
)

private fun meterColor(db: Float) = when { db > -3f -> RED; db > -12f -> AMBER; else -> GREEN }

/**
 * Fader vertikal bergaya mixer: skala di kiri, kenop di tengah, meter di kanan.
 * Sentuh/seret di mana saja di kolom ini untuk mengatur nilai; nilai terakhir dikirim saat jari diangkat.
 */
@Composable
fun Fader(
    value: Double, scale: FaderScale, color: Color,
    meterDb: Float, grDb: Float,
    onChange: (Double, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onChangeNow by rememberUpdatedState(onChange)
    Canvas(
        modifier.pointerInput(scale) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val padV = 14.dp.toPx()
                val top = padV; val bot = size.height - padV
                fun valueAt(y: Float): Double {
                    val t = (1f - (y - top) / (bot - top)).coerceIn(0f, 1f)
                    val v = scale.fromT(t)
                    return ((v / scale.step).roundToInt() * scale.step).coerceIn(scale.min, scale.max)
                }
                down.consume()
                var last = valueAt(down.position.y)
                onChangeNow(last, false)
                drag(down.id) { ch ->
                    ch.consume()
                    last = valueAt(ch.position.y)
                    onChangeNow(last, false)
                }
                onChangeNow(last, true)
            }
        }
    ) {
        val w = size.width; val h = size.height
        val padV = 14.dp.toPx()
        val top = padV; val bot = h - padV
        fun yOf(db: Double) = bot - scale.toT(db.coerceIn(scale.min, scale.max)) * (bot - top)
        val trackX = w * 0.46f

        // ---- skala
        val paint = android.graphics.Paint().apply {
            this.color = 0xFF7C8CA8.toInt(); textSize = 8.sp.toPx(); isAntiAlias = true
        }
        for ((db, label) in scale.ticks) {
            val y = yOf(db)
            drawLine(GRID, Offset(trackX - 26.dp.toPx(), y), Offset(trackX + 26.dp.toPx(), y), 1f)
            drawContext.canvas.nativeCanvas.drawText(label, 1.dp.toPx(), y - 1.5.dp.toPx(), paint)
        }

        // ---- jalur
        drawRoundRect(Color(0xFF05080F), Offset(trackX - 3.dp.toPx(), top), Size(6.dp.toPx(), bot - top), CornerRadius(3.dp.toPx()))
        val ty = yOf(value)
        drawRoundRect(color.copy(alpha = 0.55f), Offset(trackX - 3.dp.toPx(), ty), Size(6.dp.toPx(), bot - ty), CornerRadius(3.dp.toPx()))

        // ---- meter (kanan)
        val mx = w - 11.dp.toPx(); val mw = 7.dp.toPx()
        drawRoundRect(Color(0xFF05080F), Offset(mx, top), Size(mw, bot - top), CornerRadius(2.dp.toPx()))
        val mf = ((meterDb + 60f) / 66f).coerceIn(0f, 1f)
        if (mf > 0.01f) {
            val mh = (bot - top) * mf
            drawRoundRect(
                Brush.verticalGradient(listOf(meterColor(meterDb), GREEN), startY = bot - mh, endY = bot),
                Offset(mx, bot - mh), Size(mw, mh), CornerRadius(2.dp.toPx()))
        }
        if (grDb < -0.1f) {
            val gh = (bot - top) * ((-grDb).coerceAtMost(20f) / 20f)
            drawRect(RED, Offset(mx, top), Size(mw, gh))
        }

        // ---- kenop
        val kw = 40.dp.toPx(); val kh = 22.dp.toPx()
        val kl = trackX - kw / 2
        drawRoundRect(Color(0xAA000000), Offset(kl + 1.dp.toPx(), ty - kh / 2 + 2.dp.toPx()), Size(kw, kh), CornerRadius(4.dp.toPx()))
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFE6EAF2), Color(0xFF8E98AA), Color(0xFFC9D0DC)), startY = ty - kh / 2, endY = ty + kh / 2),
            Offset(kl, ty - kh / 2), Size(kw, kh), CornerRadius(4.dp.toPx()))
        for (i in -1..1) {
            drawLine(Color(0x66000000), Offset(kl + 4.dp.toPx(), ty + i * 4.dp.toPx() - 1.dp.toPx()),
                Offset(kl + kw - 4.dp.toPx(), ty + i * 4.dp.toPx() - 1.dp.toPx()), 1f)
        }
        drawLine(color, Offset(kl + 2.dp.toPx(), ty), Offset(kl + kw - 2.dp.toPx(), ty), 2.dp.toPx())
    }
}

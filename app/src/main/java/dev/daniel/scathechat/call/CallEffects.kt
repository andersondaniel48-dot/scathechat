package dev.daniel.scathechat.call

import kotlinx.coroutines.flow.MutableStateFlow
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink
import kotlin.math.roundToInt

/**
 * Filters applied to my own camera before it's encoded, so everyone on the call sees them.
 * Works directly on the YUV planes: cheap enough for 540p at 24 fps on a phone.
 */
object CallEffects {
    enum class Effect(val label: String) {
        NONE("Normal"), NIGHT("Night vision"), THERMAL("Thermal"), NOIR("Noir"), EMBER("Ember")
    }

    val current = MutableStateFlow(Effect.NONE)

    // Thermal palette: black -> violet -> red -> orange -> yellow -> white, as Y/U/V.
    private val palY = IntArray(256)
    private val palU = IntArray(256)
    private val palV = IntArray(256)

    init {
        val stops = listOf(
            0f to intArrayOf(10, 0, 30), 0.25f to intArrayOf(110, 10, 140), 0.5f to intArrayOf(220, 30, 40),
            0.7f to intArrayOf(255, 130, 0), 0.88f to intArrayOf(255, 220, 40), 1f to intArrayOf(255, 255, 230),
        )
        for (i in 0 until 256) {
            val t = i / 255f
            val hi = stops.indexOfFirst { it.first >= t }.coerceAtLeast(1)
            val (t0, c0) = stops[hi - 1]
            val (t1, c1) = stops[hi]
            val p = if (t1 > t0) (t - t0) / (t1 - t0) else 0f
            val r = c0[0] + (c1[0] - c0[0]) * p
            val g = c0[1] + (c1[1] - c0[1]) * p
            val b = c0[2] + (c1[2] - c0[2]) * p
            palY[i] = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt().coerceIn(0, 255)
            palU[i] = (-0.169f * r - 0.331f * g + 0.5f * b + 128).roundToInt().coerceIn(0, 255)
            palV[i] = (0.5f * r - 0.419f * g - 0.081f * b + 128).roundToInt().coerceIn(0, 255)
        }
    }

    private var vigW = 0
    private var vigH = 0
    private var vx = FloatArray(0)
    private var vy = FloatArray(0)
    private var seed = 12345

    private fun vignette(w: Int, h: Int) {
        if (w == vigW && h == vigH) return
        vigW = w; vigH = h
        vx = FloatArray(w) { val d = (it - w / 2f) / (w / 2f); 1f - d * d * 0.45f }
        vy = FloatArray(h) { val d = (it - h / 2f) / (h / 2f); 1f - d * d * 0.45f }
    }

    private fun noise(): Int {
        seed = seed * 1103515245 + 12345
        return ((seed ushr 16) and 0x1F) - 16
    }

    class Processor : VideoProcessor {
        private var sink: VideoSink? = null
        override fun setSink(sink: VideoSink?) { this.sink = sink }
        override fun onCapturerStarted(success: Boolean) {}
        override fun onCapturerStopped() {}
        override fun onFrameCaptured(frame: VideoFrame) {
            val s = sink ?: return
            val e = current.value
            if (e == Effect.NONE) {
                s.onFrame(frame)
                return
            }
            val out = try { apply(frame, e) } catch (_: Throwable) { null }
            if (out == null) {
                s.onFrame(frame)
            } else {
                s.onFrame(out)
                out.release()
            }
        }
    }

    private fun apply(frame: VideoFrame, e: Effect): VideoFrame? {
        val src = frame.buffer.toI420() ?: return null
        try {
            val w = src.width
            val h = src.height
            val cw = (w + 1) / 2
            val ch = (h + 1) / 2
            val dst = JavaI420Buffer.allocate(w, h)
            vignette(w, h)
            val row = ByteArray(w)
            val sY = src.dataY
            val dY = dst.dataY
            for (r in 0 until h) {
                sY.position(r * src.strideY); sY.get(row, 0, w)
                val scan = e == Effect.NIGHT && r % 4 == 0
                for (c in 0 until w) {
                    val y = row[c].toInt() and 0xFF
                    var o = when (e) {
                        Effect.NIGHT -> (((y - 16) * 1.7f + 18 + noise()) * vx[c] * vy[r]).toInt()
                        Effect.THERMAL -> palY[y]
                        Effect.NOIR -> ((y - 128) * 1.35f + 128).toInt()
                        Effect.EMBER -> (((y - 128) * 1.15f + 124) * (0.75f + 0.25f * vx[c] * vy[r])).toInt()
                        Effect.NONE -> y
                    }
                    if (scan) o = (o * 0.78f).toInt()
                    row[c] = o.coerceIn(0, 255).toByte()
                }
                dY.position(r * dst.strideY); dY.put(row, 0, w)
            }
            val sU = src.dataU
            val sV = src.dataV
            val dU = dst.dataU
            val dV = dst.dataV
            val rowU = ByteArray(cw)
            val rowV = ByteArray(cw)
            for (r in 0 until ch) {
                when (e) {
                    Effect.NIGHT -> { rowU.fill(82); rowV.fill(72) }
                    Effect.NOIR -> { rowU.fill(128.toByte()); rowV.fill(128.toByte()) }
                    Effect.THERMAL -> for (c in 0 until cw) {
                        val y = sY.get(((r * 2).coerceAtMost(h - 1)) * src.strideY + (c * 2).coerceAtMost(w - 1)).toInt() and 0xFF
                        rowU[c] = palU[y].toByte(); rowV[c] = palV[y].toByte()
                    }
                    Effect.EMBER -> {
                        sU.position(r * src.strideU); sU.get(rowU, 0, cw)
                        sV.position(r * src.strideV); sV.get(rowV, 0, cw)
                        for (c in 0 until cw) {
                            val u = rowU[c].toInt() and 0xFF
                            val v = rowV[c].toInt() and 0xFF
                            rowU[c] = ((u - 128) * 0.55f + 108).toInt().coerceIn(0, 255).toByte()
                            rowV[c] = ((v - 128) * 0.8f + 152).toInt().coerceIn(0, 255).toByte()
                        }
                    }
                    Effect.NONE -> {}
                }
                dU.position(r * dst.strideU); dU.put(rowU, 0, cw)
                dV.position(r * dst.strideV); dV.put(rowV, 0, cw)
            }
            return VideoFrame(dst, frame.rotation, frame.timestampNs)
        } finally {
            src.release()
        }
    }
}

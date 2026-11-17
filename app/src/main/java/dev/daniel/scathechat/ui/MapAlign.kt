package dev.daniel.scathechat.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Lines a map image up with the tac-map grid automatically.
 * - [detectGrid]: finds the evenly spaced, full-length grid lines drawn on a map image.
 * - [register]: matches a sharper map without a grid against the gridded one (scale + offset per axis),
 *   so the grid found on one can be carried over to the other.
 */
object MapAlign {
    private class Lum(val px: FloatArray, val w: Int, val h: Int) {
        operator fun get(x: Int, y: Int) = px[y * w + x]
    }

    fun decode(f: File, maxSide: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (max(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    private fun lum(src: Bitmap, maxW: Int): Lum {
        val b = if (src.width > maxW) Bitmap.createScaledBitmap(src, maxW, (src.height * maxW.toFloat() / src.width).roundToInt().coerceAtLeast(1), true) else src
        val w = b.width; val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val out = FloatArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            out[i] = 0.299f * ((c shr 16) and 255) + 0.587f * ((c shr 8) and 255) + 0.114f * (c and 255)
        }
        if (b !== src) b.recycle()
        return Lum(out, w, h)
    }

    /** Per-column (vertical=true) or per-row share of pixels that sit on a thin line. */
    private fun lineProfile(L: Lum, vertical: Boolean, thr: Float = 16f): FloatArray {
        val n = if (vertical) L.w else L.h
        val m = if (vertical) L.h else L.w
        val p = FloatArray(n)
        for (i in 2 until n - 2) {
            var hits = 0
            for (j in 0 until m) {
                val d = if (vertical) 2 * L[i, j] - L[i - 2, j] - L[i + 2, j] else 2 * L[j, i] - L[j, i - 2] - L[j, i + 2]
                if (abs(d) > thr) hits++
            }
            p[i] = hits.toFloat() / m
        }
        return p
    }

    private class Axis(val start: Float, val end: Float, val strength: Float)

    /** Best comb of [cells]+1 evenly spaced lines spanning 55–100% of the image. */
    private fun comb(P: FloatArray, cells: Int): Axis? {
        val w = P.size
        if (w < 40) return null
        val pm = FloatArray(w) { i -> max(P[i], max(P[(i - 1).coerceAtLeast(0)], P[(i + 1).coerceAtMost(w - 1)])) }
        val lines = cells + 1
        val keep = lines - 2 // the outer border is sometimes not drawn
        val vals = FloatArray(lines)
        var best = -1f; var bx = 0; var bd = 0f
        var d = 0.55f * w / cells
        while (d <= w.toFloat() / cells) {
            val span = cells * d
            var x0 = 0
            while (x0 + span < w) {
                for (k in 0 until lines) vals[k] = pm[(x0 + k * d).roundToInt().coerceAtMost(w - 1)]
                vals.sortDescending()
                var s = 0f
                for (k in 0 until keep) s += vals[k]
                if (s > best) { best = s; bx = x0; bd = d }
                x0++
            }
            d += 0.25f
        }
        if (best < 0) return null
        return Axis(bx.toFloat() / w, (bx + cells * bd) / w, best / keep)
    }

    /** [left, top, right, bottom] of the grid as fractions of the image, or null if no clear grid. */
    fun detectGrid(bmp: Bitmap, cols: Int, rows: Int): FloatArray? {
        val L = lum(bmp, 1000)
        val x = comb(lineProfile(L, true), cols) ?: return null
        val y = comb(lineProfile(L, false), rows) ?: return null
        if (x.strength < 0.6f || y.strength < 0.6f) return null
        return floatArrayOf(x.start, y.start, x.end, y.end)
    }

    // ---------------------------------------------------------------- registration

    private fun meanProfile(L: Lum, vertical: Boolean): FloatArray {
        val n = if (vertical) L.w else L.h
        val m = if (vertical) L.h else L.w
        return FloatArray(n) { i ->
            var s = 0f
            for (j in 0 until m) s += if (vertical) L[i, j] else L[j, i]
            s / m
        }
    }

    /** Paint over the grid lines so they don't pull the match. */
    private fun maskLines(p: FloatArray, start: Float, end: Float, cells: Int): FloatArray {
        val q = p.copyOf(); val w = q.size
        for (k in 0..cells) {
            val x = ((start + (end - start) * k / cells) * w).roundToInt()
            val a = (x - 4).coerceIn(0, w - 1); val b = (x + 5).coerceIn(0, w - 1)
            for (i in a..b) q[i] = q[a] + (q[b] - q[a]) * (i - a) / max(1, b - a)
        }
        return q
    }

    private fun resample(p: FloatArray, n: Int): FloatArray = FloatArray(n) { i ->
        val x = i * (p.size - 1f) / (n - 1)
        val a = x.toInt().coerceAtMost(p.size - 2)
        p[a] + (p[a + 1] - p[a]) * (x - a)
    }

    private class Fit(val a: Float, val b: Float, val r: Float)

    /** Finds x = a*u + b (fractions of each image) that best overlays profile [hi] on [grid]. */
    private fun fit(hi: FloatArray, grid: FloatArray): Fit {
        val n = 400
        val h = resample(hi, n); val g = resample(grid, n)
        fun score(a: Float, b: Float): Float {
            var cnt = 0; var sh = 0.0; var sg = 0.0
            val hv = FloatArray(n); val gv = FloatArray(n)
            for (i in 0 until n) {
                val x = (a * ((i + 0.5f) / n) + b) * n - 0.5f
                if (x < 0 || x > n - 1) continue
                val k = x.toInt().coerceAtMost(n - 2)
                val v = g[k] + (g[k + 1] - g[k]) * (x - k)
                hv[cnt] = h[i]; gv[cnt] = v; sh += h[i]; sg += v; cnt++
            }
            if (cnt < n * 0.6f) return -1f
            val mh = sh / cnt; val mg = sg / cnt
            var num = 0.0; var dh = 0.0; var dg = 0.0
            for (i in 0 until cnt) {
                val x = hv[i] - mh; val y = gv[i] - mg
                num += x * y; dh += x * x; dg += y * y
            }
            return (num / (sqrt(dh * dg) + 1e-9)).toFloat()
        }
        var best = Fit(1f, 0f, -2f)
        var a = 0.5f
        while (a <= 2.0f) {
            var b = -0.5f
            while (b <= 0.5f) {
                val r = score(a, b)
                if (r > best.r) best = Fit(a, b, r)
                b += 0.01f
            }
            a += 0.01f
        }
        val ca = best.a; val cb = best.b
        a = ca - 0.012f
        while (a <= ca + 0.012f) {
            var b = cb - 0.012f
            while (b <= cb + 0.012f) {
                val r = score(a, b)
                if (r > best.r) best = Fit(a, b, r)
                b += 0.001f
            }
            a += 0.001f
        }
        return best
    }

    /**
     * Carries [gridCal] (found on [grid]) over to [hi], a sharper copy of the same map without a grid.
     * Returns the calibration for [hi], or null when the two don't match confidently.
     */
    fun register(hi: Bitmap, grid: Bitmap, gridCal: FloatArray, cols: Int, rows: Int): FloatArray? {
        val H = lum(hi, 800)
        val G = lum(grid, 800)
        val fx = fit(meanProfile(H, true), maskLines(meanProfile(G, true), gridCal[0], gridCal[2], cols))
        val fy = fit(meanProfile(H, false), maskLines(meanProfile(G, false), gridCal[1], gridCal[3], rows))
        if (fx.r < 0.8f || fy.r < 0.8f) return null
        // check (and fine-tune) the match in 2D on edges, which ignores colour and tint differences
        val Hs = lum(hi, 240); val Gs = lum(grid, 240)
        val eh = edges(Hs); val eg = edges(Gs)
        var ax = fx.a; var bx = fx.b; var ay = fy.a; var by = fy.b
        var best = ncc2d(eh, Hs.w, Hs.h, eg, Gs.w, Gs.h, ax, bx, ay, by)
        repeat(2) {
            for (pass in 0..1) {
                val a0 = if (pass == 0) ax else ay
                val b0 = if (pass == 0) bx else by
                var ia = -6
                while (ia <= 6) {
                    var ib = -10
                    while (ib <= 10) {
                        val a = a0 * (1 + ia * 0.005f); val b = b0 + ib * 0.003f
                        val r = if (pass == 0) ncc2d(eh, Hs.w, Hs.h, eg, Gs.w, Gs.h, a, b, ay, by)
                        else ncc2d(eh, Hs.w, Hs.h, eg, Gs.w, Gs.h, ax, bx, a, b)
                        if (r > best) {
                            best = r
                            if (pass == 0) { ax = a; bx = b } else { ay = a; by = b }
                        }
                        ib++
                    }
                    ia++
                }
            }
        }
        if (best < 0.5f) return null
        return floatArrayOf(
            (gridCal[0] - bx) / ax, (gridCal[1] - by) / ay,
            (gridCal[2] - bx) / ax, (gridCal[3] - by) / ay,
        )
    }

    /** Edge strength, lightly blurred so small offsets still overlap. */
    private fun edges(L: Lum): FloatArray {
        val w = L.w; val h = L.h
        val e = FloatArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val gx = L[x + 1, y] - L[x - 1, y]; val gy = L[x, y + 1] - L[x, y - 1]
            e[y * w + x] = sqrt(gx * gx + gy * gy)
        }
        val o = FloatArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            var s = 0f
            for (dy in -1..1) for (dx in -1..1) s += e[(y + dy) * w + x + dx]
            o[y * w + x] = s / 9f
        }
        return o
    }

    /** Correlation of the two edge maps where gridded x = a*u + b (fractions), sampled on a 120x120 lattice. */
    private fun ncc2d(eh: FloatArray, hw: Int, hh: Int, eg: FloatArray, gw: Int, gh: Int, ax: Float, bx: Float, ay: Float, by: Float): Float {
        val n = 120
        var cnt = 0; var sh = 0.0; var sg = 0.0; var shh = 0.0; var sgg = 0.0; var shg = 0.0
        for (j in 0 until n) {
            val y = (j + 0.5f) / n
            val v = (y - by) / ay
            if (v < 0f || v >= 1f) continue
            val hy = (v * hh).toInt().coerceIn(0, hh - 1); val gy = (y * gh).toInt().coerceIn(0, gh - 1)
            for (i in 0 until n) {
                val x = (i + 0.5f) / n
                val u = (x - bx) / ax
                if (u < 0f || u >= 1f) continue
                val a = eh[hy * hw + (u * hw).toInt().coerceIn(0, hw - 1)].toDouble()
                val b = eg[gy * gw + (x * gw).toInt().coerceIn(0, gw - 1)].toDouble()
                cnt++; sh += a; sg += b; shh += a * a; sgg += b * b; shg += a * b
            }
        }
        if (cnt < n * n * 0.5) return -1f
        val mh = sh / cnt; val mg = sg / cnt
        val cov = shg / cnt - mh * mg
        val vh = shh / cnt - mh * mh; val vg = sgg / cnt - mg * mg
        return (cov / (sqrt(vh * vg) + 1e-9)).toFloat()
    }
}

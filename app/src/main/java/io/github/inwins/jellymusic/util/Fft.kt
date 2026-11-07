package io.github.inwins.jellymusic.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place iterative radix-2 Cooley-Tukey FFT. Size must be a power of two. */
object Fft {
    fun transform(re: FloatArray, im: FloatArray) {
        val n = re.size
        require(n == im.size && n > 0 && n and (n - 1) == 0) { "size must be a power of two" }

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var tmp = re[i]; re[i] = re[j]; re[j] = tmp
                tmp = im[i]; im[i] = im[j]; im[j] = tmp
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wr = cos(angle).toFloat()
            val wi = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                val half = len shr 1
                for (k in 0 until half) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val xRe = re[i + k + half]
                    val xIm = im[i + k + half]
                    val vRe = xRe * curRe - xIm * curIm
                    val vIm = xRe * curIm + xIm * curRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + half] = uRe - vRe
                    im[i + k + half] = uIm - vIm
                    val nextRe = curRe * wr - curIm * wi
                    curIm = curRe * wi + curIm * wr
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}

package com.chaminwoo.stary.core.util

import android.graphics.Bitmap
import java.io.OutputStream

/**
 * 부메랑(3초 움짤)용 경량 GIF89a 인코더.
 *
 * - 전역 고정 팔레트(6×7×6 = 252색 RGB 큐브) + Bayer 4×4 오더드 디더링 —
 *   화질을 조금 낮추는 대신(사용자 요구: 용량 우선) 인코딩이 빠르고 결과가 안정적.
 * - LZW 압축은 고전 ppmtogif/AnimatedGifEncoder 계열 알고리즘의 Kotlin 포팅(검증된 구조).
 * - 무한 루프(NETSCAPE2.0) + 프레임당 지연(delayCs, 1/100초 단위).
 *
 * 사용: [encode] 에 같은 크기의 프레임 목록을 넘긴다(모든 프레임 동일 해상도 필수).
 */
object GifEncoder {

    private const val R_LEVELS = 6
    private const val G_LEVELS = 7
    private const val B_LEVELS = 6
    private const val PALETTE_SIZE = 256 // 252색 사용 + 4색 패딩

    // Bayer 4×4 오더드 디더링 행렬(0..15)
    private val BAYER = intArrayOf(
        0, 8, 2, 10,
        12, 4, 14, 6,
        3, 11, 1, 9,
        15, 7, 13, 5,
    )

    /** [frames] 를 [delayCs](1/100초) 간격의 무한 루프 GIF 로 [out] 에 기록한다. */
    fun encode(frames: List<Bitmap>, delayCs: Int, out: OutputStream) {
        require(frames.isNotEmpty()) { "frames empty" }
        val w = frames[0].width
        val h = frames[0].height

        // ── Header + Logical Screen Descriptor ──
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(out, w)
        writeShort(out, h)
        // GCT flag(1) + color resolution(7) + sort(0) + GCT size(7 → 2^8 = 256)
        out.write(0xF7)
        out.write(0) // background color index
        out.write(0) // pixel aspect ratio

        // ── Global Color Table (6×7×6 큐브 + 패딩) ──
        val palette = ByteArray(PALETTE_SIZE * 3)
        var pi = 0
        for (r in 0 until R_LEVELS) for (g in 0 until G_LEVELS) for (b in 0 until B_LEVELS) {
            palette[pi++] = (r * 255 / (R_LEVELS - 1)).toByte()
            palette[pi++] = (g * 255 / (G_LEVELS - 1)).toByte()
            palette[pi++] = (b * 255 / (B_LEVELS - 1)).toByte()
        }
        out.write(palette)

        // ── NETSCAPE2.0 루프 확장(무한 반복) ──
        out.write(0x21); out.write(0xFF); out.write(11)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3); out.write(1); writeShort(out, 0); out.write(0)

        val pixels = IntArray(w * h)
        val indexed = ByteArray(w * h)
        for (frame in frames) {
            frame.getPixels(pixels, 0, w, 0, 0, w, h)
            quantize(pixels, indexed, w, h)

            // Graphic Control Extension — 지연 + 불투명(disposal=1: 그대로 두기)
            out.write(0x21); out.write(0xF9); out.write(4)
            out.write(0x04) // disposal method 1, no transparency
            writeShort(out, delayCs)
            out.write(0) // transparent color index (unused)
            out.write(0) // block terminator

            // Image Descriptor — 전체 프레임, LCT 없음
            out.write(0x2C)
            writeShort(out, 0); writeShort(out, 0)
            writeShort(out, w); writeShort(out, h)
            out.write(0)

            LzwEncoder(indexed, 8).encode(out)
        }

        out.write(0x3B) // trailer
        out.flush()
    }

    /** ARGB 픽셀 → 팔레트 인덱스(디더링 포함). */
    private fun quantize(pixels: IntArray, indexed: ByteArray, w: Int, h: Int) {
        var i = 0
        for (y in 0 until h) {
            val bayerRow = (y and 3) shl 2
            for (x in 0 until w) {
                val p = pixels[i]
                // Bayer 임계값(-0.5..+0.5 스케일)을 채널 스텝 크기에 곱해 더함
                val d = (BAYER[bayerRow or (x and 3)] - 7.5f) / 16f
                val r = quantChannel((p ushr 16) and 0xFF, R_LEVELS, d)
                val g = quantChannel((p ushr 8) and 0xFF, G_LEVELS, d)
                val b = quantChannel(p and 0xFF, B_LEVELS, d)
                indexed[i] = ((r * G_LEVELS + g) * B_LEVELS + b).toByte()
                i++
            }
        }
    }

    private fun quantChannel(v: Int, levels: Int, dither: Float): Int {
        val step = 255f / (levels - 1)
        val f = (v + dither * step) / step
        val q = (f + 0.5f).toInt()
        return q.coerceIn(0, levels - 1)
    }

    private fun writeShort(out: OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    /**
     * GIF LZW 인코더 — 고전 ppmtogif(compress) 계열 해시 테이블 구현의 포팅.
     * (AnimatedGifEncoder.LZWEncoder 와 동일 알고리즘)
     */
    private class LzwEncoder(private val pixels: ByteArray, private val initCodeSize: Int) {
        private val bits = 12
        private val hSize = 5003
        private val maxMaxCode = 1 shl bits
        private var nBits = 0
        private var maxCode = 0
        private val hTab = IntArray(hSize)
        private val codeTab = IntArray(hSize)
        private var freeEnt = 0
        private var clearFlg = false
        private var gInitBits = 0
        private var clearCode = 0
        private var eofCode = 0
        private var curAccum = 0
        private var curBits = 0
        private val accum = ByteArray(256)
        private var aCount = 0
        private var curPixel = 0

        private val masks = intArrayOf(
            0x0000, 0x0001, 0x0003, 0x0007, 0x000F, 0x001F, 0x003F, 0x007F, 0x00FF,
            0x01FF, 0x03FF, 0x07FF, 0x0FFF, 0x1FFF, 0x3FFF, 0x7FFF, 0xFFFF,
        )

        fun encode(os: OutputStream) {
            os.write(initCodeSize)
            curPixel = 0
            compress(initCodeSize + 1, os)
            os.write(0) // block terminator
        }

        private fun maxCodeOf(n: Int) = (1 shl n) - 1

        private fun nextPixel(): Int =
            if (curPixel < pixels.size) pixels[curPixel++].toInt() and 0xFF else -1

        private fun compress(initBits: Int, os: OutputStream) {
            gInitBits = initBits
            clearFlg = false
            nBits = gInitBits
            maxCode = maxCodeOf(nBits)
            clearCode = 1 shl (initBits - 1)
            eofCode = clearCode + 1
            freeEnt = clearCode + 2
            aCount = 0

            var ent = nextPixel()

            var hShift = 0
            var fc = hSize
            while (fc < 65536) { hShift++; fc *= 2 }
            hShift = 8 - hShift
            clearHash()

            output(clearCode, os)

            var c = nextPixel()
            outer@ while (c != -1) {
                val fcode = (c shl bits) + ent
                var i = (c shl hShift) xor ent
                if (hTab[i] == fcode) {
                    ent = codeTab[i]
                    c = nextPixel()
                    continue
                } else if (hTab[i] >= 0) {
                    var disp = hSize - i
                    if (i == 0) disp = 1
                    do {
                        i -= disp
                        if (i < 0) i += hSize
                        if (hTab[i] == fcode) {
                            ent = codeTab[i]
                            c = nextPixel()
                            continue@outer
                        }
                    } while (hTab[i] >= 0)
                }
                output(ent, os)
                ent = c
                if (freeEnt < maxMaxCode) {
                    codeTab[i] = freeEnt++
                    hTab[i] = fcode
                } else {
                    clearBlock(os)
                }
                c = nextPixel()
            }
            output(ent, os)
            output(eofCode, os)
        }

        private fun clearBlock(os: OutputStream) {
            clearHash()
            freeEnt = clearCode + 2
            clearFlg = true
            output(clearCode, os)
        }

        private fun clearHash() {
            java.util.Arrays.fill(hTab, -1)
        }

        private fun output(code: Int, os: OutputStream) {
            curAccum = curAccum and masks[curBits]
            curAccum = if (curBits > 0) curAccum or (code shl curBits) else code
            curBits += nBits
            while (curBits >= 8) {
                charOut((curAccum and 0xFF).toByte(), os)
                curAccum = curAccum ushr 8
                curBits -= 8
            }
            // 다음 코드부터 비트 수 증가가 필요한지 판정
            if (freeEnt > maxCode || clearFlg) {
                if (clearFlg) {
                    nBits = gInitBits
                    maxCode = maxCodeOf(nBits)
                    clearFlg = false
                } else {
                    nBits++
                    maxCode = if (nBits == bits) maxMaxCode else maxCodeOf(nBits)
                }
            }
            if (code == eofCode) {
                while (curBits > 0) {
                    charOut((curAccum and 0xFF).toByte(), os)
                    curAccum = curAccum ushr 8
                    curBits -= 8
                }
                flushChar(os)
            }
        }

        private fun charOut(c: Byte, os: OutputStream) {
            accum[aCount++] = c
            if (aCount >= 254) flushChar(os)
        }

        private fun flushChar(os: OutputStream) {
            if (aCount > 0) {
                os.write(aCount)
                os.write(accum, 0, aCount)
                aCount = 0
            }
        }
    }
}

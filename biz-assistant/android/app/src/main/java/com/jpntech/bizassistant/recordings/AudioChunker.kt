package com.jpntech.bizassistant.recordings

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 통화 녹음을 폰 안에서 풀어(디코딩) 16kHz 모노 WAV 조각으로 만든다.
 * 갤럭시 녹음은 기기마다 코덱이 달라(AAC, AMR 등) 그대로 올리면 음성 인식 쪽에서 열지 못하는 일이 있다.
 * WAV는 어디서나 열리고, 조각 크기는 중계 서버 한도(약 4MB)에 맞춘다. 16kHz 모노 WAV 3.4MB ≈ 106초.
 */
object AudioChunker {
    const val DEFAULT_MAX_BYTES = 3_400_000L
    private const val TARGET_RATE = 16_000

    data class Probe(val mime: String?, val sampleRate: Int, val channels: Int, val durationMs: Long)

    /** 어떤 소리 파일인지 (오류 메시지에 쓴다) */
    fun probe(context: Context, uri: Uri): Probe? = runCatching {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
            val i = audioTrack(ex) ?: return null
            val f = ex.getTrackFormat(i)
            Probe(
                f.getString(MediaFormat.KEY_MIME),
                if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) f.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0,
                if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 0,
                if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) / 1000 else 0L,
            )
        } finally { ex.release() }
    }.getOrNull()

    private fun audioTrack(ex: MediaExtractor): Int? =
        (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }

    /** 녹음을 WAV 조각 파일들로. 말소리가 전혀 없을 만큼 짧으면 빈 목록 */
    fun toWavChunks(context: Context, uri: Uri, outDir: File, maxBytes: Long = DEFAULT_MAX_BYTES): List<File> {
        outDir.mkdirs()
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val trackIndex = audioTrack(extractor) ?: throw IllegalStateException("오디오 트랙이 없습니다")
        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IllegalStateException("형식을 알 수 없습니다")
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        var inRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 16_000
        var inChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
        val writer = WavChunkWriter(outDir, maxBytes)
        val resampler = Resampler(inRate, TARGET_RATE)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var idleRounds = 0
        try {
            while (!outputDone) {
                // 입력도 출력도 없는 상태가 30초(10ms × 3000) 넘게 이어지면 디코더가 멈춘 것으로 본다.
                if (++idleRounds > 3000) throw IllegalStateException("디코더가 응답하지 않습니다")
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val n = extractor.readSampleData(buf, 0)
                        idleRounds = 0
                        if (n < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx >= 0 -> {
                        idleRounds = 0
                        val out = codec.getOutputBuffer(outIdx)!!
                        if (info.size > 0) {
                            out.position(info.offset); out.limit(info.offset + info.size)
                            val pcm = out.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val frames = pcm.remaining() / inChannels
                            val mono = ShortArray(frames)
                            for (f in 0 until frames) {
                                var acc = 0
                                for (c in 0 until inChannels) acc += pcm.get(f * inChannels + c).toInt()
                                mono[f] = (acc / inChannels).toShort()
                            }
                            writer.write(resampler.process(mono))
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = codec.outputFormat
                        inRate = if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) of.getInteger(MediaFormat.KEY_SAMPLE_RATE) else inRate
                        inChannels = if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) of.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else inChannels
                        resampler.reset(inRate)
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }; codec.release(); extractor.release()
            writer.close()
        }
        return writer.files.filter { it.length() > 44 + TARGET_RATE } // 0.5초 미만은 버림
    }

    /** 선형 보간으로 표본율을 바꾼다. 말소리에는 충분하다. */
    private class Resampler(private var inRate: Int, private val outRate: Int) {
        private var pos = 0.0
        private var last: Short = 0
        private var hasLast = false
        fun reset(rate: Int) { inRate = rate }
        fun process(input: ShortArray): ShortArray {
            if (input.isEmpty()) return input
            if (inRate == outRate) return input
            val step = inRate.toDouble() / outRate
            val out = ArrayList<Short>((input.size * outRate / inRate) + 2)
            // 이전 조각의 마지막 표본을 앞에 붙여 이어지게 한다
            val src = if (hasLast) ShortArray(input.size + 1).also { it[0] = last; System.arraycopy(input, 0, it, 1, input.size) } else input
            var p = pos
            while (p < src.size - 1) {
                val i = p.toInt()
                val frac = p - i
                val v = src[i] + (src[i + 1] - src[i]) * frac
                out.add(v.toInt().coerceIn(-32768, 32767).toShort())
                p += step
            }
            pos = p - (src.size - 1)
            last = src[src.size - 1]; hasLast = true
            return out.toShortArray()
        }
    }

    /** 16비트 모노 WAV 를 크기 한도마다 새 파일로 이어 쓴다 */
    private class WavChunkWriter(private val dir: File, private val maxBytes: Long) {
        val files = mutableListOf<File>()
        private var raf: RandomAccessFile? = null
        private var dataBytes = 0L
        private val buf = ByteBuffer.allocate(1 shl 16).order(ByteOrder.LITTLE_ENDIAN)

        fun write(samples: ShortArray) {
            var i = 0
            while (i < samples.size) {
                if (raf == null) open()
                val room = ((maxBytes - 44 - dataBytes) / 2).toInt().coerceAtLeast(0)
                if (room == 0) { close(); continue }
                val n = minOf(room, samples.size - i)
                var written = 0
                while (written < n) {
                    buf.clear()
                    val k = minOf(buf.capacity() / 2, n - written)
                    for (j in 0 until k) buf.putShort(samples[i + written + j])
                    raf!!.write(buf.array(), 0, k * 2)
                    written += k
                }
                dataBytes += n * 2L
                i += n
            }
        }

        private fun open() {
            val f = File(dir, "part-%03d.wav".format(files.size))
            raf = RandomAccessFile(f, "rw").also { it.write(ByteArray(44)) }
            files += f
            dataBytes = 0
        }

        fun close() {
            val r = raf ?: return
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()); header.putInt((36 + dataBytes).toInt()); header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray()); header.putInt(16); header.putShort(1); header.putShort(1)
            header.putInt(TARGET_RATE); header.putInt(TARGET_RATE * 2); header.putShort(2); header.putShort(16)
            header.put("data".toByteArray()); header.putInt(dataBytes.toInt())
            r.seek(0); r.write(header.array()); r.close()
            raf = null
        }
    }
}

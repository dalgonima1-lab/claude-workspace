package com.jpntech.bizassistant.recordings

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

/**
 * 통화 녹음(m4a/AAC)을 다시 인코딩하지 않고 샘플 단위로 잘라 여러 개의 작은 m4a로 만든다.
 * 중계 서버가 한 번에 받을 수 있는 크기(약 4MB)에 맞추기 위한 것이다.
 */
object AudioChunker {
    const val DEFAULT_MAX_BYTES = 3_500_000L

    fun split(context: Context, uri: Uri, totalSize: Long, outDir: File, maxBytes: Long = DEFAULT_MAX_BYTES): List<File> {
        outDir.mkdirs()
        if (totalSize in 1..maxBytes) {
            val whole = File(outDir, "part-000.m4a")
            context.contentResolver.openInputStream(uri)!!.use { input -> whole.outputStream().use { input.copyTo(it) } }
            return listOf(whole)
        }
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalStateException("오디오 트랙이 없습니다")
        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)

        val parts = mutableListOf<File>()
        val buffer = ByteBuffer.allocate(1 shl 20)
        val info = MediaCodec.BufferInfo()
        var muxer: MediaMuxer? = null
        var muxTrack = -1
        var written = 0L

        fun open() {
            val f = File(outDir, "part-%03d.m4a".format(parts.size))
            val m = MediaMuxer(f.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxTrack = m.addTrack(format)
            m.start()
            muxer = m
            written = 0L
            parts += f
        }
        fun close() {
            muxer?.let { runCatching { it.stop() }; it.release() }
            muxer = null
        }

        try {
            open()
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                if (written + size > maxBytes && written > 0) {
                    close()
                    open()
                }
                muxer!!.writeSampleData(muxTrack, buffer, info)
                written += size
                extractor.advance()
            }
        } finally {
            close()
            extractor.release()
        }
        return parts.filter { it.length() > 0 }
    }
}

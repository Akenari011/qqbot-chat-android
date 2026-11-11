// 设计参考 Telegram for Android (GPL-2.0)
// 语音消息规格参考 TG 的 ChatMessageCell：语音气泡会绘制语音波形并显示录制时长，
// 因此一条语音富媒体除音频数据外，UI 侧还需要一份可读的时长；本类用 elapsedSeconds()
// 暴露基于 SystemClock.elapsedRealtime() 的录制秒数，供上层绘制波形 / 计时，本身仅依赖
// Android SDK，不涉及任何界面框架或上层业务类。
// QQ Bot API 语音富媒体为 file_type=3，官方常用 silk 编码；但 Android MediaRecorder 无法
// 直接产出 silk，故采用 AAC 编码 + MPEG_4 容器（.m4a）。iOS 版已验证 AAC/m4a 能被 QQ Bot
// 富媒体接口正常上传与播放，这里统一以 AAC/m4a 作为跨平台一致的语音格式。
// 运行时权限 android.permission.RECORD_AUDIO 由调用方申请；所有 MediaRecorder 操作均包裹在
// try/catch 中，任何异常都被吞掉并释放资源，绝不冒泡到 UI。
package org.example.qqbotchat.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/**
 * 基于 Android MediaRecorder 的语音录制器，输出 context.cacheDir 下的 AAC/m4a 临时文件。
 * 仅依赖 Android SDK；权限由调用方在运行时申请。
 */
class AudioRecorder(private val context: Context) {

    /** 当前 MediaRecorder 实例；尽早登记到字段，保证任一环节异常时都能被 release。 */
    private var recorder: MediaRecorder? = null

    /** 本次录制的输出文件。 */
    private var outputFile: File? = null

    /** start() 成功时的 SystemClock.elapsedRealtime() 基准值。 */
    private var startElapsedRealtime: Long = 0L

    /** 是否处于录制状态；volatile 便于计时线程安全读取。 */
    @Volatile
    private var recording: Boolean = false

    /** 当前是否在录制。 */
    val isRecording: Boolean
        get() = recording

    /**
     * 开始录制。重复调用会先放弃上一次录制。
     * 成功返回 true；SecurityException / IO / IllegalState 等任何异常均被吞掉，
     * 释放 MediaRecorder 后返回 false，不抛异常。
     * 调用方需确保已获得 RECORD_AUDIO 运行时权限。
     */
    @Suppress("MissingPermission")
    @Synchronized
    fun start(): Boolean {
        if (recording) {
            cancel()
        }

        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        return try {
            // API 31 起推荐 MediaRecorder(context)；为兼容 minSdk 26 做版本判断。
            val mr: MediaRecorder = if (Build.VERSION.SDK_INT >= 31) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            // 先登记，保证后续任一步异常时 releaseRecorder() 都能释放它。
            recorder = mr
            outputFile = file

            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(44100)
            mr.setAudioEncodingBitRate(96000)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()

            startElapsedRealtime = SystemClock.elapsedRealtime()
            recording = true
            true
        } catch (e: Exception) {
            releaseRecorder()
            deleteQuietly(file)
            outputFile = null
            startElapsedRealtime = 0L
            false
        }
    }

    /**
     * 停止录制并返回输出文件。
     * 未处于录制状态时返回 null；stop() 抛异常时返回 null 并 release；
     * 无论成功与否都会 release()，仅当文件存在且 length() > 0 时返回该文件。
     */
    @Synchronized
    fun stop(): File? {
        if (!recording) return null

        val file = outputFile
        var stopped = false
        try {
            recorder?.stop()
            stopped = true
        } catch (e: Exception) {
            stopped = false
        }

        // stop 之后必须 release（异常路径同样需要）。
        releaseRecorder()

        val result = if (stopped && file != null && file.exists() && file.length() > 0) {
            file
        } else {
            null
        }
        outputFile = null
        startElapsedRealtime = 0L
        return result
    }

    /** 放弃录制：release() 并删除临时文件；可在任意状态下安全调用。 */
    @Synchronized
    fun cancel() {
        releaseRecorder()
        outputFile?.let { deleteQuietly(it) }
        outputFile = null
        startElapsedRealtime = 0L
    }

    /**
     * 已录制秒数（基于 start 时的 SystemClock.elapsedRealtime()）；未在录制时返回 0。
     */
    fun elapsedSeconds(): Long {
        if (!recording) return 0L
        return (SystemClock.elapsedRealtime() - startElapsedRealtime) / 1000L
    }

    /** release() 本身也可能抛异常；统一在此吞掉并复位状态，绝不冒泡。 */
    private fun releaseRecorder() {
        try {
            recorder?.release()
        } catch (e: Exception) {
            // ignore
        } finally {
            recorder = null
            recording = false
        }
    }

    /** 安静删除文件，删除失败不影响主流程。 */
    private fun deleteQuietly(file: File) {
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}

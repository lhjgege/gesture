@file:Suppress("unused")

package io.legado.app.utils

import android.annotation.SuppressLint
import com.lhj.read.BuildConfig
import io.legado.app.constant.AppLog
import splitties.init.appCtx
import java.text.SimpleDateFormat
import java.util.Date
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.time.Duration.Companion.days

@SuppressLint("SimpleDateFormat")
@Suppress("unused")
object LogUtils {
    const val TIME_PATTERN = "yy-MM-dd HH:mm:ss.SSS"
    val logTimeFormat by lazy { SimpleDateFormat(TIME_PATTERN) }

    @JvmStatic
    fun d(tag: String, msg: String) {
        logger.log(Level.INFO, "$tag $msg")
    }

    inline fun d(tag: String, lazyMsg: () -> String) {
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "$tag ${lazyMsg()}")
        }
    }

    @JvmStatic
    fun e(tag: String, msg: String) {
        logger.log(Level.WARNING, "$tag $msg")
    }

    val logger: Logger by lazy {
        Logger.getLogger("Legado").apply {
            fileHandler?.let {
                addHandler(it)
            }
        }
    }

    private val fileHandler by lazy {
        try {
            val root = appCtx.externalCacheDir ?: return@lazy null
            val logFolder = FileUtils.createFolderIfNotExist(root, "logs")
            val expiredTime = System.currentTimeMillis() - 7.days.inWholeMilliseconds
            logFolder.listFiles()?.forEach {
                if (it.lastModified() < expiredTime || it.name.endsWith(".lck")) {
                    it.delete()
                }
            }
            val date = getCurrentDateStr(TIME_PATTERN)
            val logPath = FileUtils.getPath(root = logFolder, "appLog-$date.txt")
            AsyncFileHandler(logPath).apply {
                formatter = object : java.util.logging.Formatter() {
                    override fun format(record: LogRecord): String {
                        // 设置文件输出格式
                        return (getCurrentDateStr(TIME_PATTERN) + ": " + record.message + "\n")
                    }
                }
                level = Level.INFO
            }
        } catch (e: Exception) {
            e.printStackTrace()
            AppLog.putNotSave("创建fileHandler出错\n$e", e)
            return@lazy null
        }
    }

    fun upLevel() {
        val level = Level.INFO
        fileHandler?.level = level
    }

    /**
     * 获取当前时间
     */
    @SuppressLint("SimpleDateFormat")
    fun getCurrentDateStr(pattern: String): String {
        val date = Date()
        val sdf = SimpleDateFormat(pattern)
        return sdf.format(date)
    }

}

fun Throwable.printOnDebug() {
    if (BuildConfig.DEBUG) {
        printStackTrace()
    }
}

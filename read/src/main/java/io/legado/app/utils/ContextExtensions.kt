@file:Suppress("unused", "UnusedReceiverParameter")

package io.legado.app.utils

import android.content.Context
import android.content.Intent
import android.os.Process
import java.io.File
import kotlin.system.exitProcess

fun Context.restart() {
    val intent: Intent? = packageManager.getLaunchIntentForPackage(packageName)
    intent?.let {
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        startActivity(intent)
        //杀掉以前进程
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}

val Context.externalFiles: File
    get() = this.getExternalFilesDir(null) ?: this.filesDir

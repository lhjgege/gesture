package io.legado.app.constant

import android.annotation.SuppressLint
import android.provider.Settings
import splitties.init.appCtx
import java.text.SimpleDateFormat

@Suppress("ConstPropertyName")
@SuppressLint("SimpleDateFormat")
object AppConst {


    const val UA_NAME = "User-Agent"

    const val MAX_THREAD = 9


    val dateFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy/MM/dd HH:mm")
    }

    val androidId: String by lazy {
        Settings.System.getString(appCtx.contentResolver, Settings.Secure.ANDROID_ID) ?: "null"
    }

}

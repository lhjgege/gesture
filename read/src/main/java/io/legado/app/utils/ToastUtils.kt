@file:Suppress("unused")

package io.legado.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.lhj.read.BuildConfig
import com.lhj.read.databinding.ViewToastBinding
import splitties.systemservices.layoutInflater

private var toast: Toast? = null

private var toastLegacy: Toast? = null

fun Context.toastOnUi(message: Int, duration: Int = Toast.LENGTH_SHORT) {
    toastOnUi(getString(message), duration)
}

@SuppressLint("InflateParams")
@Suppress("DEPRECATION")
fun Context.toastOnUi(message: CharSequence?, duration: Int = Toast.LENGTH_SHORT) {
    runOnUI {
        kotlin.runCatching {
            toast?.cancel()
            toast = Toast(this)
            ViewToastBinding.inflate(layoutInflater).run {
                toast?.view = root
                tvText.text = message
            }
            toast?.duration = duration
            toast?.show()
        }
    }
}

fun Context.toastOnUiLegacy(message: CharSequence) {
    runOnUI {
        kotlin.runCatching {
            if (toastLegacy == null || BuildConfig.DEBUG) {
                toastLegacy = Toast.makeText(this, message, Toast.LENGTH_SHORT)
            } else {
                toastLegacy?.setText(message)
                toastLegacy?.duration = Toast.LENGTH_SHORT
            }
            toastLegacy?.show()
        }
    }
}

fun Context.longToastOnUi(message: Int) {
    toastOnUi(message, Toast.LENGTH_LONG)
}

fun Context.longToastOnUi(message: CharSequence?) {
    toastOnUi(message, Toast.LENGTH_LONG)
}

fun Context.longToastOnUiLegacy(message: CharSequence) {
    runOnUI {
        kotlin.runCatching {
            if (toastLegacy == null || BuildConfig.DEBUG) {
                toastLegacy = Toast.makeText(this, message, Toast.LENGTH_LONG)
            } else {
                toastLegacy?.setText(message)
                toastLegacy?.duration = Toast.LENGTH_LONG
            }
            toastLegacy?.show()
        }
    }
}

fun Fragment.toastOnUi(message: Int) = requireActivity().toastOnUi(message)

fun Fragment.toastOnUi(message: CharSequence) = requireActivity().toastOnUi(message)

fun Fragment.longToast(message: Int) = requireContext().longToastOnUi(message)

fun Fragment.longToast(message: CharSequence) = requireContext().longToastOnUi(message)

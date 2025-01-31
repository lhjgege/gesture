package io.legado.app.constant

@Suppress("MemberVisibilityCanBePrivate", "ConstPropertyName")
object AppConfig {
    var userAgent: String = getPrefUserAgent()
    private fun getPrefUserAgent(): String {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0" + " Safari/537.36"
    }
}


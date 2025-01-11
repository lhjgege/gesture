package io.legado.app.help

import android.net.Uri
import android.webkit.WebSettings
import androidx.annotation.Keep
import cn.hutool.core.codec.Base64
import cn.hutool.core.util.HexUtil
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppConst.dateFormat
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.BaseSource
import io.legado.app.help.http.BackstageWebView
import io.legado.app.help.http.CookieManager.cookieJarHeader
import io.legado.app.help.http.CookieStore
import io.legado.app.help.http.SSLHelper
import io.legado.app.help.http.StrResponse
import io.legado.app.model.Debug
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.QueryTTF
import io.legado.app.script.rhino.RhinoContext
import io.legado.app.utils.EncoderUtils
import io.legado.app.utils.EncodingDetect
import io.legado.app.utils.GSON
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.JsURL
import io.legado.app.utils.StringUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.readBytes
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toStringArray
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okio.use
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.mozilla.javascript.Context
import splitties.init.appCtx
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.SimpleTimeZone
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * js扩展类, 在js中通过java变量调用
 * 添加方法，请更新文档/legado/app/src/main/assets/help/JsHelp.md
 * 所有对于文件的读写删操作都是相对路径,只能操作阅读缓存内的文件
 * /android/data/{package}/cache/...
 */
@Keep
@Suppress("unused")
interface JsExtensions : JsEncodeUtils {

    fun getSource(): BaseSource?

    private val context: CoroutineContext
        get() {
            val rhinoContext = Context.getCurrentContext() as RhinoContext
            return rhinoContext.coroutineContext ?: EmptyCoroutineContext
        }

    /**
     * 访问网络,返回String
     */
    fun ajax(url: Any): String? {
        val urlStr = if (url is List<*>) {
            url.firstOrNull().toString()
        } else {
            url.toString()
        }
        val analyzeUrl = AnalyzeUrl(urlStr, source = getSource(), coroutineContext = context)
        return runBlocking(context) {
            kotlin.runCatching {
                analyzeUrl.getStrResponseAwait().body
            }.onFailure {
                AppLog.put("ajax(${urlStr}) error\n${it.localizedMessage}", it)
            }.getOrElse {
                it.stackTraceStr
            }
        }
    }

    /**
     * 并发访问网络
     */
    fun ajaxAll(urlList: Array<String>): Array<StrResponse?> {
        return runBlocking(context) {
            val asyncArray = Array(urlList.size) {
                async(IO) {
                    val url = urlList[it]
                    val analyzeUrl = AnalyzeUrl(url, source = getSource())
                    analyzeUrl.getStrResponseAwait()
                }
            }
            val resArray = Array<StrResponse?>(urlList.size) {
                asyncArray[it].await()
            }
            resArray
        }
    }

    /**
     * 访问网络,返回Response<String>
     */
    fun connect(urlStr: String): StrResponse {
        return runBlocking(context) {
            val analyzeUrl = AnalyzeUrl(urlStr, source = getSource())
            kotlin.runCatching {
                analyzeUrl.getStrResponseAwait()
            }.onFailure {
                AppLog.put("connect(${urlStr}) error\n${it.localizedMessage}", it)
            }.getOrElse {
                StrResponse(analyzeUrl.url, it.stackTraceStr)
            }
        }
    }

    fun connect(urlStr: String, header: String?): StrResponse {
        return runBlocking(context) {
            val headerMap = GSON.fromJsonObject<Map<String, String>>(header).getOrNull()
            val analyzeUrl = AnalyzeUrl(urlStr, headerMapF = headerMap, source = getSource())
            kotlin.runCatching {
                analyzeUrl.getStrResponseAwait()
            }.onFailure {
                AppLog.put("ajax($urlStr,$header) error\n${it.localizedMessage}", it)
            }.getOrElse {
                StrResponse(analyzeUrl.url, it.stackTraceStr)
            }
        }
    }

    /**
     * 使用webView访问网络
     * @param html 直接用webView载入的html, 如果html为空直接访问url
     * @param url html内如果有相对路径的资源不传入url访问不了
     * @param js 用来取返回值的js语句, 没有就返回整个源代码
     * @return 返回js获取的内容
     */
    fun webView(html: String?, url: String?, js: String?): String? {
        return runBlocking(context) {
            BackstageWebView(
                url = url,
                html = html,
                javaScript = js,
                headerMap = getSource()?.getHeaderMap(true),
                tag = getSource()?.getKey()
            ).getStrResponse().body
        }
    }

    /**
     * 使用webView获取资源url
     */
    fun webViewGetSource(html: String?, url: String?, js: String?, sourceRegex: String): String? {
        return runBlocking(context) {
            BackstageWebView(
                url = url,
                html = html,
                javaScript = js,
                headerMap = getSource()?.getHeaderMap(true),
                tag = getSource()?.getKey(),
                sourceRegex = sourceRegex
            ).getStrResponse().body
        }
    }

    /**
     * 使用webView获取跳转url
     */
    fun webViewGetOverrideUrl(
        html: String?,
        url: String?,
        js: String?,
        overrideUrlRegex: String,
    ): String? {
        return runBlocking(context) {
            BackstageWebView(
                url = url,
                html = html,
                javaScript = js,
                headerMap = getSource()?.getHeaderMap(true),
                tag = getSource()?.getKey(),
                overrideUrlRegex = overrideUrlRegex
            ).getStrResponse().body
        }
    }


    /**
     *js实现读取cookie
     */
    fun getCookie(tag: String): String {
        return getCookie(tag, null)
    }

    fun getCookie(tag: String, key: String?): String {
        return if (key != null) {
            CookieStore.getKey(tag, key)
        } else {
            CookieStore.getCookie(tag)
        }
    }


    /**
     * js实现重定向拦截,网络访问get
     */
    fun get(urlStr: String, headers: Map<String, String>): Connection.Response {
        val requestHeaders = if (getSource()?.enabledCookieJar == true) {
            headers.toMutableMap().apply { put(cookieJarHeader, "1") }
        } else headers
        val response = Jsoup.connect(urlStr)
            .sslSocketFactory(SSLHelper.unsafeSSLSocketFactory)
            .ignoreContentType(true)
            .followRedirects(false)
            .headers(requestHeaders)
            .method(Connection.Method.GET)
            .execute()
        return response
    }

    /**
     * js实现重定向拦截,网络访问head,不返回Response Body更省流量
     */
    fun head(urlStr: String, headers: Map<String, String>): Connection.Response {
        val requestHeaders = if (getSource()?.enabledCookieJar == true) {
            headers.toMutableMap().apply { put(cookieJarHeader, "1") }
        } else headers
        val response = Jsoup.connect(urlStr)
            .sslSocketFactory(SSLHelper.unsafeSSLSocketFactory)
            .ignoreContentType(true)
            .followRedirects(false)
            .headers(requestHeaders)
            .method(Connection.Method.HEAD)
            .execute()
        return response
    }

    /**
     * 网络访问post
     */
    fun post(urlStr: String, body: String, headers: Map<String, String>): Connection.Response {
        val requestHeaders = if (getSource()?.enabledCookieJar == true) {
            headers.toMutableMap().apply { put(cookieJarHeader, "1") }
        } else headers
        val response = Jsoup.connect(urlStr)
            .sslSocketFactory(SSLHelper.unsafeSSLSocketFactory)
            .ignoreContentType(true)
            .followRedirects(false)
            .requestBody(body)
            .headers(requestHeaders)
            .method(Connection.Method.POST)
            .execute()
        return response
    }

    /* Str转ByteArray */
    fun strToBytes(str: String): ByteArray {
        return str.toByteArray(charset("UTF-8"))
    }

    fun strToBytes(str: String, charset: String): ByteArray {
        return str.toByteArray(charset(charset))
    }

    /* ByteArray转Str */
    fun bytesToStr(bytes: ByteArray): String {
        return String(bytes, charset("UTF-8"))
    }

    fun bytesToStr(bytes: ByteArray, charset: String): String {
        return String(bytes, charset(charset))
    }

    /**
     * js实现base64解码,不能删
     */
    fun base64Decode(str: String?): String {
        return Base64.decodeStr(str)
    }

    fun base64Decode(str: String?, charset: String): String {
        return Base64.decodeStr(str, charset(charset))
    }

    fun base64Decode(str: String, flags: Int): String {
        return EncoderUtils.base64Decode(str, flags)
    }

    fun base64DecodeToByteArray(str: String?): ByteArray? {
        if (str.isNullOrBlank()) {
            return null
        }
        return EncoderUtils.base64DecodeToByteArray(str, 0)
    }

    fun base64DecodeToByteArray(str: String?, flags: Int): ByteArray? {
        if (str.isNullOrBlank()) {
            return null
        }
        return EncoderUtils.base64DecodeToByteArray(str, flags)
    }

    fun base64Encode(str: String): String? {
        return EncoderUtils.base64Encode(str, 2)
    }

    fun base64Encode(str: String, flags: Int): String? {
        return EncoderUtils.base64Encode(str, flags)
    }


    /**
     * 格式化时间
     */
    fun timeFormatUTC(time: Long, format: String, sh: Int): String? {
        val utc = SimpleTimeZone(sh, "UTC")
        return SimpleDateFormat(format, Locale.getDefault()).run {
            timeZone = utc
            format(Date(time))
        }
    }

    /**
     * 时间格式化
     */
    fun timeFormat(time: Long): String {
        return dateFormat.format(Date(time))
    }

    /**
     * utf8编码转gbk编码
     */
    fun utf8ToGbk(str: String): String {
        val utf8 = String(str.toByteArray(charset("UTF-8")))
        val unicode = String(utf8.toByteArray(), charset("UTF-8"))
        return String(unicode.toByteArray(charset("GBK")))
    }

    fun encodeURI(str: String): String {
        return try {
            URLEncoder.encode(str, "UTF-8")
        } catch (e: Exception) {
            ""
        }
    }

    fun encodeURI(str: String, enc: String): String {
        return try {
            URLEncoder.encode(str, enc)
        } catch (e: Exception) {
            ""
        }
    }

    fun htmlFormat(str: String): String {
        return HtmlFormatter.formatKeepImg(str)
    }

    fun getWebViewUA(): String {
        return WebSettings.getDefaultUserAgent(appCtx)
    }

//****************文件操作******************//

    /**
     * 获取网络zip文件里面的数据
     * @param url zip文件的链接或十六进制字符串
     * @param path 所需获取文件在zip内的路径
     * @return zip指定文件的数据
     */
    fun getZipStringContent(url: String, path: String): String {
        val byteArray = getZipByteArrayContent(url, path) ?: return ""
        val charsetName = EncodingDetect.getEncode(byteArray)
        return String(byteArray, Charset.forName(charsetName))
    }

    fun getZipStringContent(url: String, path: String, charsetName: String): String {
        val byteArray = getZipByteArrayContent(url, path) ?: return ""
        return String(byteArray, Charset.forName(charsetName))
    }

    /**
     * 获取网络zip文件里面的数据
     * @param url zip文件的链接或十六进制字符串
     * @param path 所需获取文件在zip内的路径
     * @return zip指定文件的数据
     */
    fun getZipByteArrayContent(url: String, path: String): ByteArray? {
        val bytes = if (url.isAbsUrl()) {
            AnalyzeUrl(url, source = getSource(), coroutineContext = context).getByteArray()
        } else {
            HexUtil.decodeHex(url)
        }
        val bos = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry: ZipEntry
            while (zis.nextEntry.also { entry = it } != null) {
                if (entry.name.equals(path)) {
                    zis.use { it.copyTo(bos) }
                    return bos.toByteArray()
                }
                entry = zis.nextEntry
            }
        }

        log("getZipContent 未发现内容")
        return null
    }


//******************文件操作************************//

    /**
     * 解析字体Base64数据,返回字体解析类
     */
    fun queryBase64TTF(data: String?): QueryTTF? {
        log("queryBase64TTF(String)方法已过时,并将在未来删除；请无脑使用queryTTF(Any)替代，新方法支持传入 url、本地文件、base64、ByteArray 自动判断&自动缓存，特殊情况需禁用缓存请传入第二可选参数false:Boolean")
        return queryTTF(data)
    }

    /**
     * 返回字体解析类
     * @param data 支持url,本地文件,base64,ByteArray,自动判断,自动缓存
     * @param useCache 可选开关缓存,不传入该值默认开启缓存
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun queryTTF(data: Any?, useCache: Boolean): QueryTTF? {
        try {
            var key: String? = null
            var qTTF: QueryTTF?
            when (data) {
                is String -> {
                    if (useCache) {
                        key = MessageDigest.getInstance("SHA-256").digest(data.toByteArray())
                            .toHexString()
                        qTTF = CacheManager.getQueryTTF(key)
                        if (qTTF != null) return qTTF
                    }
                    val font: ByteArray? = when {
                        data.isContentScheme() -> Uri.parse(data).readBytes(appCtx)
                        data.startsWith("/storage") -> File(data).readBytes()
                        data.isAbsUrl() -> AnalyzeUrl(
                            data,
                            source = getSource(),
                            coroutineContext = context
                        ).getByteArray()

                        else -> base64DecodeToByteArray(data)
                    }
                    font ?: return null
                    qTTF = QueryTTF(font)
                }

                is ByteArray -> {
                    if (useCache) {
                        key = MessageDigest.getInstance("SHA-256").digest(data).toHexString()
                        qTTF = CacheManager.getQueryTTF(key)
                        if (qTTF != null) return qTTF
                    }
                    qTTF = QueryTTF(data)
                }

                else -> return null
            }
            if (key != null) CacheManager.put(key, qTTF)
            return qTTF
        } catch (e: Exception) {
            AppLog.put("[queryTTF] 获取字体处理类出错", e)
            throw e
        }
    }

    fun queryTTF(data: Any?): QueryTTF? {
        return queryTTF(data, true)
    }

    /**
     * @param text 包含错误字体的内容
     * @param errorQueryTTF 错误的字体
     * @param correctQueryTTF 正确的字体
     * @param filter 删除 errorQueryTTF 中不存在的字符
     */
    fun replaceFont(
        text: String,
        errorQueryTTF: QueryTTF?,
        correctQueryTTF: QueryTTF?,
        filter: Boolean,
    ): String {
        if (errorQueryTTF == null || correctQueryTTF == null) return text
        val contentArray = text.toStringArray() //这里不能用toCharArray,因为有些文字占多个字节
        contentArray.forEachIndexed { index, s ->
            val oldCode = s.codePointAt(0)
            // 忽略正常的空白字符
            if (errorQueryTTF.isBlankUnicode(oldCode)) {
                return@forEachIndexed
            }
            // 删除轮廓数据不存在的字符
            var glyf = errorQueryTTF.getGlyfByUnicode(oldCode)  // 轮廓数据不存在
            if (errorQueryTTF.getGlyfIdByUnicode(oldCode) == 0) glyf = null // 轮廓数据指向保留索引0
            if (filter && (glyf == null)) {
                contentArray[index] = ""
                return@forEachIndexed
            }
            // 使用轮廓数据反查Unicode
            val code = correctQueryTTF.getUnicodeByGlyf(glyf)
            if (code != 0) {
                contentArray[index] = code.toChar().toString()
            }
        }
        return contentArray.joinToString("")
    }

    /**
     * @param text 包含错误字体的内容
     * @param errorQueryTTF 错误的字体
     * @param correctQueryTTF 正确的字体
     */
    fun replaceFont(
        text: String,
        errorQueryTTF: QueryTTF?,
        correctQueryTTF: QueryTTF?,
    ): String {
        return replaceFont(text, errorQueryTTF, correctQueryTTF, false)
    }


    /**
     * 章节数转数字
     */
    fun toNumChapter(s: String?): String? {
        s ?: return null
        val matcher = AppPattern.titleNumPattern.matcher(s)
        if (matcher.find()) {
            return "${matcher.group(1)}${StringUtils.stringToInt(matcher.group(2))}${matcher.group(3)}"
        }
        return s
    }


    fun toURL(urlStr: String): JsURL {
        return JsURL(urlStr)
    }

    fun toURL(url: String, baseUrl: String? = null): JsURL {
        return JsURL(url, baseUrl)
    }

    /**
     * 弹窗提示
     */
    fun toast(msg: Any?) {
        appCtx.toastOnUi("${getSource()?.getTag()}: ${msg.toString()}")
    }

    /**
     * 弹窗提示 停留时间较长
     */
    fun longToast(msg: Any?) {
        appCtx.longToastOnUi("${getSource()?.getTag()}: ${msg.toString()}")
    }

    /**
     * 输出调试日志
     */
    fun log(msg: Any?): Any? {
        getSource()?.let {
            Debug.log(it.getKey(), msg.toString())
        } ?: Debug.log(msg.toString())
        AppLog.putDebug("源调试输出：$msg")
        return msg
    }

    /**
     * 输出对象类型
     */
    fun logType(any: Any?) {
        if (any == null) {
            log("null")
        } else {
            log(any.javaClass.name)
        }
    }

    /**
     * 生成UUID
     */
    fun randomUUID(): String {
        return UUID.randomUUID().toString()
    }

    fun androidId(): String {
        return AppConst.androidId
    }

}

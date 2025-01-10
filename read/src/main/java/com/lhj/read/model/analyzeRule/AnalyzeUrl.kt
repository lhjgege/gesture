package com.lhj.read.model.analyzeRule

import com.lhj.read.constant.AppConst
import com.lhj.read.constant.AppConst.UA_NAME
import com.lhj.read.constant.AppPattern
import com.lhj.read.constant.AppPattern.JS_PATTERN
import com.lhj.read.constant.AppPattern.dataUriRegex
import com.lhj.read.data.entities.BaseSource
import com.lhj.read.data.entities.Book
import com.lhj.read.data.entities.BookChapter
import com.lhj.read.exception.ConcurrentException
import com.lhj.read.help.CacheManager
import com.lhj.read.help.JsExtensions
import com.lhj.read.help.http.BackstageWebView
import com.lhj.read.help.http.CookieStore
import com.lhj.read.help.http.RequestMethod
import com.lhj.read.help.http.StrResponse
import com.lhj.read.help.http.addHeaders
import com.lhj.read.help.http.get
import com.lhj.read.help.http.getProxyClient
import com.lhj.read.help.http.newCallResponse
import com.lhj.read.help.http.newCallResponseBody
import com.lhj.read.help.http.newCallStrResponse
import com.lhj.read.help.http.postForm
import com.lhj.read.help.http.postJson
import com.lhj.read.help.http.postMultipart
import com.lhj.read.model.DebugLog
import com.lhj.read.script.buildScriptBindings
import com.lhj.read.script.rhino.RhinoScriptEngine
import com.lhj.read.utils.Base64
import com.lhj.read.utils.EncoderUtils
import com.lhj.read.utils.GSON
import com.lhj.read.utils.NetworkUtils
import com.lhj.read.utils.StringUtils
import com.lhj.read.utils.fromJsonArray
import com.lhj.read.utils.fromJsonObject
import com.lhj.read.utils.isDataUrl
import com.lhj.read.utils.isJson
import com.lhj.read.utils.isJsonArray
import com.lhj.read.utils.isJsonObject
import com.lhj.read.utils.isXml
import com.lhj.read.utils.splitNotBlank
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.net.URLEncoder
import java.util.regex.Pattern
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Created by GKF on 2018/1/24.
 * 搜索URL规则解析
 */
class AnalyzeUrl(
    val mUrl: String,
    val key: String? = null,
    val page: Int? = null,
    val speakText: String? = null,
    val speakSpeed: Int? = null,
    var baseUrl: String = "",
    private val source: BaseSource? = null,
    private val ruleData: RuleDataInterface? = null,
    private val chapter: BookChapter? = null,
    headerMapF: Map<String, String>? = null,
    private var coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : JsExtensions {
    companion object {
        val paramPattern: Pattern = Pattern.compile("\\s*,\\s*(?=\\{)")
        private val pagePattern = Pattern.compile("<(.*?)>")
        private val concurrentRecordMap = hashMapOf<String, ConcurrentRecord>()
    }

    var ruleUrl = ""
        private set
    var url: String = ""
        private set
    var body: String? = null
        private set
    var type: String? = null
        private set
    val headerMap = HashMap<String, String>()
    private var urlNoQuery: String = ""
    private var queryStr: String? = null
    private val fieldMap = LinkedHashMap<String, String>()
    private var charset: String? = null
    private var method = RequestMethod.GET
    private var proxy: String? = null
    private var retry: Int = 0
    private var useWebView: Boolean = false
    private var webJs: String? = null
    private var webViewDelayTime: Long = 0

    init {
        if (!mUrl.isDataUrl()) {
            val urlMatcher = paramPattern.matcher(baseUrl)
            if (urlMatcher.find()) baseUrl = baseUrl.substring(0, urlMatcher.start())
            (headerMapF ?: source?.getHeaderMap(true))?.let {
                headerMap.putAll(it)
                if (it.containsKey("proxy")) {
                    proxy = it["proxy"]
                    headerMap.remove("proxy")
                }
            }
            initUrl()
        }
    }

    /**
     * 处理url
     */
    fun initUrl() {
        ruleUrl = mUrl
        //执行@js,<js></js>
        analyzeJs()
        //替换参数
        replaceKeyPageJs()
        //处理URL
        analyzeUrl()
    }

    /**
     * 执行@js,<js></js>
     */
    private fun analyzeJs() {
        var start = 0
        val jsMatcher = JS_PATTERN.matcher(ruleUrl)
        var result = ruleUrl
        while (jsMatcher.find()) {
            if (jsMatcher.start() > start) {
                ruleUrl.substring(start, jsMatcher.start()).trim().let {
                    if (it.isNotEmpty()) {
                        result = it.replace("@result", result)
                    }
                }
            }
            result = evalJS(jsMatcher.group(2) ?: jsMatcher.group(1), result).toString()
            start = jsMatcher.end()
        }
        if (ruleUrl.length > start) {
            ruleUrl.substring(start).trim().let {
                if (it.isNotEmpty()) {
                    result = it.replace("@result", result)
                }
            }
        }
        ruleUrl = result
    }

    /**
     * 替换关键字,页数,JS
     */
    private fun replaceKeyPageJs() { //先替换内嵌规则再替换页数规则，避免内嵌规则中存在大于小于号时，规则被切错
        //js
        if (ruleUrl.contains("{{") && ruleUrl.contains("}}")) {
            val analyze = RuleAnalyzer(ruleUrl) //创建解析
            //替换所有内嵌{{js}}
            val url = analyze.innerRule("{{", "}}") {
                val jsEval = evalJS(it) ?: ""
                when {
                    jsEval is String -> jsEval
                    jsEval is Double && jsEval % 1.0 == 0.0 -> String.format("%.0f", jsEval)
                    else -> jsEval.toString()
                }
            }
            if (url.isNotEmpty()) ruleUrl = url
        }
        //page
        page?.let {
            val matcher = pagePattern.matcher(ruleUrl)
            while (matcher.find()) {
                val pages = matcher.group(1)!!.split(",")
                ruleUrl = if (page < pages.size) { //pages[pages.size - 1]等同于pages.last()
                    ruleUrl.replace(matcher.group(), pages[page - 1].trim { it <= ' ' })
                } else {
                    ruleUrl.replace(matcher.group(), pages.last().trim { it <= ' ' })
                }
            }
        }
    }

    /**
     * 解析Url
     */
    private fun analyzeUrl() {
        //replaceKeyPageJs已经替换掉额外内容，此处url是基础形式，可以直接切首个‘,’之前字符串。
        val urlMatcher = paramPattern.matcher(ruleUrl)
        val urlNoOption =
            if (urlMatcher.find()) ruleUrl.substring(0, urlMatcher.start()) else ruleUrl
        url = NetworkUtils.getAbsoluteURL(baseUrl, urlNoOption)
        NetworkUtils.getBaseUrl(url)?.let {
            baseUrl = it
        }
        if (urlNoOption.length != ruleUrl.length) {
            GSON.fromJsonObject<UrlOption>(ruleUrl.substring(urlMatcher.end())).getOrNull()
                ?.let { option ->
                    option.getMethod()?.let {
                        if (it.equals("POST", true)) method = RequestMethod.POST
                    }
                    option.getHeaderMap()?.forEach { entry ->
                        headerMap[entry.key.toString()] = entry.value.toString()
                    }
                    option.getBody()?.let {
                        body = it
                    }
                    type = option.getType()
                    charset = option.getCharset()
                    retry = option.getRetry()
                    useWebView = option.useWebView()
                    webJs = option.getWebJs()
                    option.getJs()?.let { jsStr ->
                        evalJS(jsStr, url)?.toString()?.let {
                            url = it
                        }
                    }
                }
        }
        urlNoQuery = url
        when (method) {
            RequestMethod.GET -> {
                val pos = url.indexOf('?')
                if (pos != -1) {
                    analyzeFields(url.substring(pos + 1))
                    urlNoQuery = url.substring(0, pos)
                }
            }

            RequestMethod.POST -> body?.let {
                if (!it.isJson() && !it.isXml() && headerMap["Content-Type"].isNullOrEmpty()) {
                    analyzeFields(it)
                }
            }
        }
    }

    /**
     * 解析QueryMap
     */
    private fun analyzeFields(fieldsTxt: String) {
        queryStr = fieldsTxt
        val queryS = fieldsTxt.splitNotBlank("&")
        for (query in queryS) {
            val queryPair = query.splitNotBlank("=", limit = 2)
            val key = queryPair[0]
            val value = queryPair.getOrNull(1) ?: ""
            if (charset.isNullOrEmpty()) {
                if (NetworkUtils.hasUrlEncoded(value)) {
                    fieldMap[key] = value
                } else {
                    fieldMap[key] = URLEncoder.encode(value, "UTF-8")
                }
            } else if (charset == "escape") {
                fieldMap[key] = EncoderUtils.escape(value)
            } else {
                fieldMap[key] = URLEncoder.encode(value, charset)
            }
        }
    }

    /**
     * 执行JS
     */
    fun evalJS(jsStr: String, result: Any? = null): Any? {
        val bindings = buildScriptBindings { bindings ->
            bindings["java"] = this
            bindings["baseUrl"] = baseUrl
            bindings["cookie"] = CookieStore
            bindings["cache"] = CacheManager
            bindings["page"] = page
            bindings["key"] = key
            bindings["speakText"] = speakText
            bindings["speakSpeed"] = speakSpeed
            bindings["book"] = ruleData as? Book
            bindings["source"] = source
            bindings["result"] = result
        }
        val scope = RhinoScriptEngine.getRuntimeScope(bindings)
        source?.getShareScope()?.let {
            scope.prototype = it
        }
        return RhinoScriptEngine.eval(jsStr, scope, coroutineContext)
    }

    fun put(key: String, value: String): String {
        chapter?.putVariable(key, value)
            ?: ruleData?.putVariable(key, value)
        return value
    }

    fun get(key: String): String {
        when (key) {
            "bookName" -> (ruleData as? Book)?.let {
                return it.name
            }

            "title" -> chapter?.let {
                return it.title
            }
        }
        return chapter?.getVariable(key)?.takeIf { it.isNotEmpty() }
            ?: ruleData?.getVariable(key)?.takeIf { it.isNotEmpty() }
            ?: ""
    }

    /**
     * 开始访问,并发判断
     */
    private fun fetchStart(): ConcurrentRecord? {
        source ?: return null
        val concurrentRate = source.concurrentRate
        if (concurrentRate.isNullOrEmpty() || concurrentRate == "0") {
            return null
        }
        val rateIndex = concurrentRate.indexOf("/")
        var fetchRecord = concurrentRecordMap[source.getKey()]
        if (fetchRecord == null) {
            synchronized(concurrentRecordMap) {
                fetchRecord = concurrentRecordMap[source.getKey()]
                if (fetchRecord == null) {
                    fetchRecord = ConcurrentRecord(rateIndex > 0, System.currentTimeMillis(), 1)
                    concurrentRecordMap[source.getKey()] = fetchRecord!!
                    return fetchRecord
                }
            }
        }

        val waitTime: Int = synchronized(fetchRecord!!) {
            try {
                if (!fetchRecord!!.isConcurrent) {
                    //并发控制非 次数/毫秒
                    if (fetchRecord!!.frequency > 0) {
                        //已经有访问线程,直接等待
                        return@synchronized concurrentRate.toInt()
                    }
                    //没有线程访问,判断还剩多少时间可以访问
                    val nextTime = fetchRecord!!.time + concurrentRate.toInt()
                    if (System.currentTimeMillis() >= nextTime) {
                        fetchRecord!!.time = System.currentTimeMillis()
                        fetchRecord!!.frequency = 1
                        return@synchronized 0
                    }
                    return@synchronized (nextTime - System.currentTimeMillis()).toInt()
                } else {
                    //并发控制为 次数/毫秒
                    val sj = concurrentRate.substring(rateIndex + 1)
                    val nextTime = fetchRecord!!.time + sj.toInt()
                    if (System.currentTimeMillis() >= nextTime) {
                        //已经过了限制时间,重置开始时间
                        fetchRecord!!.time = System.currentTimeMillis()
                        fetchRecord!!.frequency = 1
                        return@synchronized 0
                    }
                    val cs = concurrentRate.substring(0, rateIndex)
                    if (fetchRecord!!.frequency > cs.toInt()) {
                        return@synchronized (nextTime - System.currentTimeMillis()).toInt()
                    } else {
                        fetchRecord!!.frequency += 1
                        return@synchronized 0
                    }
                }
            } catch (e: Exception) {
                return@synchronized 0
            }
        }
        if (waitTime > 0) {
            throw ConcurrentException(
                "根据并发率还需等待${waitTime}毫秒才可以访问",
                waitTime = waitTime
            )
        }
        return fetchRecord
    }

    /**
     * 访问结束
     */
    private fun fetchEnd(concurrentRecord: ConcurrentRecord?) {
        if (concurrentRecord != null && !concurrentRecord.isConcurrent) {
            synchronized(concurrentRecord) {
                concurrentRecord.frequency -= 1
            }
        }
    }

    /**
     * 访问网站,返回StrResponse
     */
    suspend fun getStrResponseAwait(
        jsStr: String? = null,
        sourceRegex: String? = null,
        useWebView: Boolean = true,
        debugLog: DebugLog? = null
    ): StrResponse {
        if (type != null) {
            return StrResponse(url, StringUtils.byteToHexString(getByteArrayAwait()))
        }
        val concurrentRecord = getConcurrentRecord()
        try {
            setCookie(source?.getKey())
            val strResponse: StrResponse
            if (this.useWebView && useWebView) {
                strResponse = when (method) {
                    RequestMethod.POST -> {
                        val res = getProxyClient(proxy, debugLog).newCallStrResponse(retry) {
                            addHeaders(headerMap)
                            url(urlNoQuery)
                            if (fieldMap.isNotEmpty() || body.isNullOrBlank()) {
                                postForm(fieldMap, true)
                            } else {
                                postJson(body)
                            }
                        }
                        BackstageWebView(
                            url = res.url,
                            html = res.body,
                            tag = source?.getKey(),
                            javaScript = webJs ?: jsStr,
                            sourceRegex = sourceRegex,
                            headerMap = headerMap,
                            delayTime = webViewDelayTime
                        ).getStrResponse()
                    }

                    else -> BackstageWebView(
                        url = url,
                        tag = source?.getKey(),
                        javaScript = webJs ?: jsStr,
                        sourceRegex = sourceRegex,
                        headerMap = headerMap,
                        delayTime = webViewDelayTime
                    ).getStrResponse()
                }
            } else {
                strResponse = getProxyClient(proxy, debugLog).newCallStrResponse(retry) {
                    addHeaders(headerMap)
                    when (method) {
                        RequestMethod.POST -> {
                            url(urlNoQuery)
                            val contentType = headerMap["Content-Type"]
                            val body = body
                            if (fieldMap.isNotEmpty() || body.isNullOrBlank()) {
                                postForm(fieldMap, true)
                            } else if (!contentType.isNullOrBlank()) {
                                val requestBody = body.toRequestBody(contentType.toMediaType())
                                post(requestBody)
                            } else {
                                postJson(body)
                            }
                        }

                        else -> get(urlNoQuery, fieldMap, true)
                    }
                }.let {
                    val isXml = it.raw.body?.contentType()?.toString()
                        ?.matches(AppPattern.xmlContentTypeRegex) == true
                    if (isXml && it.body?.trim()?.startsWith("<?xml", true) == false) {
                        StrResponse(it.raw, "<?xml version=\"1.0\"?>" + it.body)
                    } else it
                }
            }
            return strResponse
        } finally {
            //saveCookie()
            fetchEnd(concurrentRecord)
        }
    }

    /**
     * 获取并发记录，若处于并发限制状态下则会等待
     */
    private suspend fun getConcurrentRecord(): ConcurrentRecord? {
        while (true) {
            try {
                return fetchStart()
            } catch (e: ConcurrentException) {
                delay(e.waitTime.toLong())
            }
        }
    }

    @JvmOverloads
    fun getStrResponse(
        jsStr: String? = null,
        sourceRegex: String? = null,
        useWebView: Boolean = true,
        debugLog: DebugLog? = null
    ): StrResponse {
        return runBlocking {
            getStrResponseAwait(jsStr, sourceRegex, useWebView, debugLog)
        }
    }

    /**
     * 访问网站,返回Response
     */
    suspend fun getResponseAwait(): Response {
        val concurrentRecord = fetchStart()
        setCookie(source?.getKey())
        @Suppress("BlockingMethodInNonBlockingContext")
        val response = getProxyClient(proxy).newCallResponse(retry) {
            addHeaders(headerMap)
            when (method) {
                RequestMethod.POST -> {
                    url(urlNoQuery)
                    val contentType = headerMap["Content-Type"]
                    val body = body
                    if (fieldMap.isNotEmpty() || body.isNullOrBlank()) {
                        postForm(fieldMap, true)
                    } else if (!contentType.isNullOrBlank()) {
                        val requestBody = body.toRequestBody(contentType.toMediaType())
                        post(requestBody)
                    } else {
                        postJson(body)
                    }
                }

                else -> get(urlNoQuery, fieldMap, true)
            }
        }
        fetchEnd(concurrentRecord)
        return response
    }

    fun getResponse(): Response {
        return runBlocking {
            getResponseAwait()
        }
    }

    /**
     * 访问网站,返回ByteArray
     */
    suspend fun getByteArrayAwait(): ByteArray {
        val concurrentRecord = fetchStart()

        @Suppress("RegExpRedundantEscape")
        val dataUriFindResult = dataUriRegex.find(urlNoQuery)
        @Suppress("BlockingMethodInNonBlockingContext")
        if (dataUriFindResult != null) {
            val dataUriBase64 = dataUriFindResult.groupValues[1]
            val byteArray = Base64.decode(dataUriBase64, Base64.DEFAULT)
            fetchEnd(concurrentRecord)
            return byteArray
        } else {
            setCookie(source?.getKey())
            val byteArray = getProxyClient(proxy).newCallResponseBody(retry) {
                addHeaders(headerMap)
                when (method) {
                    RequestMethod.POST -> {
                        url(urlNoQuery)
                        val contentType = headerMap["Content-Type"]
                        val body = body
                        if (fieldMap.isNotEmpty() || body.isNullOrBlank()) {
                            postForm(fieldMap, true)
                        } else if (!contentType.isNullOrBlank()) {
                            val requestBody = body.toRequestBody(contentType.toMediaType())
                            post(requestBody)
                        } else {
                            postJson(body)
                        }
                    }

                    else -> get(urlNoQuery, fieldMap, true)
                }
            }.bytes()
            fetchEnd(concurrentRecord)
            return byteArray
        }
    }

    fun getByteArray(): ByteArray {
        return runBlocking {
            getByteArrayAwait()
        }
    }

    /**
     * 上传文件
     */
    suspend fun upload(fileName: String, file: Any, contentType: String): StrResponse {
        return getProxyClient(proxy).newCallStrResponse(retry) {
            url(urlNoQuery)
            val bodyMap = GSON.fromJsonObject<HashMap<String, Any>>(body).getOrNull()!!
            bodyMap.forEach { entry ->
                if (entry.value.toString() == "fileRequest") {
                    bodyMap[entry.key] = mapOf(
                        Pair("fileName", fileName),
                        Pair("file", file),
                        Pair("contentType", contentType)
                    )
                }
            }
            postMultipart(type, bodyMap)
        }
    }

    /**
     *设置cookie urlOption的优先级大于书源保存的cookie
     *@param tag 书源url 缺省为传入的url
     */
    private fun setCookie(tag: String?) {
        val cookie = CookieStore.getCookie(tag ?: url)
        if (cookie.isNotEmpty()) {
            val cookieMap = CookieStore.cookieToMap(cookie)
            val customCookieMap = CookieStore.cookieToMap(headerMap["Cookie"] ?: "")
            cookieMap.putAll(customCookieMap)
            val newCookie = CookieStore.mapToCookie(cookieMap)
            newCookie?.let {
                headerMap.put("Cookie", it)
            }
        }
    }

    fun getUserAgent(): String {
        return headerMap[UA_NAME] ?: AppConst.userAgent
    }

    fun isPost(): Boolean {
        return method == RequestMethod.POST
    }

    override fun getSource(): BaseSource? {
        return source
    }

    data class UrlOption(
        private var method: String? = null,
        private var charset: String? = null,
        private var headers: Any? = null,
        private var body: Any? = null,
        private var retry: Int? = null,
        private var type: String? = null,
        private var webView: Any? = null,
        private var webJs: String? = null,
        private var js: String? = null,
    ) {
        fun setMethod(value: String?) {
            method = if (value.isNullOrBlank()) null else value
        }

        fun getMethod(): String? {
            return method
        }

        fun setCharset(value: String?) {
            charset = if (value.isNullOrBlank()) null else value
        }

        fun getCharset(): String? {
            return charset
        }

        fun setRetry(value: String?) {
            retry = if (value.isNullOrEmpty()) null else value.toIntOrNull()
        }

        fun getRetry(): Int {
            return retry ?: 0
        }

        fun setType(value: String?) {
            type = if (value.isNullOrBlank()) null else value
        }

        fun getType(): String? {
            return type
        }

        fun useWebView(): Boolean {
            return when (webView) {
                null, "", false, "false" -> false
                else -> true
            }
        }

        fun useWebView(boolean: Boolean) {
            webView = if (boolean) true else null
        }

        fun setHeaders(value: String?) {
            headers = if (value.isNullOrBlank()) {
                null
            } else {
                GSON.fromJsonObject<Map<String, Any>>(value).getOrNull()
            }
        }

        fun getHeaderMap(): Map<*, *>? {
            return when (val value = headers) {
                is Map<*, *> -> value
                is String -> GSON.fromJsonObject<Map<String, Any>>(value).getOrNull()
                else -> null
            }
        }

        fun setBody(value: String?) {
            body = when {
                value.isNullOrBlank() -> null
                value.isJsonObject() -> GSON.fromJsonObject<Map<String, Any>>(value)
                value.isJsonArray() -> GSON.fromJsonArray<Map<String, Any>>(value)
                else -> value
            }
        }

        fun getBody(): String? {
            return body?.let {
                if (it is String) it else GSON.toJson(it)
            }
        }

        fun setWebJs(value: String?) {
            webJs = if (value.isNullOrBlank()) null else value
        }

        fun getWebJs(): String? {
            return webJs
        }

        fun setJs(value: String?) {
            js = if (value.isNullOrBlank()) null else value
        }

        fun getJs(): String? {
            return js
        }
    }

    data class ConcurrentRecord(
        val isConcurrent: Boolean,
        var time: Long,
        var frequency: Int
    )

}

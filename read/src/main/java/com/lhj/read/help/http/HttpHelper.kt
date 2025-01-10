package com.lhj.read.help.http

import com.lhj.read.constant.AppConst
import com.lhj.read.help.http.CookieManager.cookieJarHeader
import com.lhj.read.model.DebugLog
import okhttp3.Authenticator
import okhttp3.ConnectionSpec
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private val proxyClientCache: ConcurrentHashMap<String, OkHttpClient> by lazy {
    ConcurrentHashMap()
}

val okHttpClient: OkHttpClient by lazy {
    val specs = arrayListOf(
        ConnectionSpec.MODERN_TLS,
        ConnectionSpec.COMPATIBLE_TLS,
        ConnectionSpec.CLEARTEXT
    )

    val builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .sslSocketFactory(SSLHelper.unsafeSSLSocketFactory, SSLHelper.unsafeTrustManager)
        .retryOnConnectionFailure(true)
        .hostnameVerifier(SSLHelper.unsafeHostnameVerifier)
        .connectionSpecs(specs)
        .addInterceptor(OkHttpExceptionInterceptor)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder()
            if (request.header(AppConst.UA_NAME) == null) {
                builder.addHeader(AppConst.UA_NAME, AppConst.userAgent)
            } else if (request.header(AppConst.UA_NAME) == "null") {
                builder.removeHeader(AppConst.UA_NAME)
            }
            builder.addHeader("Keep-Alive", "300")
            builder.addHeader("Connection", "Keep-Alive")
            builder.addHeader("Cache-Control", "no-cache")
            chain.proceed(builder.build())
        })
        .addNetworkInterceptor { chain ->
            var request = chain.request()
            val enableCookieJar = request.header(cookieJarHeader) != null

            if (enableCookieJar) {
                val requestBuilder = request.newBuilder()
                requestBuilder.removeHeader(cookieJarHeader)
                request = CookieManager.loadRequest(requestBuilder.build())
            }

            val networkResponse = chain.proceed(request)

            if (enableCookieJar) {
                CookieManager.saveResponse(networkResponse)
            }
            networkResponse
        }
    builder.addInterceptor(DecompressInterceptor)
    builder.build().apply {
        val okHttpName =
            OkHttpClient::class.java.name.removePrefix("okhttp3.").removeSuffix("Client")
        val executor = dispatcher.executorService as ThreadPoolExecutor
        val threadName = "$okHttpName Dispatcher"
        executor.threadFactory = ThreadFactory { runnable ->
            Thread(runnable, threadName).apply {
                isDaemon = false
                uncaughtExceptionHandler = OkhttpUncaughtExceptionHandler
            }
        }
    }
}

/**
 * 缓存代理okHttp
 */
fun getProxyClient(proxy: String? = null, debugLog: DebugLog? = null): OkHttpClient {
    if (proxy.isNullOrBlank()) {
        if (debugLog == null) {
            return okHttpClient
        }
        val builder = okHttpClient.newBuilder()
        val logInterceptor = HttpLoggingInterceptor(debugLog);//创建拦截对象
        logInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);//这一句一定要记得写，否则没有数据输出

        builder.addNetworkInterceptor(logInterceptor)  //设置打印拦截日志
        return builder.build()
    }
    if (debugLog == null) {
        proxyClientCache[proxy]?.let {
            return it
        }
    }
    val r = Regex("(http|socks4|socks5)://(.*):(\\d{2,5})(@.*@.*)?")
    val ms = r.findAll(proxy)
    val group = ms.first()
    var username = ""       //代理服务器验证用户名
    var password = ""       //代理服务器验证密码
    val type = if (group.groupValues[1] == "http") "http" else "socks"
    val host = group.groupValues[2]
    val port = group.groupValues[3].toInt()
    if (group.groupValues[4] != "") {
        username = group.groupValues[4].split("@")[1]
        password = group.groupValues[4].split("@")[2]
    }
    if (type != "direct" && host != "") {
        val builder = okHttpClient.newBuilder()
        if (type == "http") {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
        } else {
            builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port)))
        }
        if (username != "" && password != "") {
            val proxyAuthenticator = object : Authenticator {
                @Throws(IOException::class)
                override fun authenticate(route: Route?, response: Response): Request {
                    //设置代理服务器账号密码
                    val credential = Credentials.basic(username, password);
                    return response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
                }
            }
            builder.proxyAuthenticator(proxyAuthenticator);
             builder.proxyAuthenticator { _, response -> //设置代理服务器账号密码
                 val credential: String = Credentials.basic(username, password)
                 response.request.newBuilder()
                     .header("Proxy-Authorization", credential)
                     .build()
             }
        }
        if (debugLog != null) {
            val logInterceptor = HttpLoggingInterceptor(debugLog);//创建拦截对象
            logInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);//这一句一定要记得写，否则没有数据输出

            builder.addNetworkInterceptor(logInterceptor)  //设置打印拦截日志
            return builder.build()
        }
        val proxyClient = builder.build()
        proxyClientCache[proxy] = proxyClient
        return proxyClient
    }
    return okHttpClient
}

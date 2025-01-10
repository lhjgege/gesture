@file:Suppress("unused")

package com.lhj.read.help.http

import com.lhj.read.constant.AppPattern.equalsRegex
import com.lhj.read.constant.AppPattern.semicolonRegex
import com.lhj.read.data.appDb
import com.lhj.read.data.entities.Cookie
import com.lhj.read.help.CacheManager
import com.lhj.read.help.http.CookieManager.getCookieNoSession
import com.lhj.read.help.http.CookieManager.mergeCookiesToMap
import com.lhj.read.help.http.api.CookieManagerInterface
import com.lhj.read.utils.NetworkUtils
import com.lhj.read.utils.removeCookie

object CookieStore : CookieManagerInterface {

    /**
     *保存cookie到数据库，会自动识别url的二级域名
     */
    override fun setCookie(url: String, cookie: String?) {
        try {
            val domain = NetworkUtils.getSubDomain(url)
            CacheManager.putMemory("${domain}_cookie", cookie ?: "")
            val cookieBean = Cookie(domain, cookie ?: "")
            appDb.cookieDao.insert(cookieBean)
        } catch (e: Exception) {
        }
    }

    override fun replaceCookie(url: String, cookie: String) {
        if (android.text.TextUtils.isEmpty(url) || android.text.TextUtils.isEmpty(cookie)) {
            return
        }
        val oldCookie = getCookieNoSession(url)
        if (android.text.TextUtils.isEmpty(oldCookie)) {
            setCookie(url, cookie)
        } else {
            val cookieMap = cookieToMap(oldCookie)
            cookieMap.putAll(cookieToMap(cookie))
            val newCookie = mapToCookie(cookieMap)
            setCookie(url, newCookie)
        }
    }

    /**
     *获取url所属的二级域名的cookie
     */
    override fun getCookie(url: String): String {
        val domain = NetworkUtils.getSubDomain(url)

        val cookie = getCookieNoSession(url)
        val sessionCookie = CookieManager.getSessionCookie(domain)

        val cookieMap = mergeCookiesToMap(cookie, sessionCookie)

        var ck = mapToCookie(cookieMap) ?: ""
        while (ck.length > 4096) {
            val removeKey = cookieMap.keys.random()
            CookieManager.removeCookie(url, removeKey)
            cookieMap.remove(removeKey)
            ck = mapToCookie(cookieMap) ?: ""
        }
        return ck
    }

    fun getKey(url: String, key: String): String {
        val cookie = getCookie(url)
        val sessionCookie = CookieManager.getSessionCookie(url)
        val cookieMap = mergeCookiesToMap(cookie, sessionCookie)
        return cookieMap[key] ?: ""
    }

    override fun removeCookie(url: String) {
        val domain = NetworkUtils.getSubDomain(url)
        appDb.cookieDao.delete(domain)
        CacheManager.deleteMemory("${domain}_cookie")
        CacheManager.deleteMemory("${domain}_session_cookie")
        android.webkit.CookieManager.getInstance().removeCookie(url)
    }

    override fun cookieToMap(cookie: String): MutableMap<String, String> {
        val cookieMap = mutableMapOf<String, String>()
        if (cookie.isBlank()) {
            return cookieMap
        }
        val pairArray = cookie.split(semicolonRegex).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (pair in pairArray) {
            val pairs = pair.split(equalsRegex, 2).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (pairs.size <= 1) {
                continue
            }
            val key = pairs[0].trim { it <= ' ' }
            val value = pairs[1]
            if (value.isNotBlank() || value.trim { it <= ' ' } == "null") {
                cookieMap[key] = value.trim { it <= ' ' }
            }
        }
        return cookieMap
    }

    override fun mapToCookie(cookieMap: Map<String, String>?): String? {
        if (cookieMap.isNullOrEmpty()) {
            return null
        }
        val builder = StringBuilder()
        cookieMap.keys.forEachIndexed { index, key ->
            if (index > 0) builder.append("; ")
            builder.append(key).append("=").append(cookieMap[key])
        }
        return builder.toString()
    }

    fun clear() {
        appDb.cookieDao.deleteOkHttp()
    }

}
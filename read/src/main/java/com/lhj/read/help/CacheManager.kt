package com.lhj.read.help

import androidx.collection.LruCache
import com.lhj.read.data.entities.Cache
import com.lhj.read.model.analyzeRule.QueryTTF
import com.lhj.read.utils.ACache
import com.lhj.read.utils.memorySize

// TODO 处理缓存
@Suppress("unused")
object CacheManager {

    private val queryTTFMap = hashMapOf<String, Pair<Long, QueryTTF>>()
    /**
     * 最多只缓存50M的数据,防止OOM
     */
    private val memoryLruCache = object : LruCache<String, Any>(1024 * 1024 * 50) {

        override fun sizeOf(key: String, value: Any): Int {
            return value.toString().memorySize()
        }

    }
    /**
     * saveTime 单位为秒
     */
    @JvmOverloads
    fun put(key: String, value: Any, saveTime: Int = 0) {
        val deadline =
            if (saveTime == 0) 0 else System.currentTimeMillis() + saveTime * 1000
        when (value) {
            is QueryTTF -> queryTTFMap[key] = Pair(deadline, value)
            is ByteArray -> ACache.get().put(key, value, saveTime)
            else -> {
                val cache = Cache(key, value.toString(), deadline)
                // appDb.cacheDao.insert(cache)
            }
        }
    }

    fun get(key: String): String? {
        // return appDb.cacheDao.get(key, System.currentTimeMillis())
        return null
    }

    fun getInt(key: String): Int? {
        return get(key)?.toIntOrNull()
    }

    fun getLong(key: String): Long? {
        return get(key)?.toLongOrNull()
    }

    fun getDouble(key: String): Double? {
        return get(key)?.toDoubleOrNull()
    }

    fun getFloat(key: String): Float? {
        return get(key)?.toFloatOrNull()
    }

    fun getByteArray(key: String): ByteArray? {
        return ACache.get().getAsBinary(key)
    }

    fun getQueryTTF(key: String): QueryTTF? {
        val cache = queryTTFMap[key] ?: return null
        if (cache.first == 0L || cache.first > System.currentTimeMillis()) {
            return cache.second
        }
        return null
    }

    fun putFile(key: String, value: String, saveTime: Int = 0) {
        ACache.get().put(key, value, saveTime)
    }

    fun getFile(key: String): String? {
        return ACache.get().getAsString(key)
    }

    fun delete(key: String) {
        ACache.get().remove(key)
    }

    fun putMemory(key: String, value: Any) {
        memoryLruCache.put(key, value)
    }

    //从内存中获取数据 使用lruCache
    fun getFromMemory(key: String): Any? {
        return memoryLruCache.get(key)
    }

    fun deleteMemory(key: String) {
        memoryLruCache.remove(key)
    }
}
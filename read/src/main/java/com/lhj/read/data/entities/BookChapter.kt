package com.lhj.read.data.entities


import com.lhj.read.utils.GSON
import com.lhj.read.utils.fromJsonObject
import com.lhj.read.utils.MD5Utils
import com.lhj.read.model.analyzeRule.AnalyzeUrl
import com.lhj.read.model.analyzeRule.RuleDataInterface
import com.lhj.read.utils.NetworkUtils

data class BookChapter(
    var url: String = "",               // 章节地址
    var title: String = "",             // 章节标题
    var isVolume: Boolean = false,      // 是否是卷名
    var baseUrl: String = "",           // 用来拼接相对url
    var bookUrl: String = "",           // 书籍地址
    var index: Int = 0,                 // 章节序号
    var isVip: Boolean = false,         // 是否VIP
    var isPay: Boolean = false,         // 是否已购买
    var resourceUrl: String? = null,    // 音频真实URL
    var tag: String? = null,            // 更新时间或其他章节附加信息
    var wordCount: String? = null,      // 本章节字数
    var start: Long? = null,            // 章节起始位置
    var end: Long? = null,              // 章节终止位置
    var startFragmentId: String? = null,  //EPUB书籍当前章节的fragmentId
    var endFragmentId: String? = null,    //EPUB书籍下一章节的fragmentId
    var variable: String? = null        //变量
): RuleDataInterface {

    @delegate:Transient
    override val variableMap: HashMap<String, String> by lazy {
        GSON.fromJsonObject<HashMap<String, String>>(variable).getOrNull() ?: hashMapOf()
    }

    override fun putVariable(key: String, value: String?) {
        if (value != null) {
            variableMap[key] = value
        } else {
            variableMap.remove(key)
        }
        variable = GSON.toJson(variableMap)
    }

    override fun hashCode() = url.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other is BookChapter) {
            return other.url == url
        }
        return false
    }

    fun getAbsoluteURL():String{
        val urlMatcher = AnalyzeUrl.paramPattern.matcher(url)
        val urlBefore = if(urlMatcher.find())url.substring(0,urlMatcher.start()) else url
        val urlAbsoluteBefore = NetworkUtils.getAbsoluteURL(baseUrl,urlBefore)
        return if(urlBefore.length == url.length) urlAbsoluteBefore else urlAbsoluteBefore + ',' + url.substring(urlMatcher.end())
    }


    fun getFileName(): String = String.format("%05d-%s.nb", index, MD5Utils.md5Encode16(title))
}


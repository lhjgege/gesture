package io.legado.app.api.controller

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import io.legado.app.api.ReturnData
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.help.CacheManager
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.WeakHashMap

object BookController {

    private lateinit var book: Book
    private var bookSource: BookSource? = null
    private var bookUrl: String = ""
    private val defaultCoverCache by lazy { WeakHashMap<Drawable, Bitmap>() }

    /**
     * 书架所有书籍
     */
    val bookshelf: ReturnData
        get() {
            val books = appDb.bookDao.all
            val returnData = ReturnData()
            return if (books.isEmpty()) {
                returnData.setErrorMsg("还没有添加小说")
            } else {
                val data = books
                returnData.setData(data)
            }
        }

    /**
     * 获取正文图片
     */
    fun getImg(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        val bookUrl = parameters["url"]?.firstOrNull()
            ?: return returnData.setErrorMsg("bookUrl为空")
        val src = parameters["path"]?.firstOrNull()
            ?: return returnData.setErrorMsg("图片链接为空")
        val width = parameters["width"]?.firstOrNull()?.toInt() ?: 640
        if (this.bookUrl != bookUrl) {
            this.book = appDb.bookDao.getBook(bookUrl)
                ?: return returnData.setErrorMsg("bookUrl不对")
            this.bookSource = appDb.bookSourceDao.getBookSource(book.origin)
        }
        this.bookUrl = bookUrl
        return returnData.setData(null)
    }

    /**
     * 更新目录
     */
    fun refreshToc(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        try {
            val bookUrl = parameters["url"]?.firstOrNull()
            if (bookUrl.isNullOrEmpty()) {
                return returnData.setErrorMsg("参数url不能为空，请指定书籍地址")
            }
            val book = appDb.bookDao.getBook(bookUrl)
                ?: return returnData.setErrorMsg("未在数据库找到对应书籍，请先添加")
            val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
                ?: return returnData.setErrorMsg("未找到对应书源,请换源")
            val toc = runBlocking {
                if (book.tocUrl.isBlank()) {
                    WebBook.getBookInfoAwait(bookSource, book)
                }
                WebBook.getChapterListAwait(bookSource, book).getOrThrow()
            }
            appDb.bookChapterDao.delByBook(book.bookUrl)
            appDb.bookChapterDao.insert(*toc.toTypedArray())
            appDb.bookDao.update(book)
            return returnData.setData(toc)
        } catch (e: Exception) {
            return returnData.setErrorMsg(e.localizedMessage ?: "refresh toc error")
        }
    }

    /**
     * 获取目录
     */
    fun getChapterList(parameters: Map<String, List<String>>): ReturnData {
        val bookUrl = parameters["url"]?.firstOrNull()
        val returnData = ReturnData()
        if (bookUrl.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数url不能为空，请指定书籍地址")
        }
        val chapterList = appDb.bookChapterDao.getChapterList(bookUrl)
        if (chapterList.isEmpty()) {
            return refreshToc(parameters)
        }
        return returnData.setData(chapterList)
    }

    /**
     * 获取正文
     */
    fun getBookContent(parameters: Map<String, List<String>>): ReturnData {
        val bookUrl = parameters["url"]?.firstOrNull()
        val index = parameters["index"]?.firstOrNull()?.toInt()
        val returnData = ReturnData()
        if (bookUrl.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数url不能为空，请指定书籍地址")
        }
        if (index == null) {
            return returnData.setErrorMsg("参数index不能为空, 请指定目录序号")
        }
        val book = appDb.bookDao.getBook(bookUrl)
        val chapter = runBlocking {
            var chapter = appDb.bookChapterDao.getChapter(bookUrl, index)
            var wait = 0
            while (chapter == null && wait < 30) {
                delay(1000)
                chapter = appDb.bookChapterDao.getChapter(bookUrl, index)
                wait++
            }
            chapter
        }
        if (book == null || chapter == null) {
            return returnData.setErrorMsg("未找到")
        }
        var content: String? = BookHelp.getContent(book, chapter)
        if (content != null) {
            val contentProcessor = ContentProcessor.get(book.name, book.origin)
            content = runBlocking {
                contentProcessor.getContent(book, chapter, content!!, includeTitle = false)
                    .toString()
            }
            return returnData.setData(content)
        }
        val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
            ?: return returnData.setErrorMsg("未找到书源")
        try {
            content = runBlocking {
                WebBook.getContentAwait(bookSource, book, chapter).let {
                    val contentProcessor = ContentProcessor.get(book.name, book.origin)
                    contentProcessor.getContent(book, chapter, it, includeTitle = false)
                        .toString()
                }
            }
            returnData.setData(content)
        } catch (e: Exception) {
            returnData.setErrorMsg(e.stackTraceStr)
        }
        return returnData
    }

    /**
     * 删除书籍
     */
    fun deleteBook(postData: String?): ReturnData {
        val returnData = ReturnData()
        GSON.fromJsonObject<Book>(postData).getOrNull()?.let { book ->
            book.delete()
            return returnData.setData("")
        }
        return returnData.setErrorMsg("格式不对")
    }


    /**
     * 保存web阅读界面配置
     */
    fun saveWebReadConfig(postData: String?): ReturnData {
        val returnData = ReturnData()
        postData?.let {
            CacheManager.put("webReadConfig", postData)
        } ?: CacheManager.delete("webReadConfig")
        return returnData.setData("")
    }

    /**
     * 获取web阅读界面配置
     */
    fun getWebReadConfig(): ReturnData {
        val returnData = ReturnData()
        val data = CacheManager.get("webReadConfig")
            ?: return returnData.setErrorMsg("没有配置")
        return returnData.setData(data)
    }

}

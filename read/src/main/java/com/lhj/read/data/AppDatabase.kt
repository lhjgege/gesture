package com.lhj.read.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lhj.read.data.dao.CookieDao
import splitties.init.appCtx
import java.util.Locale

val appDb by lazy {
    Room.databaseBuilder(appCtx, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
        .fallbackToDestructiveMigration()
        .allowMainThreadQueries()
        .addCallback(AppDatabase.dbCallback)
        .build()
}

@Database(
    version = 1,
    exportSchema = true,
    entities = [],
)
abstract class AppDatabase : RoomDatabase() {
    abstract val cookieDao: CookieDao

    companion object {
        const val DATABASE_NAME = "read.db"

        val dbCallback = object : Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                db.setLocale(Locale.CHINESE)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {

            }
        }

    }

}
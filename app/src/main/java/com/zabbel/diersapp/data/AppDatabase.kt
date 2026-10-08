package com.zabbel.diersapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.zabbel.diersapp.data.dao.*
import com.zabbel.diersapp.data.model.*
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import net.zetetic.database.sqlcipher.SQLiteDatabase

@Database(
    entities = [Betriebsauftrag::class, Arbeitszeit::class, UserSettings::class, WochenberichtInfo::class, KundenUnterschrift::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        androidx.room.AutoMigration(from = 2, to = 3)
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun auftragDao(): AuftragDao
    abstract fun arbeitszeitDao(): ArbeitszeitDao
    abstract fun settingsDao(): UserSettingsDao
    abstract fun wochenInfoDao(): WochenberichtInfoDao
    abstract fun unterschriftDao(): UnterschriftDao

    fun changePassphrase(newPassphrase: ByteArray) {
        val database = openHelper.writableDatabase
        if (database is SQLiteDatabase) {
            database.changePassword(newPassphrase)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, passphrase: ByteArray): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val factory = SupportOpenHelperFactory(passphrase)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "diersapp_database.db"
                )
                    .openHelperFactory(factory)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

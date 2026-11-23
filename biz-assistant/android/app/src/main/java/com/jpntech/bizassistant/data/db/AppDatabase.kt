package com.jpntech.bizassistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [OrgEntity::class, ProjectEntity::class, RecordEntity::class, EventEntity::class, TaskEntity::class, MoneyEntity::class, TicketEntity::class, PendingEntity::class, RecordingEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orgs(): OrgDao
    abstract fun projects(): ProjectDao
    abstract fun records(): RecordDao
    abstract fun events(): EventDao
    abstract fun tasks(): TaskDao
    abstract fun money(): MoneyDao
    abstract fun tickets(): TicketDao
    abstract fun pending(): PendingDao
    abstract fun recordings(): RecordingDao

    companion object {
        /** 1 → 2: 대기함과 녹음 처리 기록 테이블 추가. 기존 데이터는 그대로 둔다. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `pending` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `createdAt` INTEGER NOT NULL, `source` TEXT NOT NULL, `rawText` TEXT NOT NULL, `analysisJson` TEXT NOT NULL, `callerHint` TEXT, `title` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `recordings` (`mediaId` INTEGER NOT NULL, `displayName` TEXT NOT NULL, `dateAdded` INTEGER NOT NULL, `status` TEXT NOT NULL, `processedAt` INTEGER, `pendingId` INTEGER, `error` TEXT, PRIMARY KEY(`mediaId`))")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "biz-assistant.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}

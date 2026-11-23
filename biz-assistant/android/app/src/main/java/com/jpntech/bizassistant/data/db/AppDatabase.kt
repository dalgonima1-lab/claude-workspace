package com.jpntech.bizassistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 3버전부터 모든 표가 서버와 같은 uid 로 바뀌었다.
 * 1·2버전 데이터는 Room을 열기 전에 LegacyImporter 가 읽어 두었다가 새 표에 옮겨 넣는다(BizApp 참고).
 */
@Database(
    entities = [OrgEntity::class, ProjectEntity::class, RecordEntity::class, EventEntity::class, TaskEntity::class, MoneyEntity::class, TicketEntity::class, PendingEntity::class, RecordingEntity::class],
    version = 3,
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
        const val NAME = "biz-assistant.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}

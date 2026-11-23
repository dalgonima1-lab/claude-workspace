package com.jpntech.bizassistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [OrgEntity::class, ProjectEntity::class, RecordEntity::class, EventEntity::class, TaskEntity::class, MoneyEntity::class, TicketEntity::class],
    version = 1,
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

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "biz-assistant.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}

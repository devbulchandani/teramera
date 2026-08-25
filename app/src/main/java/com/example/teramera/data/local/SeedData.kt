package com.example.teramera.data.local

import android.content.Context
import androidx.room.Room
import javax.inject.Inject
import javax.inject.Singleton

// Fresh start: no demo data. All ledger data lives server-side and syncs in.
@Singleton
class DatabaseProvider @Inject constructor() {
    fun provide(context: Context): TerameraDatabase =
        Room.databaseBuilder(context, TerameraDatabase::class.java, TerameraDatabase.NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
}

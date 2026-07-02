package com.example.myrana.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS usage_sync_buffer (
                day TEXT NOT NULL,
                package_name TEXT NOT NULL,
                total_seconds INTEGER NOT NULL DEFAULT 0,
                uploaded_seconds INTEGER NOT NULL DEFAULT 0,
                updated_at_ms INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(day, package_name)
            )
            """.trimIndent()
        )
    }
}

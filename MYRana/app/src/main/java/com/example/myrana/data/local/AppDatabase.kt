package com.example.myrana.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * قاعدة بيانات Room المحلية لتطبيق الطفل.
 *
 * تخزّن ثلاث كيانات:
 * - [BlockedSiteEntity]: المواقع/النطاقات المحظورة.
 * - [BlockedAppEntity]: حزم التطبيقات المحظورة.
 * - [SyncStateEntity]: آخر أوقات المزامنة مع السيرفر.
 * - [PendingOutboxEntity]: استخدام التطبيقات مؤجّل عند انقطاع النت.
 * - [DailyAppUsageEntity]: وقت الاستخدام اليومي لكل تطبيق (وقت الشاشة).
 * - [ScreenTimeEventEntity]: أحداث التحذير والإغلاق التلقائي.
 *
 * اسم الملف على الجهاز: `myrana_policy.db`
 */
@Database(
    entities = [
        BlockedSiteEntity::class,
        BlockedAppEntity::class,
        SyncStateEntity::class,
        PendingOutboxEntity::class,
        DailyAppUsageEntity::class,
        ScreenTimeEventEntity::class,
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /** وصول جدول المواقع المحظورة. */
    abstract fun blockedSiteDao(): BlockedSiteDao

    /** وصول جدول التطبيقات المحظورة. */
    abstract fun blockedAppDao(): BlockedAppDao

    /** وصول صف حالة المزامنة الوحيد. */
    abstract fun syncStateDao(): SyncStateDao

    /** طابور استخدام الجهاز المعلّق للرفع. */
    abstract fun pendingOutboxDao(): PendingOutboxDao

    /** استخدام يومي لكل تطبيق (وقت الشاشة). */
    abstract fun dailyAppUsageDao(): DailyAppUsageDao

    /** سجل أحداث وقت الاستخدام. */
    abstract fun screenTimeEventDao(): ScreenTimeEventDao

    companion object {
        /** اسم ملف SQLite على التخزين الداخلي. */
        private const val NAME = "myrana_policy.db"

        @Volatile
        private var instance: AppDatabase? = null

        /**
         * نمط Singleton مزدوج القفل: نسخة واحدة لكل عملية التطبيق.
         * يُفضّل تمرير `applicationContext` لتجنّب تسريب النشاط.
         */
        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME
                )
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
        }
    }
}

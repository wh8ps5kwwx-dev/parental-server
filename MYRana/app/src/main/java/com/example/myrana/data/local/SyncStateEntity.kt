package com.example.myrana.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * صف واحد ثابت (`id = 1`) يخزّن ملخص حالة المزامنة مع السيرفر.
 *
 * يُحدَّث بعد كل رفع ناجح أو سحب ناجح في [com.example.myrana.data.repo.PolicyRepository].
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    /** آخر وقت نجح فيه سحب السياسة من الخادم. */
    @ColumnInfo(name = "last_pull_ms") val lastPullEpochMs: Long = 0,
    /** آخر وقت نجح فيه رفع القوائم المعلّقة. */
    @ColumnInfo(name = "last_push_ms") val lastPushEpochMs: Long = 0,
    /** رقم مراجعة السيرفر إن وُجد في JSON (حقل `revision`). */
    @ColumnInfo(name = "last_known_revision") val lastKnownRevision: Long = 0
)

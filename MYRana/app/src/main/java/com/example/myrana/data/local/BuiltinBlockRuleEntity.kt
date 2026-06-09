package com.example.myrana.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * قواعد الحظر المدمجة (لا تُمسح عند مزامنة السياسة من السيرفر).
 *
 * @param ruleType `package` | `label` | `site`
 * @param pattern حزمة أو كلمة في اسم التطبيق أو نطاق موقع
 * @param category فئة للتنبيه (رعب، دردشة، مواعدة…)
 * @param displayName اسم للأم في الرسالة
 */
@Entity(
    tableName = "builtin_block_rules",
    indices = [Index(value = ["rule_type"]), Index(value = ["pattern"])]
)
data class BuiltinBlockRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "rule_type") val ruleType: String,
    @ColumnInfo(name = "pattern") val pattern: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "display_name") val displayName: String
) {
    companion object {
        const val TYPE_PACKAGE = "package"
        const val TYPE_LABEL = "label"
        const val TYPE_SITE = "site"
    }
}

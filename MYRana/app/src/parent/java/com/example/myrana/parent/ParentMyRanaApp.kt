package com.example.myrana.parent

import android.app.Application
import com.example.myrana.data.local.AppDatabase

/** تطبيق الأم — بدون Python ولا مراقبة خلفية للطفل. */
class ParentMyRanaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDatabase.getInstance(this)
    }
}

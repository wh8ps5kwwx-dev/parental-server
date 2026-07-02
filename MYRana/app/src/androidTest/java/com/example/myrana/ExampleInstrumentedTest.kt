package com.example.myrana

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.assertTrue

/**
 * اختبار على جهاز/محاكي — يتحقق من اسم الحزمة حسب النكهة المُثبَّتة.
 *
 * - child: `com.example.myrana.child`
 * - parent: `com.example.myrana.parent`
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val pkg = appContext.packageName
        // يقبل أي نكهة من المشروع
        assertTrue(
            pkg == "com.example.myrana.child" || pkg == "com.example.myrana.parent"
        )
    }
}
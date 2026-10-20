package com.darayi.app

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFlowTest {
    @get:Rule val ui=createEmptyComposeRule()
    @Test fun actualAppMigratesLegacyPortfolioUndoesDeletionAndOpensBackupPicker(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        val legacy=context.getSharedPreferences("portfolio",0)
        val h=Holding("app-gold","طلای ۱۸ عیار","IR_GOLD_18K",BigDecimal("67.38"),"گرم",AssetCategory.PHYSICAL_GOLD,true,BigDecimal("1000"),"تومان")
        legacy.edit().clear().putString("holdings",LegacyCodec.encode(listOf(h))).putString("quotes","{}").putString("usd","100").commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            ui.waitUntil(10_000){ui.onAllNodesWithTag("holding-app-gold").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("holding-app-gold").performScrollTo().performClick()
            ui.onNodeWithContentDescription("حذف دارایی").performClick()
            ui.waitUntil(10_000){ui.onAllNodesWithText("برگردان").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("برگردان").performClick()
            ui.waitUntil(10_000){ui.onAllNodesWithTag("holding-app-gold").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("holding-app-gold").assertExists()
            ui.onNodeWithContentDescription("تنظیمات").performClick()
            ui.onNodeWithText("ذخیرهٔ فایل پشتیبان").performScrollTo().performClick()
            scenario.onActivity { activity ->
                val intent=Shadows.shadowOf(activity).nextStartedActivity
                assertEquals(Intent.ACTION_CREATE_DOCUMENT,intent.action)
                assertEquals("application/json",intent.type)
            }
            val repo=PortfolioRepository.create(context)
            val persisted=java.util.concurrent.Executors.newSingleThreadExecutor().let { executor ->
                try{executor.submit<PortfolioSnapshot>{repo.snapshot()}.get()}finally{executor.shutdown()}
            }
            assertEquals(h,persisted.holdings.single())
            assertEquals("100",persisted.manualFx)
        }
    }
}

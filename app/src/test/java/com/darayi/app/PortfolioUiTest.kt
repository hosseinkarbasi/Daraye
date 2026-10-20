package com.darayi.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w320dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PortfolioUiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private fun holding(id:String="gold")=Holding(id,"طلای ۱۸ عیار","IR_GOLD_18K",BigDecimal("67.38"),"گرم",AssetCategory.PHYSICAL_GOLD,false,zero,"تومان")
    @Test fun addingGoldThroughSearchAndKeyboardSavesExactWeight(){
        var saved:Holding?=null
        rule.setContent{DarayiTheme{HoldingEditor(null,AssetCatalog.physicalGold,false,null,{_,_->},{},{saved=it},{})}}
        rule.onNodeWithTag("assetSearch").performTextInput("۱۸")
        rule.onNodeWithText("طلای ۱۸ عیار").performClick()
        rule.onNodeWithTag("goldGrams").performScrollTo().performTextInput("۶۷")
        rule.onNodeWithTag("goldMilligrams").performScrollTo().performTextInput("۳۸۰")
        rule.onNodeWithTag("goldMilligrams").performImeAction()
        rule.onNodeWithTag("saveHolding").performClick()
        rule.runOnIdle{assertNotNull(saved);assertEquals(0,BigDecimal("67.38").compareTo(saved!!.qty));assertEquals("گرم",saved!!.measure)}
    }
    @Test fun editingLargeFontOnSmallScreenPreservesValueAndDeleteTargetsCorrectHolding(){
        var saved:Holding?=null;var removed:Holding?=null
        val h=holding()
        rule.runOnUiThread{org.robolectric.RuntimeEnvironment.setFontScale(1.6f)}
        rule.setContent{DarayiTheme{HoldingEditor(h,AssetCatalog.physicalGold,false,null,{_,_->},{},{saved=it},{removed=it})}}
        val textLayouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        rule.onNodeWithText("وزن طلا").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult){it(textLayouts)}
        assertEquals(1.6f,textLayouts.single().layoutInput.density.fontScale,0.01f)
        rule.onNodeWithTag("goldGrams").performScrollTo().assertTextContains("67")
        rule.onNodeWithTag("goldMilligrams").performScrollTo().assertTextContains("380")
        rule.onNodeWithTag("saveHolding").assertIsDisplayed().performClick()
        rule.runOnIdle{assertEquals(h.id,saved!!.id);assertEquals(0,h.qty.compareTo(saved!!.qty))}
        saveImage("editor-large-font",rule.onNodeWithTag("editorScroll"))
        rule.onNodeWithContentDescription("حذف دارایی").performClick()
        rule.runOnIdle{assertEquals(h,removed)}
    }
    @Test fun unavailableOnlinePriceIsExplainedBeforeSaving(){
        val h=holding()
        val state=SourceState(PriceSource.NAVASAN,System.currentTimeMillis(),System.currentTimeMillis(),availableKeys=setOf("USD"))
        rule.setContent{DarayiTheme{HoldingEditor(h,AssetCatalog.physicalGold,false,null,{_,_->},{},{},{},states=mapOf(PriceSource.NAVASAN to state))}}
        rule.onNodeWithTag("availabilityNotice").performScrollTo().assertTextContains("موجود نیست",substring=true)
    }
    @Test fun portfolioSearchCategoryFilterAndCompactAllocationAreInteractive(){
        val first=holding();val second=holding("stock").copy(name="فملی",key="فملی",type=AssetCategory.STOCKS,measure="سهم")
        val q=Quote(BigDecimal("1000"),"تومان","cached",fetchedAt=1)
        rule.setContent{DarayiTheme{PortfolioHome(PortfolioHomeState(listOf(first,second),mapOf(first.key to q,second.key to q),emptyMap(),"100"),100_000_000,{},{},{},{})}}
        rule.onNodeWithTag("allocation").performScrollTo().performClick()
        rule.onNodeWithText(AssetCategory.PHYSICAL_GOLD).assertExists()
        rule.onNodeWithTag("portfolioList").performScrollToNode(hasContentDescription("جست‌وجو و فیلتر"))
        rule.onNodeWithContentDescription("جست‌وجو و فیلتر").performClick()
        rule.onNodeWithTag("portfolioList").performScrollToNode(hasTestTag("portfolioSearch"))
        rule.onNodeWithTag("portfolioSearch").performTextInput("فملی")
        rule.onNodeWithTag("portfolioList").performScrollToNode(hasTestTag("holding-stock"))
        rule.onNodeWithTag("holding-stock").assertIsDisplayed()
        rule.onNodeWithTag("holding-gold").assertDoesNotExist()
        rule.onNodeWithTag("portfolioList").performScrollToNode(hasTestTag("portfolioSearch"))
        rule.onNodeWithTag("portfolioSearch").performTextClearance()
        rule.onNodeWithTag("filter-${AssetCategory.STOCKS}").performScrollTo().performClick()
        rule.onNodeWithTag("portfolioList").performScrollToNode(hasTestTag("holding-stock"))
        rule.onNodeWithTag("holding-gold").assertDoesNotExist()
    }
    @Test fun cachedDollarAndSourceFailureAreVisibleAndUndoDispatchesOriginalToken(){
        var undo:String?=null
        val h=holding();val now=100_000_000L
        val quotes=mapOf(h.key to Quote(BigDecimal.TEN,"تومان","cached",fetchedAt=1),"USD" to Quote(BigDecimal("100"),"تومان","old",fetchedAt=1))
        rule.setContent{DarayiTheme{
            val host=remember{SnackbarHostState()}
            PortfolioHome(PortfolioHomeState(listOf(h),quotes,emptyMap()),now,{},{},{},{},host)
            UndoRemovalEffect(RemovedHolding("undo-token",h,0),host,{undo=it},{})
        }}
        rule.onNodeWithText("نمایش قیمت‌های ذخیره‌شده").assertExists()
        saveImage("portfolio-small",rule.onNodeWithTag("portfolioList"))
        rule.onNodeWithText("برگردان").performClick()
        rule.runOnIdle{assertEquals("undo-token",undo)}
    }
    @Test fun customAssetShowsOnlyRelevantFieldsAndSavesManualPrice(){
        var saved:Holding?=null
        rule.setContent{DarayiTheme{HoldingEditor(null,AssetCatalog.physicalGold,false,null,{_,_->},{},{saved=it},{})}}
        rule.onNodeWithText("تغییر",substring=true).performClick()
        rule.onNodeWithTag("category-${AssetCategory.OTHER}").performScrollTo().performClick()
        rule.onNodeWithTag("assetName").performScrollTo().performTextInput("خانه")
        rule.onNodeWithTag("holdingQuantity").performScrollTo().performTextInput("1")
        rule.onNodeWithTag("manualPrice").performScrollTo().performTextInput("1000000")
        rule.onNodeWithTag("manualPrice").performImeAction()
        rule.onNodeWithTag("saveHolding").performClick()
        rule.runOnIdle{assertEquals("خانه",saved!!.name);assertTrue(saved!!.manual);assertEquals(BigDecimal("1000000"),saved!!.price)}
    }
    @Test fun homeAtLargeSystemFontKeepsCardValueAndActionsAccessible(){
        rule.runOnUiThread{org.robolectric.RuntimeEnvironment.setFontScale(1.6f)}
        val h=holding()
        rule.setContent{DarayiTheme{PortfolioHome(PortfolioHomeState(listOf(h),mapOf(h.key to Quote(BigDecimal("1000000"),"تومان","",fetchedAt=1)),emptyMap(),"100"),100_000_000,{},{},{},{})}}
        rule.onNodeWithTag("portfolioList").performScrollToNode(hasTestTag("holding-gold"))
        rule.onNodeWithTag("holding-gold").assertIsDisplayed()
        rule.onNodeWithText("افزودن دارایی").assertIsDisplayed()
        saveImage("portfolio-large-font",rule.onNodeWithTag("portfolioList"))
    }
    private fun saveImage(name:String,node:SemanticsNodeInteraction){
        node.assertExists()
        rule.runOnIdle {
            val view=WindowInspector.getGlobalWindowViews().last{it.isShown}
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file=File("build/reports/ui/$name.png");file.parentFile?.mkdirs()
            file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }

}

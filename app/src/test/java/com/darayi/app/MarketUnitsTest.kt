package com.darayi.app

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class MarketUnitsTest {
    @Test fun goldMilligramsBecomeExactGramWeightAndPortfolioValue() {
        for (input in listOf("67380", "۶۷۳۸۰", "٦٧٣٨٠", "67,380", "۶۷٬۳۸۰")) {
            val grams = goldGrams(input)!!
            assertEquals(0, BigDecimal("67.380").compareTo(grams))
            val h = Holding(name="طلای ۱۸ عیار",key="IR_GOLD_18K",qty=grams,measure="گرم",type=AssetCategory.PHYSICAL_GOLD,manual=false,price=zero,unit="تومان")
            assertEquals(0, BigDecimal("67380000").compareTo(tomanValue(h,Quote(BigDecimal("1000000"),"تومان","test"),zero)))
            assertEquals(0, grams.compareTo(LegacyCodec.holdings(LegacyCodec.encode(listOf(h))).single().qty))
        }
        assertNull(goldGrams(""));assertNull(goldGrams("0"));assertNull(goldGrams("-1000"))
        assertEquals(0, BigDecimal("0.001").compareTo(goldGrams("1")))
    }

    @Test fun editingLegacyGoldPreservesGramQuantityInsteadOfReinterpretingIt() {
        val h = LegacyCodec.holdings("""[{"name":"طلا","key":"IR_GOLD_18K","qty":"67.38","measure":"گرم","type":"طلا و سکه"}]""").single()
        val displayedMilligrams = h.qty * BigDecimal(1000)
        assertEquals(0, BigDecimal("67380").compareTo(displayedMilligrams))
        assertEquals(0, h.qty.compareTo(goldGrams(displayedMilligrams.toPlainString())))
        assertFalse(isGoldWeight("IR_COIN_EMAMI"))
        assertFalse(isGoldWeight("IME_GOLDBAR"))
    }

    @Test fun firstOpenRefreshesAndQuickResumeWaitsForFifteenMinutes() {
        val first = 100L
        assertTrue(RefreshPolicy.shouldRefresh(first,null))
        assertFalse(RefreshPolicy.shouldRefresh(first + 1000,first))
        assertFalse(RefreshPolicy.shouldRefresh(first + RefreshPolicy.INTERVAL_MS - 1,first))
        assertTrue(RefreshPolicy.shouldRefresh(first + RefreshPolicy.INTERVAL_MS,first))
        assertTrue(RefreshPolicy.shouldRefresh(first + RefreshPolicy.INTERVAL_MS * 2,first))
        assertFalse(RefreshPolicy.shouldRefresh(first + RefreshPolicy.INTERVAL_MS + 1000,first + RefreshPolicy.INTERVAL_MS))
    }
}

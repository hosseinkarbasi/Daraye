package com.darayi.app

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class PortfolioPoliciesTest {
    private fun holding(key: String = "BTC", category: String = AssetCategory.CRYPTO) = Holding("id",key,key,BigDecimal.ONE,"عدد",category,false,zero,"تومان")
    private val now = 100_000_000L
    @Test fun twoGoldFieldsPreserveExactGramsAndRejectAmbiguousOverflow() {
        assertEquals(0,BigDecimal("67.380").compareTo(combinedGoldGrams("۶۷","۳۸۰")))
        assertEquals(0,BigDecimal("0.001").compareTo(combinedGoldGrams("","۱")))
        assertNull(combinedGoldGrams("67.38",""));assertNull(combinedGoldGrams("67","1000"));assertNull(combinedGoldGrams("-1","380"));assertNull(combinedGoldGrams("0","0"))
    }
    @Test fun draftEditingAndLegacyWeightCorrectionKeepIdsAndDecimalPrecision() {
        val h=holding("IR_GOLD_18K",AssetCategory.PHYSICAL_GOLD).copy(qty=BigDecimal("67.3805"),measure="گرم")
        val result=HoldingDraft.from(h).validate().holding!!
        assertEquals(h.id,result.id);assertEquals(0,h.qty.compareTo(result.qty))
        val wrong=h.copy(qty=BigDecimal("67380"))
        assertEquals(0,BigDecimal("67.38").compareTo(HoldingDraft.from(wrong).withPreviousMilligrams(wrong).validate().holding!!.qty))
    }
    @Test fun formSelectionResetsUnitsAndValidatesMissingSelectionAndManualPrice() {
        assertNotNull(HoldingDraft(name="طلا",quantity="2").validate().error)
        val draft=HoldingDraft().changeCategory(AssetCategory.OTHER).copy(name="خانه",quantity="1",price="100")
        assertTrue(draft.validate().holding!!.manual)
        assertNull(draft.copy(price="0").validate().holding)
        val selected=HoldingDraft().select(AssetOption("IR_GOLD_18K","طلای ۱۸ عیار",AssetCategory.PHYSICAL_GOLD,"گرم")).copy(grams="67",milligrams="380")
        assertEquals("گرم",selected.validate().holding!!.measure)
        assertNull(selected.search("نقره").validate().holding)
    }
    @Test fun staleDollarUsesManualFallbackAndCachedRateIsExplicitWithoutFallback() {
        val cached=Quote(BigDecimal("100"),"تومان","old",fetchedAt=now-PRICE_FRESH_MS-1)
        assertEquals(FxKind.MANUAL,selectFx(mapOf("USD" to cached),"200",now).kind)
        assertEquals(BigDecimal("200"),selectFx(mapOf("USD" to cached),"200",now).rate)
        assertEquals(FxKind.CACHED,selectFx(mapOf("USD" to cached),"",now).kind)
        assertEquals(FxKind.MISSING,selectFx(emptyMap(),"",now).kind)
        assertEquals(FxKind.FRESH,selectFx(mapOf("USD" to cached.copy(fetchedAt=now)),"200",now).kind)
        val ancientSource=cached.copy(fetchedAt=now,sourceTime=(now-4*60*60*1000L)/1000)
        assertEquals(FxKind.MANUAL,selectFx(mapOf("USD" to ancientSource),"200",now).kind)
        assertEquals(FxKind.CACHED,selectFx(mapOf("USD" to ancientSource),"",now).kind)
    }
    @Test fun sourceErrorsAndCachedHoldingsNeverClaimFreshPrices() {
        val h=holding()
        val quote=Quote(BigDecimal.TEN,"تومان","",fetchedAt=now)
        val states=mapOf(PriceSource.CRYPTO to SourceState(PriceSource.CRYPTO,now,now,availableKeys=setOf("BTC")))
        val fx=FxSelection(BigDecimal.ONE,FxKind.MANUAL)
        assertEquals(PriceHealth.FRESH,priceHealth(listOf(h),mapOf("BTC" to quote),states,fx,now))
        assertEquals(PriceHealth.PARTIAL,priceHealth(listOf(h),mapOf("BTC" to quote),states.mapValues{it.value.copy(error="timeout")},fx,now))
        assertEquals(PriceHealth.CACHED,priceHealth(listOf(h),mapOf("BTC" to quote.copy(fetchedAt=0)),states,fx,now))
        assertEquals(PriceHealth.PARTIAL,priceHealth(listOf(h),emptyMap(),states,fx,now))
        assertEquals(PriceHealth.PARTIAL,priceHealth(listOf(h.copy(type=AssetCategory.OTHER)),mapOf("BTC" to quote),states,fx,now))
        val failedFx=states+(PriceSource.NAVASAN to SourceState(PriceSource.NAVASAN,now,now-100,error="offline"))
        assertEquals(PriceHealth.PARTIAL,priceHealth(listOf(h),mapOf("BTC" to quote),failedFx,fx,now,includeFxSource=true))
        assertEquals(PriceHealth.FRESH,priceHealth(listOf(h),mapOf("BTC" to quote),failedFx,fx,now,includeFxSource=false))
    }
    @Test fun availableAssetsSortAheadAndFailedChecksAreUnknown() {
        val assets=listOf(AssetOption("OLD","Old",AssetCategory.CRYPTO,"عدد"),AssetOption("BTC","Bitcoin",AssetCategory.CRYPTO,"عدد"))
        assertEquals("BTC",AssetCatalog.search(assets,AssetCategory.CRYPTO,"",activeKeys=setOf("BTC")).first().key)
        val state=SourceState(PriceSource.CRYPTO,now,now,availableKeys=setOf("BTC"))
        assertEquals(Availability.UNAVAILABLE,availability(assets[0],mapOf(PriceSource.CRYPTO to state),now))
        assertEquals(Availability.ACTIVE,availability(assets[1],mapOf(PriceSource.CRYPTO to state),now))
        assertEquals(Availability.UNCHECKED,availability(assets[1],mapOf(PriceSource.CRYPTO to state.copy(error="offline")),now))
    }
    @Test fun portfolioFilteringHandlesPersianAndArabicAndSorting() {
        val rows=valued(listOf(holding("کیان",AssetCategory.STOCKS).copy(id="stock",qty=BigDecimal(2)),holding().copy(id="crypto",qty=BigDecimal(3))),emptyMap(),zero)
        assertEquals("stock",filterHoldings(rows,"كيان",null,HoldingSort.VALUE).single().holding.id)
        assertEquals("crypto",filterHoldings(rows,"",AssetCategory.CRYPTO,HoldingSort.NAME).single().holding.id)
        assertEquals("crypto",filterHoldings(rows,"",null,HoldingSort.QUANTITY).first().holding.id)
    }
    @Test fun backupRoundTripContainsNoCredentialFieldsAndMergeDoesNotOverwrite() {
        val current=holding().copy(qty=BigDecimal("0.000000123456789"))
        val text=BackupCodec.encode(listOf(current),"200",now)
        assertFalse(text.contains("apiKey"));assertFalse(text.contains("navasanApiKey"));assertFalse(text.contains("sealed"))
        val decoded=BackupCodec.decode(text)
        assertEquals(current,decoded.holdings.single())
        val merge=BackupCodec.merge(listOf(current),PortfolioBackup(listOf(current.copy(qty=BigDecimal.TEN),current.copy(id="new"))))
        assertEquals(2,merge.size);assertEquals(current,merge.first())
        assertEquals(merge,BackupCodec.merge(merge,PortfolioBackup(listOf(current.copy(id="new")))))
    }
    @Test fun invalidOrDuplicateBackupIsRejectedEntirely() {
        val h=holding()
        for (raw in listOf("{}",BackupCodec.encode(listOf(h,h),""),BackupCodec.encode(listOf(h.copy(qty=zero)),""),BackupCodec.encode(listOf(h),"-1"))) {
            try { BackupCodec.decode(raw);fail("Invalid backup accepted") } catch (_:Exception) {}
        }
    }
}

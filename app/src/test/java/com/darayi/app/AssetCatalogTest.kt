package com.darayi.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.math.BigDecimal

class AssetCatalogTest {
    @Test fun partialTickerAndCompanyNameFindTheSameAssetEvenWithoutCurrentPrice() {
        val assets = AssetCatalog.stocks("""[{"l18":"غمهرا","l30":"تولیدی\\u200cمهرام","pl":0},{"l18":"غمینو","l30":"صنایع غذایی مینو"},{"l18":"غمهرا3","l30":"مهرام"}]""")
        val partial = AssetCatalog.search(assets, AssetCategory.STOCKS, "غمهر")
        assertEquals("غمهرا", partial.first().key)
        assertEquals("سهم", partial.first().measure)
        assertTrue(AssetCatalog.search(assets, AssetCategory.STOCKS, "غم").any { it.key == "غمهرا" })
        assertEquals("غمهرا", AssetCatalog.search(assets, AssetCategory.STOCKS, "مهرام").first().key)
        assertFalse(partial.first().details.contains("\\u200c"))
    }

    @Test fun arabicPersianLettersAndSpacesDoNotBreakSearchOrQuoteBinding() {
        val raw = """[{"l18":"جام طلا","l30":"صندوق کالای فارابی","cs":"صندوق قابل معامله","isin":"IRTKTEST0001","pl":1000},{"l18":"کیان","l30":"گروه کیان","pl":100}]"""
        val assets = AssetCatalog.stocks(raw)
        val fund = AssetCatalog.search(assets, AssetCategory.METAL_FUNDS, "جام طلا").single()
        assertEquals("جامطلا", fund.key)
        assertEquals("جام طلا", fund.name)
        assertNotNull(PriceParser.stocks(raw)[fund.key])
        assertEquals("کیان", AssetCatalog.search(assets, AssetCategory.STOCKS, "كيان").single().key)
    }

    @Test fun fundUnitsAndCompanySharesAreInferredWithoutConfusingCopperCompaniesWithFunds() {
        val assets = AssetCatalog.stocks("""[{"l18":"عیار","l30":"صندوق طلای عیار","cs":"صندوق قابل معامله","isin":"IRTKMOFD0001"},{"l18":"سیمین","l30":"صندوق نقره سیمین","cs":"صندوق"},{"l18":"فملی","l30":"صنایع ملی مس ایران","cs":"فلزات اساسی"},{"l18":"اهرم","l30":"صندوق اهرمی","cs":"صندوق","isin":"IRT1"},{"l18":"نهال","l30":"صندوق کالای نوویرا","cs":"صندوق","isin":"IRTKTEST0001"}]""")
        assertEquals(setOf("عیار", "سیمین"), assets.filter { it.category == AssetCategory.METAL_FUNDS }.map { it.key }.toSet())
        assertTrue(assets.filter { it.key != "فملی" }.all { it.measure == "واحد" })
        assertEquals("سهم", assets.single { it.key == "فملی" }.measure)
    }

    @Test fun catalogCachePreservesAssetBindingAndAutomaticUnits() {
        val assets = AssetCatalog.physicalGold + AssetOption("BTC", "بیت‌کوین", AssetCategory.CRYPTO, "عدد", "Bitcoin BTC")
        assertEquals(assets, AssetCatalog.decode(AssetCatalog.encode(assets)))
        assertEquals("گرم", assets.single { it.key == "IR_GOLD_18K" }.measure)
        assertEquals("عدد", assets.single { it.key == "IR_COIN_EMAMI" }.measure)
        assertTrue(assets.single { it.key == "IR_GOLD_24K" }.manualOnly)
        assertEquals("BTC", AssetCatalog.search(assets, AssetCategory.CRYPTO, "بیت کوین").single().key)
    }

    @Test fun currencyNamesAndUnitsUseCurrencyMetadataAndExcludeNonCurrencyCodes() {
        val assets = AssetCatalog.localizeMarketAssets(listOf(
            AssetOption("USD", "USD", AssetCategory.CURRENCY, "USD"),
            AssetOption("EUR", "EUR", AssetCategory.CURRENCY, "EUR"),
            AssetOption("BTC", "BTC", AssetCategory.CURRENCY, "BTC")
        ))
        assertEquals(setOf("USD", "EUR"), assets.map { it.key }.toSet())
        assertEquals("دلار", assets.single { it.key == "USD" }.measure)
        assertEquals("یورو", assets.single { it.key == "EUR" }.measure)
        assertEquals("USD", AssetCatalog.search(assets, AssetCategory.CURRENCY, "دلار").single().key)
    }

    @Test fun freshProviderSymbolsCanExtendCatalogWithoutConfusingCashDollarAndTether() {
        val assets = AssetCatalog.additionsFromQuotes(mapOf(
            "USDT" to Quote(BigDecimal.TEN, "تومان", "نوبیتکس · زمان دریافت"),
            "USD" to Quote(BigDecimal.TEN, "تومان", "نوسان · زمان منبع"),
            "IR_GOLD_18K" to Quote(BigDecimal.TEN, "تومان", "نوسان · زمان منبع")
        ))
        assertEquals(2, assets.size)
        assertEquals(AssetCategory.CRYPTO, assets.single { it.key == "USDT" }.category)
        assertEquals(AssetCategory.CURRENCY, assets.single { it.key == "USD" }.category)
    }

    @Test fun bundledRealSymbolsSupportRequestedPrefixAndEveryCategoryHasUniqueKeys() {
        val directory = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.exists() }
        val stocks = AssetCatalog.stocks(File(directory, "stock_catalog.json").readText())
        assertTrue(stocks.size > 1000)
        assertEquals("غمهرا", AssetCatalog.search(stocks, AssetCategory.STOCKS, "غمهر").first().key)
        val market = AssetCatalog.localizeMarketAssets(AssetCatalog.decode(File(directory, "market_catalog.json").readText()))
        val all = stocks + market + AssetCatalog.physicalGold
        assertEquals(all.size, all.distinctBy { it.category to it.key }.size)
        assertTrue(all.all { it.measure.isNotBlank() && it.key.isNotBlank() })
        assertTrue(market.any { it.key == "BTC" && it.name == "بیت‌کوین" })
        assertTrue(market.any { it.key == "USD" && it.measure == "دلار" })
    }
}

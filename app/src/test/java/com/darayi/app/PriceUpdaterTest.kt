package com.darayi.app

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.net.URL
import java.io.ByteArrayInputStream
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

class PriceUpdaterTest {
    private class Response(url: URL, private val status: Int, private val body: String) : HttpsURLConnection(url) {
        override fun getResponseCode() = status
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun disconnect() {}
        override fun connect() {}
        override fun usingProxy() = false
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
    private fun holding(key: String, category: Int, manual: Boolean = false) = Holding(
        name=key, key=key, qty=BigDecimal.ONE, measure="عدد", type=categories[category],
        manual=manual, price=BigDecimal.TEN, unit="تومان"
    )
    private val cryptoBody = """{"status":"ok","stats":{"btc-rls":{"latest":"1000"}}}"""

    @Test fun failedStocksPreserveCacheWhileCryptoAndNavasanStillUpdate() {
        val visited = mutableListOf<String>()
        val updater = PriceUpdater(PriceApiClient { url ->
            visited += url.host
            when (url.host) {
                "api.brsapi.ir" -> Response(url, 403, "secret-stock-key")
                "apiv2.nobitex.ir" -> Response(url, 200, cryptoBody)
                "api.navasan.tech" -> Response(url, 200, """{"sekkeh":{"value":"270000"},"usd_sell":{"value":"266800"}}""")
                else -> throw AssertionError("Unexpected provider")
            }
        })
        val old = Quote(BigDecimal("99"), "تومان", "old")
        val result = updater.refresh(listOf(holding("رمپنا", 2), holding("BTC", 3), holding("IR_COIN_EMAMI", 0)), mapOf("رمپنا" to old), "secret-stock-key", "nav-key")
        assertEquals(listOf("api.brsapi.ir", "apiv2.nobitex.ir", "api.navasan.tech"), visited)
        assertEquals(old, result.quotes["رمپنا"])
        assertEquals(0, BigDecimal("100").compareTo(result.quotes["BTC"]!!.price))
        assertEquals(0, BigDecimal("270000000").compareTo(result.quotes["IR_COIN_EMAMI"]!!.price))
        assertEquals(0, BigDecimal("266800").compareTo(result.quotes["USD"]!!.price))
        assertTrue(result.errors.single().contains("سهام") && result.errors.single().contains("حفظ"))
        assertFalse(result.messages.joinToString().contains("secret-stock-key"))
    }

    @Test fun missingKeysDoNotPreventPublicCryptoUpdate() {
        val updater = PriceUpdater(PriceApiClient { url ->
            assertEquals("apiv2.nobitex.ir", url.host)
            Response(url, 200, cryptoBody)
        })
        val result = updater.refresh(listOf(holding("رمپنا", 2), holding("BTC", 3), holding("USD", 1)), emptyMap(), "", "")
        assertEquals(setOf("BTC"), result.quotes.keys)
        assertEquals(2, result.messages.count { it.contains("کلید") })
        assertEquals(2, result.errors.size)
    }

    @Test fun failedNavasanKeepsCashDollarAndDoesNotReplaceItWithTether() {
        val updater = PriceUpdater(PriceApiClient { url ->
            when (url.host) {
                "apiv2.nobitex.ir" -> Response(url, 200, """{"status":"ok","stats":{"usdt-rls":{"latest":"2650000"}}}""")
                "api.navasan.tech" -> Response(url, 429, "rate limited")
                else -> throw AssertionError("Unexpected provider")
            }
        })
        val old = Quote(BigDecimal("260000"), "تومان", "old cash USD")
        val result = updater.refresh(listOf(holding("USDT", 3)), mapOf("USD" to old), "", "nav-key")
        assertEquals(old, result.quotes["USD"])
        assertEquals(0, BigDecimal("265000").compareTo(result.quotes["USDT"]!!.price))
        assertTrue(result.messages.any { it.contains("محدودیت") })
        assertTrue(result.errors.single().contains("محدودیت"))
    }

    @Test fun absentHeldPriceIsReportedAndItsOldValuePreserved() {
        val updater = PriceUpdater(PriceApiClient { Response(it, 200, """{"18ayar":{"value":"1000"}}""") })
        val old = Quote(BigDecimal("999"), "تومان", "old")
        val result = updater.refresh(listOf(holding("IR_GOLD_24K", 0)), mapOf("IR_GOLD_24K" to old), "", "nav-key")
        assertEquals(old, result.quotes["IR_GOLD_24K"])
        assertTrue(result.messages.any { it.contains("IR_GOLD_24K") && it.contains("دریافت نشد") })
        assertTrue(result.errors.single().contains("IR_GOLD_24K"))
        assertEquals(0, BigDecimal("1000").compareTo(result.quotes["IR_GOLD_18K"]!!.price))
    }

    @Test fun emptyPortfolioOrManualPricesWithoutNavasanKeyMakeNoRequests() {
        val updater = PriceUpdater(PriceApiClient { throw AssertionError("No network needed") })
        assertTrue(updater.refresh(emptyList(), emptyMap(), "stock-key", "nav-key").quotes.isEmpty())
        val manual = listOf(holding("BTC", 3, manual=true), holding("رمپنا", 2, manual=true), holding("عیار", 4, manual=true))
        val result = updater.refresh(manual, emptyMap(), "stock-key", "")
        assertTrue(result.quotes.isEmpty())
        assertTrue(result.errors.isEmpty())
    }

    @Test fun metalFundsWithoutAnySharesUseStockApi() {
        var calls = 0
        val updater = PriceUpdater(PriceApiClient { url ->
            calls++
            assertEquals("api.brsapi.ir", url.host)
            Response(url, 200, """[{"l18":"عیار","pl":"320000","plp":"1.25"}]""")
        })
        val result = updater.refresh(listOf(holding("عیار", 4)), emptyMap(), "stock-key", "")
        assertEquals(1, calls)
        assertEquals(0, BigDecimal("32000").compareTo(result.quotes["عیار"]!!.price))
        assertTrue(result.errors.isEmpty())
    }

    @Test fun sharesAndMetalFundsShareOneRequestAndBothHavePortfolioValues() {
        var calls = 0
        val updater = PriceUpdater(PriceApiClient { url ->
            calls++
            Response(url, 200, """[{"l18":"رمپنا","pl":"1000"},{"l18":"عیار","pl":"3000"}]""")
        })
        val items = listOf(holding("رمپنا", 2), holding("عیار", 4))
        val result = updater.refresh(items, emptyMap(), "stock-key", "")
        assertEquals(1, calls)
        assertTrue(result.errors.isEmpty())
        val values = valued(items, result.quotes, zero)
        val total = values.mapNotNull { it.toman }.fold(zero, BigDecimal::add)
        val fundValue = values.single { it.holding.type == AssetCategory.METAL_FUNDS }.toman!!
        assertEquals(0, BigDecimal("400").compareTo(total))
        assertEquals(0, BigDecimal("75").compareTo(share(fundValue, total)))
    }

    @Test fun unsupportedAutomaticAssetReportsRefreshFailureWithoutNetwork() {
        val updater = PriceUpdater(PriceApiClient { throw AssertionError("No provider for this category") })
        val result = updater.refresh(listOf(holding("OTHER", 5)), emptyMap(), "", "")
        assertTrue(result.errors.single().contains("قیمت دستی"))
    }

    @Test fun certificatesUseDedicatedApiInsteadOfRequestingOrdinaryShares() {
        val requests=mutableListOf<String>()
        val updater=PriceUpdater(PriceApiClient { url ->
            requests+=url.path
            assertEquals("/IME/Certificate.php",url.path)
            Response(url,200,"""{"successful":true,"data":[{"contract_code":"SilverBar","commodity":"شمش نقره","contract_currency":"ریال","pl":"5294970"}]}""")
        })
        val result=updater.refresh(listOf(holding("IME_SILVERBAR",2)),emptyMap(),"stock-key","")
        assertEquals(1,requests.size)
        assertTrue(result.errors.isEmpty())
        assertEquals("IME_SILVERBAR",result.certificateAssets.single().key)
        assertTrue(result.stockAssets.isEmpty())
    }

    @Test fun failureOfCertificateApiKeepsItsCacheWhileSharesAndCryptoStillRefresh() {
        val updater=PriceUpdater(PriceApiClient { url ->
            when(url.path){
                "/IME/Certificate.php"->Response(url,403,"do not show this body")
                "/Tsetmc/AllSymbols.php"->Response(url,200,"""[{"l18":"غمهرا","pl":"1000"}]""")
                "/market/stats"->Response(url,200,cryptoBody)
                else->throw AssertionError("Unexpected endpoint")
            }
        })
        val old=Quote(BigDecimal("99"),"تومان","old")
        val result=updater.refresh(listOf(holding("IME_SILVERBAR",2),holding("غمهرا",2),holding("BTC",3)),mapOf("IME_SILVERBAR" to old),"stock-key","")
        assertEquals(old,result.quotes["IME_SILVERBAR"])
        assertNotNull(result.quotes["غمهرا"])
        assertNotNull(result.quotes["BTC"])
        assertTrue(result.errors.single().contains("گواهی"))
        assertFalse(result.errors.single().contains("do not show this body"))
    }
}

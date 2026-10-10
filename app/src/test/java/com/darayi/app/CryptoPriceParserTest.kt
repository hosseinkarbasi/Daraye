package com.darayi.app

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CryptoPriceParserTest {
    @Test fun rialPricesPreservePrecisionAndDoNotNeedDollarRate() {
        val quotes = CryptoPriceParser.nobitex("""{"status":"ok","stats":{"btc-rls":{"isClosed":false,"latest":"219500000060","dayChange":"-0.36"}}}""")
        val q = quotes["BTC"]!!
        assertEquals("تومان", q.unit)
        assertEquals(0, BigDecimal("21950000006").compareTo(q.price))
        assertEquals(BigDecimal("-0.36"), q.change)
        assertEquals("۲۴ ساعت", q.period)
        assertEquals(0L, q.sourceTime)
        assertTrue(q.fetchedAt > 0)
        assertTrue(q.stamp.contains("زمان دریافت"))
        val h = Holding(name="بیت‌کوین", key="BTC", qty=BigDecimal("0.002"), measure="عدد", type=categories[3], manual=false, price=zero, unit="تومان")
        assertEquals(0, BigDecimal("43900000.012").compareTo(tomanValue(h, q, zero)))
    }

    @Test fun tetherDoesNotBecomeCashDollarAndGlobalDataIsIgnored() {
        val quotes = CryptoPriceParser.nobitex("""{"status":"ok","stats":{"usdt-rls":{"latest":"2658800"},"btc-usdt":{"latest":"100000"}},"global":{"binance":{"btc":{"price":"999"}}}}""")
        assertEquals(setOf("USDT"), quotes.keys)
        assertFalse(quotes.containsKey("USD"))
        assertNull(quotes["USDT"]!!.change)
    }

    @Test fun invalidOrClosedMarketsDoNotInvalidateHealthyRecords() {
        val quotes = CryptoPriceParser.nobitex("""{"status":"ok","stats":{"btc-rls":{"latest":"100"},"eth-rls":{"latest":"200","isClosed":true},"sol-rls":{"latest":"bad"},"doge-rls":{"latest":"0"},"bad":"invalid"}}""")
        assertEquals(setOf("BTC"), quotes.keys)
    }

    @Test fun failedAndEmptyResponsesAreRejected() {
        for (raw in listOf("""{"status":"failed","stats":{"btc-rls":{"latest":"100"}}}""", """{"status":"ok","stats":{}}""")) {
            try {
                CryptoPriceParser.nobitex(raw)
                fail("Expected invalid response to be rejected")
            } catch (_: IllegalArgumentException) {
                // Failed requests and empty valid-price sets must not become success.
            }
        }
    }
}

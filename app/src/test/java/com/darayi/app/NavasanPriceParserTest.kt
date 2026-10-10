package com.darayi.app

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class NavasanPriceParserTest {
    @Test fun goldIsTomanPerGramButCoinsAreThousandsOfToman() {
        val quotes = NavasanPriceParser.latest("""{"18ayar":{"value":"26524770","change":"16160","timestamp":1791628559,"date":"1405-07-18 14:05:59"},"sekkeh":{"value":"270000"},"bahar":{"value":"260000"},"nim":{"value":"143000"},"rob":{"value":"76500"}}""")
        for ((key, price) in mapOf("IR_GOLD_18K" to "26524770", "IR_COIN_EMAMI" to "270000000", "IR_COIN_BAHAR" to "260000000", "IR_COIN_HALF" to "143000000", "IR_COIN_QUARTER" to "76500000")) {
            assertEquals(0, BigDecimal(price).compareTo(quotes[key]!!.price))
            assertEquals("تومان", quotes[key]!!.unit)
        }
        assertEquals(1791628559L, quotes["IR_GOLD_18K"]!!.sourceTime)
        assertTrue(quotes["IR_GOLD_18K"]!!.stamp.contains("1405-07-18"))
        assertTrue(quotes["IR_GOLD_18K"]!!.fetchedAt > 0)
    }

    @Test fun absoluteChangeBecomesPercentOfPreviousPrice() {
        val quotes = NavasanPriceParser.latest("""{"usd":{"value":"110","change":"10"},"eur":{"value":"90","change":"-10"},"gbp":{"value":"10","change":"10"},"aed":{"value":"50"}}""")
        assertEquals(0, BigDecimal("10").compareTo(quotes["USD"]!!.change))
        assertEquals(0, BigDecimal("-10").compareTo(quotes["EUR"]!!.change))
        assertNull(quotes["GBP"]!!.change)
        assertNull(quotes["AED"]!!.change)
    }

    @Test fun sellRatesOverrideBaseRatesWithoutConfusingTetherWithDollar() {
        val quotes = NavasanPriceParser.latest("""{"usd":{"value":"260000"},"usd_sell":{"value":"266800"},"usd_buy":{"value":"265000"},"aed":{"value":"72000"},"aed_sell":{"value":"73340"},"usdt":{"value":"265880"},"btc":{"value":"99999"},"24ayar":{"value":"99999"}}""")
        assertEquals(setOf("USD", "AED"), quotes.keys)
        assertEquals(0, BigDecimal("266800").compareTo(quotes["USD"]!!.price))
        assertEquals(0, BigDecimal("73340").compareTo(quotes["AED"]!!.price))
    }

    @Test fun invalidRecordsDoNotDiscardHealthyRatesOrOverrideWithZero() {
        val quotes = NavasanPriceParser.latest("""{"usd":{"value":"100"},"usd_sell":{"value":"0"},"eur":{"value":"bad"},"sekkeh":{"value":"-1"},"18ayar":"bad"}""")
        assertEquals(setOf("USD"), quotes.keys)
        assertEquals(0, BigDecimal("100").compareTo(quotes["USD"]!!.price))
    }

    @Test fun errorAndEmptyResponsesAreRejected() {
        for (raw in listOf("{}", """{"error":"Invalid API key"}""", """{"usd":{"value":"0"}}""")) {
            try {
                NavasanPriceParser.latest(raw)
                fail("Expected response without valid prices to be rejected")
            } catch (_: IllegalArgumentException) { }
        }
    }
}

package com.darayi.app

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CertificatePriceParserTest {
    @Test fun certificatesUsePerCertificateTomanPriceAndDoNotMultiplyWarehouseConversionFactor() {
        val raw = """{"successful":true,"data":[{"commodity":"شمش طلا","contract_code":"GoldBar","contract_description":"گواهی سپرده شمش طلا","contract_size":10,"contract_currency":"ریال","pl":"35290000","plp":"0.41"},{"commodity":"شمش نقره","contract_code":"SilverBar","contract_currency":"ریال","pl":"5294970"}]}"""
        val prices = CertificatePriceParser.prices(raw)
        val assets = AssetCatalog.certificates(raw)
        assertEquals(setOf("IME_GOLDBAR", "IME_SILVERBAR"),prices.keys)
        assertEquals(0, BigDecimal("3529000").compareTo(prices["IME_GOLDBAR"]!!.price))
        assertEquals(0, BigDecimal("529497").compareTo(prices["IME_SILVERBAR"]!!.price))
        assertEquals(BigDecimal("0.41"),prices["IME_GOLDBAR"]!!.change)
        assertTrue(assets.all { it.measure == "گواهی" && it.category == AssetCategory.STOCKS })
        val h=Holding(name=assets[0].name,key=assets[0].key,qty=BigDecimal(2),measure="گواهی",type=AssetCategory.STOCKS,manual=false,price=zero,unit="تومان")
        assertEquals(0, BigDecimal("7058000").compareTo(tomanValue(h,prices[h.key],zero)))
    }

    @Test fun invalidPricesAndUnknownCurrencyAreSkippedWithoutLosingCertificateMetadata() {
        val raw="""[{"contract_code":"SilverBar","contract_currency":"تومان","pl":"100"},{"contract_code":"CopperCthd","contract_currency":"ریال","pl":"0"},{"contract_code":"Other","contract_currency":"unknown","pl":"500"}]"""
        assertEquals(setOf("IME_SILVERBAR"),CertificatePriceParser.prices(raw).keys)
        assertEquals(3,AssetCatalog.certificates(raw).size)
        assertEquals(0,BigDecimal("100").compareTo(CertificatePriceParser.prices(raw).values.single().price))
    }

    @Test fun failedProviderResponseCannotMasqueradeAsSuccessfulCertificateData() {
        val raw="""{"successful":false,"data":[{"contract_code":"GoldBar","contract_currency":"ریال","pl":"1000"}]}"""
        try { CertificatePriceParser.prices(raw);fail("Expected rejection") } catch (_:IllegalArgumentException) {}
        try { AssetCatalog.certificates(raw);fail("Expected rejection") } catch (_:IllegalArgumentException) {}
    }
}

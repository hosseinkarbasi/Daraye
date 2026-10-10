package com.darayi.app

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

class PriceApiClientTest {
    private class Response(
        url: URL,
        private val status: Int = 200,
        private val body: String = "",
        private val failure: IOException? = null,
        private val stream: InputStream? = null
    ) : HttpsURLConnection(url) {
        var disconnected = false
        override fun getResponseCode(): Int {
            failure?.let { throw it }
            return status
        }
        override fun getInputStream(): InputStream = stream ?: ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun disconnect() { disconnected = true }
        override fun connect() {}
        override fun usingProxy() = false
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }

    private fun expectFailure(expected: PriceApiFailure, action: () -> Unit): PriceApiException {
        try {
            action()
        } catch (e: PriceApiException) {
            assertEquals(expected, e.failure)
            return e
        }
        throw AssertionError("Expected $expected")
    }

    @Test fun stocksRequestEncodesKeyAndConvertsRialToToman() {
        lateinit var connection: Response
        val client = PriceApiClient { url ->
            assertEquals("https", url.protocol)
            assertEquals("api.brsapi.ir", url.host)
            assertEquals("/Tsetmc/AllSymbols.php", url.path)
            assertEquals("key=test+key%2B%26&type=1", url.query)
            Response(url, body = """[{"l18":"رمپنا","pl":"1000000"}]""")
                .also { connection = it }
        }
        val quote = client.stocks(" test key+& ")["رمپنا"]!!
        assertEquals("تومان", quote.unit)
        assertEquals(0, java.math.BigDecimal("100000").compareTo(quote.price))
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        assertEquals(18000, connection.connectTimeout)
        assertEquals(18000, connection.readTimeout)
        assertTrue(connection.disconnected)
    }

    @Test fun stocksUseCorrectEndpointAndReturnLastTradePrice() {
        val client = PriceApiClient { url ->
            assertEquals("/Tsetmc/AllSymbols.php", url.path)
            assertEquals("key=test&type=1", url.query)
            Response(url, body = """[{"l18":"رمپنا","pl":22000,"plp":1.2}]""")
        }
        assertEquals(0, java.math.BigDecimal("2200").compareTo(client.stocks("test")["رمپنا"]!!.price))
    }

    @Test fun missingKeyDoesNotMakeNetworkRequest() {
        val client = PriceApiClient { throw AssertionError("Network should not be used") }
        expectFailure(PriceApiFailure.MISSING_KEY) { client.stocks(" ") }
    }

    @Test fun cryptoUsesPublicRialMarketsWithoutSendingCredentials() {
        lateinit var connection: Response
        val client = PriceApiClient { url ->
            assertEquals("https", url.protocol)
            assertEquals("apiv2.nobitex.ir", url.host)
            assertEquals("/market/stats", url.path)
            assertEquals("dstCurrency=rls", url.query)
            Response(url, body="""{"status":"ok","stats":{"eth-rls":{"latest":"25000000","dayChange":"2.5"}}}""")
                .also { connection = it }
        }
        assertEquals(0, java.math.BigDecimal("2500000").compareTo(client.crypto()["ETH"]!!.price))
        assertNull(connection.getRequestProperty("Authorization"))
        assertTrue(connection.disconnected)
    }

    @Test fun cryptoProviderErrorsAreNotSuccessfulUpdates() {
        val client = PriceApiClient { Response(it, body="""{"status":"failed","code":"Unavailable"}""") }
        expectFailure(PriceApiFailure.INVALID_RESPONSE) { client.crypto() }
    }

    @Test fun navasanUsesItsOwnHttpsEndpointAndEncodedKey() {
        val client = PriceApiClient { url ->
            assertEquals("https", url.protocol)
            assertEquals("api.navasan.tech", url.host)
            assertEquals("/latest/", url.path)
            assertEquals("api_key=nav+key%2B%26", url.query)
            Response(url, body="""{"sekkeh":{"value":"270000"}}""")
        }
        assertEquals(0, java.math.BigDecimal("270000000").compareTo(client.navasan(" nav key+& ")["IR_COIN_EMAMI"]!!.price))
    }

    @Test fun missingNavasanKeyDoesNotMakeNetworkRequest() {
        val client = PriceApiClient { throw AssertionError("Network should not be used") }
        expectFailure(PriceApiFailure.MISSING_KEY) { client.navasan(" ") }
    }

    @Test fun navasanErrorWithHttp200IsNotSuccessfulUpdate() {
        val client = PriceApiClient { Response(it, body="""{"error":"Invalid API key"}""") }
        expectFailure(PriceApiFailure.INVALID_RESPONSE) { client.navasan("test") }
    }

    @Test fun accessDenialDoesNotExposeServerBodyOrKey() {
        for (status in listOf(401, 403)) {
            lateinit var connection: Response
            val client = PriceApiClient { url ->
                Response(url, status, "secret-echoed-by-provider").also { connection = it }
            }
            val error = expectFailure(PriceApiFailure.ACCESS_DENIED) { client.stocks("secret-echoed-by-provider") }
            assertEquals(status, error.httpStatus)
            assertFalse(error.message!!.contains("secret-echoed-by-provider"))
            assertTrue(connection.disconnected)
        }
    }

    @Test fun throttlingServerFailureAndOtherHttpErrorsAreDistinct() {
        for ((status, expected) in listOf(
            429 to PriceApiFailure.RATE_LIMITED,
            503 to PriceApiFailure.SERVER_ERROR,
            400 to PriceApiFailure.HTTP_ERROR
        )) {
            val client = PriceApiClient { Response(it, status) }
            val error = expectFailure(expected) { client.stocks("test") }
            assertEquals(status, error.httpStatus)
        }
    }

    @Test fun missingEndpointIsNotReportedAsInvalidKey() {
        val client = PriceApiClient { Response(it, 404, "<html>Not Found</html>") }
        val error = expectFailure(PriceApiFailure.ENDPOINT_NOT_FOUND) { client.stocks("test") }
        assertEquals(404, error.httpStatus)
    }

    @Test fun timeoutAndConnectionFailureAreDistinctAndDisconnect() {
        for ((failure, expected) in listOf(
            SocketTimeoutException() to PriceApiFailure.TIMEOUT,
            IOException() to PriceApiFailure.CONNECTION_FAILED
        )) {
            lateinit var connection: Response
            val client = PriceApiClient { url -> Response(url, failure = failure).also { connection = it } }
            expectFailure(expected) { client.stocks("test") }
            assertTrue(connection.disconnected)
        }
    }

    @Test fun invalidJsonAndApiErrorWithHttp200AreNotSuccessfulPrices() {
        for (body in listOf("<html>error</html>", """{"error":"secret-echoed-by-provider"}""", "{}")) {
            val client = PriceApiClient { Response(it, body = body) }
            val error = expectFailure(PriceApiFailure.INVALID_RESPONSE) { client.stocks("test") }
            assertFalse(error.message!!.contains("secret-echoed-by-provider"))
        }
    }

    @Test fun oversizedResponseIsRejectedAndConnectionIsClosed() {
        lateinit var connection: Response
        val client = PriceApiClient { url ->
            Response(url, stream = ByteArrayInputStream(ByteArray(12_000_001))).also { connection = it }
        }
        expectFailure(PriceApiFailure.RESPONSE_TOO_LARGE) { client.stocks("test") }
        assertTrue(connection.disconnected)
    }
}

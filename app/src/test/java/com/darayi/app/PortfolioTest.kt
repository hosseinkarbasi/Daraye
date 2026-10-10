package com.darayi.app
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
class PortfolioTest {
 private fun n(s:String)=BigDecimal(s)
 private fun holding(id:String,qty:String,type:String=categories[3])=Holding(id,id,id,n(qty),"عدد",type,false,zero,"تومان")
 @Test fun oldHoldingsSurviveKotlinMigration(){
  val legacy="""[{"name":"رمپنا","key":"رمپنا","qty":"7835","measure":"سهم","type":"بورس ایران","manual":false,"price":"0","unit":"تومان"},{"name":"پس‌انداز","key":"","qty":"125.75","measure":"دلار","type":"سایر دارایی‌ها","manual":true,"price":"1","unit":"دلار"}]"""
  val items=LegacyCodec.holdings(legacy)
  assertEquals("legacy-0",items[0].id);assertEquals(n("7835"),items[0].qty);assertEquals("رمپنا",items[0].key);assertTrue(items[1].manual)
  assertEquals(items,LegacyCodec.holdings(LegacyCodec.encode(items)))
 }
 @Test fun mixedPortfolioSortedAndSixPercentShare(){
  val holdings=listOf(holding("missing","1"),holding("BTC","0.002"),holding("gold","10",categories[0]),holding("stock","20000",categories[2]))
  val quotes=mapOf("BTC" to Quote(n("30000"),"دلار",""),"gold" to Quote(n("5000000"),"تومان",""),"stock" to Quote(n("2200"),"تومان",""))
  val rows=valued(holdings,quotes,n("100000"));assertEquals(listOf("gold","stock","BTC","missing"),rows.map{it.holding.id})
  val total=rows.mapNotNull{it.toman}.fold(zero,BigDecimal::add)
  assertEquals(0,n("100000000").compareTo(total));assertEquals(0,n("6").compareTo(share(rows[2].toman!!,total)))
  assertNull(tomanValue(holdings[1],quotes["BTC"],zero));assertNull(rows.last().toman)
 }
 @Test fun pricesNormalizeUnitsAndKeepMissingChangeUnknown(){
  val q=PriceParser.market("""{"gold":[{"symbol":"IR_GOLD_18K","price":12000005,"unit":"ریال"}],"cryptocurrency":[{"symbol":"BTC","price":"103959","unit":"دلار","change_percent":-2.34}]}""")
  assertEquals(0,n("1200000.5").compareTo(q["IR_GOLD_18K"]!!.price));assertNull(q["IR_GOLD_18K"]!!.change)
  assertEquals(n("-2.34"),q["BTC"]!!.change);assertEquals("۲۴ ساعت",q["BTC"]!!.period)
  val shares=PriceParser.stocks("""[{"l18":"رمپنا","pl":22000,"plp":1.2,"time":"12:30"}]""")
  assertEquals(0,n("2200").compareTo(shares["رمپنا"]!!.price));assertEquals("آخرین جلسه",shares["رمپنا"]!!.period)
 }
 @Test fun persianNumbersAndSmallCryptoValues(){assertEquals(n("1234.5"),number("۱٬۲۳۴٫۵"));assertEquals(0,n("1.477").compareTo(tomanValue(holding("SHIB","1"),Quote(n("0.00001477"),"دلار",""),n("100000"))))}
 @Test fun cachedQuotesRoundTrip(){val q=mapOf("USD" to Quote(n("100000"),"تومان","1405/07/13",n("1.2"),"روزانه",100,200));assertEquals(q,LegacyCodec.quotes(LegacyCodec.encodeQuotes(q)))}
 @Test(expected=Exception::class) fun rejectedApiErrorIsNotAZeroPrice(){PriceParser.market("""{"error":"invalid key"}""")}
}

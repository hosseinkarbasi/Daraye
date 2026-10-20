package com.darayi.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.darayi.app.storage.PortfolioDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class PortfolioStorageTest {
    private lateinit var db:PortfolioDatabase
    private lateinit var context:Context
    @Before fun setup(){context=ApplicationProvider.getApplicationContext();db=Room.inMemoryDatabaseBuilder(context,PortfolioDatabase::class.java).allowMainThreadQueries().build()}
    @After fun close(){db.close()}
    private fun legacy()=context.getSharedPreferences("legacy-test",0).apply{edit().clear().commit()}
    private fun h(id:String="gold")=Holding(id,"طلای ۱۸ عیار","IR_GOLD_18K",BigDecimal("67.3805"),"گرم",AssetCategory.PHYSICAL_GOLD,false,zero,"تومان")
    @Test fun migrationIsAtomicExactAndRunsOnceWithoutReimportingDeletedItems(){
        val old=legacy();val quote=Quote(BigDecimal("123456.789"),"تومان","cached",BigDecimal("-1.25"),fetchedAt=123)
        old.edit().putString("holdings",LegacyCodec.encode(listOf(h()))).putString("quotes",LegacyCodec.encodeQuotes(mapOf(h().key to quote))).putString("usd","200").commit()
        val repo=PortfolioRepository(db,old);val migrated=repo.load()
        assertEquals(h(),migrated.holdings.single());assertEquals(quote,migrated.quotes[h().key]);assertEquals("200",migrated.manualFx)
        assertTrue(old.contains("holdings"))
        repo.remove(h().id)
        assertTrue(PortfolioRepository(db,old).load().holdings.isEmpty())
    }
    @Test fun brokenLegacyPortfolioNeverCommitsMigrationMarkerOrPartialRows(){
        val old=legacy();val raw="""[{"name":"broken","qty":"bad"}]""";old.edit().putString("holdings",raw).commit()
        try{PortfolioRepository(db,old).load();fail("Expected failure")}catch(_:Exception){}
        assertNull(db.portfolio().meta("legacy_imported"));assertTrue(db.portfolio().holdings().isEmpty());assertEquals(raw,old.getString("holdings",null))
    }
    @Test fun corruptPriceCacheDoesNotBlockMigrationOfActualHoldings(){
        val old=legacy();old.edit().putString("holdings",LegacyCodec.encode(listOf(h()))).putString("quotes","broken cache").commit()
        val snapshot=PortfolioRepository(db,old).load()
        assertEquals(h(),snapshot.holdings.single());assertTrue(snapshot.quotes.isEmpty())
    }
    @Test fun undoAndBackupImportPreserveCurrentDataAndAreIdempotent(){
        val repo=PortfolioRepository(db,legacy());repo.load();repo.save(h());repo.save(h("other"))
        val position=repo.remove("gold")!!;assertEquals(1,repo.snapshot().holdings.size)
        repo.undo(h(),position);repo.undo(h(),position)
        assertEquals(listOf("gold","other"),repo.snapshot().holdings.map{it.id})
        val incoming=PortfolioBackup(listOf(h().copy(qty=BigDecimal.ONE),h("new")),"300")
        assertEquals(1,repo.importBackup(incoming));assertEquals(0,repo.importBackup(incoming))
        assertEquals(h(),repo.snapshot().holdings.first());assertEquals("300",repo.snapshot().manualFx)
    }
    @Test fun slowerRequestCannotOverwriteNewerProviderStatusOrQuotes(){
        val repo=PortfolioRepository(db,legacy());repo.load()
        val current=Quote(BigDecimal.TEN,"تومان","new",fetchedAt=200)
        val state=SourceState(PriceSource.CRYPTO,200,200,availableKeys=setOf("BTC"))
        repo.cache(mapOf("BTC" to current),mapOf(PriceSource.CRYPTO to state))
        repo.cache(mapOf("BTC" to current.copy(price=BigDecimal.ONE,fetchedAt=100)),mapOf(PriceSource.CRYPTO to state.copy(lastAttemptAt=100,error="old failure")))
        assertEquals(current,repo.snapshot().quotes["BTC"]);assertEquals(state,repo.snapshot().states[PriceSource.CRYPTO])
        // An older request may finish later: completion time alone must not win the race.
        repo.cache(mapOf("BTC" to current.copy(price=BigDecimal.ONE,fetchedAt=300)),mapOf(PriceSource.CRYPTO to state.copy(lastAttemptAt=100)))
        assertEquals(current,repo.snapshot().quotes["BTC"])
    }
    @Test fun secureMigrationRemovesPlaintextOnlyAfterVerifiedEncryptedWrite(){
        val old=legacy();old.edit().putString("apiKey","stock-secret").putString("navasanApiKey","nav-secret").commit()
        val secure=context.getSharedPreferences("secure-test",0).apply{edit().clear().commit()}
        val key=KeyGenerator.getInstance("AES").apply{init(128)}.generateKey()
        val store=SecureKeys(secure,old,AesKeyCipher{key})
        assertEquals(MarketKeys("stock-secret","nav-secret"),store.load())
        assertFalse(old.contains("apiKey"));assertFalse(old.contains("navasanApiKey"))
        val sealed=secure.getString("sealed","")!!
        assertFalse(sealed.contains("stock-secret"));assertFalse(sealed.contains("nav-secret"))
        assertEquals(MarketKeys("stock-secret","nav-secret"),store.load())
        val first=sealed;store.save(MarketKeys("stock-secret","nav-secret"));assertNotEquals(first,secure.getString("sealed",""))
    }
    @Test fun failedEncryptionLeavesLegacyKeysRecoverableAndCipherRejectsTampering(){
        val old=legacy();old.edit().putString("apiKey","keep-me").commit()
        val secure=context.getSharedPreferences("bad-secure-test",0).apply{edit().clear().commit()}
        val failing=object:KeyCipher{override fun seal(text:String):String=error("no key");override fun open(text:String):String=error("no key")}
        try{SecureKeys(secure,old,failing).load();fail("Expected failure")}catch(_:Exception){}
        assertEquals("keep-me",old.getString("apiKey",""));assertFalse(secure.contains("sealed"))
        val key=KeyGenerator.getInstance("AES").apply{init(128)}.generateKey();val cipher=AesKeyCipher{key}
        val sealed=cipher.seal("private")
        val wrong=KeyGenerator.getInstance("AES").apply{init(128)}.generateKey()
        try{AesKeyCipher{wrong}.open(sealed);fail("Tamper accepted")}catch(_:Exception){}
    }
}

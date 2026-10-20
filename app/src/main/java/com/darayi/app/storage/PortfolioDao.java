package com.darayi.app.storage;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface PortfolioDao {
    @Query("SELECT * FROM holdings ORDER BY position, id") List<HoldingEntity> holdings();
    @Query("SELECT * FROM quotes") List<QuoteEntity> quotes();
    @Query("SELECT * FROM sources") List<SourceEntity> sources();
    @Query("SELECT * FROM metadata WHERE id = :key") MetaEntity meta(String key);
    @Query("SELECT * FROM holdings WHERE id = :id") HoldingEntity holding(String id);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putHolding(HoldingEntity holding);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putQuote(QuoteEntity quote);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putSource(SourceEntity source);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putMeta(MetaEntity meta);
    @Query("DELETE FROM holdings WHERE id = :id") void deleteHolding(String id);
}

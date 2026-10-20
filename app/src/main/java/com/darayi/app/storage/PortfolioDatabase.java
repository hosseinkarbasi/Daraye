package com.darayi.app.storage;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {HoldingEntity.class, QuoteEntity.class, SourceEntity.class, MetaEntity.class}, version = 1, exportSchema = true)
public abstract class PortfolioDatabase extends RoomDatabase {
    public abstract PortfolioDao portfolio();
}

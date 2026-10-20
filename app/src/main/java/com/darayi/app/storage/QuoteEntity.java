package com.darayi.app.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "quotes")
public class QuoteEntity {
    @PrimaryKey @NonNull public String symbol = "";
    @NonNull public String price = "0";
    @NonNull public String unit = "تومان";
    @NonNull public String stamp = "";
    @Nullable public String changePercent;
    @NonNull public String period = "";
    public long sourceTime;
    public long fetchedAt;
}

package com.darayi.app.storage;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "holdings")
public class HoldingEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String name = "";
    @NonNull public String symbol = "";
    @NonNull public String quantity = "0";
    @NonNull public String measure = "";
    @NonNull public String category = "";
    public boolean manual;
    @NonNull public String price = "0";
    @NonNull public String priceUnit = "تومان";
    public int position;
}

package com.darayi.app.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sources")
public class SourceEntity {
    @PrimaryKey @NonNull public String id = "";
    public long lastAttemptAt;
    public long lastSuccessAt;
    @Nullable public String error;
    @NonNull public String availableKeys = "[]";
}

package com.darayi.app.storage;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "metadata")
public class MetaEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String value = "";
}

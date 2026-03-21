package com.example.fakeproductdetector.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.fakeproductdetector.domain.model.Product
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Database(
    entities = [ScanEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(ScanDatabase.Converters::class)
abstract class ScanDatabase : RoomDatabase() {

    abstract fun scanDao(): ScanDao

    class Converters {
        private val gson = Gson()

        @TypeConverter
        fun fromProduct(product: Product): String = gson.toJson(product)

        @TypeConverter
        fun toProduct(json: String): Product = gson.fromJson(json, Product::class.java)

        @TypeConverter
        fun fromStringList(list: List<String>): String = gson.toJson(list)

        @TypeConverter
        fun toStringList(json: String): List<String> {
            val type = object : TypeToken<List<String>>() {}.type
            return gson.fromJson(json, type) ?: emptyList()
        }
    }
}
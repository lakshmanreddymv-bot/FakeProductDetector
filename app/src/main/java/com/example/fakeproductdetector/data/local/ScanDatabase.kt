package com.example.fakeproductdetector.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.fakeproductdetector.domain.model.Product
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// S: Single Responsibility — manages Room database setup and type conversion for non-primitive fields
/**
 * Room database for the FakeProductDetector app.
 *
 * Holds the `scan_history` table (see [ScanEntity]) and provides type converters that
 * serialise [Product] objects and [List]<[String]> fields to/from JSON for storage.
 */
@Database(
    entities = [ScanEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(ScanDatabase.Converters::class)
abstract class ScanDatabase : RoomDatabase() {

    /**
     * Returns the DAO for all scan-history CRUD operations.
     *
     * @return The [ScanDao] associated with this database instance.
     */
    abstract fun scanDao(): ScanDao

    /**
     * Room type converters that serialise complex types to/from JSON strings for SQLite storage.
     *
     * Used for [Product] objects and [List]<[String]> fields in [ScanEntity].
     */
    class Converters {
        private val gson = Gson()

        /**
         * Serialises a [Product] domain object to a JSON string for database storage.
         *
         * @param product The [Product] to serialise.
         * @return JSON string representation of the product.
         */
        @TypeConverter
        fun fromProduct(product: Product): String = gson.toJson(product)

        /**
         * Deserialises a JSON string back into a [Product] domain object.
         *
         * @param json JSON string previously produced by [fromProduct].
         * @return The deserialised [Product].
         */
        @TypeConverter
        fun toProduct(json: String): Product = gson.fromJson(json, Product::class.java)

        /**
         * Serialises a list of strings to a JSON array string for database storage.
         *
         * @param list The list of strings to serialise.
         * @return JSON array string representation of the list.
         */
        @TypeConverter
        fun fromStringList(list: List<String>): String = gson.toJson(list)

        /**
         * Deserialises a JSON array string back into a list of strings.
         *
         * @param json JSON array string previously produced by [fromStringList].
         * @return The deserialised list of strings; empty list if the JSON is null or blank.
         */
        @TypeConverter
        fun toStringList(json: String): List<String> {
            val type = object : TypeToken<List<String>>() {}.type
            return gson.fromJson(json, type) ?: emptyList()
        }
    }
}
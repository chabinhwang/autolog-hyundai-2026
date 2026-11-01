package com.autolog.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.Instant

class Converters {
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}

@Database(
    entities = [TripEntity::class, TripEditEntity::class, PlaceEntity::class, DriverEntity::class, ConsentEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AutologDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun tripEditDao(): TripEditDao
    abstract fun placeDao(): PlaceDao
    abstract fun driverDao(): DriverDao
    abstract fun consentDao(): ConsentDao

    companion object {
        fun create(context: Context): AutologDatabase =
            Room.databaseBuilder(context, AutologDatabase::class.java, "autolog.db").build()
    }
}

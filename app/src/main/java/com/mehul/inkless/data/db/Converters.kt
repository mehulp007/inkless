package com.mehul.inkless.data.db

import androidx.room.TypeConverter
import com.mehul.inkless.data.db.entity.CaptureSource
import kotlinx.serialization.json.Json

class Converters {

    @TypeConverter
    fun sourcesToString(sources: Set<CaptureSource>): String =
        sources.sortedBy { it.ordinal }.joinToString(",") { it.name }

    @TypeConverter
    fun stringToSources(value: String): Set<CaptureSource> =
        if (value.isBlank()) emptySet() else value.split(",").map(CaptureSource::valueOf).toSet()

    @TypeConverter
    fun tagsToString(tags: List<String>): String = Json.encodeToString(tags)

    @TypeConverter
    fun stringToTags(value: String): List<String> = Json.decodeFromString(value)
}

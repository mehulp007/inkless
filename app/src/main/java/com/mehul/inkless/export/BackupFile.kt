package com.mehul.inkless.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class BadBackupFile(message: String) : Exception(message)

/** One row of a table, as its backup document. */
data class BackupRow(val id: String, val data: Map<String, Any?>)

/**
 * The backup file's contents: every backed-up table, each row in the same shape as its cloud
 * backup document (parsed fields only, never SMS text), so the same mappers read both.
 *
 * ```
 * { "format": "inkless-backup", "version": 1, "exportedAt": 1762500000000,
 *   "tables": { "categories": [ { "id": "food_canteen", "data": { … } } ], "transactions": [ … ] } }
 * ```
 */
object BackupFile {

    const val FORMAT = "inkless-backup"
    const val VERSION = 1

    private val json = Json { prettyPrint = true }

    fun write(tables: Map<String, List<BackupRow>>, exportedAt: Long): String = json.encodeToString(
        JsonElement.serializer(),
        JsonObject(
            mapOf(
                "format" to JsonPrimitive(FORMAT),
                "version" to JsonPrimitive(VERSION),
                "exportedAt" to JsonPrimitive(exportedAt),
                "tables" to JsonObject(
                    tables.mapValues { (_, rows) ->
                        JsonArray(rows.map { JsonObject(mapOf("id" to JsonPrimitive(it.id), "data" to toJson(it.data))) })
                    },
                ),
            ),
        ),
    )

    fun read(text: String): Map<String, List<BackupRow>> {
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: IllegalArgumentException) {
            throw BadBackupFile("This isn't an Inkless backup file.")
        }
        if (root["format"]?.jsonPrimitive?.content != FORMAT) throw BadBackupFile("This isn't an Inkless backup file.")
        val version = root["version"]?.jsonPrimitive?.longOrNull ?: 0
        if (version > VERSION) throw BadBackupFile("This backup is from a newer version of Inkless. Update the app first.")
        val tables = root["tables"] as? JsonObject ?: throw BadBackupFile("The backup file is damaged.")
        return tables.mapValues { (_, rows) ->
            rows.jsonArray.map { row ->
                val obj = row.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: throw BadBackupFile("The backup file is damaged.")
                @Suppress("UNCHECKED_CAST")
                BackupRow(id, fromJson(obj["data"] ?: JsonNull) as? Map<String, Any?> ?: emptyMap())
            }
        }
    }

    /** Maps, lists, strings, numbers and booleans, the way the cloud documents hold them. */
    fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
        is Iterable<*> -> JsonArray(value.map(::toJson))
        else -> JsonPrimitive(value.toString())
    }

    /** Whole numbers come back as Long and the rest as Double, which is what the document readers expect. */
    fun fromJson(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.content
            else -> element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull
        }
        is JsonObject -> element.mapValues { fromJson(it.value) }
        is JsonArray -> element.map(::fromJson)
    }
}

// ItemButtonEntity.kt
package com.example.firebaselabelapp.data.local.entities

import androidx.room.*
import java.util.Date

@Entity(
    tableName = "item_buttons",
    foreignKeys = [
        ForeignKey(
            entity = MenuButtonEntity::class,
            parentColumns = ["id"],
            childColumns = ["menuId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // ADDED: Composite index to ensure item number is unique within a menu
    indices = [Index(value = ["menuId"]), Index(value = ["menuId", "number"], unique = true)]
)
data class ItemButtonEntity(
    @PrimaryKey val id: String = "",
    val number: String? = null, // ADDED: number field
    val name: String = "",
    val menuId: String = "",
    val userId: String = "",
    val expDurationYears: Int? = null,
    val expDurationMonths: Int? = null,
    val expDurationDays: Int? = null,
    val expDurationHours: Int? = null,
    val description: String? = null,
    val isDeleted: Boolean = false,
    val needsUpload: Boolean = true,
    val lastModified: Date = Date()
) {
    // No-argument constructor required for Firestore deserialization
    constructor() : this("", null, "", "", "", null, null, null, null, null, false, true, Date()) // UPDATED: constructor
}
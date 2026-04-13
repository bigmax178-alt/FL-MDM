package com.example.firebaselabelapp.data.mappers

import com.example.firebaselabelapp.data.local.entities.*
import com.example.firebaselabelapp.model.*
import java.util.Date

/** MenuButton Mappers **/
fun MenuButton.toEntity(userId: String): MenuButtonEntity {
    return MenuButtonEntity(
        id = this.id ?: "",
        number = this.number, // ADDED
        name = this.name,
        userId = userId,
        lastModified = Date(this.lastModified)
    )
}

fun MenuButtonEntity.toModel(): MenuButton {
    return MenuButton(
        id = this.id,
        number = this.number, // ADDED
        name = this.name,
        lastModified = this.lastModified.time
    )
}

/** ItemButton Mappers **/
fun ItemButton.toEntity(userId: String): ItemButtonEntity {
    return ItemButtonEntity(
        id = this.id ?: "",
        number = this.number, // ADDED
        name = this.name,
        menuId = this.menuId,
        userId = userId,
        expDurationYears = this.expDurationYears,
        expDurationMonths = this.expDurationMonths,
        expDurationDays = this.expDurationDays,
        expDurationHours = this.expDurationHours,
        description = this.description,
        lastModified = Date(this.lastModified)
    )
}

fun ItemButtonEntity.toModel(): ItemButton {
    return ItemButton(
        id = this.id,
        number = this.number, // ADDED
        name = this.name,
        menuId = this.menuId,
        expDurationYears = this.expDurationYears,
        expDurationMonths = this.expDurationMonths,
        expDurationDays = this.expDurationDays,
        expDurationHours = this.expDurationHours,
        description = this.description,
        lastModified = this.lastModified.time
    )
}

fun ItemButtonEntity.toFirestoreMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "number" to number, // ADDED
        "name" to name,
        "menuId" to menuId,
        "userId" to userId,
        "expDurationYears" to expDurationYears,
        "expDurationMonths" to expDurationMonths,
        "expDurationDays" to expDurationDays,
        "expDurationHours" to expDurationHours,
        "description" to description,
        "isDeleted" to isDeleted,
        "lastModified" to lastModified
    )
}
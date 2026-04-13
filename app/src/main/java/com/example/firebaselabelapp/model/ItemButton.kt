package com.example.firebaselabelapp.model

import java.time.LocalDateTime

data class ItemButton(
    val number: String? = null,
    val id: String? = null,
    val name: String = "",
    val menuId: String = "",
    val expDurationYears: Int? = 0,
    val expDurationMonths: Int? = 0,
    val expDurationDays: Int? = 0,
    val expDurationHours: Int? = 0,
    val description: String? = null,
    val lastModified: Long = System.currentTimeMillis()
)
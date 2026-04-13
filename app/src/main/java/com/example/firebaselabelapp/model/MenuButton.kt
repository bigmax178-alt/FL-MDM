package com.example.firebaselabelapp.model

data class MenuButton(
    val number: String? = null,
    val id: String? = null,
    val name: String = "",
    val lastModified: Long = System.currentTimeMillis()
)

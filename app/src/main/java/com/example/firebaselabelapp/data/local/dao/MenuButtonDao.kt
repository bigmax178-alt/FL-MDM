package com.example.firebaselabelapp.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.firebaselabelapp.data.local.entities.MenuButtonEntity

@Dao
interface MenuButtonDao {
    @Query("SELECT * FROM menu_buttons WHERE userId = :userId AND isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun getAllMenuButtons(userId: String): Flow<List<MenuButtonEntity>>

    @Query("SELECT * FROM menu_buttons WHERE userId = :userId AND isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllMenuButtonsSync(userId: String): List<MenuButtonEntity>

    @Query("SELECT * FROM menu_buttons WHERE id = :id AND isDeleted = 0")
    suspend fun getMenuButtonById(id: String): MenuButtonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenuButton(menuButton: MenuButtonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenuButtons(menuButtons: List<MenuButtonEntity>)

    @Update
    suspend fun updateMenuButton(menuButton: MenuButtonEntity)

    @Query("UPDATE menu_buttons SET isDeleted = 1, lastModified = :timestamp WHERE id = :id")
    suspend fun softDeleteMenuButton(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM menu_buttons WHERE id = :id")
    suspend fun hardDeleteMenuButton(id: String)

    @Query("DELETE FROM menu_buttons WHERE id IN(:ids)")
    suspend fun deleteMenuButtonsByIds(ids: List<String>)

    @Query("DELETE FROM menu_buttons WHERE userId = :userId")
    suspend fun deleteAllMenuButtons(userId: String)

    @Query("UPDATE menu_buttons SET name = :newName WHERE id = :id")
    suspend fun editMenuButton(newName: String, id: String)
}
package com.example.firebaselabelapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.firebaselabelapp.data.local.entities.ItemButtonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemButtonDao {

    @Query("SELECT * FROM item_buttons WHERE userId = :userId AND isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllItemButtons(userId: String): List<ItemButtonEntity>

    // ADDED: This function was called by your repository but didn't exist
    @Query("SELECT * FROM item_buttons WHERE userId = :userId AND isDeleted = 0")
    suspend fun getAllItemButtonsSync(userId: String): List<ItemButtonEntity>

    @Query("SELECT * FROM item_buttons WHERE menuId = :menuId AND userId = :userId AND isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun getItemButtonsByMenuId(menuId: String, userId: String): Flow<List<ItemButtonEntity>>

    @Query("SELECT * FROM item_buttons WHERE menuId = :menuId AND userId = :userId AND isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getItemButtonsByMenuIdSync(menuId: String, userId: String): List<ItemButtonEntity>

    @Query("SELECT * FROM item_buttons WHERE id = :id AND isDeleted = 0")
    suspend fun getItemButtonById(id: String): ItemButtonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemButton(itemButton: ItemButtonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemButtons(itemButtons: List<ItemButtonEntity>)

    @Update
    suspend fun updateItemButton(itemButton: ItemButtonEntity)

    @Query("UPDATE item_buttons SET isDeleted = 1, lastModified = :timestamp WHERE id = :id")
    suspend fun softDeleteItemButton(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM item_buttons WHERE id = :id")
    suspend fun hardDeleteItemButton(id: String)

    @Query("DELETE FROM item_buttons WHERE menuId = :menuId")
    suspend fun deleteItemButtonsByMenuId(menuId: String)

    @Query("DELETE FROM item_buttons WHERE id IN(:ids)")
    suspend fun deleteItemButtonsByIds(ids: @JvmSuppressWildcards List<String>) // <- FIX IS HERE

    @Query("DELETE FROM item_buttons WHERE userId = :userId")
    suspend fun deleteAllItemButtons(userId: String)

}
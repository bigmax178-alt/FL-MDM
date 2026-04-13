package com.example.firebaselabelapp.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.firebaselabelapp.data.local.dao.ItemButtonDao
import com.example.firebaselabelapp.data.local.dao.MenuButtonDao
import com.example.firebaselabelapp.data.local.entities.ItemButtonEntity
import com.example.firebaselabelapp.data.local.entities.MenuButtonEntity
import java.util.Date

// ADD THIS CLASS
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

// In AppDatabase.kt

// An empty migration to get from v3 to v4 without changing the schema
val MIGRATION_3_4_EMPTY = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Do nothing. The schema is already at this state.
    }
}

// An empty migration to get from v4 to v5 without changing the schema
val MIGRATION_4_5_EMPTY = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Do nothing. The schema is already at this state.
    }
}

// ADD THIS NEW MIGRATION
val Migration_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            // This will run for users with a clean v5 database
            db.execSQL("ALTER TABLE item_buttons ADD COLUMN expDurationMinutes INTEGER")
        } catch (e: Exception) {
            // This will catch the "duplicate column" error for users
            // in a broken state and allow the migration to continue.
            android.util.Log.w("Migration_5_6", "Column expDurationMinutes might already exist, ignoring. $e")
        }
    }
}
val Migration_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create the new V7 table with `closeTimeMin` and WITHOUT `expDurationMinutes`
        db.execSQL("""
            CREATE TABLE item_buttons_new (
                id TEXT NOT NULL, 
                name TEXT NOT NULL, 
                menuId TEXT NOT NULL, 
                userId TEXT NOT NULL, 
                expDurationYears INTEGER, 
                expDurationMonths INTEGER, 
                expDurationDays INTEGER, 
                expDurationHours INTEGER, 
                closeTime INTEGER, 
                closeTimeMin INTEGER,  -- The new column
                description TEXT, 
                isDeleted INTEGER NOT NULL, 
                lastModified INTEGER NOT NULL, 
                PRIMARY KEY(id), 
                FOREIGN KEY(menuId) REFERENCES menu_buttons(id) ON DELETE CASCADE
            )
        """)

        // 2. Re-create the index from your ItemButtonEntity
        db.execSQL("CREATE INDEX IF NOT EXISTS index_item_buttons_menuId ON item_buttons_new(menuId)")

        // 3. Copy all data from the old table to the new one.
        //    (This automatically "drops" expDurationMinutes by not selecting it)
        db.execSQL("""
            INSERT INTO item_buttons_new (id, name, menuId, userId, expDurationYears, expDurationMonths, expDurationDays, expDurationHours, closeTime, closeTimeMin, description, isDeleted, lastModified)
            SELECT id, name, menuId, userId, expDurationYears, expDurationMonths, expDurationDays, expDurationHours, closeTime, 
                   NULL, -- Set the new closeTimeMin to null for all existing rows
                   description, isDeleted, lastModified
            FROM item_buttons
        """)

        // 4. Drop the old V6 table
        db.execSQL("DROP TABLE item_buttons")

        // 5. Rename the new V7 table to the original name
        db.execSQL("ALTER TABLE item_buttons_new RENAME TO item_buttons")
    }
}

// ADD THIS NEW MIGRATION
// REPLACE your empty Migration_7_8 with this one
val Migration_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create the new, correct V8 table (based on your "Expected" schema)
        //    This schema does NOT include `expDurationMinutes`.
        db.execSQL("""
            CREATE TABLE item_buttons_new (
                id TEXT NOT NULL, 
                name TEXT NOT NULL, 
                menuId TEXT NOT NULL, 
                userId TEXT NOT NULL, 
                expDurationYears INTEGER, 
                expDurationMonths INTEGER, 
                expDurationDays INTEGER, 
                expDurationHours INTEGER, 
                closeTime INTEGER, 
                closeTimeMin INTEGER, 
                description TEXT, 
                isDeleted INTEGER NOT NULL, 
                lastModified INTEGER NOT NULL, 
                PRIMARY KEY(id), 
                FOREIGN KEY(menuId) REFERENCES menu_buttons(id) ON DELETE CASCADE
            )
        """)

        // 2. Re-create the index
        db.execSQL("CREATE INDEX IF NOT EXISTS index_item_buttons_menuId ON item_buttons_new(menuId)")

        // 3. Copy data from the old broken table (V7) to the new clean table (V8).
        //    We SELECT all columns *except* the bad `expDurationMinutes`.
        //    This time, we COPY the existing `closeTimeMin` value.
        db.execSQL("""
            INSERT INTO item_buttons_new (id, name, menuId, userId, expDurationYears, expDurationMonths, expDurationDays, expDurationHours, closeTime, closeTimeMin, description, isDeleted, lastModified)
            SELECT id, name, menuId, userId, expDurationYears, expDurationMonths, expDurationDays, expDurationHours, closeTime, 
                   closeTimeMin, -- <-- We copy the existing value
                   description, isDeleted, lastModified
            FROM item_buttons
        """)

        // 4. Drop the old broken V7 table
        db.execSQL("DROP TABLE item_buttons")

        // 5. Rename the new V8 table to the original name
        db.execSQL("ALTER TABLE item_buttons_new RENAME TO item_buttons")
    }
}

val Migration_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // This migration just adds the index that was
        // missing from the 'Found' schema in the previous error.
        // We run it on 'item_buttons' directly because the table is already correct.
        db.execSQL("CREATE INDEX IF NOT EXISTS index_item_buttons_menuId ON item_buttons(menuId)")
    }
}
val Migration_9_10 = object: Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create the new, correct V10 table
        db.execSQL("""
            CREATE TABLE item_buttons_new (
                id TEXT NOT NULL, 
                name TEXT NOT NULL, 
                menuId TEXT NOT NULL, 
                userId TEXT NOT NULL, 
                expDurationYears INTEGER, 
                expDurationMonths INTEGER, 
                expDurationDays INTEGER, 
                expDurationHours INTEGER, 
                description TEXT, 
                isDeleted INTEGER NOT NULL, 
                lastModified INTEGER NOT NULL, 
                PRIMARY KEY(id), 
                FOREIGN KEY(menuId) REFERENCES menu_buttons(id) ON DELETE CASCADE
            )
        """)

        // 2. Copy data from the old V9 table to the new V10 table.
        db.execSQL("""
            INSERT INTO item_buttons_new (id, name, menuId, userId, expDurationYears, expDurationMonths, expDurationDays, expDurationHours, description, isDeleted, lastModified)
            SELECT id, name, menuId, userId, expDurationYears, expDurationMonths, expDurationDays, expDurationHours,
                   description, isDeleted, lastModified
            FROM item_buttons
        """)

        // 3. Drop the old V9 table
        db.execSQL("DROP TABLE item_buttons")

        // 4. Rename the new V10 table to the original name
        db.execSQL("ALTER TABLE item_buttons_new RENAME TO item_buttons")

        // 5. ***** CREATE THE INDEX ON THE FINAL TABLE *****
        db.execSQL("CREATE INDEX IF NOT EXISTS index_item_buttons_menuId ON item_buttons(menuId)")
    }
}

// UPDATED: Replaced empty 10_11 migration
val Migration_10_11 = object: Migration(10,11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // This was an empty migration in your file, leaving it as such
        // If 10 -> 11 was meant to add the 'number' columns, it should have been done here.
        // We will do it in 11 -> 12
    }
}

// ADDED: New migration from 11 to 12
// REPLACE YOUR EXISTING Migration_11_12 WITH THIS FIXED VERSION
val Migration_11_12 = object: Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Add 'number' column to the old table temporarily so we can select it later
        //    (We don't add needsUpload here because we will hardcode a default value for it)
        db.execSQL("ALTER TABLE item_buttons ADD COLUMN number TEXT")
        db.execSQL("ALTER TABLE menu_buttons ADD COLUMN number TEXT")

        // 2. Create the NEW table with ALL columns matching your Entity exactly
        //    Including 'needsUpload' which was missing before!
        db.execSQL("""
            CREATE TABLE item_buttons_new (
                id TEXT NOT NULL, 
                number TEXT, 
                name TEXT NOT NULL, 
                menuId TEXT NOT NULL, 
                userId TEXT NOT NULL, 
                expDurationYears INTEGER, 
                expDurationMonths INTEGER, 
                expDurationDays INTEGER, 
                expDurationHours INTEGER, 
                description TEXT, 
                isDeleted INTEGER NOT NULL, 
                needsUpload INTEGER NOT NULL DEFAULT 1,
                lastModified INTEGER NOT NULL, 
                PRIMARY KEY(id), 
                FOREIGN KEY(menuId) REFERENCES menu_buttons(id) ON DELETE CASCADE
            )
        """)

        // 3. Copy data from old table to new table
        //    Notice we put '1' for needsUpload to set it to true for all existing items
        db.execSQL("""
            INSERT INTO item_buttons_new (
                id, number, name, menuId, userId, 
                expDurationYears, expDurationMonths, expDurationDays, expDurationHours, 
                description, isDeleted, needsUpload, lastModified
            )
            SELECT 
                id, number, name, menuId, userId, 
                expDurationYears, expDurationMonths, expDurationDays, expDurationHours, 
                description, isDeleted, 
                1, -- <--- Hardcoded default value for needsUpload (1 = true)
                lastModified
            FROM item_buttons
        """)

        // 4. Drop old table
        db.execSQL("DROP TABLE item_buttons")

        // 5. Rename new table
        db.execSQL("ALTER TABLE item_buttons_new RENAME TO item_buttons")

        // 6. Create indices (Must match your @Entity indices exactly)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_item_buttons_menuId ON item_buttons(menuId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_item_buttons_menuId_number ON item_buttons(menuId, number)")
    }
}


@Database(
    entities = [MenuButtonEntity::class, ItemButtonEntity::class],
    version = 12, // UPDATED: version
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun menuButtonDao(): MenuButtonDao
    abstract fun itemButtonDao(): ItemButtonDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addMigrations(
                        MIGRATION_3_4_EMPTY,
                        MIGRATION_4_5_EMPTY,
                        Migration_5_6,
                        Migration_6_7,
                        Migration_7_8,
                        Migration_8_9,
                        Migration_9_10,
                        Migration_10_11,
                        Migration_11_12 // ADDED: new migration
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun destroyInstance() {
            try {
                if (INSTANCE?.isOpen == true) {
                    INSTANCE?.close()
                }
            } catch (e: Exception) {
                // Ignore errors during close
            }
            INSTANCE = null
        }
    }
}
import re

file_path = "app/src/main/java/com/example/SamAiApplication.kt"
with open(file_path, "r") as f:
    content = f.read()

migration_code = """
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE translations ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
    }
}
"""

if "MIGRATION_13_14" not in content:
    content = content.replace("import com.example.data.local.DictionaryData", "import com.example.data.local.DictionaryData" + migration_code)

    builder_target = """        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "sam_ai_db"
        )
            .fallbackToDestructiveMigration(true)
            .build()"""

    builder_replacement = """        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "sam_ai_db"
        )
            .addMigrations(MIGRATION_13_14)
            .fallbackToDestructiveMigration(true)
            .build()"""
    
    content = content.replace(builder_target, builder_replacement)

with open(file_path, "w") as f:
    f.write(content)
print("Patched SamAiApplication.kt")

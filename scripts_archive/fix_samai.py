import re

file_path = "app/src/main/java/com/example/SamAiApplication.kt"
with open(file_path, "r") as f:
    content = f.read()

bad_snippet = """import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE translations ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
    }
}
"""

content = content.replace(bad_snippet, "import androidx.room.migration.Migration\nimport androidx.sqlite.db.SupportSQLiteDatabase\n")

good_snippet = """    lateinit var database: AppDatabase
        private set

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE translations ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
        }
    }
"""

content = content.replace("    lateinit var database: AppDatabase\n        private set\n", good_snippet)

with open(file_path, "w") as f:
    f.write(content)
print("Fixed SamAiApplication")

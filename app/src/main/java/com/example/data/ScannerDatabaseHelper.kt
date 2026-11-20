package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ScannerDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE folders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE documents (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                folder_id INTEGER REFERENCES folders(id) ON DELETE SET NULL,
                tag TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                last_pdf_id INTEGER,
                last_pdf_size_bytes INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE pages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                document_id INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
                order_index INTEGER NOT NULL,
                raw_image_path TEXT NOT NULL,
                cropped_image_path TEXT NOT NULL,
                enhanced_image_path TEXT NOT NULL,
                thumb_image_path TEXT NOT NULL DEFAULT '',
                corners_serialized TEXT NOT NULL,
                rotation_degrees INTEGER NOT NULL DEFAULT 0,
                flip_horizontal INTEGER NOT NULL DEFAULT 0,
                filter_mode TEXT NOT NULL DEFAULT 'SHARP_BW',
                contrast_boost REAL NOT NULL DEFAULT 1.0,
                brightness_offset REAL NOT NULL DEFAULT 0.0,
                ocr_text TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE pdf_files (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                file_path TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                page_count INTEGER NOT NULL,
                page_size TEXT NOT NULL,
                quality_preset TEXT NOT NULL,
                is_password_protected INTEGER NOT NULL DEFAULT 0,
                watermark_text TEXT NOT NULL DEFAULT '',
                folder_name TEXT,
                document_id INTEGER,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        val now = System.currentTimeMillis()
        listOf("Documents", "Receipts", "IDs").forEach { folderName ->
            db.execSQL(
                "INSERT INTO folders (name, created_at) VALUES (?, ?)",
                arrayOf<Any>(folderName, now)
            )
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            runCatching {
                db.execSQL("ALTER TABLE pages ADD COLUMN thumb_image_path TEXT NOT NULL DEFAULT ''")
            }
        }
        if (oldVersion < 3) {
            runCatching {
                db.execSQL("ALTER TABLE pages ADD COLUMN ocr_text TEXT NOT NULL DEFAULT ''")
            }
        }
    }

    companion object {
        private const val DATABASE_NAME = "naim_scanner.db"
        private const val DATABASE_VERSION = 3
    }
}

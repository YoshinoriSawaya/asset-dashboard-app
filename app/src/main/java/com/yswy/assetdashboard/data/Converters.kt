package com.yswy.assetdashboard.data

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Roomが直接扱えない型の変換。
 *
 * 日付は `2026-09-25` の文字列で持つ。epoch日数の整数より
 * sqlite3で覗いたときに読めるし、文字列のままでも大小比較・
 * 並べ替えが日付順になる。
 */
class Converters {

    @TypeConverter
    fun fromDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toDate(text: String?): LocalDate? = text?.let(LocalDate::parse)
}

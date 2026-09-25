package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.data.Item
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * 目標の編集画面(E07-01)の入力を、保存できる[Item.Goal]にする。画面から切り離した純粋関数。
 */
object GoalForm {

    sealed interface Result {
        data class Ok(val goal: Item.Goal) : Result
        data class Invalid(val message: String) : Result
    }

    /**
     * @param existing 編集するときの元の目標。新規ならnull(idを新しく振る)
     * @param metricKey 進捗を測る系列。未設定ならnull
     * @param dueDate 空なら期日なし
     */
    fun parse(
        existing: Item.Goal?,
        name: String,
        target: String,
        metricKey: String?,
        dueDate: String,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): Result {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return Result.Invalid("名前を入れてください")

        val yen = CorrectionForm.parseYen(target)
            ?: return Result.Invalid("目標額は数字で入れてください(例: 1,000,000)")
        if (yen <= 0) return Result.Invalid("目標額は0より大きくしてください")

        val due = dueDate.trim().takeIf { it.isNotEmpty() }?.let {
            try {
                LocalDate.parse(it)
            } catch (e: DateTimeParseException) {
                return Result.Invalid("期日は 2030-04-01 の形で入れてください(無ければ空のまま)")
            }
        }

        return Result.Ok(
            Item.Goal(
                id = existing?.id ?: newId(),
                name = trimmedName,
                targetYen = yen,
                metricKey = metricKey,
                dueDate = due,
                // 目標は一覧の上に出す。Metric項目(並び順0)より前
                sortOrder = existing?.sortOrder ?: -1,
                hidden = existing?.hidden ?: false,
            ),
        )
    }
}

package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.Repeat
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

/** リマインダーの編集(E05-05/06)の入力と、「済みにする」の計算。純粋関数。 */
object ReminderForm {

    sealed interface Result {
        data class Ok(val reminder: Item.Reminder) : Result
        data class Invalid(val message: String) : Result
    }

    fun parse(
        existing: Item.Reminder?,
        name: String,
        dueDate: String,
        repeat: Repeat,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): Result {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.Invalid("名前を入れてください")
        val due = try {
            LocalDate.parse(dueDate.trim())
        } catch (e: DateTimeParseException) {
            return Result.Invalid("期日は 2027-03-01 の形で入れてください")
        }
        return Result.Ok(
            Item.Reminder(
                id = existing?.id ?: newId(),
                name = trimmed,
                dueDate = due,
                repeat = repeat,
                // リマインダーは目標(-1)の次、系列(0)より前
                sortOrder = existing?.sortOrder ?: -1,
                hidden = existing?.hidden ?: false,
            ),
        )
    }

    /**
     * 済みにしたあとのリマインダー。繰り返すものは次の期日へ進め、
     * 繰り返さないものは消す(null)。
     *
     * 期日を過ぎてから済みにしても、次の期日は「元の期日 + 1か月/1年」。
     * 車検や保険の更新日のように、期日そのものが毎年決まっているため。
     * それでもまだ過去なら、今日より後になるまで進める。
     */
    fun completed(reminder: Item.Reminder, today: LocalDate): Item.Reminder? {
        if (reminder.repeat == Repeat.NONE) return null
        var next = reminder.dueDate
        do {
            next = when (reminder.repeat) {
                Repeat.MONTHLY -> next.plusMonths(1)
                Repeat.YEARLY -> next.plusYears(1)
                Repeat.NONE -> return null
            }
        } while (!next.isAfter(today))
        return reminder.copy(dueDate = next)
    }
}

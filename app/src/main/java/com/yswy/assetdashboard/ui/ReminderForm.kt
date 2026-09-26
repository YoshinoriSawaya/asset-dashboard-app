package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.Repeat
import java.time.LocalDate
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
        /** 見込み額(任意。E09-04)。空なら無し。 */
        amount: String = "",
        /** 毎年のとき何年ごとか(E07-15)。空なら1年ごと。 */
        everyYears: String = "",
        /** 見込み額を積み立てる目標のid(E07-15)。無ければnull。 */
        fundId: String? = null,
    ): Result {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.Invalid("名前を入れてください")
        val due = DateInput.parse(dueDate)
            ?: return Result.Invalid("期日は ${DateInput.EXAMPLE} の形で入れてください")
        val amountYen = amount.trim().takeIf { it.isNotEmpty() }?.let {
            CorrectionForm.parseYen(it)?.takeIf { yen -> yen > 0 }
                ?: return Result.Invalid("見込み額は数字で入れてください(無ければ空のまま)")
        }
        val years = if (repeat == Repeat.YEARLY) {
            everyYears.trim().takeIf { it.isNotEmpty() }?.let {
                it.toIntOrNull()?.takeIf { y -> y in 1..MAX_EVERY_YEARS }
                    ?: return Result.Invalid("何年ごとかは1〜${MAX_EVERY_YEARS}で入れてください")
            } ?: 1
        } else {
            1
        }
        if (fundId != null && amountYen == null) {
            return Result.Invalid("積立先を選ぶときは、見込み額を入れてください")
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
                amountYen = amountYen,
                repeatYears = years,
                fundId = fundId,
            ),
        )
    }

    private const val MAX_EVERY_YEARS = 50

    /**
     * 済みにしたあとのリマインダー。繰り返すものは次の期日へ進め、
     * 繰り返さないものは消す(null)。
     *
     * 期日を過ぎてから済みにしても、次の期日は「元の期日 + 1か月/N年」(E07-15で何年ごとかを足した)。
     * 車検や保険の更新日のように、期日そのものが毎年決まっているため。
     * それでもまだ過去なら、今日より後になるまで進める。
     */
    fun completed(reminder: Item.Reminder, today: LocalDate): Item.Reminder? {
        if (reminder.repeat == Repeat.NONE) return null
        var next = reminder.dueDate
        do {
            next = when (reminder.repeat) {
                Repeat.MONTHLY -> next.plusMonths(1)
                Repeat.YEARLY -> next.plusYears(reminder.repeatYears.coerceAtLeast(1).toLong())
                Repeat.NONE -> return null
            }
        } while (!next.isAfter(today))
        return reminder.copy(dueDate = next)
    }
}

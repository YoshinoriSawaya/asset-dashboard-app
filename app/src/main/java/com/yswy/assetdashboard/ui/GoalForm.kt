package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.data.AutoTarget
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
     * @param auto 目標額を生活費から出す(E07-06)なら、平均を取る月数と何か月分か。
     *   そのときは[target]を見ない
     * @param rampUp 期日の何か月前から積み増すか(E07-11)。空なら決めない。期日が要る
     * @param floor 生活費から出す目標の下限、生活費の何か月分か(E07-12)。空なら決めない
     */
    fun parse(
        existing: Item.Goal?,
        name: String,
        target: String,
        metricKey: String?,
        dueDate: String,
        newId: () -> String = { UUID.randomUUID().toString() },
        auto: Pair<String, String>? = null,
        resetsYearly: Boolean = false,
        rampUp: String = "",
        floor: String = "",
    ): Result {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return Result.Invalid("名前を入れてください")

        val autoTarget = auto?.let { (average, cover) ->
            val a = average.trim().toIntOrNull()?.takeIf { it in 1..MAX_MONTHS }
                ?: return Result.Invalid("平均を取る月数は1〜${MAX_MONTHS}で入れてください")
            val c = cover.trim().toIntOrNull()?.takeIf { it in 1..MAX_MONTHS }
                ?: return Result.Invalid("何か月分かは1〜${MAX_MONTHS}で入れてください")
            val f = floor.trim().takeIf { it.isNotEmpty() }?.let {
                it.toIntOrNull()?.takeIf { m -> m in 1 until c }
                    ?: return Result.Invalid("下限は1〜${c - 1}か月分で入れてください(目標の${c}か月分より少なく)")
            }
            AutoTarget(a, c, f)
        }

        val yen = if (autoTarget != null) {
            null
        } else {
            val parsed = CorrectionForm.parseYen(target)
                ?: return Result.Invalid("目標額は数字で入れてください(例: 1,000,000)")
            if (parsed <= 0) return Result.Invalid("目標額は0より大きくしてください")
            parsed
        }

        val due = dueDate.trim().takeIf { it.isNotEmpty() }?.let {
            try {
                LocalDate.parse(it)
            } catch (e: DateTimeParseException) {
                return Result.Invalid("期日は 2030-04-01 の形で入れてください(無ければ空のまま)")
            }
        }

        val rampUpMonths = rampUp.trim().takeIf { it.isNotEmpty() }?.let {
            val months = it.toIntOrNull()?.takeIf { m -> m in 1..MAX_RAMP_UP_MONTHS }
                ?: return Result.Invalid("積み増しを始める時期は1〜${MAX_RAMP_UP_MONTHS}か月前で入れてください")
            if (due == null) return Result.Invalid("積み増しを始める時期を決めるには、期日を入れてください")
            if (autoTarget != null || resetsYearly) {
                return Result.Invalid("積み増しは、金額を入れる目標で使えます(生活費から出す目標・毎年の枠では使えません)")
            }
            months
        }

        return Result.Ok(
            Item.Goal(
                id = existing?.id ?: newId(),
                name = trimmedName,
                targetYen = yen,
                // 毎年リセットする枠で系列を選ばなければ、目標の名前の系列を使う
                // (最初に「使った分を足す」ときに手入力の系列としてできる。E07-09)
                metricKey = metricKey ?: trimmedName.takeIf { resetsYearly },
                dueDate = due,
                // 目標は一覧の上に出す。Metric項目(並び順0)より前
                sortOrder = existing?.sortOrder ?: -1,
                hidden = existing?.hidden ?: false,
                autoTarget = autoTarget,
                resetsYearly = resetsYearly,
                rampUpMonths = rampUpMonths,
            ),
        )
    }

    private const val MAX_MONTHS = 24
    private const val MAX_RAMP_UP_MONTHS = 120
}

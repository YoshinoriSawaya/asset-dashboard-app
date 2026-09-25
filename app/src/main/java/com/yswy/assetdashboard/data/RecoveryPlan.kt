package com.yswy.assetdashboard.data

/**
 * 目標に届いていないとき、月々いくら足せば何か月で届くか(E07-07)。
 *
 * 生活防衛資金を取り崩したあとに戻す、という使い方が中心だが、計算は
 * 「貯めて届かせる」目標でも同じなので、Goal全般に出す。
 * 期日に合わせた積み立て(期日から逆算する)はE07-11で扱う。
 */
data class RecoveryPlan(
    /** 目標まで足りない額。届いていれば0。 */
    val shortfallYen: Long,
    /** 何か月で届かせるか → 月々いくら。 */
    val options: List<Option>,
) {
    data class Option(val months: Int, val monthlyYen: Long)

    val isShort: Boolean get() = shortfallYen > 0

    companion object {
        /** 出す期間。「3か月・半年・1年」の目安。 */
        val DEFAULT_MONTHS = listOf(3, 6, 12)

        /** 月額はこの単位で切り上げる。切り捨てるとN か月後にわずかに届かない。 */
        const val ROUND_UP_TO = 1_000L

        /** 目標額か現在の値が分からなければnull(計算できない)。 */
        fun of(targetYen: Long?, currentYen: Long?, months: List<Int> = DEFAULT_MONTHS): RecoveryPlan? {
            if (targetYen == null || currentYen == null) return null
            val shortfall = (targetYen - currentYen).coerceAtLeast(0)
            val options = if (shortfall == 0L) {
                emptyList()
            } else {
                months.filter { it > 0 }.map { m -> Option(m, roundUp(ceilDiv(shortfall, m.toLong()))) }
            }
            return RecoveryPlan(shortfall, options)
        }

        private fun ceilDiv(a: Long, b: Long): Long = (a + b - 1) / b

        private fun roundUp(value: Long): Long = ceilDiv(value, ROUND_UP_TO) * ROUND_UP_TO
    }
}

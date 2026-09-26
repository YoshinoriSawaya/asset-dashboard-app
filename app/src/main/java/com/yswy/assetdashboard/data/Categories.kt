package com.yswy.assetdashboard.data

import java.text.Normalizer

/**
 * 明細のカテゴリの種類(E07-21)。種類で、計算での扱いが決まる(本人が選んだ5種類)。
 *
 * | 種類 | 生活防衛資金の元(生活費) | 消費(積立投資の目安で引く) | 収入・支出(サマリー) |
 * |------|------|------|------|
 * | 生活費 | 入る | 入る | 入る |
 * | 遊び代 | 入れない | 入る | 入る |
 * | 大型出費 | 入れない | 入れない(積立(E07-15)で準備する) | 入る |
 * | 振替 | 入れない | 入れない | 入れない(自分の口座どうし) |
 * | 積立投資 | 入れない | 入れない(今の積立額として数える) | 入る |
 *
 * カテゴリの付いていない明細は生活費として数える(今までと同じ。多めに見積もる側)。
 */
enum class CategoryKind(val label: String, val note: String) {
    LIVING("生活費", "生活防衛資金の元にも、消費にも入る"),
    DISCRETIONARY("遊び代", "生活防衛資金の元には入れず、消費には入る"),
    PLANNED("大型出費", "大型出費の積立で準備するので、生活費にも消費にも入れない"),
    TRANSFER("振替", "自分の口座どうし。収入にも支出にも入れない"),
    INVESTMENT("積立投資", "消費に入れず、今の積立額として数える"),
    ;

    /** 生活防衛資金の元(生活費)に入るか。 */
    val isLiving: Boolean get() = this == LIVING

    /** 消費(積立投資の目安で収入から引くもの)に入るか。 */
    val isConsumption: Boolean get() = this == LIVING || this == DISCRETIONARY
}

/** カテゴリ。名前は人が付ける(食費・遊び代・家具など)。 */
data class Category(val name: String, val kind: CategoryKind)

/** 摘要にこの言葉を含む明細を、このカテゴリにする。 */
data class CategoryRule(val keyword: String, val category: String)

/**
 * カテゴリとその付け方(E07-21)。Driveの `settings/categories.json` が正。
 *
 * ## 摘要の言葉で付ける(本人が選んだ)
 * 同じ摘要の明細は、過去の分もこれからの分も同じカテゴリになる。明細を1件ずつ付ける案は、
 * 明細が増えるたびに手間がかかるので捨てた。出金にも入金にも付く(振替は両方に出るため)。
 *
 * 当たる言葉が2つ以上あれば、**長い言葉**(=細かい決まり)を使う。「ＳＢＩ」で積立投資、
 * 「ＳＢＩ証券投信積立」で…のように、広い言葉と細かい言葉を両方置けるようにするため。
 */
data class CategorySettings(
    val categories: List<Category>,
    val rules: List<CategoryRule>,
) {
    private val byName = categories.associateBy { it.name }

    /** 摘要のカテゴリ。当たらなければnull(生活費として数える)。 */
    fun categoryOf(description: String): Category? {
        val text = normalize(description)
        return rules
            .filter { it.keyword.isNotBlank() && text.contains(normalize(it.keyword)) }
            .maxByOrNull { normalize(it.keyword).length }
            ?.let { byName[it.category] }
    }

    /** 摘要をカテゴリにする(同じ摘要の決まりがあれば置き換える)。nullならその摘要の決まりを外す。 */
    fun assign(description: String, category: String?): CategorySettings {
        val others = rules.filterNot { normalize(it.keyword) == normalize(description) }
        return copy(rules = if (category == null) others else others + CategoryRule(description, category))
    }

    /** カテゴリを足す。同じ名前があれば種類を置き換える。 */
    fun withCategory(category: Category): CategorySettings =
        copy(categories = categories.filterNot { it.name == category.name } + category)

    /** カテゴリを消す。そのカテゴリの決まりも消える。 */
    fun withoutCategory(name: String): CategorySettings =
        copy(categories = categories.filterNot { it.name == name }, rules = rules.filterNot { it.category == name })

    companion object {
        /** 初めて開いたときのカテゴリ。名前は自由に足せる。 */
        val DEFAULT_CATEGORIES = listOf(
            Category("食費", CategoryKind.LIVING),
            Category("遊び代", CategoryKind.DISCRETIONARY),
            Category("家具・家電", CategoryKind.PLANNED),
            Category("振替", CategoryKind.TRANSFER),
            Category("積立投資", CategoryKind.INVESTMENT),
        )

        /** 前の「生活費から除く言葉」(E07-06)を引き継ぐカテゴリ。 */
        const val LEGACY_CATEGORY = "生活費以外(引き継ぎ)"

        val EMPTY = CategorySettings(DEFAULT_CATEGORIES, emptyList())

        /**
         * 前の「生活費から除く言葉」から作る。除いていたものは「生活費以外」として引き継ぐ
         * (生活防衛資金の元から外れたまま)。遊び代・振替などには人が付け直す。
         */
        fun fromLegacy(keywords: List<String>): CategorySettings =
            if (keywords.isEmpty()) {
                EMPTY
            } else {
                CategorySettings(
                    DEFAULT_CATEGORIES + Category(LEGACY_CATEGORY, CategoryKind.DISCRETIONARY),
                    keywords.map { CategoryRule(it, LEGACY_CATEGORY) },
                )
            }

        /** NFKCで全角・半角をそろえ、空白を落として小文字にする(生活費から除く言葉と同じ比べ方)。 */
        fun normalize(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFKC).replace(Regex("\\s"), "").lowercase()
    }
}

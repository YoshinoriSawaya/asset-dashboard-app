package com.yswy.assetdashboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import com.yswy.assetdashboard.data.FundHoldings
import com.yswy.assetdashboard.data.FundNow
import com.yswy.assetdashboard.data.FundSource
import com.yswy.assetdashboard.data.Nav
import com.yswy.assetdashboard.data.NavHistory
import com.yswy.assetdashboard.data.NavPeriod
import com.yswy.assetdashboard.ui.chart.LineChart
import androidx.compose.material3.FilterChip
import com.yswy.assetdashboard.notify.FundSearch
import com.yswy.assetdashboard.notify.NavAlert
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 投資信託の損益(E01-17)。ファンドごとの評価額・取得額・含み益と、平均取得単価・現在値(本人が選んだ: ファンドごとだけ)。
 * 行を押すと、取り込んだ日ごとの推移が開く。値は証券口座の保有商品一覧(E01-15)を取り込んだときのもの。
 */
@Composable
fun FundHoldingsScreen(
    load: suspend () -> FundHoldings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 基準価額の取り先と取れた基準価額(E05-09)。端末の控え */
    loadSources: () -> List<FundSource> = { emptyList() },
    loadNavs: () -> Map<String, Nav> = { emptyMap() },
    /** 設定来の最高値(E05-12)。端末の控え(E05-13) */
    loadPeaks: () -> Map<String, Nav> = { emptyMap() },
    /** 取り先を保存する。(区分, ファンド名, ISIN, 協会コード, 結果) */
    onSaveSource: (String, String, String, String, (String?) -> Unit) -> Unit = { _, _, _, _, _ -> },
    /** 今すぐ基準価額を取る。取れた本数 */
    onRefreshNavs: suspend () -> Int = { 0 },
    /** 取り先の無いファンドをまとめて名前で探して保存する(E05-10)。結果の一言 */
    onFindMissing: suspend () -> String = { "" },
    /** ファンド名で取り先の候補を探す(E05-10)。探せなければnull */
    onSearch: suspend (String) -> List<FundSearch.Candidate>? = { null },
    /** 基準価額の推移を取る(E05-11)。見るときに取る。取れなければnull */
    onLoadHistory: suspend (FundSource) -> List<Nav>? = { null },
    saving: Boolean = false,
) {
    val holdings by produceState<FundHoldings?>(null) { value = load() }
    val money = LocalMoney.current
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var sources by remember { mutableStateOf(loadSources()) }
    var navs by remember { mutableStateOf(loadNavs()) }
    var peaks by remember { mutableStateOf(loadPeaks()) }
    // 何と比べるか(E05-12)。本人が「最高値を基準にする」切り替えを求めた
    var base by rememberSaveable { mutableStateOf(NavBase.COST) }
    var navMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("← 戻る") }
            Text("投資信託の損益", style = MaterialTheme.typography.headlineSmall)
        }
        val h = holdings
        if (h == null) {
            item { Text("読み込み中...") }
            return@LazyColumn
        }
        if (h.funds.isEmpty()) {
            item {
                Text(
                    "まだありません。証券口座の保有商品一覧(ポートフォリオ)のCSVを inbox に置いて同期すると、" +
                        "ファンドごとの取得額と評価額が出ます(前に取り込んだファイルは、processed から inbox に戻すと読み直します)。",
                )
            }
            return@LazyColumn
        }
        item {
            val latest = h.funds.maxOf { it.latest.date }
            Label("${latest}時点の保有商品一覧から。含み益の%は取得額に対する割合です。")
            // 基準価額(E05-09)。取り先を決めたファンドは毎朝取り、平均取得単価より10%以上上がったら知らせる
            Label("取り先(ISIN・協会コード)を決めたファンドは、毎朝基準価額を投資信託協会から取り、平均取得単価より10%以上上がったら知らせます。")
            // 取り先の無いファンドは、名前で探して書き方まで同じものが1本だけなら保存する(E05-10)
            val known = sources.map { it.fundKey }.toSet()
            val missing = h.funds.count { (it.section + "|" + it.name) !in known }
            if (missing > 0) {
                TextButton(enabled = !saving, onClick = {
                    navMessage = "探しています..."
                    scope.launch {
                        navMessage = onFindMissing()
                        sources = loadSources()
                        navs = loadNavs()
                    }
                }) { Text("取り先を名前で探す(${missing}本)") }
            }
            if (sources.isNotEmpty()) {
                TextButton(enabled = !saving, onClick = {
                    navMessage = "取得中..."
                    scope.launch {
                        val n = onRefreshNavs()
                        navs = loadNavs()
                        peaks = loadPeaks()
                        navMessage = "${sources.size}本のうち${n}本取れました"
                    }
                }) { Text("今すぐ基準価額を取る") }
            }
            navMessage?.let { Label(it) }
            if (sources.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NavBase.entries.forEach { b -> FilterChip(base == b, onClick = { base = b }, label = { Text(b.label) }) }
                }
                NowSummary(h.held, navs, peaks, base)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
        items(h.held, key = { it.section + "|" + it.name }) { fund ->
            FundRow(
                fund = fund,
                open = open == fund.section + "|" + fund.name,
                onToggle = { key -> open = if (open == key) null else key },
                sources = sources,
                navs = navs,
                peak = peaks[fund.section + "|" + fund.name],
                base = base,
                saving = saving,
                onSearch = onSearch,
                onLoadHistory = onLoadHistory,
                onSaveSource = { section, name, isin, code, done ->
                    onSaveSource(section, name, isin, code) { error ->
                        if (error == null) sources = loadSources()
                        done(error)
                    }
                },
            )
        }
        // 売り切ったファンド(E01-19)。売った値と今の値を比べられるように残す
        if (h.soldOut.isNotEmpty()) {
            item {
                Text("売り切ったファンド", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                Label("保有商品一覧に載らなくなったファンド。売った日は取り込みと取り込みのあいだまでしか分かりません。")
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }
            items(h.soldOut, key = { "sold|" + it.section + "|" + it.name }) { fund ->
                FundRow(
                    fund = fund,
                    open = open == fund.section + "|" + fund.name,
                    onToggle = { key -> open = if (open == key) null else key },
                    sources = sources,
                    navs = navs,
                    peak = peaks[fund.section + "|" + fund.name],
                    base = base,
                    saving = saving,
                    onSearch = onSearch,
                    onLoadHistory = onLoadHistory,
                    onSaveSource = { section, name, isin, code, done ->
                        onSaveSource(section, name, isin, code) { error ->
                            if (error == null) sources = loadSources()
                            done(error)
                        }
                    },
                )
            }
        }
    }
}

/** ファンド1本の行(E01-17)。押すと取り先・推移・取り込んだ日ごとの値が開く。 */
@Composable
private fun FundRow(
    fund: FundHoldings.Fund,
    open: Boolean,
    onToggle: (String) -> Unit,
    sources: List<FundSource>,
    navs: Map<String, Nav>,
    peak: Nav?,
    base: NavBase,
    saving: Boolean,
    onSearch: suspend (String) -> List<FundSearch.Candidate>?,
    onLoadHistory: suspend (FundSource) -> List<Nav>?,
    onSaveSource: (String, String, String, String, (String?) -> Unit) -> Unit,
) {
    val money = LocalMoney.current
    val key = fund.section + "|" + fund.name
    val s = fund.latest
    val sold = fund.soldOutBy != null
    Column {
            Column(modifier = Modifier.fillMaxWidth().clickable { onToggle(key) }.padding(vertical = 8.dp)) {
                Text(fund.name, style = MaterialTheme.typography.bodyLarge)
                Label("${fund.section} ・ ${s.date}")
                SalesText(fund, navs[key], sources.any { it.fundKey == key })
                if (!sold) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Label("評価額 ${money.amount(s.valueYen)}")
                        s.costYen?.let { Label("取得額 ${money.amount(it)}") }
                    }
                    s.gainYen?.let { gain ->
                        Text(
                            // 実額では額と%、%表示では%だけ(取得額に対する割合)
                            "含み益 ${gainText(money, gain, s.costYen)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (gain < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (!sold && s.unitCost != null && s.price != null) {
                    // 単価は金額ではない(1万口あたりの値など)が、実額の表示でだけ出す
                    // 単価の差は円ではないので、割合で出す
                    Label("平均取得単価 ${money.unit(s.unitCost)} → 現在値 ${money.unit(s.price)}(${ratioText(money, s.price - s.unitCost, s.unitCost)})")
                }
                // 毎朝取った基準価額(E05-09)と、平均取得単価に対する増減。10%以上なら目立たせる
                // 売り切ったファンドは平均取得単価と比べない(もう持っていない。売った値との比べは上の行)
                if (!sold) navs[key]?.let { nav ->
                    val estimate = FundNow.estimate(s, nav)
                    when (base) {
                        NavBase.COST -> {
                            val g = NavAlert.gain(s.unitCost, nav)
                            Text(
                                "基準価額 ${money.unit(nav.yen)}(${nav.date})" + (g?.let { " ・ 平均取得単価より ${ratioText(money, nav.yen - s.unitCost!!, s.unitCost)}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (g != null && g >= NavAlert.THRESHOLD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // 今の基準価額で見直した評価額と含み益(E05-12)。取り込み時の口数のまま
                            estimate?.let { e ->
                                Text(
                                    "今なら 評価額 ${money.amount(e.valueYen)}" +
                                        (e.gainYen?.let { " ・ 含み益 ${gainText(money, it, e.costYen)}" } ?: "") +
                                        "(取り込み時から ${signedRatio(money, e.sinceImport)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if ((e.gainYen ?: 0) < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        NavBase.PEAK -> {
                            val d = FundNow.fromPeak(nav, peak)
                            if (peak == null || d == null) {
                                Label("基準価額 ${money.unit(nav.yen)}(${nav.date}) ・ 最高値は「今すぐ基準価額を取る」か、次の朝の確認で取ります")
                            } else {
                                Text(
                                    "基準価額 ${money.unit(nav.yen)}(${nav.date}) ・ 最高値 ${money.unit(peak.yen)}(${peak.date})より ${signedRatio(money, d)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (d < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                                val atPeak = estimate?.let { FundNow.valueAtPeak(it, nav, peak) }
                                // 割合は上の行と同じなので、額が出せる実額のときだけ
                                if (estimate != null && atPeak != null && money.showsAmounts) {
                                    Label("最高値のときなら評価額 ${money.amount(atPeak)}(今との差 ${money.change(estimate.valueYen - atPeak, atPeak)})")
                                }
                            }
                        }
                    }
                }
                if (open) {
                    sources.firstOrNull { it.fundKey == key }?.let { source ->
                        NavHistorySection(source, if (sold) null else s.unitCost, fund.sales, onLoadHistory)
                    }
                    SourceEditor(
                        fundName = fund.name,
                        current = sources.firstOrNull { it.fundKey == key },
                        saving = saving,
                        onSearch = onSearch,
                        onSave = { isin, code, done -> onSaveSource(fund.section, fund.name, isin, code, done) },
                    )
                    Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        fund.history.forEach { past ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("${past.date}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(
                                    "評価額 ${money.amount(past.valueYen)}" +
                                        (past.gainYen?.let { " ・ 含み益 ${gainText(money, it, past.costYen)}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (fund.history.size == 1) Label("推移は、保有商品一覧を取り込むたびに1点ずつ増えます")
                    }
                }
            }
            HorizontalDivider()
    }
}

/**
 * 売却(E01-19)の一言。本人が選んだ使い道は「売った値と今の値を比べる」。
 * 売った値が分かる(一部売却)ときは、今の値(毎朝の基準価額、無ければいちばん新しい取り込みの現在値)と比べる。
 * 売り切ったときは一覧に値が無いので、行を開いたときに投資信託協会の推移から出す。
 */
@Composable
private fun SalesText(fund: FundHoldings.Fund, nav: Nav?, hasSource: Boolean) {
    val money = LocalMoney.current
    val now = nav?.yen ?: fund.latest.price.takeIf { fund.soldOutBy == null }
    fund.sales.forEach { sale ->
        val what = if (sale.share >= 1.0) "売り切り" else "一部売却(口数の約${shareText(money, sale.share)}減)"
        val compare = if (sale.price != null && now != null) {
            " ・ 売った値 ${money.unit(sale.price)} → 今 ${money.unit(now)}(${ratioText(money, now - sale.price, sale.price)})"
        } else if (sale.price == null) {
            if (hasSource) " ・ 行を開くと売った値と比べます" else " ・ 基準価額の取り先を決めると、売った値と比べられます"
        } else {
            ""
        }
        Text(
            "$what ${sale.after}〜${sale.by}$compare",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/** 何と比べるか(E05-12)。 */
internal enum class NavBase(val label: String) {
    COST("平均取得単価と比べる"),
    PEAK("最高値と比べる"),
}

/**
 * 今の基準価額で見直した合計(E05-12)。平均取得単価と比べるときは含み益の合計、最高値と比べるときは最高値のときとの差。
 * どちらも取り込み時の口数のままの見積もり。
 */
@Composable
private fun NowSummary(held: List<FundHoldings.Fund>, navs: Map<String, Nav>, peaks: Map<String, Nav>, base: NavBase) {
    val money = LocalMoney.current
    Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (base) {
            NavBase.COST -> {
                val t = FundNow.total(held, navs)
                if (t.refreshed == 0) {
                    Label(
                        if (held.any { navs.containsKey(FundSource.keyOf(it.section, it.name)) }) {
                            "取れた基準価額は取り込みより前のものなので、取り込み時の値のままです"
                        } else {
                            "「今すぐ基準価額を取る」を押すと、今の基準価額で評価額と含み益を見直します"
                        },
                    )
                } else {
                    Text(
                        "今なら 評価額 ${money.amount(t.valueYen)} ・ 含み益 ${gainText(money, t.gainYen, t.costYen)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (t.gainYen < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    Label("${t.count}本のうち${t.refreshed}本を今の基準価額で見直し、残りは取り込み時の値。取り込み時の口数のままの見積もりです(あとで積み立てた・売った分は入りません)。")
                }
            }
            NavBase.PEAK -> {
                val t = FundNow.peakTotal(held, navs, peaks)
                val r = t.ratio
                if (t.count == 0 || r == null) {
                    Label("「今すぐ基準価額を取る」を押すか次の朝の確認で、ファンドごとの設定来の最高値を取って比べます")
                } else {
                    Text(
                        // 実額では額と%、%表示では%だけ(含み益と同じ出し方)
                        "最高値のときより ${gainText(money, t.diffYen, t.atPeakYen)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (t.diffYen < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    Label("${held.size}本のうち${t.count}本の合計。ファンドごとの設定来の最高値(基準価額)のときの評価額と、取り込み時の口数のままで比べています。分配金は入れていません。")
                }
            }
        }
    }
}

/** 割合の増減。割合は金額ではないので、マスクのときだけ伏せる。 */
private fun signedRatio(money: MoneyFormat, ratio: Double): String =
    if (money.mode == PrivacyMode.MASK) MoneyFormat.HIDDEN else MoneyFormat.signedPercent(ratio)

/** 減った口数の割合(E01-19)。割合なのでマスクのときだけ伏せる。 */
private fun shareText(money: MoneyFormat, share: Double): String =
    if (money.mode == PrivacyMode.MASK) MoneyFormat.HIDDEN else "${(share * 100).roundToInt()}%"

/**
 * 基準価額の推移(E05-11)。行を開いたときに投資信託協会から取る(本人が選んだ。端末には残さない)。
 * 期間は3か月・1年・全期間を切り替え、平均取得単価の横線を付ける。
 */
@Composable
private fun NavHistorySection(
    source: FundSource,
    unitCost: Long?,
    sales: List<FundHoldings.Sale>,
    onLoadHistory: suspend (FundSource) -> List<Nav>?,
) {
    val money = LocalMoney.current
    // null: 取得中、空: 取れなかった
    val history by produceState<List<Nav>?>(null, source) { value = onLoadHistory(source) ?: emptyList() }
    var period by rememberSaveable { mutableStateOf(NavPeriod.ONE_YEAR) }
    Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("基準価額の推移", style = MaterialTheme.typography.titleSmall)
        val all = history
        when {
            all == null -> Label("投資信託協会から取得中...")
            all.isEmpty() -> Label("基準価額の推移を取れませんでした(通信できないか、取り先が違います)")
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NavPeriod.entries.forEach { p -> FilterChip(period == p, onClick = { period = p }, label = { Text(p.label) }) }
                }
                val window = NavHistory.window(all, period)
                // 売った値(E01-19): 一部売却は取り込みの現在値、売り切りは推移のその日の基準価額
                val salePrices = sales.map { it to (it.price ?: NavHistory.priceOn(all, it.by)?.yen) }
                // %表示の目盛りの基準: 平均取得単価。売り切ったファンドは最後に売った値
                val base = unitCost ?: salePrices.firstOrNull()?.second
                LineChart(
                    points = window.map { it.date to it.yen },
                    axisLabel = { v, _ -> money.unitAxis(v, base) },
                    reference = unitCost,
                    maxDots = 40,
                    markers = sales.map { it.by },
                )
                val change = NavHistory.change(window)
                Label(
                    "${window.first().date} 〜 ${window.last().date}" +
                        (change?.let { " ・ この期間 ${if (money.mode == PrivacyMode.MASK) MoneyFormat.HIDDEN else MoneyFormat.signedPercent(it)}" } ?: ""),
                )
                if (unitCost != null) Label("破線は平均取得単価")
                if (sales.isNotEmpty()) Label("縦の点線は売却に気づいた取り込みの日")
                val latest = all.last()
                salePrices.forEach { (sale, price) ->
                    if (price != null) {
                        Label("${sale.by}に売った値 ${money.unit(price)} → ${latest.date} ${money.unit(latest.yen)}(${ratioText(money, latest.yen - price, price)})")
                    }
                }
            }
        }
    }
}

/**
 * 基準価額の取り先の入力(E05-09)。ISINと協会コードを入れて保存。両方空にするとやめる。
 * 名前で探して候補を押すと、コードが入る(E05-10)。保存は人が押す。
 */
@Composable
private fun SourceEditor(
    fundName: String,
    current: FundSource?,
    saving: Boolean,
    onSearch: suspend (String) -> List<FundSearch.Candidate>?,
    onSave: (String, String, (String?) -> Unit) -> Unit,
) {
    var isin by remember(current) { mutableStateOf(current?.isin.orEmpty()) }
    var code by remember(current) { mutableStateOf(current?.code.orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    var keyword by remember(fundName) { mutableStateOf(fundName) }
    var candidates by remember { mutableStateOf<List<FundSearch.Candidate>?>(null) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("基準価額の取り先", style = MaterialTheme.typography.titleSmall)
        Label("投資信託協会の「投信総合検索ライブラリー」をファンド名で探します。当たらなければ、語を減らして(空白で区切ると全部含むもの)探し直してください。")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(keyword, { keyword = it }, label = { Text("ファンド名") }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                searchMessage = "探しています..."
                candidates = null
                scope.launch {
                    val found = onSearch(keyword)
                    candidates = found
                    searchMessage = when {
                        found == null -> "投資信託協会につながらないので探せませんでした"
                        found.isEmpty() -> "見つかりませんでした"
                        else -> null
                    }
                }
            }) { Text("名前で探す") }
        }
        searchMessage?.let { Label(it) }
        candidates?.let { list ->
            val exact = FundSearch.exactMatch(fundName, list)
            // 書き方まで同じものを先頭に
            (listOfNotNull(exact) + list.filterNot { it == exact }).forEach { c ->
                Column(modifier = Modifier.fillMaxWidth().clickable { isin = c.isin; code = c.code; message = "コードを入れました。保存を押すと決まります" }.padding(vertical = 4.dp)) {
                    Text((if (c == exact) "◎ " else "") + c.name, style = MaterialTheme.typography.bodyMedium)
                    Label("${c.company} ・ ${c.isin} / ${c.code}")
                }
            }
            if (list.size >= 20) Label("20件より多いので先頭だけ出しています。語を足して絞ってください")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(isin, { isin = it }, label = { Text("ISIN(12文字)") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(code, { code = it }, label = { Text("協会コード(8文字)") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = !saving, onClick = {
                message = "保存中..."
                onSave(isin, code) { error -> message = error ?: "保存しました(Driveの settings)" }
            }) { Text(if (current == null) "保存" else "変更を保存") }
            message?.let { Label(it) }
        }
    }
}

/** 基準に対する増減の割合(E01-17)。割合は金額ではないので、マスクのときだけ伏せる。 */
internal fun ratioText(money: MoneyFormat, diff: Long, base: Long?): String = when {
    money.mode == PrivacyMode.MASK -> MoneyFormat.HIDDEN
    base == null || base == 0L -> MoneyFormat.HIDDEN_SHORT
    else -> MoneyFormat.signedPercent(diff.toDouble() / kotlin.math.abs(base))
}

/** 含み益(E01-17)。実額では額と割合、%表示では割合だけ、マスクでは伏せる。 */
internal fun gainText(money: MoneyFormat, gain: Long, cost: Long?): String = when (money.mode) {
    PrivacyMode.REAL -> "${money.change(gain, cost)}(${ratioText(money, gain, cost)})"
    PrivacyMode.PERCENT -> ratioText(money, gain, cost)
    PrivacyMode.MASK -> MoneyFormat.HIDDEN
}

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
import com.yswy.assetdashboard.data.FundSource
import com.yswy.assetdashboard.data.Nav
import com.yswy.assetdashboard.data.NavHistory
import com.yswy.assetdashboard.data.NavPeriod
import com.yswy.assetdashboard.ui.chart.LineChart
import androidx.compose.material3.FilterChip
import com.yswy.assetdashboard.notify.FundSearch
import com.yswy.assetdashboard.notify.NavAlert
import kotlinx.coroutines.launch

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
                        navMessage = "${sources.size}本のうち${n}本取れました"
                    }
                }) { Text("今すぐ基準価額を取る") }
            }
            navMessage?.let { Label(it) }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
        items(h.funds, key = { it.section + "|" + it.name }) { fund ->
            val key = fund.section + "|" + fund.name
            val s = fund.latest
            Column(modifier = Modifier.fillMaxWidth().clickable { open = if (open == key) null else key }.padding(vertical = 8.dp)) {
                Text(fund.name, style = MaterialTheme.typography.bodyLarge)
                Label("${fund.section} ・ ${s.date}")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
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
                if (s.unitCost != null && s.price != null) {
                    // 単価は金額ではない(1万口あたりの値など)が、実額の表示でだけ出す
                    // 単価の差は円ではないので、割合で出す
                    Label("平均取得単価 ${money.unit(s.unitCost)} → 現在値 ${money.unit(s.price)}(${ratioText(money, s.price - s.unitCost, s.unitCost)})")
                }
                // 毎朝取った基準価額(E05-09)と、平均取得単価に対する増減。10%以上なら目立たせる
                navs[key]?.let { nav ->
                    val g = NavAlert.gain(s.unitCost, nav)
                    Text(
                        "基準価額 ${money.unit(nav.yen)}(${nav.date})" + (g?.let { " ・ 平均取得単価より ${ratioText(money, nav.yen - s.unitCost!!, s.unitCost)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (g != null && g >= NavAlert.THRESHOLD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (open == key) {
                    sources.firstOrNull { it.fundKey == key }?.let { source ->
                        NavHistorySection(source, s.unitCost, onLoadHistory)
                    }
                    SourceEditor(
                        fundName = fund.name,
                        current = sources.firstOrNull { it.fundKey == key },
                        saving = saving,
                        onSearch = onSearch,
                        onSave = { isin, code, done ->
                            onSaveSource(fund.section, fund.name, isin, code) { error ->
                                if (error == null) sources = loadSources()
                                done(error)
                            }
                        },
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
}

/**
 * 基準価額の推移(E05-11)。行を開いたときに投資信託協会から取る(本人が選んだ。端末には残さない)。
 * 期間は3か月・1年・全期間を切り替え、平均取得単価の横線を付ける。
 */
@Composable
private fun NavHistorySection(source: FundSource, unitCost: Long?, onLoadHistory: suspend (FundSource) -> List<Nav>?) {
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
                LineChart(
                    points = window.map { it.date to it.yen },
                    axisLabel = { v, _ -> money.unitAxis(v, unitCost) },
                    reference = unitCost,
                    maxDots = 40,
                )
                val change = NavHistory.change(window)
                Label(
                    "${window.first().date} 〜 ${window.last().date}" +
                        (change?.let { " ・ この期間 ${if (money.mode == PrivacyMode.MASK) MoneyFormat.HIDDEN else MoneyFormat.signedPercent(it)}" } ?: ""),
                )
                if (unitCost != null) Label("破線は平均取得単価")
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

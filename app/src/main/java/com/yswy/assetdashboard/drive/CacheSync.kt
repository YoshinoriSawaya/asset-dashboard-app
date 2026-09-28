package com.yswy.assetdashboard.drive

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import androidx.room.withTransaction
import com.yswy.assetdashboard.data.AppDatabase
import com.yswy.assetdashboard.data.BankTransactionEntity
import com.yswy.assetdashboard.data.CategorySettings
import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.ItemEntity
import com.yswy.assetdashboard.data.ItemType
import com.yswy.assetdashboard.data.MetricOrigin
import com.yswy.assetdashboard.data.MetricPointEntity
import com.yswy.assetdashboard.csv.Deduplication
import com.yswy.assetdashboard.csv.ParsedData

/**
 * Driveの中身からローカルキャッシュ(Room)を作り直す。
 *
 * ## 差分ではなく丸ごと作り直す
 * 入力は `backup/`(CSV由来)・`corrections/`・`settings/` の3つで、
 * 同期のたびにこれを全部読んで3テーブルを入れ替える。
 *
 * - Driveと食い違う状態が残らない。補正を消した、項目を消した、なども
 *   次の同期でそのまま反映される(差分だと「消した」を追いかける必要がある)
 * - 取り込み済みのファイルもbackupから入るので、processedのファイルを
 *   inboxへ戻して読み直す必要が無い
 * - 入れ直し(E06-02)と同じ道を通るので、「Driveから再構築できる」が
 *   毎回の同期で確かめられている
 *
 * 代わりに、同期のたびにbackupを全部読む。落とすのは中身が変わったものだけで、
 * ほかは端末の写しから読む([BackupCopies]。中身のmd5をDriveの一覧と比べる。E02-08)。
 * 作り直すときは写しからでも丸ごとなので、上の性質は変わらない。
 *
 * ## 何も変わっていなければ作り直さない(E02-09)
 * backup・corrections・settingsの一覧(中身のmd5)とアプリの入れ物(版・入れた日時)が、前に作り直したときと同じなら、
 * 作り直しても同じ中身になるので飛ばす。何か1つでも変われば丸ごと作り直すので、
 * 「Driveから作り直せる」はCSVを取り込んだ同期のたびに確かめられる。
 *
 * ## 作り直せないときは今のキャッシュを残す
 * 一覧が取れない、ダウンロードが途中で失敗した、correctionsやsettingsが
 * 読めない、のいずれかなら入れ替えない。空や欠けたデータで上書きすると、
 * 資産が消えたように見える。次の同期でやり直せばよい。
 *
 * 一方、**中身の形が読めないbackupファイル**は1つ飛ばして先へ進む
 * (1ファイルのために全部を止めない)。飛ばしたものは報告に出す。
 */
object CacheSync {

    private const val TAG = "CacheSync"

    /**
     * backupを落とせなかったときに、読み直すまで待つ時間(E02-06)。
     * 取り込んだ同じ同期の中で、書いたばかりのbackupを読むと失敗することがある
     * (Drive側がまだ追いついていないらしい。2026-09-26に2回起きた)。
     */
    val RETRY_DELAYS_MS = listOf(2_000L, 5_000L)

    /**
     * [block]が例外を投げたら、[delaysMs]の時間を待って読み直す。全部失敗したら最後の例外を投げる。
     * [wait]はテストで待たずに済ませるため差し替えられる。
     */
    suspend fun <T> withRetry(
        delaysMs: List<Long> = RETRY_DELAYS_MS,
        wait: suspend (Long) -> Unit = { delay(it) },
        block: suspend () -> T,
    ): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val pause = delaysMs.getOrNull(attempt++) ?: throw e
                Log.w(TAG, "読み直す(${attempt}回目、${pause}ms後): ${e.message}")
                wait(pause)
            }
        }
    }

    /** Roomに入れるもの一式。 */
    data class Snapshot(
        val items: List<ItemEntity>,
        val points: List<MetricPointEntity>,
        val transactions: List<BankTransactionEntity>,
        /** 重複として落とした行数(期間の重なるCSVなど)。 */
        val droppedDuplicates: Int,
        /**
         * カテゴリの月の予算(E07-29)。DBには入れず、端末に控える([com.yswy.assetdashboard.data.BudgetStore])。
         * 毎朝の確認は同期しないので、Driveの categories.json を読めないため
         */
        val budgets: Map<String, Long> = emptyMap(),
        /** 基準価額の取り先(E05-09)。funds.json を読めなければnull(端末の控えを書き換えない)。 */
        val fundSources: List<com.yswy.assetdashboard.data.FundSource>? = null,
    ) {
        /**
         * 中身の指紋(E06-03)。同じDriveの中身から作れば、いつ・どの端末で作っても
         * 同じ値になる。入れ直す前と後で比べれば、同じ状態に戻ったかが分かる。
         *
         * 並び順に左右されないよう、行を文字列にして並べ替えてからハッシュを取る。
         * 金額が入るので、画面には先頭の8文字だけを出す(元には戻せない)。
         */
        val fingerprint: String
            get() {
                val lines = buildList {
                    items.forEach { add("i|${it.id}|${it.type}|${it.name}|${it.metricKey}|${it.targetYen}|${it.dueDate}|${it.repeat}|${it.sortOrder}|${it.hidden}|${it.autoAverageMonths}|${it.autoCoverMonths}|${it.resetsYearly}") }
                    points.forEach { add("p|${it.metricKey}|${it.date}|${it.valueYen}|${it.origin}") }
                    transactions.forEach { add("t|${it.dedupKey}|${it.balance}|${it.memo}|${it.label}|${it.sourceFileId}|${it.excludedFromSpending}") }
                }.sorted()
                val digest = MessageDigest.getInstance("SHA-256").digest(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
                return digest.joinToString("") { "%02x".format(it) }.take(8)
            }
    }

    sealed interface Outcome {
        data class Rebuilt(
            val snapshot: Snapshot,
            val backupFiles: Int,
            /** 形が読めず飛ばしたbackupファイル名。 */
            val unreadable: List<String>,
            /**
             * processedにあるのに、どのbackupの元にもなっていないファイル名(E01-16)。
             * backupが上書きされて消えた・書けなかったもの。inboxに戻せば取り込み直せる。
             * processedの一覧を取れなければnull(確かめられなかった)。
             */
            val missingBackups: List<String>? = emptyList(),
        ) : Outcome

        /** 作り直さなかった。今のキャッシュはそのまま。 */
        data class Kept(val reason: String) : Outcome

        /**
         * 入力が前に作り直したときと同じなので、作り直さなかった(E02-09)。今のキャッシュがそのままDriveと同じ。
         * [fingerprint]は前に作り直したときの指紋。
         */
        data class Unchanged(
            val fingerprint: String,
            val backupFiles: Int,
            val unreadable: List<String>,
            val missingBackups: List<String>?,
        ) : Outcome
    }

    fun Outcome.summary(): String = when (this) {
        is Outcome.Rebuilt -> buildString {
            append("キャッシュ: Metric ${snapshot.points.size}点 / 明細 ${snapshot.transactions.size}件")
            append(" / 項目 ${snapshot.items.size}件")
            append(" (backup ${backupFiles}件")
            if (snapshot.droppedDuplicates > 0) append(", 重複 ${snapshot.droppedDuplicates}行")
            if (unreadable.isNotEmpty()) append(", 読めず ${unreadable.size}件")
            append(")")
            when {
                missingBackups == null -> append(" / processedの一覧を取れずbackupの抜けは未確認")
                missingBackups.isNotEmpty() -> append(" / backupの無い取り込み済み ${missingBackups.size}件: ${missingBackups.joinToString(", ")}")
            }
            append(" 指紋 ${snapshot.fingerprint}")
        }
        is Outcome.Kept -> "キャッシュは更新せず: $reason"
        is Outcome.Unchanged -> buildString {
            append("キャッシュ: Driveに変わりが無いので作り直さず (backup ${backupFiles}件")
            if (unreadable.isNotEmpty()) append(", 読めず ${unreadable.size}件")
            append(")")
            when {
                missingBackups == null -> append(" / processedの一覧を取れずbackupの抜けは未確認")
                missingBackups.isNotEmpty() -> append(" / backupの無い取り込み済み ${missingBackups.size}件: ${missingBackups.joinToString(", ")}")
            }
            append(" 指紋 $fingerprint")
        }
    }

    /** 一度に落とすbackupの数(E02-08)。多すぎるとDriveに断られるので数本ずつ。 */
    private const val PARALLEL_DOWNLOADS = 4

    private class DownloadFailed(val fileName: String, cause: Exception) : Exception(cause)

    /**
     * @param copies 落としたbackupの写し(E02-08)。中身がDriveと同じものは落とし直さない。nullなら毎回全部落とす
     */
    suspend fun rebuild(api: DriveApi, folders: AppFolders, db: AppDatabase, copies: BackupCopies? = null): Outcome {
        val files = try {
            api.listFiles(folders.backup)
        } catch (e: Exception) {
            Log.w(TAG, "backupの一覧を取れない", e)
            return Outcome.Kept("backupの一覧を取れない")
        }.filter { it.name.endsWith(".json") }
            // 後から取り込んだものを後ろに。Metricの後勝ちがこの順序に依存する。
            .sortedBy { it.modifiedTime }

        val contents = try {
            fetchAll(api, files, copies)
        } catch (e: DownloadFailed) {
            // 読み直してもだめなら、通信の問題。次回は読める。欠けたまま入れ替えない。
            Log.w(TAG, "backupを落とせない: ${e.fileName}", e.cause)
            return Outcome.Kept("backupを落とせない: ${e.fileName}")
        }
        copies?.keepOnly(files.map { it.id }.toSet())

        val backups = mutableListOf<BackupReader.Backup>()
        val unreadable = mutableListOf<String>()
        for ((file, bytes) in files.zip(contents)) {
            val backup = runCatching { BackupReader.parse(String(bytes, Charsets.UTF_8)) }.getOrNull()
            if (backup == null) {
                Log.w(TAG, "backupの形を読めないので飛ばす: ${file.name}")
                unreadable += file.name
            } else {
                backups += backup
            }
        }

        // 同じ元ファイルのbackupが2つあれば(古い名前が片付かずに残った)、新しいほうだけ使う(E01-16)
        val latest = latestPerSource(backups)

        // processed(backupの抜けを探す)・corrections・settingsの一覧は互いに関係しないので並べて取る(E02-09)
        val (processed, correctionFiles, settingFiles) = coroutineScope {
            listOf(folders.processed, folders.corrections, folders.settings)
                .map { id -> async { runCatching { api.listFiles(id) }.getOrNull() } }
                .awaitAll()
        }
        // backupの抜けを探す。取れなくても作り直しは止めない(見えるように書くだけ)
        if (processed == null) Log.w(TAG, "processedの一覧を取れない")
        val missing = processed?.let { missingBackups(it, latest) }

        // 入力(backup・corrections・settingsの中身とアプリの版)が前に作り直したときと同じなら、作り直しても同じなので飛ばす(E02-09)
        // 入れ物の印が取れなければ、入れ直したかが分からないので毎回作り直す
        val key = if (copies != null && copies.appStamp.isNotEmpty() && correctionFiles != null && settingFiles != null) {
            inputKey(copies.appStamp, files, correctionFiles, settingFiles)
        } else {
            null
        }
        val last = copies?.lastRebuild()
        if (key != null && last != null && last.first == key) {
            return Outcome.Unchanged(last.second, files.size, unreadable, missing).also { Log.i(TAG, it.summary()) }
        }

        // 読むファイルも互いに関係しないので並べて読む(E02-09)
        val (corrections, settings, categories, fundSources) = coroutineScope {
            val c = async { Corrections.load(api, folders) }
            val s = async { Settings.load(api, folders) }
            val k = async { CategoryStore.load(api, folders) }
            // 基準価額の取り先(E05-09)。読めなくても作り直しは止めない(取り先が無いだけ。端末の控えは前のまま残す)
            val f = async { FundSourceStore.load(api, folders) }
            Loaded(c.await(), s.await(), k.await(), f.await())
        }
        corrections ?: return Outcome.Kept("correctionsを読めない")
        settings ?: return Outcome.Kept("settingsを読めない")
        categories ?: return Outcome.Kept("明細のカテゴリを読めない")

        val snapshot = build(latest, corrections, settings, categories).copy(fundSources = fundSources)

        db.withTransaction {
            db.itemDao().deleteAll()
            db.metricPointDao().deleteAll()
            db.bankTransactionDao().deleteAll()
            db.itemDao().upsertAll(snapshot.items)
            db.metricPointDao().upsertAll(snapshot.points)
            db.bankTransactionDao().insertAll(snapshot.transactions)
        }

        // 入れ替えられたときだけ、入力の組み合わせを覚える。一覧を取ったあとに変わっていても、次は一覧が違うので作り直す
        if (key != null) copies?.saveRebuild(key, snapshot.fingerprint)

        return Outcome.Rebuilt(snapshot, files.size, unreadable, missing).also {
            Log.i(TAG, it.summary())
        }
    }

    private data class Loaded(
        val corrections: List<Corrections.Entry>?,
        val settings: List<ItemEntity>?,
        val categories: CategorySettings?,
        val fundSources: List<com.yswy.assetdashboard.data.FundSource>?,
    )

    /**
     * 作り直しの入力の組み合わせ(E02-09)。backupは並び順も効く(Metricの後勝ち)ので一覧の順のまま、
     * corrections・settingsは名前順にして、IDと名前・md5・更新日時をつなげてハッシュを取る。
     * アプリの入れ物の印([BackupCopies.appStamp]。版と入れた日時)も入れる(読み方・組み立て方を直したアプリでは、同じ入力でも中身が変わる)。
     * md5の無いファイルがあれば、変わったかを確かめられないのでnull(毎回作り直す)。
     */
    fun inputKey(
        appVersion: String,
        backups: List<DriveApi.DriveFile>,
        corrections: List<DriveApi.DriveFile>,
        settings: List<DriveApi.DriveFile>,
    ): String? {
        val all = backups + corrections + settings
        if (all.any { it.md5Checksum == null }) return null
        fun line(tag: String, f: DriveApi.DriveFile) = "$tag|${f.id}|${f.name}|${f.md5Checksum}|${f.modifiedTime}"
        val lines = listOf("v|$appVersion") +
            backups.map { line("b", it) } +
            corrections.sortedBy { it.name }.map { line("c", it) } +
            settings.sortedBy { it.name }.map { line("s", it) }
        return MessageDigest.getInstance("SHA-256").digest(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * backupの中身を、一覧と同じ順に返す(E02-08)。写しがDriveと同じならそれを使い、違う・無いものだけ数本ずつ並べて落とす。
     * 1つでも落とせなければ[DownloadFailed](欠けたまま作り直さない)。
     */
    private suspend fun fetchAll(api: DriveApi, files: List<DriveApi.DriveFile>, copies: BackupCopies?): List<ByteArray> {
        val gate = Semaphore(PARALLEL_DOWNLOADS)
        val downloaded = AtomicInteger()
        val contents = coroutineScope {
            files.map { file ->
                async {
                    copies?.read(file) ?: gate.withPermit {
                        downloaded.incrementAndGet()
                        try {
                            // 書いた直後で読めないことがあるので、少し待って読み直す(E02-06)
                            withRetry { api.download(file.id) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            throw DownloadFailed(file.name, e)
                        }
                    }.also { copies?.write(file.id, it) }
                }
            }.awaitAll()
        }
        Log.i(TAG, "backup ${files.size}件のうち${downloaded.get()}件を落とし、残りは写しから")
        return contents
    }

    /**
     * 元ファイルごとに、いちばん後のbackupだけを残す(E01-16)。順番は保つ。
     * 古い名前と新しい名前のbackupが両方あると、明細は先勝ちなので古い読み方が勝ってしまう。
     * @param backups 古い順
     */
    fun latestPerSource(backups: List<BackupReader.Backup>): List<BackupReader.Backup> {
        val lastIndex = backups.withIndex().associate { (i, b) -> b.sourceFileId to i }
        return backups.filterIndexed { i, b -> lastIndex[b.sourceFileId] == i }
    }

    /** processedにあるのに、どのbackupの元にもなっていないファイルの名前(E01-16)。 */
    fun missingBackups(processed: List<DriveApi.DriveFile>, backups: List<BackupReader.Backup>): List<String> {
        val backed = backups.map { it.sourceFileId }.toSet()
        return processed.filterNot { it.id in backed }.map { it.name }.sorted()
    }

    /**
     * Driveの中身から、Roomに入れるもの一式を組み立てる。
     *
     * @param backups 古い順(後から取り込んだものが後ろ)
     */
    fun build(
        backups: List<BackupReader.Backup>,
        corrections: List<Corrections.Entry>,
        settings: List<ItemEntity>,
        /** 明細のカテゴリ(E07-21)。生活費以外の種類は生活費から除く(前のE07-06の除く言葉を引き継いだもの) */
        categories: CategorySettings = CategorySettings.EMPTY,
    ): Snapshot {
        var dropped = 0

        // 取引は先勝ち、Metricは後勝ち(E01-11)。
        val transactions = LinkedHashMap<String, BankTransactionEntity>()
        val points = LinkedHashMap<String, MetricPointEntity>()
        // カードの請求の合計(E01-14)。銀行の引き落としと突き合わせる
        val statements = mutableListOf<CardPayments.Statement>()

        for (backup in backups) {
            when (val data = backup.data) {
                is ParsedData.Transactions -> {
                    data.rows.forEach { row ->
                        val category = categories.categoryOf(row.description)
                        val entity = BankTransactionEntity.from(row, backup.sourceFileId).copy(
                            category = category?.name,
                            categoryKind = category?.kind,
                            excludedFromSpending = category != null && !category.kind.isLiving,
                        )
                        if (transactions.putIfAbsent(entity.dedupKey, entity) != null) dropped++
                    }
                    val last = data.rows.maxOfOrNull { it.date }
                    if (data.statementTotal != null && last != null) statements += CardPayments.Statement(data.statementTotal, last)
                }
                is ParsedData.Metrics -> data.points.forEach { point ->
                    if (points.put(Deduplication.keyOf(point), MetricPointEntity.from(point)) != null) dropped++
                }
            }
        }

        // 銀行明細の残高から、口座ごとの残高の系列を足す(E02-07)
        AccountBalances.points(backups).forEach { point ->
            points[Deduplication.keyOf(point)] = MetricPointEntity.from(point)
        }

        // 補正を重ねる。由来を残すので Corrections.apply ではなくここで重ねる。
        for ((key, entry) in Corrections.effective(corrections)) {
            points[key] = MetricPointEntity(
                metricKey = entry.metricKey,
                date = entry.date,
                valueYen = entry.valueYen,
                origin = when (entry.kind) {
                    Corrections.Kind.OVERRIDE -> MetricOrigin.OVERRIDE
                    Corrections.Kind.MANUAL -> MetricOrigin.MANUAL
                },
            )
        }

        // カードの明細があれば、銀行の引き落としの行は生活費から除く(内訳はカードの明細が正)
        val cardPayments = CardPayments.matchedKeys(transactions.values, statements)
        for (key in cardPayments) {
            transactions[key] = transactions.getValue(key).copy(excludedFromSpending = true, cardPayment = true)
        }

        return Snapshot(
            items = withAutoMetrics(settings, points.values.map { it.metricKey }),
            points = points.values.toList(),
            transactions = transactions.values.toList(),
            droppedDuplicates = dropped,
            budgets = categories.budgets,
        )
    }

    /**
     * settingsの項目に、まだ載っていないmetricKeyのMetric項目を足す(E02-01)。
     *
     * 自動で足した分はsettingsに書かない。人が名前を変えるなどしたときに
     * 初めて書く。こうしておけば作り直しても同じ一覧になる。
     */
    private fun withAutoMetrics(settings: List<ItemEntity>, metricKeys: List<String>): List<ItemEntity> {
        val ids = settings.map { it.id }.toSet()
        val auto = metricKeys.distinct().sorted()
            // 「#」で始まる系列(ファンドごとの値。E01-17)は一覧の項目にしない
            .filterNot { it.startsWith("#") }
            .filter { Item.metricId(it) !in ids }
            .map { ItemEntity(id = Item.metricId(it), type = ItemType.METRIC, name = it, metricKey = it) }
        return settings + auto
    }
}

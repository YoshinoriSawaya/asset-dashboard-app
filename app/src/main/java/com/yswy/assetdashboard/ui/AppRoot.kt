package com.yswy.assetdashboard.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 画面遷移。
 *
 * ## ライブラリを使わず、ルート文字列のスタックで持つ
 * 画面はトップ・項目詳細・サマリー・補正の数枚で、深い階層も外部からの
 * 遷移(ディープリンク)も無い。Navigationライブラリを入れるほどではないので、
 * `"top"` `"item/<id>"` のような文字列を積むだけにした(CLAUDE.mdの
 * 「過度な抽象化を避ける」)。文字列なので回転しても`rememberSaveable`で残る。
 */
object Routes {
    const val TOP = "top"
    private const val ITEM = "item/"

    fun item(id: String) = ITEM + id

    /** `item/<id>` ならid、違えばnull。 */
    fun itemId(route: String): String? = route.removePrefix(ITEM).takeIf { route.startsWith(ITEM) }
}

@Composable
fun AppRoot(modifier: Modifier = Modifier, viewModel: DashboardViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    val stack = rememberSaveable(saver = routeStackSaver) { mutableStateListOf(Routes.TOP) }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    // 同意画面はActivityからしか出せないので、ViewModelの依頼をここで受ける。
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { viewModel.onConsentResult() }
    LaunchedEffect(state.consentRequest) {
        val intent = state.consentRequest ?: return@LaunchedEffect
        viewModel.consentLaunched()
        consentLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
    }

    val route = stack.last()
    val itemId = Routes.itemId(route)
    when {
        itemId != null -> ItemDetailScreen(
            overview = state.overviews.firstOrNull { it.item.id == itemId },
            onBack = { stack.removeAt(stack.lastIndex) },
            modifier = modifier,
        )
        else -> TopScreen(
            state = state,
            onSync = viewModel::sync,
            onOpenItem = { stack.add(Routes.item(it)) },
            modifier = modifier,
        )
    }
}

private val routeStackSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { mutableStateListOf(*it.toTypedArray()) },
)

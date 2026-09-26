package com.yswy.assetdashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 手動補正・手入力の画面(E03-04)。保存先はDriveのcorrections(E01-10)。
 *
 * @param fixedKey 詳細画面から来たときの系列。nullなら新しい系列を手入力で足す
 *   (現金など、CSVに載らない値)
 */
@Composable
fun CorrectionScreen(
    fixedKey: String?,
    initialDate: String,
    initialValue: String,
    saving: Boolean,
    onSave: (CorrectionForm.Result.Ok, (String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var key by rememberSaveable { mutableStateOf(fixedKey.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var value by rememberSaveable { mutableStateOf(initialValue) }
    var note by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 戻る") }
        Text(if (fixedKey == null) "手入力の系列を追加" else "値を補正", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Driveの corrections に保存します。元のCSVは書き換えません。" +
                "同じ日の補正は上書きされます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("系列") },
            enabled = fixedKey == null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = date,
            onValueChange = { date = it },
            label = { Text("日付(例: ${DateInput.EXAMPLE})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text("金額(円)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("メモ(任意)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            enabled = !saving,
            onClick = {
                when (val input = CorrectionForm.parse(key, date, value, note)) {
                    is CorrectionForm.Result.Invalid -> message = input.message
                    is CorrectionForm.Result.Ok -> {
                        message = "保存中..."
                        onSave(input) { error -> if (error == null) onBack() else message = error }
                    }
                }
            },
        ) { Text("保存") }

        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (it == "保存中...") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
        }
    }
}

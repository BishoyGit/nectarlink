// SPDX-License-Identifier: GPL-3.0-or-later
package app.nectarlink.android.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nectarlink.android.R
import app.nectarlink.android.core.Core
import app.nectarlink.android.core.CoreState
import app.nectarlink.android.core.CoreStatus
import app.nectarlink.android.core.LocalNetwork
import app.nectarlink.android.core.PairingState
import app.nectarlink.android.ui.pairing.PairingActions
import app.nectarlink.android.ui.pairing.PairingScreen
import app.nectarlink.core.Transfer
import app.nectarlink.core.TransferDirection
import app.nectarlink.core.TransferStatus

@Composable
internal fun TransferOnlyApp(core: Core, state: CoreState) {
    var pairingOpen by rememberSaveable { mutableStateOf(false) }
    var selectedDeviceId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.devices) {
        if (state.devices.none { it.id == selectedDeviceId }) {
            selectedDeviceId = state.devices.firstOrNull()?.id
        }
    }

    val pairingActions = remember(core) {
        object : PairingActions {
            override fun visible(open: Boolean) = core.setPairingScreenOpen(open)
            override fun join(link: String) = core.joinWithLink(link)
            override fun pairNearby(id: String) = core.pairNearby(id)
            override fun confirm(matches: Boolean) = core.confirmCode(matches)
            override fun reset() = core.resetPairing()
            override suspend fun showCode(): String? = core.pairingLink()
        }
    }

    val filesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        selectedDeviceId?.let { core.sendFiles(it, uris) }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) selectedDeviceId?.let { core.sendFolder(it, uri) }
    }
    val sharedFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            core.addSafFolder(uri)
            selectedDeviceId?.let { core.setStorageEnabled(it, true) }
        }
    }
    val context = LocalContext.current
    val localNetworkPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        core.refreshNotificationAccess()
        if (!granted) {
            runCatching {
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            }
        }
    }

    BackHandler(enabled = pairingOpen || state.pairing != PairingState.Idle) {
        core.resetPairing()
        pairingOpen = false
    }

    if (pairingOpen || state.pairing != PairingState.Idle) {
        PairingScreen(
            state = state,
            actions = pairingActions,
            cancellable = pairingOpen || state.devices.isNotEmpty(),
            onDone = { pairingOpen = false },
        )
        return
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            when (val status = state.status) {
                CoreStatus.Starting -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.transfer_only_starting))
                }
                is CoreStatus.Failed -> Text(
                    stringResource(R.string.core_failed, status.message),
                    color = MaterialTheme.colorScheme.error,
                )
                is CoreStatus.Ready -> Unit
            }

            Text(stringResource(R.string.transfer_only_devices), style = MaterialTheme.typography.titleMedium)
            if (!state.localNetwork) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.local_network_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.local_network_text), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { localNetworkPermission.launch(LocalNetwork.PERMISSION) }) {
                            Text(stringResource(R.string.action_allow))
                        }
                    }
                }
            }
            if (state.devices.isEmpty()) {
                Text(stringResource(R.string.transfer_only_pair_prompt))
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.devices.forEach { device ->
                        val label = stringResource(
                            if (device.online) R.string.transfer_only_connected else R.string.transfer_only_offline,
                            device.name,
                        )
                        if (selectedDeviceId == device.id) {
                            Button(onClick = { selectedDeviceId = device.id }) { Text(label) }
                        } else {
                            OutlinedButton(onClick = { selectedDeviceId = device.id }) { Text(label) }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = selectedDeviceId != null,
                    onClick = { filesPicker.launch(arrayOf("*/*")) },
                ) { Text(stringResource(R.string.action_send_files)) }
                OutlinedButton(
                    enabled = selectedDeviceId != null,
                    onClick = { folderPicker.launch(null) },
                ) { Text(stringResource(R.string.action_send_folder)) }
                TextButton(onClick = { pairingOpen = true }) { Text(stringResource(R.string.action_pair)) }
            }

            state.device(selectedDeviceId.orEmpty())?.let { selectedDevice ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.transfer_only_shared_folders), style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (state.storageSafFolders.isEmpty()) {
                                stringResource(R.string.transfer_only_folder_hint)
                            } else {
                                state.storageSafFolders.joinToString()
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = selectedDevice.storageEnabled,
                        onCheckedChange = { core.setStorageEnabled(selectedDevice.id, it) },
                        enabled = state.storageSafFolders.isNotEmpty(),
                    )
                }
                OutlinedButton(
                    enabled = selectedDeviceId != null,
                    onClick = { sharedFolderPicker.launch(null) },
                ) { Text(stringResource(R.string.transfer_only_choose_folders)) }
            }

            Text(stringResource(R.string.transfer_only_transfers), style = MaterialTheme.typography.titleMedium)
            if (state.transfers.isEmpty()) {
                Text(stringResource(R.string.transfer_only_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.transfers, key = { it.id }) { transfer ->
                        TransferCard(
                            transfer = transfer,
                            deviceName = state.nameOf(transfer.deviceId).orEmpty(),
                            onAccept = { core.acceptTransfer(transfer.id) },
                            onCancel = { core.cancelTransfer(transfer.id) },
                            onPause = { core.pauseTransfer(transfer.id) },
                            onResume = { core.resumeTransfer(transfer.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferCard(
    transfer: Transfer,
    deviceName: String,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val statusText = when (transfer.status) {
        is TransferStatus.Requested -> stringResource(R.string.transfer_only_waiting_approval)
        is TransferStatus.Waiting -> stringResource(R.string.transfer_only_waiting)
        is TransferStatus.Running -> stringResource(R.string.transfer_only_transferring)
        is TransferStatus.Paused -> stringResource(R.string.transfer_only_paused)
        is TransferStatus.Done -> stringResource(R.string.transfer_only_complete)
        is TransferStatus.Cancelled -> stringResource(R.string.transfer_cancelled)
        is TransferStatus.Failed -> stringResource(R.string.transfer_only_failed)
    }
    val total = transfer.total.toLong().coerceAtLeast(0L)
    val done = transfer.done.toLong().coerceAtLeast(0L)
    val progress = if (total > 0L) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val active = transfer.status is TransferStatus.Running ||
        transfer.status is TransferStatus.Waiting ||
        transfer.status is TransferStatus.Paused
    val title = transfer.names.firstOrNull().orEmpty().ifBlank { "File transfer" }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(
                    if (transfer.direction == TransferDirection.INCOMING) R.string.transfer_only_from else R.string.transfer_only_to,
                    deviceName,
                    statusText,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (active) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(
                        R.string.transfer_only_progress,
                        (progress * 100).toInt(),
                        formatBytes(done),
                        formatBytes(total),
                    ),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (transfer.status is TransferStatus.Requested && transfer.direction == TransferDirection.INCOMING) {
                    Button(onClick = onAccept) { Text(stringResource(R.string.action_accept)) }
                }
                if (transfer.status is TransferStatus.Paused) {
                    OutlinedButton(onClick = onResume) { Text(stringResource(R.string.transfer_resume)) }
                } else if (transfer.status is TransferStatus.Running) {
                    OutlinedButton(onClick = onPause) { Text(stringResource(R.string.transfer_pause)) }
                }
                if (active || transfer.status is TransferStatus.Requested) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    do {
        value /= 1024.0
        unit++
    } while (value >= 1024 && unit < units.lastIndex)
    return "%.1f %s".format(value, units[unit])
}

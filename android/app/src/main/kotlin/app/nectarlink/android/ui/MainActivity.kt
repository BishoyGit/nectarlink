// SPDX-License-Identifier: GPL-3.0-or-later
package app.nectarlink.android.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nectarlink.android.NectarlinkApplication
import app.nectarlink.android.R
import app.nectarlink.android.core.CoreStatus
import app.nectarlink.android.core.LocalNetwork
import app.nectarlink.android.service.ConnectionService
import app.nectarlink.android.ui.theme.NectarlinkTheme

class MainActivity : ComponentActivity() {
    private var pendingPairingLink by mutableStateOf<String?>(null)
    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val core = (application as NectarlinkApplication).core
        window.decorView.post {
            val wanted = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (LocalNetwork.needed()) add(LocalNetwork.PERMISSION)
            }.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (wanted.isNotEmpty()) permissions.launch(wanted.toTypedArray())
        }
        pendingPairingLink = savedInstanceState?.getString(STATE_PENDING_PAIRING_LINK)
            ?: intent?.let(::pairingLink)

        setContent {
            val state by core.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.devices.isNotEmpty(), state.status is CoreStatus.Ready) {
                if (state.devices.isNotEmpty()) {
                    ConnectionService.start(this@MainActivity)
                } else if (state.status is CoreStatus.Ready) {
                    ConnectionService.stop(this@MainActivity)
                }
            }
            NectarlinkTheme(backdrop = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TransferOnlyApp(core, state)
                    pendingPairingLink?.let { link ->
                        val pcName = android.net.Uri.parse(link).getQueryParameter("n")?.trim()
                            ?.takeIf(String::isNotEmpty)
                            ?: stringResource(R.string.pair_link_unknown_pc)
                        val clearPending = {
                            pendingPairingLink = null
                            if (intent?.action == Intent.ACTION_VIEW) {
                                setIntent(Intent(this@MainActivity, MainActivity::class.java))
                            }
                        }
                        AlertDialog(
                            onDismissRequest = clearPending,
                            title = { Text(stringResource(R.string.pair_link_title, pcName)) },
                            text = { Text(stringResource(R.string.pair_link_body)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    clearPending()
                                    if (PairingLinks(this@MainActivity).consume(link)) {
                                        core.joinWhenReady(link)
                                    }
                                }) { Text(stringResource(R.string.pair_link_confirm)) }
                            },
                            dismissButton = {
                                TextButton(onClick = clearPending) {
                                    Text(stringResource(R.string.pair_link_cancel))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as NectarlinkApplication).core.apply {
            refreshNotificationAccess()
            reconnectIfOffline()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PENDING_PAIRING_LINK, pendingPairingLink)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPairingLink = pairingLink(intent)
    }

    private fun pairingLink(intent: Intent): String? {
        val link = intent.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return null
        if (!link.startsWith("nectarlink://pair?")) return null
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        return link.takeUnless { PairingLinks(this).isUsed(it) }
    }

    private companion object {
        const val STATE_PENDING_PAIRING_LINK = "pending_pairing_link"
    }
}

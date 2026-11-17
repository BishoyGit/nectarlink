// SPDX-License-Identifier: GPL-3.0-or-later
package app.nectarlink.android.clipboard

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import app.nectarlink.android.NectarlinkApplication
import app.nectarlink.android.R
import app.nectarlink.android.core.Device
import app.nectarlink.android.files.OutgoingFiles
import java.security.MessageDigest
import java.security.SecureRandom

class SendActivity : ComponentActivity() {
    private val pickFilesLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNullOrEmpty()) finish() else sendFiles(uris, handoff = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incoming = intent ?: return finish()
        if (incoming.action == ACTION_SEND_FILES) {
            if (!isTrustedInternalCall(incoming)) {
                finish()
                return
            }
            if (savedInstanceState == null) pickFilesLauncher.launch(arrayOf("*/*"))
            return
        }

        val streams = when (incoming.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                IntentCompat.getParcelableExtra(incoming, Intent.EXTRA_STREAM, Uri::class.java),
            )
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(
                incoming,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            ).orEmpty()
            else -> emptyList()
        }
        val allowed = allowedIncomingStreams(incoming, streams)
        if (allowed.isEmpty()) {
            finishWith(getString(R.string.transfer_nothing))
        } else {
            sendFiles(allowed, handoff = incoming.action == Intent.ACTION_SEND)
        }
    }

    private fun isTrustedInternalCall(incoming: Intent): Boolean {
        val expected = internalToken(this)
        val given = incoming.getStringExtra(EXTRA_INTERNAL_TOKEN)
        if (expected.isEmpty() || given.isNullOrEmpty()) return false
        if (!MessageDigest.isEqual(expected.toByteArray(), given.toByteArray())) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val uid = launchedFromUid
            if (uid != Process.myUid() && uid != Process.SYSTEM_UID && !isSystemUid(uid)) return false
        }
        return true
    }

    private fun isSystemUid(uid: Int): Boolean {
        if (uid <= 0 || uid == Process.SYSTEM_UID) return true
        return packageManager.getPackagesForUid(uid).orEmpty().any { pkg ->
            val info = runCatching { packageManager.getApplicationInfo(pkg, 0) }.getOrNull()
            info != null && (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        }
    }

    private fun allowedIncomingStreams(incoming: Intent, streams: List<Uri>): List<Uri> {
        val hasReadGrant = incoming.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
        val clipUris = buildSet {
            incoming.data?.let(::add)
            incoming.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) clip.getItemAt(i)?.uri?.let(::add)
            }
        }
        return filterGrantedStreamUris(
            hasReadGrantFlag = hasReadGrant,
            streams = streams,
            clipUris = clipUris,
            schemeOf = Uri::getScheme,
            authorityOf = Uri::getAuthority,
            appPackage = packageName,
        )
    }

    private fun sendFiles(uris: List<Uri>, handoff: Boolean) {
        val core = (application as NectarlinkApplication).core
        val requestedPc = intent?.getStringExtra(EXTRA_PC_ID)?.takeIf(String::isNotBlank)
        val chosen = requestedPc?.let { id -> core.state.value.device(id) }
        if (chosen != null) {
            sendFilesTo(chosen, uris, handoff)
            return
        }
        val devices = core.state.value.devices
        val available = devices.filter { it.online }.ifEmpty { devices }
        when (available.size) {
            0 -> finishWith(getString(R.string.clip_no_pc))
            1 -> sendFilesTo(available[0], uris, handoff)
            else -> AlertDialog.Builder(this)
                .setTitle(R.string.transfer_pick_pc)
                .setItems(available.map { it.name }.toTypedArray()) { _, which ->
                    sendFilesTo(available[which], uris, handoff)
                }
                .setOnCancelListener { finish() }
                .show()
        }
    }

    private fun sendFilesTo(pc: Device, uris: List<Uri>, handoff: Boolean) {
        (application as NectarlinkApplication).core.sendFiles(pc.id, uris, handoff)
        if (pc.online) finish() else finishWith(getString(R.string.send_queue_offline_toast, pc.name))
    }

    private fun finishWith(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val ACTION_SEND_FILES = "app.nectarlink.action.SEND_FILES"
        const val EXTRA_PC_ID = "app.nectarlink.extra.PC_ID"
        private const val EXTRA_INTERNAL_TOKEN = "app.nectarlink.extra.INTERNAL_TOKEN"
        private const val PREFS_INTERNAL = "send_activity_internal"
        private const val KEY_TOKEN = "token"

        internal fun <U> filterGrantedStreamUris(
            hasReadGrantFlag: Boolean,
            streams: List<U>,
            clipUris: Set<U>,
            schemeOf: (U) -> String?,
            authorityOf: (U) -> String?,
            appPackage: String,
        ): List<U> {
            if (!hasReadGrantFlag) return emptyList()
            return streams.filter { uri ->
                uri in clipUris && OutgoingFiles.isAllowedShareUri(schemeOf(uri), authorityOf(uri), appPackage)
            }
        }

        @Synchronized
        private fun internalToken(context: Context): String {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_INTERNAL, Context.MODE_PRIVATE)
            val existing = prefs.getString(KEY_TOKEN, null)
            if (!existing.isNullOrEmpty()) return existing
            val bytes = ByteArray(24)
            SecureRandom().nextBytes(bytes)
            val generated = bytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(KEY_TOKEN, generated).commit()
            return generated
        }

        fun sendFilesIntent(context: Context, pcId: String? = null): Intent =
            Intent(context, SendActivity::class.java)
                .setAction(ACTION_SEND_FILES)
                .putExtra(EXTRA_INTERNAL_TOKEN, internalToken(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .apply {
                    if (!pcId.isNullOrEmpty()) putExtra(EXTRA_PC_ID, pcId)
                }
    }
}

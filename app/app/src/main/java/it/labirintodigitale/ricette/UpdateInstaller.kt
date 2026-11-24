package it.labirintodigitale.ricette

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

/**
 * Installs an app update with the system PackageInstaller (more reliable than opening the
 * APK file: Android reports the exact reason when the installation fails).
 */
object UpdateInstaller {

    /** False until the user allows this app to install apps ("Installa app sconosciute"). */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user allows this app to install apps. */
    fun openPermissionSettings(ctx: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }

    /** Version code of the APK in [file], or null if it is not a valid APK of this app. */
    fun apkVersion(ctx: Context, file: File): Long? {
        val info = ctx.packageManager.getPackageArchiveInfo(file.path, 0) ?: return null
        if (info.packageName != ctx.packageName) return null
        return PackageInfoCompat.getLongVersionCode(info)
    }

    /** Starts the installation of [apk]; the result arrives in [InstallReceiver]. */
    fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("LabirintoRicette.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, InstallReceiver::class.java).setPackage(ctx.packageName), flags,
            )
            session.commit(pending.intentSender)
        }
    }
}

/** Result of the installation: asks the user to confirm, or shows why it failed. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { ctx.startActivity(it) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // l'app viene riavviata dal sistema
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit // annullato dall'utente
            else -> {
                val reason = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Toast.makeText(ctx, ctx.getString(R.string.update_failed) + " " + reason, Toast.LENGTH_LONG).show()
            }
        }
    }
}

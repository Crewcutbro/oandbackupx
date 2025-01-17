/*
 * OAndBackupX: open-source apps backup and restore app.
 * Copyright (C) 2020  Antonios Hazim
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.machiav3lli.backup.handler

import android.content.Context
import android.content.pm.PackageManager
import com.machiav3lli.backup.BuildConfig
import com.machiav3lli.backup.HousekeepingMoment
import com.machiav3lli.backup.HousekeepingMoment.Companion.fromString
import com.machiav3lli.backup.MODE_APK
import com.machiav3lli.backup.MODE_DATA
import com.machiav3lli.backup.OABX
import com.machiav3lli.backup.PREFS_HOUSEKEEPING_MOMENT
import com.machiav3lli.backup.PREFS_NUM_BACKUP_REVISIONS
import com.machiav3lli.backup.actions.BackupAppAction
import com.machiav3lli.backup.actions.BackupSpecialAction
import com.machiav3lli.backup.actions.RestoreAppAction
import com.machiav3lli.backup.actions.RestoreSpecialAction
import com.machiav3lli.backup.actions.RestoreSystemAppAction
import com.machiav3lli.backup.dbs.entity.Backup
import com.machiav3lli.backup.handler.ShellHandler.ShellCommandFailedException
import com.machiav3lli.backup.items.ActionResult
import com.machiav3lli.backup.items.Package
import com.machiav3lli.backup.items.StorageFile
import com.machiav3lli.backup.items.StorageFile.Companion.invalidateCache
import com.machiav3lli.backup.tasks.AppActionWork
import com.machiav3lli.backup.utils.FileUtils.BackupLocationInAccessibleException
import com.machiav3lli.backup.utils.StorageLocationNotConfiguredException
import com.machiav3lli.backup.utils.getBackupDir
import com.machiav3lli.backup.utils.suCopyFileToDocument
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.IOException

object BackupRestoreHelper {

    fun backup(
        context: Context,
        work: AppActionWork?,
        shell: ShellHandler,
        packageItem: Package,
        backupMode: Int
    ): ActionResult {
        var reBackupMode = backupMode
        val housekeepingWhen = fromString(
            OABX.prefString(PREFS_HOUSEKEEPING_MOMENT, HousekeepingMoment.AFTER.value)
                ?: HousekeepingMoment.AFTER.value
        )
        if (housekeepingWhen == HousekeepingMoment.BEFORE) {
            housekeepingPackageBackups(packageItem, true)
        }
        // Select and prepare the action to use
        val action: BackupAppAction = when {
            packageItem.isSpecial -> {
                if (reBackupMode and MODE_APK == MODE_APK) {
                    Timber.e("[${packageItem.packageName}] Special Backup called with MODE_APK or MODE_BOTH. Masking invalid settings.")
                    reBackupMode = reBackupMode and MODE_DATA
                    Timber.d("[${packageItem.packageName}] New backup mode: $reBackupMode")
                }
                BackupSpecialAction(context, work, shell)
            }
            else -> {
                BackupAppAction(context, work, shell)
            }
        }
        Timber.d("[${packageItem.packageName}] Using ${action.javaClass.simpleName} class")

        // create the new backup
        val result = action.run(packageItem, reBackupMode)

        if (result.succeeded)
            Timber.i("[${packageItem.packageName}] Backup succeeded: ${result.succeeded}")
        else {
            Timber.i("[${packageItem.packageName}] Backup FAILED: ${result.succeeded} ${result.message}")
        }

        if (housekeepingWhen == HousekeepingMoment.AFTER) {
            housekeepingPackageBackups(packageItem, false)
        }
        return result
    }

    fun restore(
        context: Context, work: AppActionWork?, shellHandler: ShellHandler, appInfo: Package,
        mode: Int, backupProperties: Backup, backupDir: StorageFile?
    ): ActionResult {
        val action: RestoreAppAction = when {
            appInfo.isSpecial -> RestoreSpecialAction(context, work, shellHandler)
            appInfo.isSystem -> RestoreSystemAppAction(context, work, shellHandler)
            else -> RestoreAppAction(context, work, shellHandler)
        }
        val result = action.run(appInfo, backupProperties, backupDir, mode)
        Timber.i("[${appInfo.packageName}] Restore succeeded: ${result.succeeded}")
        return result
    }

    @Throws(IOException::class)
    fun copySelfApk(context: Context, shell: ShellHandler): Boolean {
        val filename = BuildConfig.APPLICATION_ID + '-' + BuildConfig.VERSION_NAME + ".apk"
        try {
            val backupRoot = context.getBackupDir()
            val apkFile = backupRoot.findFile(filename)
            apkFile?.delete()
            try {
                val myInfo = context.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, 0)
                val fileInfos =
                    shell.suGetDetailedDirectoryContents(myInfo.applicationInfo.sourceDir, false)
                if (fileInfos.size != 1) {
                    throw FileNotFoundException("Could not find Neo Backup's own apk file")
                }
                suCopyFileToDocument(fileInfos[0], backupRoot)
                // Invalidating cache, otherwise the next call will fail
                // Can cost a lot time, but this function won't be run that often
                invalidateCache() //TODO hg42 how to filter only the apk? or eliminate the need to invalidate
                val baseApkFile = backupRoot.findFile(fileInfos[0].filename)
                if (baseApkFile != null) {
                    baseApkFile.renameTo(filename)
                } else {
                    Timber.e("Cannot find just created file '${fileInfos[0].filename}' in backup dir for renaming. Skipping")
                    return false
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.wtf("${e.javaClass.canonicalName}! This should never happen! Message: $e")
                return false
            } catch (e: ShellCommandFailedException) {
                throw IOException(e.shellResult.err.joinToString(" "), e)
            }
        } catch (e: StorageLocationNotConfiguredException) {
            Timber.e("${e.javaClass.simpleName}: $e")
            return false
        } catch (e: BackupLocationInAccessibleException) {
            Timber.e("${e.javaClass.simpleName}: $e")
            return false
        }
        return true
    }

    private fun housekeepingPackageBackups(app: Package, before: Boolean) {
        var numBackupRevisions =
            OABX.prefInt(PREFS_NUM_BACKUP_REVISIONS, 2)
        if (numBackupRevisions == 0) {
            Timber.i("[${app.packageName}] Infinite backup revisions configured. Not deleting any backup.")
            return
        }

        // If the backup is going to be created, reduce the number of backup revisions by one.
        // It's expected that the additional deleted backup will be created in the next moments.
        // HousekeepingMoment.AFTER does not need to change anything. If 2 backups are the limit,
        // 3 should exist and housekeeping will work fine without adjustments
        if (before) numBackupRevisions--

        app.deleteOldestBackups(numBackupRevisions)
    }

    enum class ActionType {
        BACKUP, RESTORE
    }
}
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
package com.machiav3lli.backup.viewmodels

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.machiav3lli.backup.R
import com.machiav3lli.backup.activities.PrefsActivityX
import com.machiav3lli.backup.handler.LogsHandler
import com.machiav3lli.backup.handler.showNotification
import com.machiav3lli.backup.items.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogViewModel(private val appContext: Application) : AndroidViewModel(appContext) {

    var logsList = MediatorLiveData<MutableList<Log>>()

    fun refreshList() {
        viewModelScope.launch {
            logsList.value = recreateLogsList()
        }
    }

    private suspend fun recreateLogsList(): MutableList<Log> = withContext(Dispatchers.IO) {
        LogsHandler(appContext).readLogs()
    }

    fun shareLog(log: Log) {
        viewModelScope.launch {
            share(log)
        }
    }

    private suspend fun share(log: Log) {
        withContext(Dispatchers.IO) {
            val shareFileIntent: Intent
            LogsHandler(appContext).getLogFile(log.logDate)?.let {
                if (it.exists()) {
                    shareFileIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, it.uri)
                        type = "text/plain"
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(shareFileIntent)
                } else {
                    showNotification(
                        appContext, PrefsActivityX::class.java, System.currentTimeMillis().toInt(),
                        appContext.getString(R.string.logs_share_failed), "", false
                    )
                }
            }
        }
    }

    fun deleteLog(log: Log) {
        viewModelScope.launch {
            delete(log)
            refreshList()
        }
    }

    private suspend fun delete(log: Log) {
        withContext(Dispatchers.IO) {
            log.delete(appContext)
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("unchecked_cast")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LogViewModel::class.java)) {
                return LogViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
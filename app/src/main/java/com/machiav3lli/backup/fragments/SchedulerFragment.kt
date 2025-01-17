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
package com.machiav3lli.backup.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.machiav3lli.backup.MAIN_FILTER_DEFAULT
import com.machiav3lli.backup.R
import com.machiav3lli.backup.dbs.ODatabase
import com.machiav3lli.backup.dbs.entity.Schedule
import com.machiav3lli.backup.dialogs.PackagesListDialogFragment
import com.machiav3lli.backup.ui.compose.item.ElevatedActionButton
import com.machiav3lli.backup.ui.compose.item.TopBar
import com.machiav3lli.backup.ui.compose.item.TopBarButton
import com.machiav3lli.backup.ui.compose.recycler.ScheduleRecycler
import com.machiav3lli.backup.ui.compose.theme.AppTheme
import com.machiav3lli.backup.utils.specialBackupsEnabled
import com.machiav3lli.backup.viewmodels.SchedulerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SchedulerFragment : NavigationFragment() {
    private var sheetSchedule: ScheduleSheet? = null
    private lateinit var viewModel: SchedulerViewModel
    private lateinit var database: ODatabase

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreate(savedInstanceState)
        database = ODatabase.getInstance(requireContext())
        val dataSource = database.scheduleDao
        val viewModelFactory = SchedulerViewModel.Factory(dataSource, requireActivity().application)
        viewModel = ViewModelProvider(this, viewModelFactory)[SchedulerViewModel::class.java]

        return ComposeView(requireContext()).apply {
            setContent { SchedulerPage() }
        }
    }

    override fun updateProgress(progress: Int, max: Int) {
        viewModel.progress.postValue(Pair(true, progress.toFloat() / max.toFloat()))
    }

    override fun hideProgress() {
        viewModel.progress.postValue(Pair(false, 0f))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SchedulerPage() {
        val schedules by viewModel.schedules.observeAsState(null)
        val progress by viewModel.progress.observeAsState(Pair(false, 0f))

        AppTheme(
            darkTheme = isSystemInDarkTheme()
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopBar(title = stringResource(id = R.string.sched_title)) {
                        TopBarButton(
                            icon = painterResource(id = R.drawable.ic_blocklist),
                            description = stringResource(id = R.string.sched_blocklist),
                            onClick = {
                                GlobalScope.launch(Dispatchers.IO) {
                                    val blocklistedPackages =
                                        requireMainActivity().viewModel.blocklist.value
                                            ?.mapNotNull { it.packageName }.orEmpty()

                                    PackagesListDialogFragment(
                                        blocklistedPackages,
                                        MAIN_FILTER_DEFAULT,
                                        true
                                    ) { newList: Set<String> ->
                                        requireMainActivity().viewModel.updateBlocklist(
                                            newList
                                        )
                                    }.show(
                                        requireActivity().supportFragmentManager,
                                        "BLOCKLIST_DIALOG"
                                    )
                                }
                            }
                        )
                    }
                }
            ) { paddingValues ->
                Column {
                    AnimatedVisibility(visible = progress?.first == true) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = progress.second
                        )
                    }
                    ScheduleRecycler(
                        modifier = Modifier
                            .padding(paddingValues)
                            .weight(1f)
                            .fillMaxWidth(),
                        productsList = schedules,
                        onClick = { item ->
                            if (sheetSchedule != null) sheetSchedule?.dismissAllowingStateLoss()
                            sheetSchedule = ScheduleSheet(item.id)
                            sheetSchedule?.showNow(
                                requireActivity().supportFragmentManager,
                                "Schedule ${item.id}"
                            )
                        },
                        onCheckChanged = { item: Schedule, b: Boolean ->
                            item.enabled = b
                            viewModel.updateSchedule(item, true)
                        }
                    )
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp)
                    ) {
                        ElevatedActionButton(
                            text = stringResource(id = R.string.sched_add),
                            modifier = Modifier.fillMaxWidth(),
                            fullWidth = true,
                            icon = painterResource(id = R.drawable.ic_add_sched)
                        ) {
                            viewModel.addSchedule(requireContext().specialBackupsEnabled)
                        }
                    }
                }
            }
        }
    }
}
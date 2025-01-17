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
package com.machiav3lli.backup.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.machiav3lli.backup.OABX
import com.machiav3lli.backup.PREFS_FIRST_LAUNCH
import com.machiav3lli.backup.PREFS_IGNORE_BATTERY_OPTIMIZATION
import com.machiav3lli.backup.R
import com.machiav3lli.backup.classAddress
import com.machiav3lli.backup.databinding.ActivitySplashBinding
import com.machiav3lli.backup.ui.compose.item.ElevatedActionButton
import com.machiav3lli.backup.ui.compose.theme.AppTheme
import com.machiav3lli.backup.utils.checkCallLogsPermission
import com.machiav3lli.backup.utils.checkContactsPermission
import com.machiav3lli.backup.utils.checkRootAccess
import com.machiav3lli.backup.utils.checkSMSMMSPermission
import com.machiav3lli.backup.utils.checkUsageStatsPermission
import com.machiav3lli.backup.utils.hasStoragePermissions
import com.machiav3lli.backup.utils.isStorageDirSetAndOk
import com.machiav3lli.backup.utils.setCustomTheme
import com.topjohnwu.superuser.Shell
import kotlin.system.exitProcess


@Preview
@Composable
fun DefaultPreview() {
    RootMissing()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootMissing(activity: Activity? = null) {
    AppTheme(
        darkTheme = isSystemInDarkTheme()
    ) {
        Scaffold { paddingValues ->
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(50.dp)
            ) {
                Text(
                    text = stringResource(R.string.root_missing),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(40.dp))
                Text(
                    text = stringResource(R.string.root_is_mandatory),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(40.dp))
                Text(
                    text = stringResource(R.string.see_faq),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(80.dp))
                ElevatedActionButton(
                    text = stringResource(id = R.string.dialogOK),
                    icon = painterResource(id = R.drawable.ic_issue),
                    fullWidth = true,
                    modifier = Modifier
                ) {
                    activity?.finishAffinity()
                    exitProcess(0)
                }
            }
        }
    }
}


class SplashActivity : BaseActivity() {
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        OABX.activity = this
        setCustomTheme()
        super.onCreate(savedInstanceState)
        Shell.getShell()
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val powerManager = this.getSystemService(POWER_SERVICE) as PowerManager

        if (!checkRootAccess()) {
            setContent {
                RootMissing(this)
            }
            return
        }

        val introIntent = Intent(applicationContext, IntroActivityX::class.java)
        if (OABX.prefFlag(PREFS_FIRST_LAUNCH, true)) {
            startActivity(introIntent)
        } else if (hasStoragePermissions &&
            isStorageDirSetAndOk &&
            checkSMSMMSPermission &&
            checkCallLogsPermission &&
            checkContactsPermission &&
            checkUsageStatsPermission &&
            (OABX.prefFlag(PREFS_IGNORE_BATTERY_OPTIMIZATION, false)
                    || powerManager.isIgnoringBatteryOptimizations(packageName))
        ) {
            introIntent.putExtra(classAddress(".fragmentNumber"), 3)
            startActivity(introIntent)
        } else {
            introIntent.putExtra(classAddress(".fragmentNumber"), 2)
            startActivity(introIntent)
        }
        overridePendingTransition(0, 0)
        finish()
    }

    override fun onResume() {
        super.onResume()
        OABX.activity = this
    }
}
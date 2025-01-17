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
package com.machiav3lli.backup.dbs.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.machiav3lli.backup.dbs.entity.Backup
import com.machiav3lli.backup.items.Package

@Dao
interface BackupDao : BaseDao<Backup> {
    @Query("SELECT COUNT(*) FROM backup")
    fun count(): Long

    @get:Query("SELECT * FROM backup ORDER BY packageName ASC")
    val all: MutableList<Backup>

    @get:Query("SELECT * FROM backup ORDER BY packageName ASC")
    val allLive: LiveData<MutableList<Backup>>

    @Query("SELECT * FROM backup WHERE packageName = :packageName")
    fun get(packageName: String): MutableList<Backup>

    @Query("SELECT * FROM backup WHERE packageName = :packageName")
    fun getLive(packageName: String): LiveData<List<Backup>>

    @Query("DELETE FROM backup")
    fun emptyTable()

    @Query("DELETE FROM backup WHERE packageName = :packageName")
    fun deleteAllOf(packageName: String)

    @Transaction
    fun updateList(packageItem: Package) {
        deleteAllOf(packageItem.packageName)
        insert(*packageItem.backupsNewestFirst.toTypedArray())
    }

    @Transaction
    fun updateList(vararg backups: Backup) {
        emptyTable()
        replaceInsert(*backups)
    }
}
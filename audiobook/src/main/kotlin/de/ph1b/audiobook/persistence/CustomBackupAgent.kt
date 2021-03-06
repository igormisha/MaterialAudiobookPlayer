package de.ph1b.audiobook.persistence

import android.app.backup.BackupAgentHelper
import android.app.backup.SharedPreferencesBackupHelper

import de.ph1b.audiobook.R

class CustomBackupAgent : BackupAgentHelper() {

    private val BACKUP_KEY = "BACKUP_KEY"

    override fun onCreate() {
        val resumeOnReplug = getString(R.string.pref_key_resume_on_replug)
        val seekTime = getString(R.string.pref_key_seek_time)
        val sleepTime = getString(R.string.pref_key_sleep_time)
        val theme = getString(R.string.pref_key_theme)
        val pauseOnCanDuck = getString(R.string.pref_key_pause_on_can_duck)
        val autoBugReportEnable = "acra.enable"
        val autoRewind = getString(R.string.pref_key_auto_rewind)
        val bookmarkOnSleep = getString(R.string.pref_key_bookmark_on_sleep)

        val helper = SharedPreferencesBackupHelper(this,
                resumeOnReplug, seekTime, sleepTime, theme, pauseOnCanDuck, autoBugReportEnable,
                autoRewind, bookmarkOnSleep)
        addHelper(BACKUP_KEY, helper)
    }
}

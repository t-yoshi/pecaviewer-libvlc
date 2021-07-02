/*
 * *************************************************************************
 *  PreferencesAdvanced.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.preferences

import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.AndroidDevices
import org.videolan.resources.KEY_AUDIO_LAST_PLAYLIST
import org.videolan.resources.KEY_MEDIA_LAST_PLAYLIST
import org.videolan.resources.VLCInstance
import org.videolan.tools.BitmapCache
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.R
import org.videolan.vlc.gui.DebugLogActivity
import org.videolan.vlc.gui.dialogs.ConfirmDeleteDialog
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.getWritePermission
import org.videolan.vlc.util.FeatureFlag
import org.videolan.vlc.util.FileUtils
import java.io.File
import java.io.IOException

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class PreferencesAdvanced : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun getXml() =  R.xml.preferences_adv

    override fun getTitleId(): Int {
        return R.string.advanced_prefs_category
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) findPreference<Preference>("debug_logs")?.isVisible = false
        if (BuildConfig.DEBUG && FeatureFlag.values().isNotEmpty()) findPreference<Preference>("optional_features")?.isVisible = true
    }

    override fun onStart() {
        super.onStart()
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        preferenceScreen.sharedPreferences
                .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == null)
            return false
        when (preference.key) {
            "debug_logs" -> {
                val intent = Intent(requireContext(), DebugLogActivity::class.java)
                startActivity(intent)
                return true
            }
            "clear_history" -> {
                AlertDialog.Builder(requireContext())
                        .setTitle(R.string.clear_playback_history)
                        .setMessage(R.string.validation)
                        .setIcon(R.drawable.ic_warning)
                        .setPositiveButton(R.string.yes) { _, _ ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                Medialibrary.getInstance().clearHistory()
                                Settings.getInstance(requireActivity()).edit().remove(KEY_AUDIO_LAST_PLAYLIST).remove(KEY_MEDIA_LAST_PLAYLIST).apply()
                            }
                        }

                        .setNegativeButton(R.string.cancel, null).show()
                return true
            }
            "clear_media_db" -> {
                val dialog = ConfirmDeleteDialog.newInstance(title = getString(R.string.clear_media_db), description = getString(R.string.clear_media_db_message), buttonText = getString(R.string.clear))
                dialog.show(requireActivity().supportFragmentManager, RenameDialog::class.simpleName)
                dialog.setListener {
                    lifecycleScope.launch {
                        val medialibrary = Medialibrary.getInstance()
                        requireActivity().stopService(Intent(requireActivity(), MediaParsingService::class.java))
                        withContext((Dispatchers.IO)) {
                            medialibrary.clearDatabase(false)
                            //delete thumbnails
                            try {
                                requireActivity().getExternalFilesDir(null)?. let {
                                    val files = File(it.absolutePath + Medialibrary.MEDIALIB_FOLDER_NAME).listFiles()
                                    files?.forEach { file ->
                                        if (file.isFile) FileUtils.deleteFile(file)
                                    }
                                }
                                BitmapCache.clear()
                            } catch (e: IOException) {
                                Log.e(this::class.java.simpleName, e.message, e)
                            }
                        }
                        medialibrary.discover(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
                    }
                }
                return true
            }
            "clear_app_data" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    val dialog = ConfirmDeleteDialog.newInstance(title = getString(R.string.clear_app_data), description = getString(R.string.clear_app_data_message), buttonText = getString(R.string.clear))
                    dialog.show(requireActivity().supportFragmentManager, RenameDialog::class.simpleName)
                    dialog.setListener { (requireActivity().getSystemService(ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData() }
                } else {
                    val i = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    i.addCategory(Intent.CATEGORY_DEFAULT)
                    i.data = Uri.parse("package:" + requireActivity().applicationContext.packageName)
                    startActivity(i)
                }
                return true
            }
            "quit_app" -> {
                android.os.Process.killProcess(android.os.Process.myPid())
                return true
            }
            "dump_media_db" -> {
                if (Medialibrary.getInstance().isWorking)
                    UiTools.snacker(requireActivity(), getString(R.string.settings_ml_block_scan))
                else {
                    val dst = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + Medialibrary.VLC_MEDIA_DB_NAME)
                    lifecycleScope.launch {
                        if (getWritePermission(Uri.fromFile(dst))) {
                            val copied = withContext(Dispatchers.IO) {
                                val db = File(requireContext().getDir("db", Context.MODE_PRIVATE).toString() + Medialibrary.VLC_MEDIA_DB_NAME)

                                FileUtils.copyFile(db, dst)
                            }
                            Toast.makeText(context, getString(if (copied) R.string.dump_db_succes else R.string.dump_db_failure), Toast.LENGTH_LONG).show()
                        }
                    }
                }
                return true
            }
            "optional_features" -> {
                loadFragment(PreferencesOptional())
                return true
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            "network_caching" -> {
                sharedPreferences.edit {
                    try {
                        putInt("network_caching_value", Integer.parseInt(sharedPreferences.getString(key, "0")!!))
                    } catch (e: NumberFormatException) {
                        putInt("network_caching_value", 0)
                        val networkCachingPref = findPreference<EditTextPreference>(key)
                        networkCachingPref?.text = ""
                        UiTools.snacker(requireActivity(), R.string.network_caching_popup)
                    }
                }
                restartLibVLC()
            }
            // No break because need VLCInstance.restart();
            "custom_libvlc_options" -> {
                try {
                    VLCInstance.restart()
                } catch (e: IllegalStateException){
                    UiTools.snacker(requireActivity(), R.string.custom_libvlc_options_invalid)
                    sharedPreferences.putSingle("custom_libvlc_options", "")
                } finally {
                    (activity as? PreferencesActivity)?.restartMediaPlayer()
                }
                restartLibVLC()
            }
            "opengl", "chroma_format", "deblocking", "enable_frame_skip", "enable_time_stretching_audio", "enable_verbose_mode" -> {
                VLCInstance.restart()
                (activity as? PreferencesActivity)?.restartMediaPlayer()
            }
            "prefer_smbv1" -> {
                VLCInstance.restart()
                UiTools.restartDialog(requireActivity())
            }
        }
    }
    fun restartLibVLC() {
        VLCInstance.restart()
        (activity as? PreferencesActivity)?.restartMediaPlayer()
    }
}

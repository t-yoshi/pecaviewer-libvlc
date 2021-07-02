/*
 * ************************************************************************
 *  FeatureFlag.kt
 * *************************************************************************
 * Copyright © 2021 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.StringRes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.R

/**
 * This manager allows the user to enable / disable experimental features
 */
@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
object FeatureFlagManager {

    fun isEnabled(context: Context, feature:FeatureFlag) = Settings.getInstance(context).getBoolean(feature.getKey(), false)
    fun enable(context: Context, feature:FeatureFlag, enabled:Boolean) = Settings.getInstance(context).putSingle(feature.getKey(), enabled)
    fun getByKey(key:String):FeatureFlag? = FeatureFlag.values().firstOrNull { it.getKey() == key }

}

/**
 * An experimental feature that can be disabled
 *
 * @param dependsOn: another feature this feature depends on
 * @param title: a string reference for this feature's title
 */
enum class FeatureFlag(var dependsOn:FeatureFlag?, @StringRes var title:Int) {
    NETWORK_INDEXATION(null, R.string.network_indexation);

    @SuppressLint("DefaultLocale")
    fun getKey() = "ff_${name.toLowerCase()}"
}
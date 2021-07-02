/*
 * ************************************************************************
 *  BookmarkListDelegate.kt
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

package org.videolan.vlc.gui.helpers

import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.appcompat.widget.ViewStubCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.Bookmark
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.viewmodels.BookmarkModel

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class BookmarkListDelegate(val activity: FragmentActivity, val service: PlaybackService, private val bookmarkModel: BookmarkModel) :
    LifecycleObserver, BookmarkAdapter.IBookmarkManager {

    lateinit var markerContainer: ConstraintLayout
    private lateinit var adapter: BookmarkAdapter
    lateinit var bookmarkList: RecyclerView
    lateinit var rootView: ConstraintLayout
    private lateinit var emptyView: View
    lateinit var visibilityListener: () -> Unit
    val visible: Boolean
        get() = rootView.visibility != View.GONE

    fun show() {
        activity.findViewById<ViewStubCompat>(R.id.bookmarks_stub)?.let {
            rootView = it.inflate() as ConstraintLayout
            bookmarkList = rootView.findViewById(R.id.bookmark_list)
            rootView.findViewById<ImageView>(R.id.close).setOnClickListener { hide() }
            rootView.findViewById<ImageView>(R.id.add_bookmark).setOnClickListener { bookmarkModel.addBookmark(activity) }
            rootView.findViewById<View>(R.id.top_bar).setOnTouchListener { v, event ->
                v.parent.requestDisallowInterceptTouchEvent(true)
                true
            }
            emptyView = rootView.findViewById(R.id.empty_view)
            service.lifecycle.addObserver(this)
            activity.lifecycle.addObserver(this)
            if (bookmarkList.layoutManager == null) bookmarkList.layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
            adapter = BookmarkAdapter(this)
            bookmarkList.adapter = adapter
            bookmarkList.itemAnimator = null

            bookmarkModel.dataset.observe(activity, { bookmarkList ->
                adapter.update(bookmarkList)
                markerContainer.removeAllViews()

                //show bookmark markers
                service.currentMediaWrapper?.length?.let {mediaLength ->
                    if (mediaLength < 1) return@let
                    val constraintSet = ConstraintSet()
                    constraintSet.clone(markerContainer)
                    bookmarkList.forEach { bookmark ->
                        val imageView = ImageView(activity)
                        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                        imageView.id = View.generateViewId()

                        val guidelineId = View.generateViewId()
                        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Adding guideline to: ${bookmark.time.toFloat() / mediaLength.toFloat()}")
                        constraintSet.create(guidelineId, ConstraintSet.VERTICAL_GUIDELINE)
                        constraintSet.setGuidelinePercent(guidelineId, bookmark.time.toFloat() / mediaLength.toFloat())
                        constraintSet.connect(imageView.id, ConstraintSet.START, guidelineId, ConstraintSet.START,0)
                        constraintSet.connect(imageView.id, ConstraintSet.END, guidelineId, ConstraintSet.END,0)
                        constraintSet.constrainWidth(imageView.id, ConstraintSet.WRAP_CONTENT)
                        constraintSet.constrainHeight(imageView.id, ConstraintSet.WRAP_CONTENT)
                        constraintSet.connect(imageView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                        constraintSet.connect(imageView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                        imageView.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.ic_bookmark_marker))
                        markerContainer.addView(imageView)
                    }
                    constraintSet.applyTo(markerContainer)
                }



                if (bookmarkList.isNotEmpty()) emptyView.setGone() else emptyView.setVisible()
            })
            bookmarkModel.refresh()
        }
        rootView.setVisible()
        markerContainer.setVisible()
        visibilityListener.invoke()
    }

    fun hide() {
        rootView.setGone()
        markerContainer.setGone()
        visibilityListener.invoke()
    }

    override fun onPopupMenu(view: View, position: Int, bookmark: Bookmark?) {
        if (bookmark == null) return
        val menu = PopupMenu(view.context, view)
        menu.inflate(R.menu.bookmark_options)
        menu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.bookmark_rename -> {
                    val dialog = RenameDialog.newInstance(bookmark)
                    dialog.show(activity.supportFragmentManager, RenameDialog::class.simpleName)
                    dialog.setListener { media, name ->
                        activity.lifecycleScope.launch {
                            bookmarkModel.rename(media as Bookmark, name)
                        }
                    }
                    true
                }
                R.id.bookmark_delete -> {
                    bookmarkModel.delete(bookmark)
                    true
                }
                else -> false
            }
        }
        menu.show()
    }

    override fun onBookmarkClick(position: Int, item: Bookmark) {
        service.time = item.time
    }

    fun setProgressHeight(y: Float) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(rootView)
        constraintSet.setGuidelineBegin(R.id.progressbar_guideline, y.toInt())
        constraintSet.applyTo(rootView)
    }
}
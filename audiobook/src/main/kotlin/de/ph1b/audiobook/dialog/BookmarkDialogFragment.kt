/*
 * This file is part of Material Audiobook Player.
 *
 * Material Audiobook Player is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 *
 * Material Audiobook Player is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Material Audiobook Player. If not, see <http://www.gnu.org/licenses/>.
 * /licenses/>.
 */

package de.ph1b.audiobook.dialog

import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import com.google.common.collect.ImmutableList
import com.google.common.collect.Ordering
import de.ph1b.audiobook.R
import de.ph1b.audiobook.adapter.BookmarkAdapter
import de.ph1b.audiobook.injection.App
import de.ph1b.audiobook.model.Book
import de.ph1b.audiobook.model.Bookmark
import de.ph1b.audiobook.persistence.BookChest
import de.ph1b.audiobook.persistence.PrefsManager
import de.ph1b.audiobook.playback.MediaPlayerController
import de.ph1b.audiobook.uitools.DividerItemDecoration
import de.ph1b.audiobook.utils.BookVendor
import timber.log.Timber
import java.util.*
import javax.inject.Inject

/**
 * Dialog for creating a bookmark

 * @author Paul Woitaschek
 */
class BookmarkDialogFragment : DialogFragment() {

    private lateinit var bookmarkTitle: EditText
    @Inject lateinit internal var prefs: PrefsManager
    @Inject lateinit internal var db: BookChest
    @Inject lateinit internal var bookVendor: BookVendor
    @Inject internal lateinit var mediaPlayerController: MediaPlayerController
    private lateinit var book: Book

    fun addClicked() {
        Timber.i("Add bookmark clicked.")
        var title = bookmarkTitle.text.toString()
        if (title.isEmpty()) {
            title = book.currentChapter().name
        }

        addBookmark(book.id, title, db)
        Toast.makeText(activity, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
        bookmarkTitle.setText("")
        dismiss()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        App.component().inject(this)

        val inflater = activity.layoutInflater
        val customView = inflater.inflate(R.layout.dialog_bookmark, null)
        bookmarkTitle = customView.findViewById(R.id.bookmarkEdit) as EditText

        val bookId = arguments.getLong(BOOK_ID)
        book = bookVendor.byId(bookId)!!

        // init later. Cyclic dependency here between adapter and listener.
        var adapter: BookmarkAdapter? = null
        val listener = object : BookmarkAdapter.OnOptionsMenuClickedListener {
            override fun onOptionsMenuClicked(bookmark: Bookmark, v: View) {
                val popup = PopupMenu(activity, v)
                popup.menuInflater.inflate(R.menu.bookmark_popup, popup.menu)
                popup.setOnMenuItemClickListener {
                    val builder = MaterialDialog.Builder(activity)
                    when (it.itemId) {
                        R.id.edit -> {
                            MaterialDialog.Builder(context)
                                    .title(R.string.bookmark_edit_title)
                                    .inputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
                                    .input(getString(R.string.bookmark_edit_hint), bookmark.title, false) {
                                        materialDialog, charSequence ->
                                        val newBookmark = Bookmark(bookmark.mediaFile, charSequence.toString(), bookmark.time)
                                        adapter!!.bookmarkUpdated(bookmark, newBookmark)

                                        // replaces the bookmark in the book
                                        val mutableBookmarks = ArrayList(book.bookmarks)
                                        mutableBookmarks[mutableBookmarks.indexOf(bookmark)] = newBookmark
                                        book = book.copy(bookmarks = ImmutableList.copyOf(mutableBookmarks))
                                        db.updateBook(book)
                                    }.positiveText(R.string.dialog_confirm).show()
                            return@setOnMenuItemClickListener true
                        }
                        R.id.delete -> {
                            builder.title(R.string.bookmark_delete_title)
                                    .content(bookmark.title)
                                    .positiveText(R.string.remove)
                                    .negativeText(R.string.dialog_cancel)
                                    .onPositive {
                                        materialDialog, dialogAction ->
                                        val mutableBookmarks = ArrayList(book.bookmarks)
                                        mutableBookmarks.remove(bookmark)
                                        book = book.copy(bookmarks = ImmutableList.copyOf(mutableBookmarks))
                                        adapter!!.removeItem(bookmark)
                                        db.updateBook(book)
                                    }.show()
                            return@setOnMenuItemClickListener true
                        }
                        else -> return@setOnMenuItemClickListener false
                    }
                }
                popup.show()
            }

            override fun onBookmarkClicked(bookmark: Bookmark) {
                prefs.setCurrentBookId(bookId)
                mediaPlayerController.changePosition(bookmark.time, bookmark.mediaFile)

                dialog.cancel()
            }
        }

        adapter = BookmarkAdapter(book.bookmarks, book.chapters, listener)
        val recyclerView = customView.findViewById(R.id.recycler) as RecyclerView
        recyclerView.adapter = adapter
        val layoutManager = LinearLayoutManager(activity)
        recyclerView.addItemDecoration(DividerItemDecoration(activity))
        recyclerView.layoutManager = layoutManager

        customView.findViewById(R.id.add).setOnClickListener { addClicked() }
        bookmarkTitle.setOnEditorActionListener { v1, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addClicked() //same as clicking on the +
                return@setOnEditorActionListener true
            }
            false
        }

        return MaterialDialog.Builder(activity)
                .customView(customView, false)
                .title(R.string.bookmark)
                .negativeText(R.string.dialog_cancel)
                .build()
    }

    companion object {

        val TAG = BookmarkDialogFragment::class.java.simpleName
        private val BOOK_ID = "bookId"

        fun newInstance(bookId: Long): BookmarkDialogFragment {
            val bookmarkDialogFragment = BookmarkDialogFragment()
            val args = Bundle()
            args.putLong(BookmarkDialogFragment.BOOK_ID, bookId)
            bookmarkDialogFragment.arguments = args
            return bookmarkDialogFragment
        }

        fun addBookmark(bookId: Long, title: String, db: BookChest) {
            var book: Book? = db.activeBooks
                    .toBlocking()
                    .singleOrDefault(null, { it.id == bookId })
            if (book != null) {
                val addedBookmark = Bookmark(book.currentChapter().file, title, book.time)
                val newBookmarks = ArrayList(book.bookmarks)
                newBookmarks.add(addedBookmark)
                book = book.copy(bookmarks = Ordering.natural<Bookmark>().immutableSortedCopy(newBookmarks))
                db.updateBook(book)
                Timber.v("Added bookmark=%s", addedBookmark)
            } else {
                Timber.e("Book does not exist")
            }
        }
    }
}
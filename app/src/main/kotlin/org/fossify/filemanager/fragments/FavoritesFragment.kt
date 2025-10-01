package org.fossify.filemanager.fragments

import android.content.Context
import android.util.AttributeSet
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.filemanager.activities.MainActivity
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.adapters.ItemsAdapter
import org.fossify.filemanager.databinding.FavoritesFragmentBinding
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.interfaces.ItemOperationsListener
import org.fossify.filemanager.models.ListItem
import java.io.File

class FavoritesFragment(context: Context, attributeSet: AttributeSet) :
    MyViewPagerFragment<MyViewPagerFragment.FavoritesInnerBinding>(context, attributeSet),
    ItemOperationsListener {

    private var storedFavorites = ArrayList<ListItem>()
    private var lastSearchedText = ""
    private lateinit var binding: FavoritesFragmentBinding
    private var filesIgnoringSearch = ArrayList<ListItem>()

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FavoritesFragmentBinding.bind(this)
        innerBinding = FavoritesInnerBinding(binding)
    }

    override fun setupFragment(activity: SimpleActivity) {
        if (this.activity == null) {
            this.activity = activity
            binding.apply {
                favoritesSwipeRefresh.setOnRefreshListener { refreshFragment() }
                favoritesFab.setOnClickListener {
                    FilePickerDialog(
                        activity = activity,
                        pickFile = false,
                        showHidden = activity.config.shouldShowHidden(),
                        canAddShowHiddenButton = true
                    ) { pickedPath ->
                        activity.config.addFavorite(pickedPath)
                        refreshFragment()
                    }
                }
            }
            refreshFragment()
        }
    }

    override fun onResume(textColor: Int) {
        binding.favoritesPlaceholder.setTextColor(textColor)

        getRecyclerAdapter()?.apply {
            updatePrimaryColor()
            updateTextColor(textColor)
            initDrawables()
        }

        binding.favoritesSwipeRefresh.isEnabled =
            lastSearchedText.isEmpty() && activity?.config?.enablePullToRefresh != false
    }

    override fun refreshFragment() {
        val favoritePaths = context?.config?.favorites ?: emptySet()
        val listItems = ArrayList<ListItem>().apply {
            favoritePaths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    add(
                        ListItem(
                            mPath = file.absolutePath,
                            mName = file.name,
                            mIsDirectory = file.isDirectory,
                            mChildren = if (file.isDirectory) file.list()?.size ?: 0 else 0,
                            mSize = if (file.isDirectory) 0 else file.length(),
                            mModified = file.lastModified(),
                            isSectionTitle = false,
                            isGridTypeDivider = false
                        )
                    )
                }
            }
        }
        addFavorites(listItems)
    }

    override fun deleteFiles(files: ArrayList<FileDirItem>) {
        files.forEach { file ->
            context?.config?.removeFavorite(file.path)
        }
        refreshFragment()
    }

    override fun columnCountChanged() {
        (binding.favoritesList.layoutManager as MyGridLayoutManager).spanCount =
            context!!.config.fileColumnCnt
        (activity as? MainActivity)?.refreshMenuItems()
        getRecyclerAdapter()?.apply {
            notifyItemRangeChanged(0, storedFavorites.size)
        }
    }

    override fun setupFontSize() {
        getRecyclerAdapter()?.updateFontSizes()
    }

    override fun setupDateTimeFormat() {
        getRecyclerAdapter()?.updateDateTimeFormat()
    }

    override fun toggleFilenameVisibility() {
        getRecyclerAdapter()?.updateDisplayFilenamesInGrid()
    }

    override fun searchQueryChanged(text: String) {
        lastSearchedText = text
        val normalizedText = text.normalizeString()
        val filtered = filesIgnoringSearch.filter {
            it.mName.normalizeString().contains(normalizedText, true)
        }.toMutableList() as ArrayList<ListItem>

        binding.apply {
            (favoritesList.adapter as? ItemsAdapter)?.updateItems(filtered, text)
            favoritesPlaceholder.beVisibleIf(filtered.isEmpty())
            favoritesSwipeRefresh.isEnabled =
                lastSearchedText.isEmpty() && activity?.config?.enablePullToRefresh != false
        }
    }

    private fun addFavorites(items: ArrayList<ListItem>) {
        activity?.runOnUiThread {
            binding.favoritesSwipeRefresh.isRefreshing = false
            storedFavorites = items
            filesIgnoringSearch = ArrayList(storedFavorites)

            ItemsAdapter(
                activity as SimpleActivity,
                storedFavorites,
                this,
                binding.favoritesList,
                isPickMultipleIntent,
                binding.favoritesSwipeRefresh
            ) {
                if (it is FileDirItem) {
                    if (it.isDirectory) {
                        (activity as? MainActivity)?.createFavorite()
                    }
                }
            }.apply {
                binding.favoritesList.adapter = this
            }

            binding.favoritesPlaceholder.beVisibleIf(storedFavorites.isEmpty())

            if (context.areSystemAnimationsEnabled) {
                binding.favoritesList.scheduleLayoutAnimation()
            }
        }
    }

    private fun getRecyclerAdapter() = binding.favoritesList.adapter as? ItemsAdapter

    override fun finishActMode() {
        getRecyclerAdapter()?.finishActMode()
    }

    override fun selectedPaths(paths: ArrayList<String>) {
        (activity as MainActivity).pickedPaths(paths)
    }
}

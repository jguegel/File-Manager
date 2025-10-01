package org.fossify.filemanager.fragments

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MyRecyclerView
import org.fossify.filemanager.activities.MainActivity
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.adapters.ItemsAdapter
import org.fossify.filemanager.databinding.FavoritesFragmentBinding
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.helpers.MAX_COLUMN_COUNT
import org.fossify.filemanager.interfaces.ItemOperationsListener
import org.fossify.filemanager.models.ListItem
import java.io.File

class FavoritesFragment(context: Context, attributeSet: AttributeSet) :
    MyViewPagerFragment<MyViewPagerFragment.FavoritesInnerBinding>(context, attributeSet),
    ItemOperationsListener {

    private var storedFavorites = ArrayList<ListItem>()
    private var lastSearchedText = ""
    private lateinit var binding: FavoritesFragmentBinding
    private var favoritesIgnoringSearch = ArrayList<ListItem>()
    private var zoomListener: MyRecyclerView.MyZoomListener? = null

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

        if (context != null && currentViewType != context!!.config.getFolderViewType("")) {
            setupLayoutManager()
        }

        binding.favoritesSwipeRefresh.isEnabled =
            lastSearchedText.isEmpty() && activity?.config?.enablePullToRefresh != false
    }

    override fun refreshFragment() {
        val favoritePaths = context?.config?.favorites ?: emptySet()
        val listFavorites = ArrayList<ListItem>().apply {
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
        addFavorites(listFavorites)
        setupLayoutManager()
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
        val filtered = favoritesIgnoringSearch.filter {
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
            favoritesIgnoringSearch = ArrayList(storedFavorites)

            val adapter = getRecyclerAdapter()
            if (adapter == null) {
                ItemsAdapter(
                    activity as SimpleActivity,
                    storedFavorites,
                    this,
                    binding.favoritesList,
                    isPickMultipleIntent,
                    binding.favoritesSwipeRefresh,
                    false
                ) { clickedItem ->
                    (clickedItem as? FileDirItem)?.let { fileDirItem ->
                        (activity as? MainActivity)?.openDirectoryInFilesTab(fileDirItem.path)
                    }
                }.apply {
                    setupZoomListener(zoomListener)
                    binding.favoritesList.adapter = this
                }
            } else {
                adapter.updateItems(items)
            }

            binding.favoritesPlaceholder.beVisibleIf(storedFavorites.isEmpty())

            if (context.areSystemAnimationsEnabled) {
                binding.favoritesList.scheduleLayoutAnimation()
            }
        }
    }

    private fun setupLayoutManager() {
        val viewType = context?.config?.getFolderViewType("") ?: VIEW_TYPE_GRID

        if (viewType != currentViewType) {
            currentViewType = viewType
            if (viewType == VIEW_TYPE_GRID) {
                setupGridLayoutManager()
            } else {
                setupListLayoutManager()
            }

            initZoomListener()
            val adapter = getRecyclerAdapter()
            if (adapter != null) {
                binding.favoritesList.adapter = null
                if (storedFavorites.isNotEmpty()) {
                    addFavorites(storedFavorites)
                }
            }
        } else {
            if (viewType == VIEW_TYPE_GRID) {
                setupGridLayoutManager()
            } else {
                setupListLayoutManager()
            }
            initZoomListener()
        }
    }

    private fun setupGridLayoutManager() {
        val layoutManager = binding.favoritesList.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = context?.config?.fileColumnCnt ?: 3

        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (getRecyclerAdapter()?.isASectionTitle(position) == true || getRecyclerAdapter()?.isGridTypeDivider(position) == true) {
                    layoutManager.spanCount
                } else {
                    1
                }
            }
        }
    }

    private fun setupListLayoutManager() {
        val layoutManager = binding.favoritesList.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        zoomListener = null
    }

    private fun initZoomListener() {
        val favoritesViewType = context?.config?.getFolderViewType("") ?: VIEW_TYPE_GRID

        if (favoritesViewType == VIEW_TYPE_GRID) {
            val layoutManager = binding.favoritesList.layoutManager as MyGridLayoutManager
            zoomListener = object : MyRecyclerView.MyZoomListener {
                override fun zoomIn() {
                    if (layoutManager.spanCount > 1) {
                        reduceColumnCount()
                        getRecyclerAdapter()?.finishActMode()
                    }
                }

                override fun zoomOut() {
                    if (layoutManager.spanCount < MAX_COLUMN_COUNT) {
                        increaseColumnCount()
                        getRecyclerAdapter()?.finishActMode()
                    }
                }
            }

            getRecyclerAdapter()?.setupZoomListener(zoomListener)
        } else {
            zoomListener = null
        }
    }

    private fun increaseColumnCount() {
        if (currentViewType == VIEW_TYPE_GRID) {
            context!!.config.fileColumnCnt += 1
            (activity as? MainActivity)?.updateFragmentColumnCounts()
        }
    }

    private fun reduceColumnCount() {
        if (currentViewType == VIEW_TYPE_GRID) {
            context!!.config.fileColumnCnt -= 1
            (activity as? MainActivity)?.updateFragmentColumnCounts()
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

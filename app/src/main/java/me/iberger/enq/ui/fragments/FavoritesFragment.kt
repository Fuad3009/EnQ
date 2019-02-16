package me.iberger.enq.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.annotation.ContentView
import androidx.recyclerview.widget.ItemTouchHelper
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.fastadapter.IInterceptor
import com.mikepenz.fastadapter.adapters.ModelAdapter
import com.mikepenz.fastadapter.swipe.SimpleSwipeCallback
import kotlinx.android.synthetic.main.fragment_queue.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import me.iberger.enq.R
import me.iberger.enq.ui.fragments.parents.SongListFragment
import me.iberger.enq.ui.items.SongItem
import me.iberger.enq.utils.changeFavoriteStatus
import me.iberger.enq.utils.icon
import me.iberger.enq.utils.loadFavorites
import me.iberger.jmusicbot.model.Song
import splitties.resources.color

@ContentView(R.layout.fragment_queue)
class FavoritesFragment : SongListFragment<SongItem>(), SimpleSwipeCallback.ItemSwipeCallback {

    override val songAdapter: ModelAdapter<Song, SongItem> by lazy {
        ModelAdapter(object : IInterceptor<Song, SongItem> {
            override fun intercept(element: Song): SongItem? = SongItem(element)
        })
    }
    override val isRemoveAfterEnQ = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val favorites = mBackgroundScope.async { loadFavorites(context!!) }
        mMainScope.launch { songAdapter.add(favorites.await()) }

        val swipeToDeleteIcon =
            context!!.icon(CommunityMaterial.Icon.cmd_delete).color(context!!.color(R.color.white)).sizeDp(24)
        val swipeToDeleteColor = context!!.color(R.color.delete)

        ItemTouchHelper(
            SimpleSwipeCallback(
                this, swipeToDeleteIcon, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, swipeToDeleteColor
            ).withLeaveBehindSwipeRight(swipeToDeleteIcon).withBackgroundSwipeRight(swipeToDeleteColor)
        ).attachToRecyclerView(recycler_queue)
    }

    override fun itemSwiped(position: Int, direction: Int) {
        val item = songAdapter.getAdapterItem(position)
        if (direction == ItemTouchHelper.RIGHT || direction == ItemTouchHelper.LEFT) {
            songAdapter.remove(position)
            changeFavoriteStatus(context!!, item.model)
        }
    }
}

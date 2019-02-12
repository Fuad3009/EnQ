package me.iberger.enq.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.annotation.ContentView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.adapters.FastItemAdapter
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil
import com.mikepenz.fastadapter.drag.ItemTouchCallback
import com.mikepenz.fastadapter.listeners.OnLongClickListener
import com.mikepenz.fastadapter.swipe.SimpleSwipeCallback
import com.mikepenz.fastadapter.swipe_drag.SimpleSwipeDragCallback
import com.mikepenz.fastadapter.utils.DragDropUtil
import kotlinx.android.synthetic.main.fragment_queue.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.iberger.enq.R
import me.iberger.enq.ui.MainActivity
import me.iberger.enq.ui.items.QueueItem
import me.iberger.enq.ui.viewmodel.MainViewModel
import me.iberger.enq.utils.changeFavoriteStatus
import me.iberger.enq.utils.icon
import me.iberger.enq.utils.toastShort
import me.iberger.jmusicbot.JMusicBot
import me.iberger.jmusicbot.KEY_QUEUE
import me.iberger.jmusicbot.exceptions.AuthException
import me.iberger.jmusicbot.model.Permissions
import me.iberger.jmusicbot.model.QueueEntry
import splitties.resources.color
import timber.log.Timber

@ContentView(R.layout.fragment_queue)
class QueueFragment : Fragment(), SimpleSwipeCallback.ItemSwipeCallback, ItemTouchCallback {
    companion object {
        fun newInstance() = QueueFragment()
    }

    private val mViewModel by lazy { ViewModelProviders.of(context as MainActivity).get(MainViewModel::class.java) }
    private val mMainScope = CoroutineScope(Dispatchers.Main)
    private val mBackgroundScope = CoroutineScope(Dispatchers.IO)

    private var mQueue = listOf<QueueEntry>()
    private val mFastItemAdapter: FastItemAdapter<QueueItem> by lazy { FastItemAdapter<QueueItem>() }

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("Creating Queue Fragment")
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mViewModel.queue.observe(this, Observer { updateQueue(it) })

        queue.layoutManager = LinearLayoutManager(context).apply { reverseLayout = true }
        queue.adapter = mFastItemAdapter
        savedInstanceState?.also { mFastItemAdapter.withSavedInstanceState(it, KEY_QUEUE) }

        val drawableRight =
            context!!.icon(CommunityMaterial.Icon2.cmd_star).color(context!!.color(R.color.white)).sizeDp(24)
        val drawableLeft =
            context!!.icon(CommunityMaterial.Icon.cmd_delete).color(context!!.color(R.color.white)).sizeDp(24)
        val userPermissions = JMusicBot.userPermissions
        val touchCallback = if (userPermissions.contains(Permissions.MOVE)) SimpleSwipeDragCallback(
            this, this,
            drawableLeft, ItemTouchHelper.LEFT, color(R.color.favorites)
        ) else SimpleSwipeCallback(this, drawableLeft, ItemTouchHelper.LEFT, color(R.color.favorites))

        if (userPermissions.contains(Permissions.SKIP)) if (touchCallback is SimpleSwipeCallback)
            touchCallback.withBackgroundSwipeRight(color(R.color.delete)).withLeaveBehindSwipeRight(drawableRight)
        else if (touchCallback is SimpleSwipeDragCallback)
            touchCallback.withBackgroundSwipeRight(color(R.color.delete)).withLeaveBehindSwipeRight(drawableRight)

        ItemTouchHelper(touchCallback).attachToRecyclerView(queue)

        mFastItemAdapter.onLongClickListener = object : OnLongClickListener<QueueItem> {
            override fun onLongClick(v: View, adapter: IAdapter<QueueItem>, item: QueueItem, position: Int): Boolean {
                mViewModel.queue.removeObservers(this@QueueFragment)
                return true
            }
        }
    }

    private fun updateQueue(newQueue: List<QueueEntry>) = mBackgroundScope.launch {
        if (newQueue == mQueue) return@launch
        Timber.d("Updating Queue")
        mQueue = newQueue
        val diff = FastAdapterDiffUtil.calculateDiff(
            mFastItemAdapter.itemAdapter,
            newQueue.map { QueueItem(it) },
            QueueItem.DiffCallback()
        )
        withContext(mMainScope.coroutineContext) { FastAdapterDiffUtil.set(mFastItemAdapter.itemAdapter, diff) }
    }

    override fun itemSwiped(position: Int, direction: Int) {
        mBackgroundScope.launch {
            val entry = mFastItemAdapter.getAdapterItem(position)
            when (direction) {
                ItemTouchHelper.RIGHT -> {
                    if (!mViewModel.connected) return@launch
                    try {
                        JMusicBot.dequeue(entry.song)
                    } catch (e: AuthException) {
                        Timber.e("AuthException with reason ${e.reason}")
                        withContext(Dispatchers.Main) {
                            context!!.toastShort(R.string.msg_no_permission)
                            mFastItemAdapter.notifyAdapterItemChanged(position)
                        }
                    }
                }
                ItemTouchHelper.LEFT -> {
                    changeFavoriteStatus(context!!, entry.song)
                    withContext(Dispatchers.Main) {
                        mFastItemAdapter.notifyAdapterItemChanged(position)
                    }
                }
            }
        }
    }

    override fun itemTouchOnMove(oldPosition: Int, newPosition: Int): Boolean {
        if (!mViewModel.connected) return false
        DragDropUtil.onMove(mFastItemAdapter.itemAdapter, oldPosition, newPosition)
        return true
    }

    override fun itemTouchDropped(oldPosition: Int, newPosition: Int) {
        if (!mViewModel.connected) return
        mBackgroundScope.launch {
            val entry = mFastItemAdapter.getAdapterItem(newPosition).model
            Timber.d("Moved ${entry.song.title} from $oldPosition to $newPosition")
            try {
                JMusicBot.moveSong(entry, newPosition)
            } catch (e: Exception) {
                Timber.e(e)
                mMainScope.launch {
                    context?.toastShort(R.string.msg_no_permission)
                }
            } finally {
                withContext(mMainScope.coroutineContext) {
                    mViewModel.queue.observe(
                        this@QueueFragment,
                        Observer { updateQueue(it) })
                }
            }
        }
    }
}

package me.iberger.enq.gui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter
import kotlinx.android.synthetic.main.fragment_queue.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.iberger.enq.R
import me.iberger.enq.gui.MainActivity.Companion.musicBot
import me.iberger.enq.gui.adapter.SuggestionsItem

open class ResultsFragment : Fragment() {

    val mUIScope = CoroutineScope(Dispatchers.Main)
    val mBackgroundScope = CoroutineScope(Dispatchers.IO)

    private lateinit var mFastItemAdapter: FastItemAdapter<SuggestionsItem>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_queue, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mFastItemAdapter = FastItemAdapter()
        queue.layoutManager = LinearLayoutManager(context)
        queue.adapter = mFastItemAdapter

        mFastItemAdapter.withOnClickListener { _, _, item, position ->
            mBackgroundScope.launch {
                musicBot.enqueue(item.song).await()
                withContext(Dispatchers.Main) {
                    mFastItemAdapter.remove(position)
                }
            }
            true
        }
    }

    fun displayResults(results: List<SuggestionsItem>) = mUIScope.launch { mFastItemAdapter.set(results) }
}
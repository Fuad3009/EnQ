/*
* Copyright 2019 Ivo Berger
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.ivoberger.enq.ui.fragments

import android.os.Bundle
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.ivoberger.enq.R
import com.ivoberger.enq.persistence.AppSettings
import com.ivoberger.enq.ui.MainActivity
import com.ivoberger.enq.ui.fragments.base.TabbedResultsFragment
import com.ivoberger.enq.utils.retryOnError
import com.ivoberger.jmusicbot.JMusicBot
import com.ivoberger.jmusicbot.listener.ConnectionListener
import com.ivoberger.jmusicbot.model.MusicBotPlugin
import kotlinx.android.synthetic.main.fragment_results.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import splitties.experimental.ExperimentalSplittiesApi
import splitties.lifecycle.coroutines.PotentialFutureAndroidXLifecycleKtxApi
import splitties.lifecycle.coroutines.lifecycleScope
import splitties.toast.toast
import timber.log.Timber

@PotentialFutureAndroidXLifecycleKtxApi
@ExperimentalSplittiesApi
class SearchFragment : TabbedResultsFragment(), ConnectionListener {

    private val mSearchView: SearchView by lazy { (activity as MainActivity).searchView }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mProviderPlugins = lifecycleScope.async { retryOnError { JMusicBot.getProvider() } }
        JMusicBot.connectionListeners.add(this@SearchFragment)
        lifecycleScope.launch(Dispatchers.IO) {
            mProviderPlugins.await() ?: return@launch
            AppSettings.lastProvider?.also {
                if (mProviderPlugins.await()!!.contains(it)) mSelectedPlugin = it
            }
        }

        mSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            private var oldQuery = ""
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query == oldQuery) return true
                query?.also {
                    oldQuery = it
                    search(it)
                }
                return true
            }

            override fun onQueryTextChange(newQuery: String?): Boolean {
                if (newQuery == oldQuery) return true
                newQuery?.also { oldQuery = it }
                // debounce
                lifecycleScope.launch(Dispatchers.IO) {
                    delay(300)
                    if (oldQuery != newQuery) return@launch
                    search(oldQuery)
                }
                return true
            }
        })
    }

    override fun initializeTabs() {
        lifecycleScope.launch {
            mProviderPlugins.await() ?: run {
                toast(R.string.msg_server_error)
                return@launch
            }
            mFragmentPagerAdapter = async(Dispatchers.Default) {
                SearchFragmentPager(childFragmentManager, mProviderPlugins.await()!!)
            }
            view_pager.adapter = mFragmentPagerAdapter.await()
        }
    }

    fun search(query: String) = lifecycleScope.launch {
        if (query.isNotBlank()) (mFragmentPagerAdapter.await() as SearchFragmentPager).search(query)
    }

    override fun onTabSelected(position: Int) {
        lifecycleScope.launch { mFragmentPagerAdapter.await().onTabSelected(position) }
    }

    override fun onConnectionLost(e: Exception?) {
        (activity as MainActivity).navController.navigateUp()
    }

    override fun onConnectionRecovered() {}

    override fun onDestroy() {
        super.onDestroy()
        AppSettings.lastProvider = mSelectedPlugin
        JMusicBot.connectionListeners.remove(this)
    }

    inner class SearchFragmentPager(fm: FragmentManager, provider: List<MusicBotPlugin>) :
        TabbedResultsFragment.SongListFragmentPager(fm, provider) {

        override fun getItem(position: Int): Fragment {
            val fragment = SearchResultsFragment.newInstance(provider[position].id)
            resultFragments.add(position, fragment)
            return fragment
        }

        fun search(query: String) {
            Timber.d("Searching for $query")
            resultFragments.forEach { (it as SearchResultsFragment).setQuery(query) }
            (resultFragments[view_pager.currentItem] as SearchResultsFragment).startSearch()
        }

        override fun onTabSelected(position: Int) {
            super.onTabSelected(position)
            (resultFragments[position] as SearchResultsFragment).startSearch()
        }
    }
}

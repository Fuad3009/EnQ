package me.iberger.enq.ui.listener

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.iberger.enq.R
import me.iberger.enq.ui.MainActivity
import me.iberger.jmusicbot.listener.ConnectionChangeListener

class ConnectionListener(private val mainActivity: MainActivity) : ConnectionChangeListener {
    override fun onConnectionLost(e: Exception) {
        mainActivity.mainScope.launch(Dispatchers.Main) {
            mainActivity.main_bottom_navigation.setBackgroundResource(R.color.red_500)
        }
    }

    override fun onConnectionRecovered() {
        mainActivity.mainScope.launch(Dispatchers.Main) {
            mainActivity.main_bottom_navigation.setBackgroundResource(R.color.background)
        }
    }
}

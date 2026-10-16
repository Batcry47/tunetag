package com.tunetag.player

import android.app.Application
import com.tunetag.player.data.HistoryDb
import com.tunetag.player.data.UserStore

class TuneTagApp : Application() {
    lateinit var store: UserStore
        private set
    val history: HistoryDb by lazy { HistoryDb(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = UserStore(this)
    }

    companion object {
        lateinit var instance: TuneTagApp
            private set
    }
}

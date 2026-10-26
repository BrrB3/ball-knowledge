package com.ballknowledge

import android.app.Application
import com.ballknowledge.data.EspnSource
import com.ballknowledge.data.Repository
import com.ballknowledge.data.Store

class BallKnowledgeApp : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(Store(filesDir), EspnSource())
    }
}

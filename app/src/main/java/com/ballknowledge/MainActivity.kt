package com.ballknowledge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ballknowledge.ui.App
import com.ballknowledge.ui.BallKnowledgeTheme

class MainActivity : ComponentActivity() {

    private val repo get() = (application as BallKnowledgeApp).repo

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        repo.refreshIfStale()
        setContent {
            BallKnowledgeTheme {
                App(repo)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        repo.flush()
    }
}

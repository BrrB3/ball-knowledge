package com.ballknowledge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ballknowledge.data.Repository

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Fixtures", Icons.Filled.DateRange),
    Tab("Archive", Icons.AutoMirrored.Filled.List),
    Tab("Transfers", AppIcons.Transfers),
    Tab("Settings", Icons.Filled.Settings),
)

@Composable
fun App(repo: Repository) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openPageId by rememberSaveable { mutableStateOf<String?>(null) }
    var openWindowId by rememberSaveable { mutableStateOf<String?>(null) }

    val windowId = openWindowId
    if (windowId != null) {
        BackHandler { repo.flush(); openWindowId = null }
        WindowScreen(repo, windowId, onBack = {
            repo.flush()
            openWindowId = null
        })
        return
    }

    val pageId = openPageId
    if (pageId != null) {
        BackHandler { repo.flush(); openPageId = null }
        MatchScreen(repo, pageId, onBack = {
            repo.flush()
            openPageId = null
        })
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> FixturesScreen(repo) { openPageId = it }
                1 -> ArchiveScreen(repo) { openPageId = it }
                2 -> TransfersScreen(repo) { openWindowId = it }
                else -> SettingsScreen(repo)
            }
        }
    }
}

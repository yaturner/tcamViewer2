package com.das.tcamviewer2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.das.tcamviewer2.ui.theme.TcamViewer2Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TcamViewer2Theme {
                MainScreen()
            }
        }
    }
}

// 1. Define an enum or data class to represent our distinct tabs
enum class ScreenTab(val title: String, val icon: ImageVector) {
    Camera("Camera", Icons.Filled.CameraAlt),
    Library("Library", Icons.Filled.PhotoLibrary),
    Settings("Settings", Icons.Filled.Settings)
}

@Composable
fun MainScreen() {
    // Track which tab index is selected (0, 1, or 2)
    var selectedTabItem by remember { mutableIntStateOf(0) }
    val tabs = ScreenTab.entries.toTypedArray()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTabItem == index,
                        onClick = { selectedTabItem = index },
                        label = { Text(tab.title) },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            // 2. Conditionally display the content based on state
            when (tabs[selectedTabItem]) {
                ScreenTab.Camera -> GreetingScreen(name = "Camera Screen")
                ScreenTab.Settings -> GreetingScreen(name = "Settings Screen")
                ScreenTab.Library -> GreetingScreen(name = "Library Screen")
            }
        }
    }
}

@Composable
fun GreetingScreen(name: String) {
    Text(
        text = "Hello $name!",
        fontSize = 24.sp
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    TcamViewer2Theme {
        MainScreen()
    }
}